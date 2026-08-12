package com.github.epsilon.modules.impl.player;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.NoSlowdown;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AutoMLG extends Module {

    public static final AutoMLG INSTANCE = new AutoMLG();

    private AutoMLG() {
        super("Auto MLG", Category.PLAYER);
    }

    private final IntSetting interactDelay = intSetting("Interact Delay", 60, 0, 300, 5);
    private final IntSetting collectDelayTicks = intSetting("Collect Delay Ticks", 2, 0, 10, 1);

    private boolean mlgCompleted = true;
    private final TimerUtils interactTimer = new TimerUtils();
    private int quickCollectDelayTicks = 0;

    private boolean shouldInteract = false;
    private int pendingSlot = -1;

    private Rot2f lockedRotation = null;
    private boolean waitingForRotation = false;

    private boolean grimShouldCollect = false;
    private Rot2f grimCollectRotation = null;
    private int grimCollectSlot = -1;
    private boolean grimWaitingCollectRotation = false;
    private final TimerUtils swapTimer = new TimerUtils();
    private boolean pendingSwapBack = false;

    @Override
    protected void onEnable() {
        mlgCompleted = true;
        quickCollectDelayTicks = 0;
        interactTimer.reset();
        swapTimer.reset();
        grimShouldCollect = false;
        grimCollectRotation = null;
        grimCollectSlot = -1;
        grimWaitingCollectRotation = false;
        pendingSwapBack = false;
        resetPending();
    }

    @Override
    protected void onDisable() {
        mlgCompleted = true;
        quickCollectDelayTicks = 0;
        interactTimer.reset();
        swapTimer.reset();
        grimShouldCollect = false;
        grimCollectRotation = null;
        grimCollectSlot = -1;
        grimWaitingCollectRotation = false;
        pendingSwapBack = false;
        resetPending();
    }

    @EventHandler
    public void onTick(ClientTickEvent.Pre event) {
        if (nullCheck() || Scaffold.INSTANCE.isEmergencyPlacementActive()) return;

        if ((isFalling() || mc.player.isInWater() || mc.player.isInLava()) && !mlgCompleted) {
            mc.player.setSprinting(false);
            mc.options.keySprint.setDown(false);
        }

        if (pendingSwapBack && swapTimer.passedMillise(200)) {
            InvUtils.swapBack();
            pendingSwapBack = false;
        }

        if (grimShouldCollect) {
            if (quickCollectDelayTicks > 0) {
                quickCollectDelayTicks--;
            } else if (grimWaitingCollectRotation && grimCollectRotation != null) {
                RotationManager.INSTANCE.setRotations(grimCollectRotation, 180.0f, Priority.High);

                if (isFacing(grimCollectRotation, 2.0f, 2.5f)) {
                    InvUtils.swap(grimCollectSlot, true);
                    if (useItem()) {
                        mc.player.swing(InteractionHand.MAIN_HAND);
                        pendingSwapBack = true;
                        swapTimer.reset();
                        completeMlgCycle();
                    } else {
                        grimWaitingCollectRotation = false;
                        grimCollectRotation = null;
                        grimCollectSlot = -1;
                    }
                }
            } else {
                if (InvUtils.findInHotbar(Items.WATER_BUCKET).found()) {
                    InvUtils.swapBack();
                    completeMlgCycle();
                    return;
                }

                FindItemResult bucket = InvUtils.findInHotbar(Items.BUCKET);
                if (bucket.found()) {
                    BlockPos waterPos = grimFindCollectableWater();
                    if (waterPos != null) {
                        Vec3 eyesPos = mc.player.getEyePosition();
                        double hitX = Mth.clamp(eyesPos.x, waterPos.getX(), waterPos.getX() + 1.0);
                        double hitZ = Mth.clamp(eyesPos.z, waterPos.getZ(), waterPos.getZ() + 1.0);
                        Vec3 hitVec = new Vec3(hitX, waterPos.getY() + 0.875, hitZ);
                        Rot2f rotation = RotationUtils.calculate(hitVec);
                        RotationManager.INSTANCE.setRotations(rotation, 180.0f, Priority.High);
                        grimCollectRotation = rotation;
                        grimCollectSlot = bucket.slot();
                        grimWaitingCollectRotation = true;
                    }
                }
            }
            return;
        }

        if (waitingForRotation && lockedRotation != null) {
            RotationManager.INSTANCE.setRotations(lockedRotation, 180.0f, Priority.High);

            if (!isFacing(lockedRotation, 1.6f, 2.0f)) {
                return;
            }

            if (shouldInteract && pendingSlot != -1) {
                InvUtils.swap(pendingSlot, true);
                if (interactTimer.passedMillise(interactDelay.getValue()) && useItem()) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    interactTimer.reset();
                    quickCollectDelayTicks = collectDelayTicks.getValue();
                    grimShouldCollect = true;
                    resetPending();
                }
            }
            return;
        }

        if (isFalling() && !grimShouldCollect) {
            mlgCompleted = false;

            FindItemResult waterBucket = InvUtils.findInHotbar(Items.WATER_BUCKET);

            if (waterBucket.found()) {
                BlockPos bestPos = getBestPos();
                if (bestPos != null) {
                    if (shouldSkipMlgPlacement(bestPos)) {
                        mlgCompleted = true;
                        resetPending();
                        return;
                    }

                    double dist = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(bestPos).add(0, 0.5, 0));

                    if (dist < 10) {
                        Vec3 targetVec = new Vec3(bestPos.getX() + 0.5, bestPos.getY() + 1.0, bestPos.getZ() + 0.5);
                        Rot2f rotation = RotationUtils.calculate(targetVec);

                        RotationManager.INSTANCE.setRotations(rotation, 180.0f, Priority.High);

                        if (rotation.getPitch() > 45) {
                            Vec3 eyesPos = mc.player.getEyePosition();
                            BlockPos neighbor = bestPos.below();

                            double hitX = Mth.clamp(eyesPos.x, neighbor.getX(), neighbor.getX() + 1.0);
                            double hitZ = Mth.clamp(eyesPos.z, neighbor.getZ(), neighbor.getZ() + 1.0);
                            Vec3 hitVec = new Vec3(hitX, neighbor.getY() + 1.0, hitZ);

                            if (eyesPos.distanceTo(hitVec) > 4.5) {
                                return;
                            }

                            Rot2f preciseRotation = RotationUtils.calculate(hitVec);
                            RotationManager.INSTANCE.setRotations(preciseRotation, 180.0f, Priority.High);

                            NoSlowdown noSlowdown = NoSlowdown.INSTANCE;
                            if (noSlowdown.isWorking()) {
                                NotificationManager.INSTANCE.warning(noSlowdown.getTranslatedName(), EpsilonTranslations.Notifications.NO_SLOWDOWN_DISABLED_WATER.getTranslatedName());
                                noSlowdown.stop();
                            }

                            pendingSlot = waterBucket.slot();
                            shouldInteract = true;

                            lockedRotation = preciseRotation;
                            waitingForRotation = true;
                        }
                    }
                }
            }
        }
    }

    private BlockPos grimFindCollectableWater() {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eyesPos = mc.player.getEyePosition();
        BlockPos base = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos check = base.offset(x, y, z);
                    FluidState fluidState = mc.level.getFluidState(check);
                    if (!fluidState.isSourceOfType(Fluids.WATER)) continue;

                    double hitX = Mth.clamp(eyesPos.x, check.getX(), check.getX() + 1.0);
                    double hitZ = Mth.clamp(eyesPos.z, check.getZ(), check.getZ() + 1.0);
                    Vec3 targetVec = new Vec3(hitX, check.getY() + 0.875, hitZ);
                    double distance = eyesPos.distanceToSqr(targetVec);

                    if (distance >= bestDistance) continue;
                    if (!canSeePosition(eyesPos, targetVec, check)) continue;

                    bestDistance = distance;
                    bestPos = check;
                }
            }
        }

        return bestPos;
    }

    private boolean canSeePosition(Vec3 from, Vec3 to, BlockPos targetBlock) {
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, mc.player);
        BlockHitResult result = mc.level.clip(context);
        if (result.getType() == HitResult.Type.MISS) return true;
        return result.getBlockPos().equals(targetBlock);
    }

    private boolean shouldSkipMlgPlacement(BlockPos bestPos) {
        BlockPos landingPos = bestPos.below();
        BlockState landingState = mc.level.getBlockState(landingPos);
        if (landingState.isAir()) return false;
        if (!landingState.hasProperty(BlockStateProperties.WATERLOGGED)) return false;

        if (landingState.getBlock() instanceof SlabBlock) {
            SlabType slabType = landingState.getValue(SlabBlock.TYPE);
            if (slabType == SlabType.BOTTOM) {
                return false;
            }
        }

        return true;
    }

    private void completeMlgCycle() {
        mlgCompleted = true;
        quickCollectDelayTicks = 0;
        grimShouldCollect = false;
        grimCollectRotation = null;
        grimCollectSlot = -1;
        grimWaitingCollectRotation = false;
        resetPending();
    }

    private BlockPos getBestPos() {
        Vec3 velocity = mc.player.getDeltaMovement();
        Vec3 predictedPos = new Vec3(mc.player.getX() + velocity.x * 3, mc.player.getY(), mc.player.getZ() + velocity.z * 3);

        BlockPos base = BlockPos.containing(predictedPos.x, mc.player.getY() - 1, predictedPos.z);
        BlockPos direct = BlockPos.containing(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ());

        if (!mc.level.getBlockState(base).isAir()) {
            return base.above();
        }
        if (!mc.level.getBlockState(direct).isAir()) {
            return direct.above();
        }

        for (int i = 0; i <= 20; i++) {
            BlockPos check = base.below(i);
            if (!mc.level.getBlockState(check).isAir()) {
                return check.above();
            }
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                for (int i = 0; i <= 20; i++) {
                    BlockPos check = base.offset(x, -i, z);
                    if (!mc.level.getBlockState(check).isAir()) {
                        return check.above();
                    }
                }
            }
        }

        BlockPos fallbackBase = BlockPos.containing(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ());
        for (int i = 0; i <= 20; i++) {
            BlockPos check = fallbackBase.below(i);
            if (!mc.level.getBlockState(check).isAir()) {
                return check.above();
            }
        }

        return null;
    }

    private boolean isFalling() {
        boolean falling = mc.player.fallDistance > 3.0 && !mc.player.onGround();
        if (mc.player.getDeltaMovement().y > 0) return false;
        return falling;
    }

    private boolean isFacing(Rot2f target, float yawTolerance, float pitchTolerance) {
        Rot2f current = RotationManager.INSTANCE.getRotation();
        float yawDiff = Math.abs(Mth.wrapDegrees(current.getYaw() - target.getYaw()));
        float pitchDiff = Math.abs(Mth.wrapDegrees(current.getPitch() - target.getPitch()));
        return yawDiff <= yawTolerance && pitchDiff <= pitchTolerance;
    }

    private boolean useItem() {
        InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        return result.consumesAction();
    }

    private void resetPending() {
        shouldInteract = false;
        pendingSlot = -1;
        lockedRotation = null;
        waitingForRotation = false;
    }

}
