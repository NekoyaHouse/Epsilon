package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.StartDestroyBlockEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.player.EnchantmentUtils;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.awt.*;
import java.util.TimerTask;

public class PacketMine extends Module {

    public static final PacketMine INSTANCE = new PacketMine();

    private PacketMine() {
        super("Packet Mine", Category.COMBAT);
    }

    private enum SwitchMode {
        None,
        Delay,
        Silent
    }

    private enum RenderMode {
        Box,
        Normal,
        Shrink,
        Grow,
    }

    private final BoolSetting usingPause = boolSetting("Pause On Use", true);
    private final BoolSetting onlyMain = boolSetting("Only Main", true, usingPause::getValue);
    private final EnumSetting<SwitchMode> switchMode = enumSetting("Switch Mode", SwitchMode.Silent);
    private final IntSetting range = intSetting("Range", 6, 0, 12, 1);
    private final IntSetting maxBreaks = intSetting("Try Break Time", 6, 0, 10, 1);
    private final BoolSetting farCancel = boolSetting("Far Cancel", true);
    private final BoolSetting swing = boolSetting("Swing Hand", true);
    private final BoolSetting instantMine = boolSetting("Instant Mine", true);
    private final IntSetting instantDelay = intSetting("Instant Delay", 10, 0, 1000, 10);
    private final BoolSetting fastBypass = boolSetting("Fast Bypass", true);
    private final BoolSetting doubleBreak = boolSetting("Double Break", false);
    private final BoolSetting checkGround = boolSetting("Check Ground", true);
    private final BoolSetting bypassGround = boolSetting("Bypass Ground", false);
    private final BoolSetting clientRemove = boolSetting("Client Remove", true);
    private final IntSetting switchDamage = intSetting("Switch Damage", 95, 0, 100, 1);
    private final IntSetting switchTime = intSetting("Switch Time", 100, 0, 1000, 10);
    private final IntSetting mineDelay = intSetting("Mine Delay", 300, 0, 1000, 10);
    private final IntSetting packetDelay = intSetting("Packet Delay", 200, 0, 1000, 10);
    private final DoubleSetting mineDamage = doubleSetting("Damage", 0.8, 0.0, 2.0, 0.05);

    private final EnumSetting<RenderMode> renderMode = enumSetting("Render Mode", RenderMode.Shrink);
    private final BoolSetting fading = boolSetting("Fading", true);
    private final DoubleSetting renderTime = doubleSetting("Render Time", 0.1, 0.0, 5, 0.1, fading::getValue);
    private final DoubleSetting fadeTime = doubleSetting("Fade Time", 0.2, 0.0, 5, 0.1, fading::getValue);
    private final ColorSetting fadeSideColor = colorSetting("Fade Side Color", new Color(70, 200, 155, 31));
    private final ColorSetting fadeLineColor = colorSetting("Fade Line Color", new Color(70, 200, 155, 233));
    private final ColorSetting sideStartColor = colorSetting("Side Start", new Color(255, 0, 0, 31));
    private final ColorSetting sideEndColor = colorSetting("Side End", new Color(0, 150, 10, 31));
    private final ColorSetting lineStartColor = colorSetting("Line Start", new Color(255, 0, 0, 233));
    private final ColorSetting lineEndColor = colorSetting("Line End", new Color(5, 160, 0, 233));
    private final ColorSetting secondSideStartColor = colorSetting("Second Side Start", new Color(255, 0, 0, 31));
    private final ColorSetting secondSideEndColor = colorSetting("Second Side End", new Color(0, 150, 10, 31));
    private final ColorSetting secondLineStartColor = colorSetting("Second Line Start", new Color(255, 0, 0, 233));
    private final ColorSetting secondLineEndColor = colorSetting("Second Line End", new Color(5, 160, 0, 233));

    public static BlockPos selfClickPos = null;
    public static int maxBreaksCount;
    public static int mainProgressPercent = 0, secondProgressPercent = 0;
    public static boolean completed = false;
    public static BlockPos targetPos, secondPos;
    private static float progress, secondProgress;
    private long lastTime, secondLastTime;
    private long fadeLastTime;
    private static boolean started, secondStarted;
    private BlockPos renderPos, secondRenderPos;
    private double renderProgress, secondRenderProgress;
    private int oldSlot = -1;
    private boolean hasSwitch = false, secondHasSwitch = false;

