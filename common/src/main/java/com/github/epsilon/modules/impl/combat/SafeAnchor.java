package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.combat.DamageUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SafeAnchor extends Module {

    public static final SafeAnchor INSTANCE = new SafeAnchor();

    private SafeAnchor() {
        super("Safe Anchor", Category.COMBAT);
        EventBus.INSTANCE.subscribe(new ConsumerListener<>(Render3DEvent.class,
                event -> {
                    if (!render.getValue() || renderBoxes.isEmpty()) return;

                    long time = System.currentTimeMillis();
                    long fadeTime = this.fadeTime.getValue().longValue();

                    renderBoxes.removeIf(box -> time - box.startTime() > fadeTime);

                    for (RenderBox box : renderBoxes) {
                        long age = time - box.startTime();
                        float progress = Mth.clamp((float) age / fadeTime, 0.0f, 1.0f);
                        float alphaFactor = Mth.clamp(1.0f - progress, 0.0f, 1.0f);

                        Color sideColor = box.sideColor();
                        Color lineColor = box.lineColor();

                        Color side = new Color(sideColor.getRed(), sideColor.getGreen(), sideColor.getBlue(), (int) (sideColor.getAlpha() * alphaFactor));
                        Color line = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), (int) (lineColor.getAlpha() * alphaFactor));

                        Render3DScheduler.INSTANCE.addFilledBox(box.aabb, side);
                        Render3DScheduler.INSTANCE.addOutlineBox(box.aabb, line);
                    }
                }
        ));
    }

    private enum PlaceMode {
        Adaptive,
        Cover
    }

    private enum Stage {
        None,
        Charging,
        RotToPlace,
        RotToExplode
    }

    private final EnumSetting<PlaceMode> placeMode = enumSetting("Place Mode", PlaceMode.Adaptive);
    private final IntSetting placeRotationSpeed = intSetting("Place Rotation Speed", 180, 10, 180, 10);
    private final IntSetting explodeRotationSpeed = intSetting("Explode Rotation Speed", 180, 10, 180, 10);
    private final IntSetting placeCps = intSetting("Place CPS", 10, 1, 20, 1);
    private final BoolSetting silentRotation = boolSetting("Silent Rotation", false, () -> false);
    private final BoolSetting dynamicSpeed = boolSetting("DynamicSpeed", true, () -> false);
    private final DoubleSetting farBoost = doubleSetting("FarBoost", 20.0, 0.0, 100.0, 1.0, () -> false);
    private final DoubleSetting farBoostThreshold = doubleSetting("FarBoostThreshold", 5.0, 1.0, 20.0, 0.5, () -> false);
    private final DoubleSetting nearReduction = doubleSetting("NearReduction", 15.0, 0.0, 100.0, 1.0, () -> false);
    private final DoubleSetting nearReductionThreshold = doubleSetting("NearReductionThreshold", 2.0, 0.1, 10.0, 0.5, () -> false);
    private final BoolSetting autoCharge = boolSetting("AutoCharge", true, () -> false);
    private final BoolSetting autoPlace = boolSetting("AutoPlace", true, () -> false);
    private final BoolSetting autoExplode = boolSetting("AutoExplode", true, () -> false);
    private final BoolSetting ownAnchorOnly = boolSetting("OwnAnchorOnly", false);
    private final BoolSetting gazeLock = boolSetting("Gaze Lock", true);
    private final BoolSetting switchBack = boolSetting("Switch Back", true);
    private final DoubleSetting minHealth = doubleSetting("Min Health", 4.0, 0.0, 20.0, 0.5);
    private final BoolSetting render = boolSetting("Render", true);
    private final IntSetting fadeTime = intSetting("Fade Time", 500, 0, 3000, 50, render::getValue);
    private final ColorSetting glowStoneSide = colorSetting("GlowStone Side", new Color(70, 145, 255, 85), render::getValue);
    private final ColorSetting glowStoneLine = colorSetting("GlowStone Line", new Color(70, 165, 255), render::getValue);

    private final Set<BlockPos> ownAnchors = Collections.synchronizedSet(new LinkedHashSet<>());
    private final List<RenderBox> renderBoxes = new ArrayList<>();

    private BlockPos currentAnchorPos;
    private Rot2f targetRotation;
    private BlockPos targetActionPos;
    private Direction targetPlaceSide;
    private boolean isSidePlacement;
    private boolean isDiagonalPlacement;
    private boolean explodeNoRotate;
    private double currentRotationSpeed = 2.0;
    private long nextActionTimeMs;
    private int originalSlot = -1;
    private int stageTicksElapsed;
    private static final int STAGE_TIMEOUT_TICKS = 40;
    private Stage stage = Stage.None;

    @Override
    protected void onEnable() {
        stage = Stage.None;
        nextActionTimeMs = 0;
        stageTicksElapsed = 0;
        currentAnchorPos = null;
        ownAnchors.clear();
        targetRotation = null;
        targetActionPos = null;
        targetPlaceSide = null;
        isSidePlacement = false;
        isDiagonalPlacement = false;
        explodeNoRotate = false;
        originalSlot = -1;
    }

    @Override
    protected void onDisable() {
        ownAnchors.clear();
        resetState();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;

        Packet<?> packet = event.getPacket();
        if (!(packet instanceof ServerboundUseItemOnPacket interactPacket)) return;
        if (!mc.player.getItemInHand(interactPacket.getHand()).is(Items.RESPAWN_ANCHOR)) return;

        BlockHitResult hit = interactPacket.getHitResult();
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (state.is(Blocks.RESPAWN_ANCHOR) || state.canBeReplaced()) {
            ownAnchors.add(pos);
        } else {
            BlockPos offsetPos = pos.relative(hit.getDirection());
            ownAnchors.add(offsetPos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                ownAnchors.add(pos);
            }
        }

        if (ownAnchors.size() > 30) {
            synchronized (ownAnchors) {
                ownAnchors.remove(ownAnchors.iterator().next());
            }
        }
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        // Yield only before SafeAnchor starts. Once the cover/explode sequence is running,
        // keep ownership so a manual anchor blast cannot cancel the damage-block placement.
        if ((AnchorBlast.INSTANCE.isActive() || DoubleAnchor.INSTANCE.isActive()) && stage == Stage.None) {
            return;
        }

        if (silentRotation.getValue() && targetRotation != null) {
            Managers.ROTATION.setRotations(targetRotation, currentRotationSpeed, Priority.High);
        }

        if (System.currentTimeMillis() < nextActionTimeMs) return;

        // Timeout protection: if stuck in a stage too long, reset
        if (stage != Stage.None) {
            stageTicksElapsed++;
            if (stageTicksElapsed > STAGE_TIMEOUT_TICKS) {
                resetState();
                return;
            }
            // Verify anchor still exists
            if (currentAnchorPos != null && !mc.level.getBlockState(currentAnchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                resetState();
                return;
            }
        }

        HitResult hit = getCrosshairHit();
        if (stage == Stage.None && hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            if (mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
                if (ownAnchorOnly.getValue() && !ownAnchors.contains(pos)) return;

                FindItemResult glowstone = InvUtils.find(Items.GLOWSTONE);
                if (!glowstone.found()) return;

                originalSlot = mc.player.getInventory().getSelectedSlot();
                currentAnchorPos = pos;
                stage = Stage.Charging;

                // Defer the first action by one tick so a momentary glance at an anchor
                // doesn't auto-charge it. The next tick re-verifies the gaze in handleCharge.
                if (gazeLock.getValue()) return;
            }
        }

        if (currentAnchorPos == null) return;

        switch (stage) {
            case Charging -> {
                if (autoCharge.getValue()) handleCharge();
                else preparePlace();
            }
            case RotToPlace -> {
                if (autoPlace.getValue()) handleRotatingToPlace();
                else prepareExplode();
            }
            case RotToExplode -> {
                if (autoExplode.getValue()) handleRotatingToExplode();
                else resetState();
            }
            case None -> {
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck() || targetRotation == null) return;

        HitResult hit = getCrosshairHit();
        if (isSidePlacement && isLookingAtPlayerSide(hit)) {
            targetRotation = null;
            return;
        }

        if (!silentRotation.getValue()) {
            smoothAim(targetRotation, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }

        Rot2f currentRot = silentRotation.getValue()
                ? Managers.ROTATION.getRotation()
                : new Rot2f(mc.player.getYRot(), mc.player.getXRot());

        float yawDiff = Math.abs(Mth.wrapDegrees(targetRotation.getYaw() - currentRot.getYaw()));
        float pitchDiff = Math.abs(targetRotation.getPitch() - currentRot.getPitch());
        if (yawDiff < 1.0f && pitchDiff < 1.0f) {
            targetRotation = null;
        }
    }

    private void smoothAim(Rot2f targetRotation, float tickDelta) {
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        float yawDiff = Mth.wrapDegrees(targetRotation.getYaw() - currentYaw);
        float pitchDiff = targetRotation.getPitch() - currentPitch;
        double aimSpeed = currentRotationSpeed * 0.5;

        if (dynamicSpeed.getValue()) {
            double totalDiff = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
            if (totalDiff > farBoostThreshold.getValue()) {
                aimSpeed *= 1.0 + farBoost.getValue() / 100.0;
            } else if (totalDiff < nearReductionThreshold.getValue()) {
                aimSpeed *= 1.0 - nearReduction.getValue() / 100.0;
            }
        }

        aimSpeed += ThreadLocalRandom.current().nextGaussian() * 0.08;
        aimSpeed = Math.max(0.05, aimSpeed);
        // Scale by tickDelta so speed is frame-rate independent
        aimSpeed *= tickDelta;
        float yawChange = (float) Mth.clamp(yawDiff, -aimSpeed, aimSpeed);
        float pitchChange = (float) Mth.clamp(pitchDiff, -aimSpeed, aimSpeed);
        float sens = (float) (mc.options.sensitivity().get() * 0.6 + 0.2);
        float gcd = sens * sens * sens * 8.0f * 0.15f;
        yawChange = Math.round(yawChange / gcd) * gcd;
        pitchChange = Math.round(pitchChange / gcd) * gcd;
        mc.player.setYRot(currentYaw + yawChange);
        mc.player.setXRot(currentPitch + pitchChange);
    }

    private Rot2f getTargetRotation(Vec3 targetPos) {
        Rot2f rot = RotationUtils.calculate(mc.player.getEyePosition(), targetPos);
        float jy = (float) (ThreadLocalRandom.current().nextGaussian() * 0.10);
        float jp = (float) (ThreadLocalRandom.current().nextGaussian() * 0.08);
        return new Rot2f(rot.getYaw() + jy, rot.getPitch() + jp);
    }

    private void setShiftState(boolean state) {
        mc.player.setShiftKeyDown(state);
    }

    private boolean placeAt(BlockPos placePos, Direction avoidSide) {
        if (placePos == null) return false;

        setShiftState(true);
        for (Direction dir : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            if (avoidSide != null && dir == avoidSide) continue;
            BlockPos neighbor = placePos.relative(dir);
            BlockState state = mc.level.getBlockState(neighbor);
            if (state.isAir() || state.canBeReplaced()) continue;
            interactBlock(neighbor, dir.getOpposite());
            setShiftState(false);
            return true;
        }
        setShiftState(false);
        return false;
    }

    private void handleCharge() {
        // Gaze lock: while we're still in the Charging stage the player's view is their
        // own (no module-driven rotation yet). Require the crosshair to remain on the
        // locked anchor; the moment it drifts off, abandon the sequence so we never
        // auto-charge an anchor the user isn't actively targeting.
        if (gazeLock.getValue() && !isLookingAtCurrentAnchor()) {
            resetState();
            return;
        }

        int charges = mc.level.getBlockState(currentAnchorPos).getValue(RespawnAnchorBlock.CHARGE);

        // Cover / Adaptive: too close and no place pos �?quick charge + explode
        if (isTooCloseToAnchor()) {
            BlockPos placePos = findPlacePos();
            if (placePos == null) {
                if (charges <= 0) {
                    FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
                    if (glowstone.found()) {
                        InvUtils.swap(glowstone.slot(), false);
                        interactBlock(currentAnchorPos, Direction.UP);
                        scheduleDelay();
                        if (placeMode.getValue() == PlaceMode.Cover) explodeNoRotate = true;
                        prepareExplode();
                    }
                } else {
                    if (placeMode.getValue() == PlaceMode.Cover) explodeNoRotate = true;
                    prepareExplode();
                }
                return;
            }
        }

        if (shouldExplodeNow()) {
            prepareExplode();
            return;
        }

        if (charges < 4) {
            FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
            if (glowstone.found()) {
                InvUtils.swap(glowstone.slot(), false);
                interactBlock(currentAnchorPos, Direction.UP);
                scheduleDelay();
            } else {
                resetState();
                return;
            }
        }

        if (autoPlace.getValue() && placeMode.getValue() == PlaceMode.Cover) {
            BlockPos placePos = findPlacePos();
            if (isDiagonalPlacement) {
                preparePlace();
                return;
            }

            if (placePos != null) {
                FindItemResult block = InvUtils.findInHotbar(Items.GLOWSTONE);
                if (block.found()) {
                    InvUtils.swap(block.slot(), false);
                    if (isSidePlacement && targetPlaceSide != null) {
                        if (mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
                            if (placeAt(placePos, targetPlaceSide.getOpposite())) {
                                addRenderBox(placePos);
                            }
                        } else {
                            setShiftState(true);
                            interactBlock(currentAnchorPos, targetPlaceSide);
                            setShiftState(false);
                            addRenderBox(placePos);
                        }
                    } else {
                        interactBlock(placePos.below(), Direction.UP);
                        addRenderBox(placePos);
                    }
                    prepareExplode();
                    return;
                }
            }

            if (isExplosionSafe()) prepareExplode();
            else resetState();
        } else {
            preparePlace();
        }
    }

    private boolean shouldExplodeNow() {
        if (!autoExplode.getValue()) return false;
        if (!isTooCloseToAnchor()) return false;
        return isExplosionSafe();
    }

    private boolean isTooCloseToAnchor() {
        double dx = mc.player.getX() - (currentAnchorPos.getX() + 0.5);
        double dz = mc.player.getZ() - (currentAnchorPos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) < 1.7;
    }

    private void preparePlace() {
        if (!autoPlace.getValue()) {
            if (isExplosionSafe()) prepareExplode();
            else resetState();
            return;
        }

        BlockPos placePos = findPlacePos();
        if (placePos != null) {
            FindItemResult block = InvUtils.findInHotbar(Items.GLOWSTONE);
            if (block.found()) {
                InvUtils.swap(block.slot(), false);
                targetActionPos = placePos;
                currentRotationSpeed = placeRotationSpeed.getValue();

                if (isSidePlacement && targetPlaceSide != null) {
                    HitResult hit = getCrosshairHit();
                    if (isLookingAtPlayerSide(hit)) {
                        targetRotation = null;
                    } else {
                        Vec3 sideVec = currentAnchorPos.getCenter().add(
                                targetPlaceSide.getStepX() * 0.45,
                                targetPlaceSide.getStepY() * 0.45,
                                targetPlaceSide.getStepZ() * 0.45
                        );
                        targetRotation = getTargetRotation(sideVec);
                    }
                } else {
                    targetRotation = getTargetRotation(placePos.getCenter());
                }

                stage = Stage.RotToPlace;
                return;
            }
        }

        if (isExplosionSafe()) prepareExplode();
        else resetState();
    }

    private BlockPos findPlacePos() {
        isSidePlacement = false;
        isDiagonalPlacement = false;
        if (placeMode.getValue() == PlaceMode.Cover) {
            double dx = mc.player.getX() - (currentAnchorPos.getX() + 0.5);
            double dz = mc.player.getZ() - (currentAnchorPos.getZ() + 0.5);
            double absX = Math.abs(dx);
            double absZ = Math.abs(dz);
            Direction xDir = dx > 0 ? Direction.EAST : Direction.WEST;
            Direction zDir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            boolean diagonalArea = absX > 0 && absZ > 0 && Math.min(absX, absZ) / Math.max(absX, absZ) > 0.5;

            if (diagonalArea) {
                for (Direction side : new Direction[]{xDir, zDir}) {
                    BlockPos neighbor = currentAnchorPos.relative(side);
                    if (mc.level.getBlockState(neighbor).canBeReplaced()
                            && mc.level.getEntities(null, new AABB(neighbor)).isEmpty()
                            && isSideShielding(side)) {
                        targetPlaceSide = side;
                        isSidePlacement = true;
                        isDiagonalPlacement = true;
                        return neighbor;
                    }
                }
            }

            if (Math.sqrt(dx * dx + dz * dz) < 1.5) {
                for (Direction side : new Direction[]{xDir, zDir}) {
                    BlockPos neighbor = currentAnchorPos.relative(side);
                    if (mc.level.getBlockState(neighbor).canBeReplaced()
                            && mc.level.getEntities(null, new AABB(neighbor)).isEmpty()
                            && isSideShielding(side)) {
                        targetPlaceSide = side;
                        isSidePlacement = true;
                        return neighbor;
                    }
                }

                if (isExplosionSafe()) {
                    prepareExplode();
                    return null;
                }
            }

            Direction bestDir = absX > absZ ? xDir : zDir;
            BlockPos neighbor = currentAnchorPos.relative(bestDir);
            if (mc.level.getBlockState(neighbor).canBeReplaced()
                    && mc.level.getEntities(null, new AABB(neighbor)).isEmpty()
                    && isSideShielding(bestDir)) {
                targetPlaceSide = bestDir;
                isSidePlacement = true;
                return neighbor;
            }
        }

        Vec3 playerPos = mc.player.position();
        Vec3 anchorPos = currentAnchorPos.getCenter();
        for (double i = 0.3; i <= 0.7; i += 0.1) {
            BlockPos pos = BlockPos.containing(playerPos.lerp(anchorPos, i));
            if (isValidPlacePos(pos)) {
                targetPlaceSide = Direction.UP;
                return pos;
            }
        }

        BlockPos frontPos = mc.player.blockPosition().relative(mc.player.getDirection());
        if (isValidPlacePos(frontPos)) {
            targetPlaceSide = Direction.UP;
            return frontPos;
        }

        return null;
    }

    private boolean isSideShielding(Direction side) {
        Vec3 anchor = currentAnchorPos.getCenter();
        Vec3 player = mc.player.position();
        Vec3 block = currentAnchorPos.relative(side).getCenter();
        double vx = player.x - anchor.x;
        double vz = player.z - anchor.z;
        double wx = block.x - anchor.x;
        double wz = block.z - anchor.z;
        double vv = vx * vx + vz * vz;
        if (vv < 1.0E-6) return false;
        double t = (wx * vx + wz * vz) / vv;
        if (t <= 0.0 || t >= 1.0) return false;
        double nx = wx - t * vx;
        double nz = wz - t * vz;
        return nx * nx + nz * nz <= 0.36;
    }

    private boolean isValidPlacePos(BlockPos pos) {
        if (pos.equals(currentAnchorPos)) return false;
        if (!mc.level.getEntities(null, new AABB(pos)).isEmpty()) return false;

        BlockState below = mc.level.getBlockState(pos.below());
        return !below.isAir() && !below.canBeReplaced() && mc.level.getBlockState(pos).canBeReplaced();
    }

    private boolean isExplosionSafe() {
        if (currentAnchorPos == null) return false;
        Vec3 explosionCenter = currentAnchorPos.getCenter();
        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float threshold = minHealth.getValue().floatValue();

        // Strategy 1: actual enchantments (vanilla-accurate)
        float actual = DamageUtils.anchorDamage(mc.player, explosionCenter, DamageUtils.ArmorEnchantmentMode.None);
        // Strategy 2: assume full Prot IV set (common PvP armor)
        float pppp = DamageUtils.anchorDamage(mc.player, explosionCenter, DamageUtils.ArmorEnchantmentMode.PPPP);
        // Strategy 3: assume max prot + blast prot
        float ppbp = DamageUtils.anchorDamage(mc.player, explosionCenter, DamageUtils.ArmorEnchantmentMode.PPBP);

        // Take the HIGHEST damage estimate (least optimistic)
        float worstDamage = Math.max(actual, Math.max(pppp, ppbp));
        // Add 12% safety margin for server-side rounding and custom plugins
        worstDamage *= 1.12f;

        return health - worstDamage > threshold;
    }

    private void handleRotatingToPlace() {
        HitResult hit = getCrosshairHit();
        if (isSidePlacement && isLookingAtPlayerSide(hit)) {
            targetRotation = null;
        }

        if (targetRotation == null || isLookingAt(targetActionPos)) {
            boolean placed = false;
            if (isSidePlacement) {
                if (targetPlaceSide != null && mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
                    placed = placeAt(targetActionPos, targetPlaceSide.getOpposite());
                } else {
                    setShiftState(true);
                    interactBlock(currentAnchorPos, targetPlaceSide);
                    setShiftState(false);
                    placed = true;
                }
            } else {
                interactBlock(targetActionPos.below(), Direction.UP);
                placed = true;
            }

            if (placed) {
                addRenderBox(targetActionPos);
                prepareExplode();
            } else {
                // Cover placement failed �?only explode if still safe without cover
                if (isExplosionSafe()) prepareExplode();
                else resetState();
            }
        }
    }

    private void prepareExplode() {
        if (!autoExplode.getValue()) {
            resetState();
            return;
        }

        if (!selectExplosionItem()) {
            resetState();
            return;
        }

        targetActionPos = currentAnchorPos;
        currentRotationSpeed = explodeRotationSpeed.getValue();
        targetRotation = explodeNoRotate ? null : getTargetRotation(currentAnchorPos.getCenter());
        stage = Stage.RotToExplode;
    }

    private boolean selectExplosionItem() {
        if (mc.player == null) return false;
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

    private void handleRotatingToExplode() {
        if (targetRotation == null || isLookingAt(targetActionPos)) {
            // Final safety re-check with current position/health before detonation
            if (!isExplosionSafe()) {
                resetState();
                return;
            }
            interactBlock(targetActionPos, Direction.UP);
            switchBackToAnchor();
            resetState();
        }
    }

    private void switchBackToAnchor() {
        if (!switchBack.getValue()) return;

        FindItemResult anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR);
        if (anchor.found()) {
            InvUtils.swap(anchor.slot(), false);
        }
    }

    private boolean isLookingAt(BlockPos pos) {
        HitResult hit = getCrosshairHit();
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos hitPos = blockHit.getBlockPos();
            if (isSidePlacement && currentAnchorPos != null) {
                return hitPos.equals(currentAnchorPos) && blockHit.getDirection() != Direction.UP;
            }
            return hitPos.equals(pos) || hitPos.equals(pos.below());
        }
        return false;
    }

    private boolean isLookingAtCurrentAnchor() {
        if (currentAnchorPos == null) return false;
        HitResult hit = getCrosshairHit();
        if (!(hit instanceof BlockHitResult blockHit)) return false;
        return blockHit.getBlockPos().equals(currentAnchorPos);
    }

    private HitResult getCrosshairHit() {
        if (mc.player == null) return null;
        double reach = mc.player.blockInteractionRange();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        return mc.player.pick(reach, tickDelta, false);
    }

    private boolean isLookingAtPlayerSide(HitResult hit) {
        if (!isSidePlacement || currentAnchorPos == null || mc.player == null) return false;
        if (!(hit instanceof BlockHitResult blockHit)) return false;

        Direction playerSide = getPlayerSideOfAnchor();
        if (playerSide == null) return false;

        BlockPos hitPos = blockHit.getBlockPos();
        Direction hitSide = blockHit.getDirection();
        if (hitPos.equals(currentAnchorPos) && hitSide == playerSide) return true;

        BlockPos placePos = currentAnchorPos.relative(playerSide);
        return hitPos.equals(placePos) && (hitSide == playerSide || hitSide == playerSide.getOpposite());
    }

    private Direction getPlayerSideOfAnchor() {
        if (currentAnchorPos == null) return null;
        double dx = mc.player.getX() - (currentAnchorPos.getX() + 0.5);
        double dz = mc.player.getZ() - (currentAnchorPos.getZ() + 0.5);
        return Math.abs(dx) >= Math.abs(dz)
                ? (dx >= 0.0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0.0 ? Direction.SOUTH : Direction.NORTH);
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

    private void addRenderBox(BlockPos pos) {
        renderBoxes.add(new RenderBox(new AABB(pos), glowStoneLine.getValue(), glowStoneSide.getValue(), System.currentTimeMillis()));
    }

    private void resetState() {
        stage = Stage.None;
        currentAnchorPos = null;
        targetRotation = null;
        targetActionPos = null;
        targetPlaceSide = null;
        isSidePlacement = false;
        isDiagonalPlacement = false;
        scheduleDelay();
        explodeNoRotate = false;
        originalSlot = -1;
        stageTicksElapsed = 0;
    }

    public boolean isActive() {
        return isEnabled() && stage != Stage.None;
    }

    private void scheduleDelay() {
        int cps = Math.max(1, placeCps.getValue());
        double baseMs = 1000.0 / cps;
        double jitter = baseMs * 0.25 * ThreadLocalRandom.current().nextGaussian();
        long delayMs = (long) Mth.clamp(baseMs + jitter, 50.0, 250.0);
        nextActionTimeMs = System.currentTimeMillis() + delayMs;
    }

    private record RenderBox(AABB aabb, Color lineColor, Color sideColor, long startTime) {
    }

}
