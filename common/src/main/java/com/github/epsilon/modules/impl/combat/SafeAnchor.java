package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.ExtrapolationManager;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.combat.DamageUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SafeAnchor extends Module {

    public static final SafeAnchor INSTANCE = new SafeAnchor();

    private SafeAnchor() {
        super("Safe Anchor", Category.COMBAT);
    }

    private enum Mode {Assist, Auto}

    private enum PlaceMode {Adaptive, Cover}

    private enum Order {ShieldFirst, ChargeFirst}

    private enum Stage {
        IDLE, PLACE_ANCHOR, PLACE_SHIELD, CHARGE, DETONATE, WAIT_SHIELD, WAIT_ANCHOR
    }

    private record RenderBox(BlockPos pos, long startTime) {
    }

    private final KeybindSetting triggerKey = keybindSetting("Trigger Key", -1);

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Auto);
    private final EnumSetting<PlaceMode> placeMode = enumSetting("Place Mode", PlaceMode.Adaptive);
    private final EnumSetting<Order> order = enumSetting("Order", Order.ShieldFirst);

    private final BoolSetting rotate = boolSetting("Rotate", true);
    private final IntSetting rotationSpeedMin = intSetting("Rotation Speed Min", 15, 1, 360, 1);
    private final IntSetting rotationSpeedMax = intSetting("Rotation Speed Max", 30, 1, 360, 1);
    private final BoolSetting strict = boolSetting("Strict", true);
    private final BoolSetting breakCrystal = boolSetting("Break Crystal", false);
    private final BoolSetting swapBack = boolSetting("SwapBack", true);

    private final DoubleSetting anchorPlaceMinDelay = doubleSetting("Anchor Place Min Delay", 0, 0, 50, 0.1);
    private final DoubleSetting anchorPlaceMaxDelay = doubleSetting("Anchor Place Max Delay", 2.5, 0, 50, 0.1);

    private final SettingGroup sgShield = settingGroup("Shield");
    private final DoubleSetting shieldPlaceMinDelay = doubleSetting("Shield Place Min Delay", 0, 0, 50, 0.1).group(sgShield);
    private final DoubleSetting shieldPlaceMaxDelay = doubleSetting("Shield Place Max Delay", 2.5, 0, 50, 0.1).group(sgShield);
    private final BoolSetting multiShield = boolSetting("Multi Shield", false, () -> mode.getValue() == Mode.Auto).group(sgShield);
    private final IntSetting multiShieldChance = intSetting("Multi Shield Chance", 50, 0, 100, 1, multiShield::getValue).group(sgShield);
    private final IntSetting maxExtraShields = intSetting("Max Extra Shields", 1, 1, 5, 1, multiShield::getValue).group(sgShield);
    private final IntSetting shieldDistanceMin = intSetting("Shield Distance Min", 1, 1, 3, 1, () -> mode.getValue() == Mode.Auto).group(sgShield);
    private final IntSetting shieldDistanceMax = intSetting("Shield Distance Max", 1, 1, 3, 1, () -> mode.getValue() == Mode.Auto).group(sgShield);

    private final DoubleSetting chargeMinDelay = doubleSetting("Charge Min Delay", 0, 0, 50, 0.1);
    private final DoubleSetting chargeMaxDelay = doubleSetting("Charge Max Delay", 2.5, 0, 50, 0.1);

    private final DoubleSetting detonateMinDelay = doubleSetting("Detonate Min Delay", 0, 0, 50, 0.1);
    private final DoubleSetting detonateMaxDelay = doubleSetting("Detonate Max Delay", 2.5, 0, 50, 0.1);
    private final IntSetting detonateSlot = intSetting("Detonate Slot", 1, 1, 9, 1);
    private final DoubleSetting minHealth = doubleSetting("Min Health", 4.0, 0.0, 20.0, 0.5);

    public final BoolSetting prediction = boolSetting("Prediction", false);
    public final DoubleSetting predictionStrength = doubleSetting("Prediction Strength", 3.0, 0.5, 20.0, 0.1, () -> prediction.getValue());

    private final BoolSetting multiCharge = boolSetting("Multi Charge", false);
    private final IntSetting multiChargeChance = intSetting("Multi Charge Chance", 50, 0, 100, 1, multiCharge::getValue);
    private final IntSetting minExtraCharges = intSetting("Min Extra Charges", 1, 1, 3, 1, multiCharge::getValue);
    private final IntSetting maxExtraCharges = intSetting("Max Extra Charges", 1, 1, 3, 1, multiCharge::getValue);

    private final BoolSetting render = boolSetting("Render", true);

    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 255, 255, 255), render::getValue);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 255, 255, 50), render::getValue);
    private final DoubleSetting lineWidth = doubleSetting("Line Width", 1.0, 0.0, 5.0, 0.1, render::getValue);

    private Stage stage = Stage.IDLE;
    private BlockPos currentAnchorPos;
    private BlockPos shieldPos;
    private Direction shieldSide;
    private boolean shieldSidePlacement;
    private boolean shieldDiagonal;
    private Rot2f targetRotation;
    private BlockPos targetBlockPos;
    private Rot2f originalRotation;
    private boolean returningRotation;
    private int originalSlot = -1;
    private BlockPos pendingAnchorPos;
    private BlockHitResult pendingAnchorHit;
    private int stageTicksElapsed;
    private boolean wasKeyDown;
    private boolean holdingSequenceStarted;
    private int cooldownMs;
    private int chargesRemaining = -1;
    private final List<BlockPos> placedShields = new ArrayList<>();
    private final List<RenderBox> renderBoxes = new ArrayList<>();
    private final TimerUtils actionTimer = new TimerUtils();

    private static final int STAGE_TIMEOUT_TICKS = 60;

    @Override
    protected void onEnable() {
        resetState();
        wasKeyDown = false;
        holdingSequenceStarted = false;
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || mc.gui.screen() != null) return;

        int key = triggerKey.getValue();
        if (key == -1) return;

        boolean keyDown = KeybindUtils.isPressed(key);
        boolean newPress = keyDown && !wasKeyDown;
        wasKeyDown = keyDown;

        if (stage == Stage.IDLE && !keyDown) {
            holdingSequenceStarted = false;
        }

        if (newPress || (keyDown && stage == Stage.IDLE && holdingSequenceStarted)) {
            abortConflicting();
            resetState();
            holdingSequenceStarted = true;
            originalSlot = mc.player.getInventory().getSelectedSlot();
            originalRotation = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            stage = Stage.PLACE_ANCHOR;
            scheduleCooldown(anchorPlaceMinDelay, anchorPlaceMaxDelay);
        }

        if (stage == Stage.IDLE) return;

        while (stage != Stage.IDLE && actionTimer.passedMillise(cooldownMs)) {
            if (stage != Stage.WAIT_SHIELD && stage != Stage.WAIT_ANCHOR) {
                stageTicksElapsed++;
                if (stageTicksElapsed > STAGE_TIMEOUT_TICKS) {
                    resetState();
                    return;
                }
            }

            if (currentAnchorPos != null && stage != Stage.PLACE_ANCHOR
                    && !mc.level.getBlockState(currentAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                resetState();
                return;
            }

            applyTargetRotation();

            if (returningRotation) {
                if (rotationAimed()) {
                    targetRotation = null;
                    returningRotation = false;
                    resetState();
                }
                return;
            }

            if (targetRotation != null && !rotationAimed()) {
                return;
            }

            switch (stage) {
                case PLACE_ANCHOR -> doPlaceAnchor();
                case PLACE_SHIELD -> doPlaceShield();
                case CHARGE -> doCharge();
                case DETONATE -> doDetonate();
                case WAIT_SHIELD -> {
                    doWaitShield();
                    if (stage == Stage.WAIT_SHIELD) return;
                }
                case WAIT_ANCHOR -> {
                    doWaitAnchor();
                    if (stage == Stage.WAIT_ANCHOR) return;
                }
                default -> {
                    return;
                }
            }
        }
    }

    private void doPlaceAnchor() {
        if (breakCrystal.getValue() && DamageUtils.breakCrosshairCrystal()) {
            scheduleCooldown(anchorPlaceMinDelay, anchorPlaceMaxDelay);
            return;
        }

        if (pendingAnchorPos == null) {
            HitResult hit = mc.hitResult;
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
                resetState();
                return;
            }

            BlockPos hitPos = blockHit.getBlockPos();
            if (mc.level.getBlockState(hitPos).is(Blocks.RESPAWN_ANCHOR)) {
                currentAnchorPos = hitPos;
                targetRotation = null;
                targetBlockPos = null;
                advanceFromPlaceAnchor();
                return;
            }

            BlockState hitState = mc.level.getBlockState(hitPos);
            ItemStack anchorStack = Items.RESPAWN_ANCHOR.getDefaultInstance();
            BlockPos placePos = !hitState.isAir() && BlockUtils.canBeReplacedWith(hitState, anchorStack)
                    ? hitPos
                    : hitPos.relative(blockHit.getDirection());
            if (!BlockUtils.isWithinRange(Vec3.atCenterOf(placePos))
                    || !canPlaceAnchor(placePos, anchorStack)) {
                resetState();
                return;
            }

            pendingAnchorPos = placePos;
            pendingAnchorHit = blockHit;

            if (rotate.getValue()) {
                targetRotation = getTargetRotation(pendingAnchorHit.getLocation());
                targetBlockPos = pendingAnchorPos;
                return;
            }
        }

        if (rotate.getValue()) {
            if (!rotationAimed()) return;
        }

        FindItemResult anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
        if (!anchor.found()) {
            resetState();
            return;
        }

        InvUtils.swap(anchor.slot(), false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, pendingAnchorHit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        currentAnchorPos = pendingAnchorPos;
        targetRotation = null;
        targetBlockPos = null;
        pendingAnchorPos = null;
        pendingAnchorHit = null;
        addRenderBox(currentAnchorPos);
        advanceFromPlaceAnchor();
    }

    private boolean canPlaceAnchor(BlockPos pos, ItemStack item) {
        if (pos == null) return false;
        if (!BlockUtils.canBeReplacedWith(mc.level.getBlockState(pos), item)) return false;
        return !BlockUtils.hasBlockingEntity(new AABB(pos));
    }

    private void doPlaceShield() {
        shieldPos = null;

        if (hasExistingShield() && !shouldPlaceExtraShield()) {
            targetRotation = null;
            targetBlockPos = null;
            advanceFromPlaceShield();
            return;
        }

        shieldPos = findShieldPos();
        if (shieldPos == null) {
            shieldPos = findFallbackShieldPos();
        }
        if (shieldPos == null) {
            if (!placedShields.isEmpty()) {
                targetRotation = null;
                targetBlockPos = null;
                advanceFromPlaceShield();
            } else if (isExplosionSafe()) {
                targetRotation = null;
                targetBlockPos = null;
                scheduleCooldown(chargeMinDelay, chargeMaxDelay);
                stage = Stage.CHARGE;
            } else {
                abortSequence();
            }
            return;
        }

        FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
        if (!glowstone.found()) {
            abortSequence();
            return;
        }

        InvUtils.swap(glowstone.slot(), false);

        Vec3 shieldInteractPos = shieldSidePlacement && shieldSide != null
                ? Vec3.atCenterOf(currentAnchorPos)
                : Vec3.atCenterOf(shieldPos.below());
        if (!BlockUtils.isWithinRange(shieldInteractPos)) {
            abortSequence();
            return;
        }

        if (rotate.getValue()) {
            if (targetRotation == null) {
                Vec3 lookTarget = shieldSidePlacement && shieldSide != null
                        ? Vec3.atCenterOf(currentAnchorPos).add(
                        shieldSide.getStepX() * 0.5,
                        shieldSide.getStepY() * 0.5,
                        shieldSide.getStepZ() * 0.5)
                        : Vec3.atCenterOf(shieldPos.below());
                targetRotation = getTargetRotation(lookTarget);
                targetBlockPos = shieldSidePlacement && shieldSide != null ? currentAnchorPos : shieldPos.below();
                return;
            }
            if (!rotationAimed()) return;
        }

        if (shieldSidePlacement && shieldSide != null) {
            setShiftState(true);
            if (!placeAtAvoiding(shieldPos, currentAnchorPos)) {
                setShiftState(false);
                BlockPos fallback = findFallbackShieldPos();
                if (fallback == null) {
                    abortSequence();
                    return;
                }
                shieldPos = fallback;
                shieldSidePlacement = false;
                if (!BlockUtils.isWithinRange(Vec3.atCenterOf(shieldPos.below()))) {
                    abortSequence();
                    return;
                }
                if (rotate.getValue()) {
                    targetRotation = getTargetRotation(Vec3.atCenterOf(shieldPos.below()));
                    targetBlockPos = shieldPos.below();
                    return;
                }
            }
            setShiftState(false);
        }
        interactBlock(shieldPos.below(), Direction.UP);

        placedShields.add(shieldPos);
        addRenderBox(shieldPos);
        targetRotation = null;
        targetBlockPos = null;

        if (shouldPlaceExtraShield()) {
            shieldPos = null;
            scheduleCooldown(shieldPlaceMinDelay, shieldPlaceMaxDelay);
            return;
        }

        shieldPos = null;
        advanceFromPlaceShield();
    }

    private void doCharge() {
        BlockState state = mc.level.getBlockState(currentAnchorPos);
        if (!state.is(Blocks.RESPAWN_ANCHOR)) {
            abortSequence();
            return;
        }

        if (state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
            advanceFromCharge();
            return;
        }

        if (!BlockUtils.isWithinRange(Vec3.atCenterOf(currentAnchorPos))) {
            abortSequence();
            return;
        }

        if (chargesRemaining < 0) {
            chargesRemaining = calculateChargeActions(state);
        }

        if (chargesRemaining <= 0) {
            scheduleCooldown(detonateMinDelay, detonateMaxDelay);
            stage = Stage.DETONATE;
            return;
        }

        if (rotate.getValue()) {
            if (targetRotation == null) {
                targetRotation = getTargetRotation(Vec3.atCenterOf(currentAnchorPos));
                targetBlockPos = currentAnchorPos;
                return;
            }
            if (!rotationAimed()) return;
        }

        FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
        if (!glowstone.found()) {
            abortSequence();
            return;
        }

        InvUtils.swap(glowstone.slot(), false);
        targetRotation = null;
        targetBlockPos = null;
        interactBlock(currentAnchorPos, BlockUtils.getVisibleFace(currentAnchorPos));
        chargesRemaining--;

        if (chargesRemaining > 0) {
            scheduleCooldown(chargeMinDelay, chargeMaxDelay);
            stage = Stage.CHARGE;
        } else {
            advanceFromCharge();
        }
    }

    private int calculateChargeActions(BlockState state) {
        int currentCharge = state.getValue(RespawnAnchorBlock.CHARGE);
        int availableCharges = Math.max(0, 4 - currentCharge);
        if (availableCharges <= 0) return 0;

        int charges = 1;
        if (multiCharge.getValue()
                && ThreadLocalRandom.current().nextInt(100) < multiChargeChance.getValue()) {
            charges += randomInclusive(minExtraCharges.getValue(), maxExtraCharges.getValue());
        }

        return Math.min(charges, availableCharges);
    }

    private void doDetonate() {
        BlockState state = mc.level.getBlockState(currentAnchorPos);
        if (!state.is(Blocks.RESPAWN_ANCHOR)) {
            abortSequence();
            return;
        }

        if (!BlockUtils.isWithinRange(Vec3.atCenterOf(currentAnchorPos))) {
            abortSequence();
            return;
        }

        if (!isExplosionSafe()) {
            abortSequence();
            return;
        }

        if (rotate.getValue()) {
            if (targetRotation == null) {
                targetRotation = getTargetRotation(Vec3.atCenterOf(currentAnchorPos));
                targetBlockPos = currentAnchorPos;
                return;
            }
            if (!rotationAimed()) return;
        }

        if (!selectExplosionItem()) {
            abortSequence();
            return;
        }

        targetRotation = null;
        targetBlockPos = null;
        interactBlock(currentAnchorPos, BlockUtils.getVisibleFace(currentAnchorPos));
        finishSequence();
    }

    private void finishSequence() {
        switchBackToOriginal();
        if (rotate.getValue() && originalRotation != null) {
            targetRotation = originalRotation;
            targetBlockPos = null;
            returningRotation = true;
        } else {
            resetState();
        }
    }

    private void advanceFromPlaceAnchor() {
        if (mode.getValue() == Mode.Assist) {
            cooldownMs = 0;
            actionTimer.reset();
            stage = Stage.WAIT_SHIELD;
            return;
        }

        boolean alreadyCharged = isAnchorCharged(currentAnchorPos);

        if (hasExistingShield()) {
            if (alreadyCharged) {
                scheduleCooldown(detonateMinDelay, detonateMaxDelay);
                stage = Stage.DETONATE;
            } else {
                scheduleCooldown(chargeMinDelay, chargeMaxDelay);
                stage = Stage.CHARGE;
            }
            return;
        }

        if (order.getValue() == Order.ChargeFirst) {
            if (alreadyCharged) {
                scheduleCooldown(shieldPlaceMinDelay, shieldPlaceMaxDelay);
                stage = Stage.PLACE_SHIELD;
            } else {
                scheduleCooldown(chargeMinDelay, chargeMaxDelay);
                stage = Stage.CHARGE;
            }
        } else {
            scheduleCooldown(shieldPlaceMinDelay, shieldPlaceMaxDelay);
            stage = Stage.PLACE_SHIELD;
        }
    }

    private void advanceFromPlaceShield() {
        if (mode.getValue() == Mode.Assist) {
            cooldownMs = 0;
            actionTimer.reset();
            stage = Stage.WAIT_ANCHOR;
            return;
        }

        boolean alreadyCharged = isAnchorCharged(currentAnchorPos);

        if (alreadyCharged) {
            scheduleCooldown(detonateMinDelay, detonateMaxDelay);
            stage = Stage.DETONATE;
        } else if (order.getValue() == Order.ChargeFirst) {
            scheduleCooldown(detonateMinDelay, detonateMaxDelay);
            stage = Stage.DETONATE;
        } else {
            scheduleCooldown(chargeMinDelay, chargeMaxDelay);
            stage = Stage.CHARGE;
        }
    }

    private void advanceFromCharge() {
        if (order.getValue() == Order.ChargeFirst) {
            if (hasExistingShield()) {
                scheduleCooldown(detonateMinDelay, detonateMaxDelay);
                stage = Stage.DETONATE;
            } else {
                scheduleCooldown(shieldPlaceMinDelay, shieldPlaceMaxDelay);
                stage = Stage.PLACE_SHIELD;
            }
        } else {
            scheduleCooldown(detonateMinDelay, detonateMaxDelay);
            stage = Stage.DETONATE;
        }
    }

    private BlockPos findShieldPos() {
        shieldSidePlacement = false;
        shieldDiagonal = false;

        int min = Math.min(shieldDistanceMin.getValue(), shieldDistanceMax.getValue());
        int max = Math.max(shieldDistanceMin.getValue(), shieldDistanceMax.getValue());

        BlockPos pos = findDistanceShieldPos(min, max);
        if (pos != null) return pos;

        if (min > 1 || max < 1) {
            pos = findDistanceShieldPos(1, 1);
            if (pos != null) return pos;
        }

        Vec3 playerPos = getShieldPlayerPos();
        Vec3 anchorPos = Vec3.atCenterOf(currentAnchorPos);
        Vec3 toAnchor = anchorPos.subtract(playerPos);
        double distance = toAnchor.length();
        if (distance < 1.0) return null;

        Vec3 dir = toAnchor.scale(1.0 / distance);
        double start = Math.min(distance - 0.5, 3.5);
        double end = 1.5;
        for (double d = start; d >= end; d -= 0.5) {
            Vec3 point = playerPos.add(dir.scale(d));
            BlockPos pathPos = BlockPos.containing(point);
            if (trySetShieldPlacement(pathPos) && isShieldBlocking(pathPos)) {
                return pathPos;
            }
        }

        if (placeMode.getValue() == PlaceMode.Cover && mc.player.isShiftKeyDown()) {
            double dx = playerPos.x - (currentAnchorPos.getX() + 0.5);
            double dz = playerPos.z - (currentAnchorPos.getZ() + 0.5);
            double absX = Math.abs(dx);
            double absZ = Math.abs(dz);
            Direction xDir = dx > 0 ? Direction.EAST : Direction.WEST;
            Direction zDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            boolean diagonalArea = absX > 0 && absZ > 0 && Math.min(absX, absZ) / Math.max(absX, absZ) > 0.5;

            Direction[] sides = diagonalArea
                    ? new Direction[]{xDir, zDir}
                    : new Direction[]{absX > absZ ? xDir : zDir};
            ItemStack glowStack = Items.GLOWSTONE.getDefaultInstance();
            for (Direction side : sides) {
                BlockPos sidePos = currentAnchorPos.relative(side);
                if (isValidShieldPos(sidePos, side, glowStack)) {
                    if (isSupportedOnGround(sidePos)) {
                        shieldSide = Direction.UP;
                        shieldSidePlacement = false;
                        shieldDiagonal = false;
                        return sidePos;
                    }
                    if (trySetShieldPlacement(sidePos)) {
                        shieldDiagonal = false;
                        return sidePos;
                    }
                }
            }
        }

        return null;
    }

    private BlockPos findDistanceShieldPos(int min, int max) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                if (dist < min || dist > max) continue;
                BlockPos pos = currentAnchorPos.offset(dx, 0, dz);
                ItemStack glowStack = Items.GLOWSTONE.getDefaultInstance();
                if (isValidShieldPos(pos, Direction.UP, glowStack) && isSupportedOnGround(pos)) {
                    candidates.add(pos);
                }
            }
        }

        if (candidates.isEmpty()) return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private BlockPos findFallbackShieldPos() {
        if (!mc.player.isShiftKeyDown()) return null;

        Vec3 playerPos = getShieldPlayerPos();
        Vec3 anchorPos = Vec3.atCenterOf(currentAnchorPos);
        double dx = playerPos.x - anchorPos.x;
        double dz = playerPos.z - anchorPos.z;
        Direction facing = Direction.getNearest((int) Math.signum(dx), 0, (int) Math.signum(dz), Direction.NORTH);
        ItemStack glowStack = Items.GLOWSTONE.getDefaultInstance();

        BlockPos sidePos = currentAnchorPos.relative(facing);
        if (isValidShieldPos(sidePos, facing, glowStack) && isSupportedOnGround(sidePos)) {
            shieldSide = Direction.UP;
            shieldSidePlacement = false;
            return sidePos;
        }

        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side == facing) continue;
            BlockPos pos = currentAnchorPos.relative(side);
            if (!isValidShieldPos(pos, side, glowStack)) continue;
            if (isSupportedOnGround(pos)) {
                shieldSide = Direction.UP;
                shieldSidePlacement = false;
                return pos;
            }
        }

        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = currentAnchorPos.relative(side);
            if (!isValidShieldPos(pos, side, glowStack)) continue;
            if (trySetShieldPlacement(pos)) return pos;
        }

        return null;
    }

    private boolean trySetShieldPlacement(BlockPos pos) {
        if (pos.equals(currentAnchorPos)) return false;
        ItemStack glowStack = Items.GLOWSTONE.getDefaultInstance();
        if (!BlockUtils.canBeReplacedWith(mc.level.getBlockState(pos), glowStack)) return false;
        if (BlockUtils.hasBlockingEntity(new AABB(pos))) return false;

        BlockPos below = pos.below();
        if (!below.equals(currentAnchorPos)) {
            BlockState belowState = mc.level.getBlockState(below);
            if (!belowState.isAir() && !BlockUtils.canBeReplacedWith(belowState, glowStack)
                    && !belowState.getCollisionShape(mc.level, below).isEmpty()) {
                shieldSide = Direction.UP;
                shieldSidePlacement = false;
                return true;
            }
        }

        for (Direction side : Direction.values()) {
            if (side == Direction.DOWN) continue;
            BlockPos neighbor = pos.relative(side);
            if (neighbor.equals(currentAnchorPos)) continue;
            if (!BlockUtils.isSolidBlock(neighbor)) continue;
            Direction clickFace = side.getOpposite();
            if (strict.getValue() && !BlockUtils.isFaceVisible(neighbor, clickFace)) continue;
            shieldSide = clickFace;
            shieldSidePlacement = true;
            return true;
        }

        return false;
    }

    private boolean isShieldBlocking(BlockPos pos) {
        Vec3 anchor = Vec3.atCenterOf(currentAnchorPos);
        Vec3 player = getShieldPlayerPos();
        Vec3 block = Vec3.atCenterOf(pos);
        double vx = player.x - anchor.x;
        double vy = player.y - anchor.y;
        double vz = player.z - anchor.z;
        double wx = block.x - anchor.x;
        double wy = block.y - anchor.y;
        double wz = block.z - anchor.z;
        double vv = vx * vx + vy * vy + vz * vz;
        if (vv < 1.0E-6) return false;
        double t = (wx * vx + wy * vy + wz * vz) / vv;
        if (t <= 0.0 || t >= 1.0) return false;
        double nx = wx - t * vx;
        double ny = wy - t * vy;
        double nz = wz - t * vz;
        return nx * nx + ny * ny + nz * nz <= 0.64;
    }

    private boolean isValidShieldPos(BlockPos pos, Direction side, ItemStack item) {
        if (pos.equals(currentAnchorPos)) return false;
        if (placedShields.contains(pos)) return false;
        if (BlockUtils.hasBlockingEntity(new AABB(pos))) return false;
        if (!BlockUtils.canBeReplacedWith(mc.level.getBlockState(pos), item)) return false;
        return isShieldBlocking(pos);
    }

    private boolean isSupportedOnGround(BlockPos pos) {
        BlockPos below = pos.below();
        if (below.equals(currentAnchorPos)) return false;
        BlockState state = mc.level.getBlockState(below);
        return !state.isAir() && !BlockUtils.canBeReplacedWith(state, Items.GLOWSTONE.getDefaultInstance())
                && !state.getCollisionShape(mc.level, below).isEmpty();
    }

    private boolean isAnchorCharged(BlockPos pos) {
        if (pos == null) return false;
        BlockState state = mc.level.getBlockState(pos);
        return state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0;
    }

    private boolean hasExistingShield() {
        Vec3 playerPos = getShieldPlayerPos();
        Vec3 anchorPos = Vec3.atCenterOf(currentAnchorPos);
        Vec3 toAnchor = anchorPos.subtract(playerPos);
        double distance = toAnchor.length();
        if (distance < 1.0) return false;

        Vec3 dir = toAnchor.scale(1.0 / distance);
        double start = Math.min(distance, 3.5);
        double end = 0.5;
        for (double d = start; d >= end; d -= 0.3) {
            Vec3 point = playerPos.add(dir.scale(d));
            BlockPos pos = BlockPos.containing(point);
            if (isExistingShield(pos)) return true;
        }

        if (placeMode.getValue() == PlaceMode.Cover) {
            double dx = playerPos.x - (currentAnchorPos.getX() + 0.5);
            double dz = playerPos.z - (currentAnchorPos.getZ() + 0.5);
            Direction xDir = dx > 0 ? Direction.EAST : Direction.WEST;
            Direction zDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            for (Direction side : new Direction[]{xDir, zDir}) {
                BlockPos pos = currentAnchorPos.relative(side);
                if (isShieldBlocking(pos) && isExistingShield(pos)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isExistingShield(BlockPos pos) {
        if (pos.equals(currentAnchorPos)) return false;
        BlockState state = mc.level.getBlockState(pos);
        if (BlockUtils.canBeReplacedWith(state, Items.GLOWSTONE.getDefaultInstance())) return false;
        return Block.isShapeFullBlock(state.getCollisionShape(mc.level, pos));
    }

    private boolean isExplosionSafe() {
        if (currentAnchorPos == null) return false;
        Vec3 explosionCenter = Vec3.atCenterOf(currentAnchorPos);
        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float threshold = minHealth.getValue().floatValue();

        float actual = DamageUtils.anchorDamage(mc.player, explosionCenter, DamageUtils.ArmorEnchantmentMode.None);
        float pppp = DamageUtils.anchorDamage(mc.player, explosionCenter, DamageUtils.ArmorEnchantmentMode.PPPP);
        float ppbp = DamageUtils.anchorDamage(mc.player, explosionCenter, DamageUtils.ArmorEnchantmentMode.PPBP);

        float worstDamage = Math.max(actual, Math.max(pppp, ppbp));
        worstDamage *= 1.12f;

        return health - worstDamage > threshold;
    }

    private boolean selectExplosionItem() {
        if (mc.player == null) return false;

        int preferredSlot = detonateSlot.getValue() - 1;
        if (isHotbarSlotSafe(preferredSlot)) {
            InvUtils.swap(preferredSlot, false);
            return true;
        }

        if (isExplosionHandSafe(mc.player.getMainHandItem())) return true;

        if (isHotbarSlotSafe(originalSlot)) {
            InvUtils.swap(originalSlot, false);
            return true;
        }

        for (int slot = 0; slot < 9; slot++) {
            if (isHotbarSlotSafe(slot)) {
                InvUtils.swap(slot, false);
                return true;
            }
        }

        return false;
    }

    private boolean isHotbarSlotSafe(int slot) {
        if (mc.player == null || slot < 0 || slot > 8) return false;
        return isExplosionHandSafe(mc.player.getInventory().getItem(slot));
    }

    private boolean isExplosionHandSafe(ItemStack stack) {
        return !stack.is(Items.GLOWSTONE) && !stack.is(Items.RESPAWN_ANCHOR);
    }

    private void switchBackToOriginal() {
        if (!swapBack.getValue()) return;
        if (originalSlot >= 0 && originalSlot <= 8) {
            InvUtils.swap(originalSlot, false);
        }
    }

    private void abortSequence() {
        switchBackToOriginal();
        resetState();
    }

    private void abortConflicting() {
        if (DoubleAnchor.INSTANCE.isActive()) {
            return;
        }
    }

    private void applyTargetRotation() {
        if (targetRotation == null || !rotate.getValue()) return;
        Rot2f patchedTarget = RotationUtils.applySensitivityPatch(targetRotation);
        int minSpeed = Math.min(rotationSpeedMin.getValue(), rotationSpeedMax.getValue());
        int maxSpeed = Math.max(rotationSpeedMin.getValue(), rotationSpeedMax.getValue());
        int speedValue = ThreadLocalRandom.current().nextInt(minSpeed, maxSpeed + 1);
        RotationManager.INSTANCE.setRotations(patchedTarget, speedValue, Priority.High);
    }

    private boolean rotationAimed() {
        if (targetRotation == null || !rotate.getValue()) return true;
        Rot2f currentRot = RotationManager.INSTANCE.isActive()
                ? RotationManager.INSTANCE.getRotation()
                : new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        float yawDiff = Math.abs(Mth.wrapDegrees(targetRotation.getYaw() - currentRot.getYaw()));
        float pitchDiff = Math.abs(targetRotation.getPitch() - currentRot.getPitch());
        return yawDiff < 0.5f && pitchDiff < 0.5f;
    }

    private Rot2f getTargetRotation(Vec3 targetPos) {
        return RotationUtils.calculate(mc.player.getEyePosition(), targetPos);
    }

    private boolean placeAtAvoiding(BlockPos placePos, BlockPos avoidPos) {
        if (placePos == null) return false;

        setShiftState(true);
        ItemStack handItem = mc.player.getMainHandItem();
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = placePos.relative(dir);
            if (neighbor.equals(avoidPos)) continue;
            BlockState state = mc.level.getBlockState(neighbor);
            if (state.isAir() || BlockUtils.canBeReplacedWith(state, handItem)) continue;
            interactBlock(neighbor, dir.getOpposite());
            setShiftState(false);
            return true;
        }
        setShiftState(false);
        return false;
    }

    private void interactBlock(BlockPos pos, Direction side) {
        Vec3 hitVec = new Vec3(
                pos.getX() + 0.5 + side.getStepX() * 0.45,
                pos.getY() + 0.5 + side.getStepY() * 0.45,
                pos.getZ() + 0.5 + side.getStepZ() * 0.45
        );
        BlockHitResult hit = new BlockHitResult(hitVec, side, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void setShiftState(boolean state) {
        mc.player.setShiftKeyDown(state);
    }

    private void addRenderBox(BlockPos pos) {
        if (render.getValue()) {
            renderBoxes.add(new RenderBox(pos, System.currentTimeMillis()));
        }
    }

    private void doWaitShield() {
        if (hasAssistShield()) {
            cooldownMs = 0;
            actionTimer.reset();
            stage = Stage.WAIT_ANCHOR;
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult blockHit)) return;

        BlockPos hitPos = blockHit.getBlockPos();
        Direction hitSide = blockHit.getDirection();

        if (tryAssistShieldPlace(hitPos, hitPos.below(), Direction.UP)) return;
        if (hitPos.equals(currentAnchorPos)) return;
        BlockPos placePos = hitPos.relative(hitSide);
        if (tryAssistShieldPlace(placePos, hitPos, hitSide)) return;
    }

    private boolean hasAssistShield() {
        for (BlockPos pos : placedShields) {
            BlockState state = mc.level.getBlockState(pos);
            if (Block.isShapeFullBlock(state.getCollisionShape(mc.level, pos))) return true;
        }
        return hasExistingShield();
    }

    private boolean tryAssistShieldPlace(BlockPos pos, BlockPos interactPos, Direction interactSide) {
        if (pos.equals(currentAnchorPos) || placedShields.contains(pos)) return false;
        if (!BlockUtils.canBeReplacedWith(mc.level.getBlockState(pos), Items.GLOWSTONE.getDefaultInstance()))
            return false;
        if (BlockUtils.hasBlockingEntity(new AABB(pos))) return false;

        FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
        if (!glowstone.found()) return false;

        InvUtils.swap(glowstone.slot(), false);
        setShiftState(true);
        interactBlock(interactPos, interactSide);
        setShiftState(false);

        placedShields.add(pos);
        return true;
    }

    private void doWaitAnchor() {
        if (!isAimingAtAnchor()) return;

        if (mode.getValue() == Mode.Assist && !hasAssistShield()) {
            cooldownMs = 0;
            actionTimer.reset();
            stage = Stage.WAIT_SHIELD;
            return;
        }

        BlockState state = mc.level.getBlockState(currentAnchorPos);
        if (!state.is(Blocks.RESPAWN_ANCHOR)) {
            abortSequence();
            return;
        }

        if (chargesRemaining < 0) {
            chargesRemaining = calculateChargeActions(state);
        }

        if (chargesRemaining > 0) {
            if (!isExplosionSafe()) {
                abortSequence();
                return;
            }
            FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
            if (!glowstone.found()) return;
            InvUtils.swap(glowstone.slot(), false);
            interactBlock(currentAnchorPos, BlockUtils.getVisibleFace(currentAnchorPos));
            chargesRemaining--;

            if (chargesRemaining > 0) {
                scheduleCooldown(chargeMinDelay, chargeMaxDelay);
            } else {
                scheduleCooldown(detonateMinDelay, detonateMaxDelay);
            }
            return;
        }

        if (!isExplosionSafe()) {
            abortSequence();
            return;
        }
        if (selectExplosionItem()) {
            interactBlock(currentAnchorPos, BlockUtils.getVisibleFace(currentAnchorPos));
            finishSequence();
        } else {
            abortSequence();
        }
    }

    private boolean isAimingAtAnchor() {
        return mc.hitResult instanceof BlockHitResult blockHit
                && blockHit.getBlockPos().equals(currentAnchorPos);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        long time = System.currentTimeMillis();
        renderBoxes.removeIf(box -> time - box.startTime() > 1000);

        for (RenderBox box : renderBoxes) {
            float progress = Mth.clamp((float) (time - box.startTime()) / 1000.0f, 0.0f, 1.0f);
            float alpha = 1.0f - progress;

            Color fc = sideColor.getValue();
            int fillAlpha = Mth.clamp((int) (fc.getAlpha() * alpha), 0, 255);
            Render3DScheduler.INSTANCE.addFilledBox(new AABB(box.pos()), new Color(fc.getRed(), fc.getGreen(), fc.getBlue(), fillAlpha));

            Color bc = lineColor.getValue();
            int lineAlpha = Mth.clamp((int) (bc.getAlpha() * alpha), 0, 255);
            Render3DScheduler.INSTANCE.addOutlineBox(new AABB(box.pos()), new Color(bc.getRed(), bc.getGreen(), bc.getBlue(), lineAlpha).getRGB(), lineWidth.getValue().floatValue());
        }

        if (mode.getValue() != Mode.Assist || currentAnchorPos == null) return;

        Vec3 anchorCenter = Vec3.atCenterOf(currentAnchorPos);
        Vec3 playerFeet = mc.player.position();
        Render3DScheduler.INSTANCE.addLine(
                anchorCenter, playerFeet,
                lineColor.getValue(), lineWidth.getValue().floatValue());

        if (stage == Stage.WAIT_ANCHOR) {
            AABB box = new AABB(currentAnchorPos);
            Render3DScheduler.INSTANCE.addFilledBox(box, sideColor.getValue());
            Render3DScheduler.INSTANCE.addOutlineBox(box, lineColor.getValue().getRGB(), lineWidth.getValue().floatValue());
        }
    }

    private void resetState() {
        stage = Stage.IDLE;
        currentAnchorPos = null;
        shieldPos = null;
        shieldSide = null;
        shieldSidePlacement = false;
        shieldDiagonal = false;
        targetRotation = null;
        targetBlockPos = null;
        originalRotation = null;
        returningRotation = false;
        originalSlot = -1;
        stageTicksElapsed = 0;
        cooldownMs = 0;
        chargesRemaining = -1;
        placedShields.clear();
        renderBoxes.clear();
        pendingAnchorPos = null;
        pendingAnchorHit = null;
        actionTimer.setMs(0);
    }

    private Vec3 getShieldPlayerPos() {
        if (prediction.getValue()) {
            AABB extrapolatedBox = ExtrapolationManager.INSTANCE.extrapolate(
                    mc.player,
                    (int) Math.ceil(predictionStrength.getValue()),
                    2
            );
            if (extrapolatedBox != null) {
                return extrapolatedBox.getCenter();
            }
        }
        return mc.player.position();
    }

    public boolean isActive() {
        return isEnabled() && stage != Stage.IDLE;
    }

    public void abort() {
        resetState();
    }

    private void scheduleCooldown(DoubleSetting minDelay, DoubleSetting maxDelay) {
        cooldownMs = getDelayMs(minDelay, maxDelay);
        actionTimer.reset();
    }

    private int getDelayMs(DoubleSetting minDelay, DoubleSetting maxDelay) {
        int min = (int) Math.max(0, minDelay.getValue() * 10);
        int max = (int) Math.max(min + 1, maxDelay.getValue() * 10);
        return randomInclusive(min, max);
    }

    private boolean shouldPlaceExtraShield() {
        if (mode.getValue() == Mode.Assist) return false;
        return multiShield.getValue()
                && placedShields.size() < maxExtraShields.getValue()
                && ThreadLocalRandom.current().nextInt(100) < multiShieldChance.getValue();
    }

    private int randomInclusive(int min, int max) {
        int actualMin = Math.max(0, Math.min(min, max));
        int actualMax = Math.max(actualMin, Math.max(min, max));
        return ThreadLocalRandom.current().nextInt(actualMin, actualMax + 1);
    }

}