    private final TimerUtils bypassTimer = new TimerUtils();
    private final TimerUtils timer = new TimerUtils();
    private final TimerUtils secondTimer = new TimerUtils();
    public final TimerUtils mineTimer = new TimerUtils();
    private final TimerUtils instantTimer = new TimerUtils();

    @Override
    protected void onEnable() {
        maxBreaksCount = 0;
        hasSwitch = false;
        secondHasSwitch = false;
        bypassTimer.setMs(917813L);
        mineTimer.setMs(917813L);
        instantTimer.setMs(917813L);
        timer.setMs(917813L);
        secondTimer.setMs(917813L);
        targetPos = null;
        secondPos = null;
        started = false;
        secondStarted = false;
        mainProgressPercent = 0;
        secondProgressPercent = 0;
        progress = 0;
        secondProgress = 0;
        lastTime = System.currentTimeMillis();
        secondLastTime = System.currentTimeMillis();
        fadeLastTime = System.currentTimeMillis();
        renderPos = null;
        secondRenderPos = null;
        renderProgress = 0;
        secondRenderProgress = 0;
    }

    @Override
    protected void onDisable() {
        if (hasSwitch) {
            InvUtils.swap(oldSlot, false);
            hasSwitch = false;
        }
        if (secondHasSwitch) {
            InvUtils.swap(oldSlot, false);
            secondHasSwitch = false;
        }
    }

    @EventHandler
    private void onStartBreakingBlock(StartDestroyBlockEvent event) {
        if (!canBreak(event.getBlockPos())) return;
        event.cancel();
        if (!mineTimer.passedMillise(mineDelay.getValue())) return;
        selfClickPos = event.getBlockPos();
        mine(event.getBlockPos());
    }

    public boolean isInstantMining(BlockPos pos) {
        if (!isEnabled() || !instantMine.getValue() || pos == null) return false;
        if (!completed || targetPos == null || !targetPos.equals(pos)) return false;
        BlockState state = mc.level.getBlockState(pos);
        return !state.isAir() && !state.canBeReplaced();
    }

    public void mine(BlockPos pos) {
        mineTimer.reset();
        maxBreaksCount = 0;
        if (doubleBreak.getValue()) {
            if (targetPos != null && secondPos == null && !targetPos.equals(pos)) {
                if (completed) {
                    targetPos = pos;
                    secondStarted = false;
                    secondProgress = 0;
                    secondProgressPercent = 0;
                    mainProgressPercent = 0;
                    started = false;
                    progress = 0;
                    completed = false;
                } else {
                    secondPos = targetPos;
                    targetPos = pos;
                    secondStarted = false;
                    secondProgress = 0;
                    secondProgressPercent = 0;
                    started = false;
                }
            } else if (targetPos == null || !targetPos.equals(pos)) {
                mainProgressPercent = 0;
                targetPos = pos;
                started = false;
                progress = 0;
                completed = false;
            }
        } else {
            if (!pos.equals(targetPos)) {
                mainProgressPercent = 0;
                targetPos = pos;
                started = false;
                progress = 0;
                completed = false;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        long now = System.currentTimeMillis();
        double fadeDelta = (now - fadeLastTime) / 1000d;
        fadeLastTime = now;

        if (targetPos == null && secondPos == null) selfClickPos = null;
        if (mainProgressPercent >= 100) {
            if (!instantMine.getValue()) targetPos = null;
        }
        if (secondProgressPercent >= 100) {
            secondPos = null;
        }
        if (timer.passedMillise(switchTime.getValue()) && hasSwitch && switchMode.getValue() != SwitchMode.None) {
            if (switchMode.is(SwitchMode.Delay)) {
                InvUtils.swap(oldSlot, false);
            } else if (switchMode.is(SwitchMode.Silent)) {
                mc.getConnection().send(new ServerboundSetCarriedItemPacket(oldSlot));
            }
            hasSwitch = false;
        }
        if (maxBreaksCount >= maxBreaks.getValue() * 10) {
            maxBreaksCount = 0;
            targetPos = null;
        }
        if (targetPos != null) {
            renderPos = targetPos;
        }
        if (secondPos != null) {
            secondRenderPos = secondPos;
        }

        updateFadeProgress(fadeDelta);
        renderFadeBoxes(event.getPoseStack());

        if (secondPos != null && doubleBreak.getValue()) {
            if (farCancel.getValue() && Math.sqrt(mc.player.getEyePosition().distanceToSqr(secondPos.getCenter())) > range.getValue()) {
                secondPos = null;
                return;
            }
            double secondMax = getMineTicksSecond(getTool(secondPos));
            double secondDelta = (System.currentTimeMillis() - secondLastTime) / 1000d;
            secondProgressPercent = (int) (secondProgress / (secondMax * mineDamage.getValue()) * 100);
            secondLastTime = System.currentTimeMillis();
            if (!secondStarted) {
                sendStart(secondPos);
                secondStarted = true;
                secondProgress = 0;
                return;
            }
            Double secondDamage = mineDamage.getValue();
            if (!checkGround.getValue() || mc.player.onGround()) {
                secondProgress += (float) (secondDelta * 20);
            } else if (checkGround.getValue() && !mc.player.onGround()) {
                secondProgress += (float) (secondDelta * 4);
            }
            secondBlockRender(event.getPoseStack());
            if (secondProgress >= secondMax * secondDamage) {
                sendStopSecond();
            }
        }
        if (
                doubleBreak.getValue()
                        && (!usingPause.getValue() || !checkPause(onlyMain.getValue()))
                        && ((secondProgressPercent >= switchDamage.getValue() || mainProgressPercent >= switchDamage.getValue()) && !hasSwitch && secondPos != null)
        ) {
            int bestSlot = getTool(secondPos);
            if (!hasSwitch) oldSlot = mc.player.getInventory().getSelectedSlot();
            if (!switchMode.is(SwitchMode.None) && bestSlot != -1) {
                if (switchMode.is(SwitchMode.Delay)) {
                    InvUtils.swap(bestSlot, false);
                } else if (switchMode.is(SwitchMode.Silent)) {
                    mc.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
                }
                timer.reset();
                hasSwitch = true;
            }
        }
        if (targetPos != null) {
            if (farCancel.getValue() && Math.sqrt(mc.player.getEyePosition().distanceToSqr(targetPos.getCenter())) > range.getValue()) {
                targetPos = null;
                return;
            }
            double max = getMineTicks(getTool(targetPos));
            mainProgressPercent = (int) (progress / (max * mineDamage.getValue()) * 100);
            if (progress >= max * mineDamage.getValue() && completed) {
                if (isAir(targetPos) || mc.level.getBlockState(targetPos).canBeReplaced()) maxBreaksCount = 0;
                if (!isAir(targetPos) && !mc.level.getBlockState(targetPos).canBeReplaced() && !(usingPause.getValue() && checkPause(onlyMain.getValue()))) {
                    maxBreaksCount++;
                }
            }
            if (instantMine.getValue() && completed) {
                Color color1 = progress >= 0.95 ? sideEndColor.getValue() : sideStartColor.getValue();
                Color color2 = progress >= 0.95 ? lineEndColor.getValue() : lineStartColor.getValue();

                AABB box = new AABB(targetPos);
                Render3DScheduler.INSTANCE.addFilledBox(box, color1);
                Render3DScheduler.INSTANCE.addOutlineBox(box, color2);

                if (!mc.level.getBlockState(targetPos).isAir() && !mc.level.getBlockState(targetPos).canBeReplaced() && instantTimer.passedMillise(instantDelay.getValue())) {
                    sendStop();
                    instantTimer.reset();
                }
                return;
            }
            double delta = (System.currentTimeMillis() - lastTime) / 1000d;
            lastTime = System.currentTimeMillis();
            if (!started) {
                sendStart(targetPos);
                return;
            }
            Double damage = mineDamage.getValue();
            if (!checkGround.getValue() || mc.player.onGround()) {
                progress += (float) (delta * 20);
            } else if (checkGround.getValue() && !mc.player.onGround()) {
                progress += (float) (delta * 4);
            }
            mainBlockRender(event.getPoseStack());
            if (progress >= max * damage) {
                sendStop();
                completed = true;
                if (!instantMine.getValue() && secondPos == null) {
                    targetPos = null;
                }
            }
        }
    }

    private void sendStart(BlockPos pos) {
        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, RotationUtils.getDirection(pos)));
        if (fastBypass.getValue()) {
            BlockPos bypassPos = BlockPos.containing(mc.player.getX(), 321, mc.player.getZ());
            mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, bypassPos, Direction.DOWN, mc.level.getBlockStatePredictionHandler().startPredicting().currentSequence()));
        }
        if (doubleBreak.getValue()) {
            long delay = packetDelay.getValue();
            java.util.Timer timer = new java.util.Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mc.execute(() -> {
                        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, RotationUtils.getDirection(pos)));
                    });
                    timer.cancel();
                }
            }, delay);
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (pos.equals(targetPos)) {
            started = true;
            progress = 0;
        } else {
            secondStarted = true;
            secondProgress = 0;
        }
    }

    private void sendStop() {
        if (usingPause.getValue() && checkPause(onlyMain.getValue())) {
            return;
        }
        if (!doubleBreak.getValue() || secondPos == null) {
            int bestSlot = getTool(targetPos);
            if (!hasSwitch) oldSlot = mc.player.getInventory().getSelectedSlot();
            if (switchMode.getValue() != SwitchMode.None && bestSlot != -1) {
                if (switchMode.is(SwitchMode.Delay)) {
                    InvUtils.swap(bestSlot, false);
                }
                if (switchMode.is(SwitchMode.Silent)) {
                    mc.getConnection().send(new ServerboundSetCarriedItemPacket(bestSlot));
                }
                timer.reset();
                hasSwitch = true;
            }
        }
        if (bypassGround.getValue() && !mc.player.isFallFlying() && targetPos != null && !isAir(targetPos) && !mc.player.onGround()) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY() + 1.0e-9, mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), true, mc.player.horizontalCollision));
            mc.player.resetFallDistance();
        }
        if (swing.getValue()) mc.player.swing(InteractionHand.MAIN_HAND);
        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, targetPos, RotationUtils.getDirection(targetPos), mc.level.getBlockStatePredictionHandler().startPredicting().currentSequence()));
        if (clientRemove.getValue() && targetPos != null && !isAir(targetPos)) {
            mc.gameMode.destroyBlock(targetPos);
        }
    }

    private void sendStopSecond() {
        if (bypassGround.getValue() && !mc.player.isFallFlying() && secondPos != null && !isAir(secondPos) && !mc.player.onGround()) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY() + 1.0e-9, mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), true, mc.player.horizontalCollision));
            mc.player.resetFallDistance();
        }
        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        if (clientRemove.getValue() && secondPos != null && !isAir(secondPos)) {
            mc.gameMode.destroyBlock(secondPos);
        }
    }

    private float getMineTicks(int slot) {
        if (targetPos == null) return 20;
        BlockState state = mc.level.getBlockState(targetPos);
        float hardness = state.getDestroySpeed(mc.level, targetPos);
        if (hardness < 0) return Float.MAX_VALUE;
        if (hardness == 0) return 1;
        ItemStack stack = slot == -1 ? ItemStack.EMPTY : mc.player.getInventory().getItem(slot);
        boolean canHarvest = stack.isCorrectToolForDrops(state);
        float speed = stack.getDestroySpeed(state);
        int efficiency = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0 && speed > 1.0f) {
            speed += efficiency * efficiency + 1;
        }
        if (mc.player.hasEffect(MobEffects.HASTE)) {
            int amp = mc.player.getEffect(MobEffects.HASTE).getAmplifier();
            speed *= 1.0f + (amp + 1) * 0.2f;
        }
        if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
            int amp = mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier();
            speed *= switch (amp) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }
        float damage = speed / hardness / (canHarvest ? 30f : 100f);
        if (damage <= 0) return Float.MAX_VALUE;
        return 1f / damage;
    }

    private float getMineTicksSecond(int slot) {
        if (secondPos == null) return 20;
        BlockState state = mc.level.getBlockState(secondPos);
        float hardness = state.getDestroySpeed(mc.level, secondPos);
        if (hardness < 0) return Float.MAX_VALUE;
        if (hardness == 0) return 1;
        ItemStack stack = slot == -1 ? ItemStack.EMPTY : mc.player.getInventory().getItem(slot);
        boolean canHarvest = stack.isCorrectToolForDrops(state);
        float speed = stack.getDestroySpeed(state);
        int efficiency = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        if (efficiency > 0 && speed > 1.0f) {
            speed += efficiency * efficiency + 1;
        }
        if (mc.player.hasEffect(MobEffects.HASTE)) {
            int amp = mc.player.getEffect(MobEffects.HASTE).getAmplifier();
            speed *= 1.0f + (amp + 1) * 0.2f;
        }
        if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
            int amp = mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier();
            speed *= switch (amp) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
        }
        float damage = speed / hardness / (canHarvest ? 30f : 100f);
        if (damage <= 0) return Float.MAX_VALUE;
        return 1f / damage;
    }

    private boolean isAir(BlockPos breakPos) {
        return mc.level.getBlockState(breakPos).isAir() || mc.level.getBlockState(breakPos).getBlock() == Blocks.FIRE && hasCrystal(breakPos);
    }

    private boolean isSolidForFade(BlockPos pos) {
        if (pos == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        return !isAir(pos) && !state.canBeReplaced();
    }

    private boolean hasCrystal(BlockPos pos) {
        for (Entity entity : mc.level.getEntities(null, new AABB(pos))) {
            if (entity instanceof EndCrystal endCrystal && endCrystal.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private int getTool(BlockPos pos) {
        int index = -1;
        float CurrentFastest = 1.0f;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != ItemStack.EMPTY) {
                final float digSpeed = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
                final float destroySpeed = stack.getDestroySpeed(mc.level.getBlockState(pos));
                if (digSpeed + destroySpeed > CurrentFastest) {
                    CurrentFastest = digSpeed + destroySpeed;
                    index = i;
                }
            }
        }
        return index;
    }

    private boolean checkPause(boolean onlyMain) {
        return mc.options.keyUse.isDown() && (!onlyMain || mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND);
    }

    private boolean canBreak(BlockPos blockPos) {
        BlockState state = mc.level.getBlockState(blockPos);
        if (!mc.player.isCreative() && state.getDestroySpeed(mc.level, blockPos) < 0) return false;
        return state.getCollisionShape(mc.level, blockPos) != Shapes.empty();
    }

    private void updateFadeProgress(double delta) {
        if (!fading.getValue()) {
            renderProgress = 0;
            secondRenderProgress = 0;
            return;
        }

        boolean paused = usingPause.getValue() && checkPause(onlyMain.getValue());
        if (isSolidForFade(renderPos) && !paused) {
            renderProgress = fadeTime.getValue() + renderTime.getValue();
        } else {
            renderProgress = Math.max(0, renderProgress - delta);
        }

        if (isSolidForFade(secondRenderPos) && !paused) {
            secondRenderProgress = fadeTime.getValue() + renderTime.getValue();
        } else {
            secondRenderProgress = Math.max(0, secondRenderProgress - delta);
        }
    }

    private void renderFadeBoxes(PoseStack stack) {
        if (fading.getValue()) {
            renderFadeBox(stack, renderPos, renderProgress);
            renderFadeBox(stack, secondRenderPos, secondRenderProgress);
        }
    }

    private void renderFadeBox(PoseStack stack, BlockPos pos, double progress) {
        if (pos == null || progress <= 0 || isSolidForFade(pos)) return;

        Color color1 = new Color(fadeSideColor.getValue().getRed(),
                fadeSideColor.getValue().getGreen(),
                fadeSideColor.getValue().getBlue(),
                (int) Math.round(fadeSideColor.getValue().getAlpha() * Math.min(1, progress / fadeTime.getValue()))
        );
        Color color2 = new Color(fadeLineColor.getValue().getRed(),
                fadeLineColor.getValue().getGreen(),
                fadeLineColor.getValue().getBlue(),
                (int) Math.round(fadeLineColor.getValue().getAlpha() * Math.min(1, progress / fadeTime.getValue()))
        );
        AABB box = new AABB(pos);
        Render3DScheduler.INSTANCE.addFilledBox(box, color1);
        Render3DScheduler.INSTANCE.addOutlineBox(box, color2);
    }

    private void mainBlockRender(PoseStack stack) {
        if (targetPos == null) return;

        double max = getMineTicks(getTool(targetPos));
        double rawProgress = Mth.clamp(progress / (max * mineDamage.getValue()), 0.0, 1.0);

        Color color1 = rawProgress >= 0.95 ? sideEndColor.getValue() : sideStartColor.getValue();
        Color color2 = rawProgress >= 0.95 ? lineEndColor.getValue() : lineStartColor.getValue();

        switch (renderMode.getValue()) {
            case Box -> {
                AABB box = new AABB(targetPos);
                Render3DScheduler.INSTANCE.addFilledBox(box, color1);
                Render3DScheduler.INSTANCE.addOutlineBox(box, color2);
            }
            case Normal -> {
                AABB box = AABB.ofSize(Vec3.atCenterOf(targetPos), rawProgress, rawProgress, rawProgress);
                Render3DScheduler.INSTANCE.addFilledBox(box, color1);
                Render3DScheduler.INSTANCE.addOutlineBox(box, color2);
            }
            case Grow -> {
                AABB box = new AABB(targetPos).setMaxY(targetPos.getY() + rawProgress);
                Render3DScheduler.INSTANCE.addFilledBox(box, color1);
                Render3DScheduler.INSTANCE.addOutlineBox(box, color2);
            }
            case Shrink -> {
                double maxBound = Math.round(rawProgress * 100.0) / 100.0;
                AABB box = new AABB(targetPos).deflate(1.0 - maxBound);
                Render3DScheduler.INSTANCE.addFilledBox(box, color1);
                Render3DScheduler.INSTANCE.addOutlineBox(box, color2);
            }
        }

    }

    private void secondBlockRender(PoseStack stack) {
        if (secondPos == null) return;

        double max = getMineTicksSecond(getTool(secondPos));
        double rawProgress = Mth.clamp(secondProgress / (max * mineDamage.getValue()), 0.0, 1.0);

        Color color1 = rawProgress >= 0.95 ? secondSideEndColor.getValue() : secondSideStartColor.getValue();
        Color color2 = rawProgress >= 0.95 ? secondLineEndColor.getValue() : secondLineStartColor.getValue();

        switch (renderMode.getValue()) {
            case Box -> {
                AABB box = new AABB(secondPos);
                Render3DScheduler.INSTANCE.addFilledBox(box, color1);
                Render3DScheduler.INSTANCE.addOutlineBox(box, color2);
            }
            case Normal -> {
                AABB box = AABB.ofSize(Vec3.atCenterOf(secondPos), rawProgress, rawProgress, rawProgress);
                Render3DScheduler.INSTANCE.addFilledBox(box, color1);
                Render3DScheduler.INSTANCE.addOutlineBox(box, color2);
            }
            case Grow -> {
                AABB box = new AABB(secondPos).setMaxY(secondPos.getY() + rawProgress);
                Render3DScheduler.INSTANCE.addFilledBox(box, color1);
                Render3DScheduler.INSTANCE.addOutlineBox(box, color2);
            }
            case Shrink -> {
                double maxBound = Math.round(rawProgress * 100.0) / 100.0;
                AABB box = new AABB(secondPos).deflate(1.0 - maxBound);
                Render3DScheduler.INSTANCE.addFilledBox(box, color1);
                Render3DScheduler.INSTANCE.addOutlineBox(box, color2);
            }
        }
    }

}
