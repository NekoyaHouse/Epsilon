package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AutoMLG extends Module {

    public static final AutoMLG INSTANCE = new AutoMLG();

    private AutoMLG() {
        super("Auto MLG", Category.PLAYER);
    }

    private enum MLGMode {
        Grim,
        Hypixel
    }

    private final EnumSetting<MLGMode> mode = enumSetting("MLG Mode", MLGMode.Grim);
    private final IntSetting interactDelay = intSetting("Interact Delay", 60, 0, 300, 5, () -> mode.is(MLGMode.Grim));
    private final IntSetting collectDelayTicks = intSetting("Collect Delay Ticks", 2, 0, 10, 1, () -> mode.is(MLGMode.Grim));

    private boolean mlgCompleted = true;
    private BlockPos placedWaterPos = null;
    private final TimerUtils interactTimer = new TimerUtils();
    private int quickCollectDelayTicks = 0;

    private boolean shouldInteract = false;
    private int pendingSlot = -1;
    private BlockPos pendingBestPos = null;

    private Rot2f lockedRotation = null;
    private boolean waitingForRotation = false;

    private boolean grimShouldCollect = false;
    private Rot2f grimCollectRotation = null;
    private int grimCollectSlot = -1;
    private boolean grimWaitingCollectRotation = false;
    private final TimerUtils swapTimer = new TimerUtils();
    private boolean pendingSwapBack = false;

    private int hypixelTicksExisted = -1;
    private boolean hypixelInteractRequired = false;
    private int hypixelOldSlot = -1;
    private boolean hypixelHandleStopMove = false;
    private boolean hypixelShouldReceive = false;
    private HypixelPlaceData hypixelLastData;
    private int hypixelTicksSinceTeleport = 0;
    private boolean hypixelPacketOnGround = false;
    private Rot2f hypixelTargetRotation = null;

    public boolean isGrimMlgMode() {
        return isEnabled() && mode.is(MLGMode.Grim) && !Scaffold.INSTANCE.isEmergencyPlacementActive();
    }

    @Override
    public String getInfo() {
        return mode.getTranslatedValue();
    }

    @Override
    protected void onEnable() {
        mlgCompleted = true;
        placedWaterPos = null;
        quickCollectDelayTicks = 0;
        interactTimer.reset();
        swapTimer.reset();
        grimShouldCollect = false;
        grimCollectRotation = null;
        grimCollectSlot = -1;
        grimWaitingCollectRotation = false;
        pendingSwapBack = false;
        resetPending();
        resetHypixel();
    }

    @Override
    protected void onDisable() {
        mlgCompleted = true;
        placedWaterPos = null;
        quickCollectDelayTicks = 0;
        interactTimer.reset();
        swapTimer.reset();
        grimShouldCollect = false;
        grimCollectRotation = null;
        grimCollectSlot = -1;
        grimWaitingCollectRotation = false;
        pendingSwapBack = false;
        resetPending();
        resetHypixel();
    }

    private void resetPending() {
        shouldInteract = false;
        pendingSlot = -1;
        pendingBestPos = null;
        lockedRotation = null;
        waitingForRotation = false;
    }

    private void resetHypixel() {
        hypixelInteractRequired = false;
        hypixelShouldReceive = false;
        hypixelHandleStopMove = false;
        hypixelTicksExisted = -1;
        hypixelOldSlot = -1;
        hypixelLastData = null;
        hypixelTicksSinceTeleport = 0;
        hypixelPacketOnGround = false;
        hypixelTargetRotation = null;
    }

    private boolean isFacing(Rot2f target, float yawTolerance, float pitchTolerance) {
        Rot2f current = Managers.ROTATION.getRotation();
        float yawDiff = Math.abs(Mth.wrapDegrees(current.getYaw() - target.getYaw()));
        float pitchDiff = Math.abs(Mth.wrapDegrees(current.getPitch() - target.getPitch()));
        return yawDiff <= yawTolerance && pitchDiff <= pitchTolerance;
    }

    private boolean useItemWithManagedRotation() {
        Rot2f rotation = Managers.ROTATION.getRotation();
        float playerYaw = mc.player.getYRot();
        float playerPitch = mc.player.getXRot();

        mc.player.setYRot(rotation.getYaw());
        mc.player.setXRot(rotation.getPitch());
        try {
            InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return result.consumesAction();
        } finally {
            mc.player.setYRot(playerYaw);
            mc.player.setXRot(playerPitch);
        }
    }

    @EventHandler
    public void onTick(ClientTickEvent.Pre event) {
        if (nullCheck() || Scaffold.INSTANCE.isEmergencyPlacementActive()) return;

        if (mode.is(MLGMode.Hypixel)) {
            return;
        }

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
                Managers.ROTATION.setRotations(grimCollectRotation, 180.0f, Priority.High);

                if (isFacing(grimCollectRotation, 2.0f, 2.5f)) {
                    InvUtils.swap(grimCollectSlot, true);
                    if (useItemWithManagedRotation()) {
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
                        Managers.ROTATION.setRotations(rotation, 180.0f, Priority.High);
                        grimCollectRotation = rotation;
                        grimCollectSlot = bucket.slot();
                        grimWaitingCollectRotation = true;
                    }
                }
            }
            return;
        }

        if (waitingForRotation && lockedRotation != null) {
            Managers.ROTATION.setRotations(lockedRotation, 180.0f, Priority.High);

            if (!isFacing(lockedRotation, 1.6f, 2.0f)) {
                return;
            }

            if (shouldInteract && pendingSlot != -1) {
                InvUtils.swap(pendingSlot, true);
                if (interactTimer.passedMillise(interactDelay.getValue()) && useItemWithManagedRotation()) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    interactTimer.reset();

                    if (pendingBestPos != null) {
                        placedWaterPos = pendingBestPos;
                    }

                    quickCollectDelayTicks = collectDelayTicks.getValue();
                    grimShouldCollect = true;
                    resetPending();
                }
            }
            return;
        }

        if (isFalling() && !grimShouldCollect) {
            mlgCompleted = false;
            placedWaterPos = null;

            FindItemResult waterBucket = InvUtils.findInHotbar(Items.WATER_BUCKET);

            if (waterBucket.found()) {
                BlockPos bestPos = getBestPos();
                if (bestPos != null) {
                    if (shouldSkipMlgPlacement(bestPos)) {
                        mlgCompleted = true;
                        resetPending();
                        return;
                    }

                    double dist = mc.player.getEyePosition().distanceTo(bestPos.getCenter().add(0, 0.5, 0));

                    if (dist < 10) {
                        Vec3 targetVec = new Vec3(bestPos.getX() + 0.5, bestPos.getY() + 1.0, bestPos.getZ() + 0.5);
                        Rot2f rotation = RotationUtils.calculate(targetVec);

                        Managers.ROTATION.setRotations(rotation, 180.0f, Priority.High);

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
                            Managers.ROTATION.setRotations(preciseRotation, 180.0f, Priority.High);

                            pendingSlot = waterBucket.slot();
                            shouldInteract = true;
                            pendingBestPos = bestPos;

                            lockedRotation = preciseRotation;
                            waitingForRotation = true;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onSendPosition(SendPositionEvent event) {
        if (Scaffold.INSTANCE.isEmergencyPlacementActive()) return;
        if (mode.is(MLGMode.Hypixel)) {
            onHypixelMotionPre();
        }
    }

    @EventHandler
    private void onAfterSendPosition(AfterSendPositionEvent event) {
        if (Scaffold.INSTANCE.isEmergencyPlacementActive()) return;
        onHypixelMotionPost();
    }

    @EventHandler
    public void onMoveInput(KeyboardInputEvent event) {
        if (Scaffold.INSTANCE.isEmergencyPlacementActive()) return;
        if (mode.is(MLGMode.Hypixel)) {
            if (hypixelLastData != null) {
                mc.player.setSprinting(false);
            }
        }
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            hypixelPacketOnGround = packet.isOnGround();
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            hypixelTicksSinceTeleport = 0;
        }
    }

    private void onHypixelTick() {
        if (mc.player.isCreative()) return;
        if (mc.player.isUsingItem() || mc.screen != null) return;

        hypixelTicksSinceTeleport++;

        if (hypixelTicksSinceTeleport < 3) return;

        boolean shouldMLG = mc.player.fallDistance > mc.player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) && !mc.player.isInWater() && hypixelNextTickWillLanding();

        if (hypixelTicksExisted >= 0) {
            hypixelTicksExisted--;
        }

        if (shouldMLG) {
            int waterBucketSlot = hypixelFindSlot(Items.WATER_BUCKET);
            if (waterBucketSlot == -1) {
                shouldMLG = false;
            }
            hypixelTicksExisted = 1;
            hypixelHandleStopMove = true;
            hypixelPlaceWaterBucket(waterBucketSlot);
        }
        if (!shouldMLG && hypixelHasEmptyBucket() && hypixelShouldReceive && hypixelTicksExisted <= 0) {
            hypixelRetrieveWaterBlock();
        }

        if (hypixelHandleStopMove) {
            if (!mc.player.isInWater() && hypixelClientOnGround()) {
                hypixelHandleStopMove = false;
            }
        }
    }

    private void onHypixelMotionPre() {
        onHypixelTick();
        if (hypixelInteractRequired && hypixelTargetRotation != null) {
            Managers.ROTATION.setRotations(hypixelTargetRotation, 180.0f, Priority.High);
        }
    }

    private void onHypixelMotionPost() {
        if (hypixelTargetRotation == null) return;

        if (hypixelInteractRequired) {
            if (!isFacing(hypixelTargetRotation, 2.0f, 2.5f)) return;

            Rot2f interactionRotation = Managers.ROTATION.getRotation();
            BlockHitResult result = hypixelRayCast(interactionRotation, 4.5);
            if (result.getType() != HitResult.Type.BLOCK) return;

            hypixelInteractRequired = false;
            mc.hitResult = result;

            if (useItemWithManagedRotation()) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }

            if (mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot()).is(Items.BUCKET)) {
                hypixelShouldReceive = true;
            }
            if (hypixelOldSlot != -1) {
                mc.player.getInventory().setSelectedSlot(hypixelOldSlot);
                hypixelOldSlot = -1;
            }
        }
    }

    private void hypixelRetrieveWaterBlock() {
        if (mc.player == null) return;

        hypixelOldSlot = mc.player.getInventory().getSelectedSlot();
        int emptyBucketSlot = hypixelFindSlot(Items.BUCKET);
        if (emptyBucketSlot == -1) return;
        BlockPos pos = hypixelFindScoopableWaterBlock();
        if (pos == null) {
            return;
        }
        var r = hypixelGetLookAtWaterBlock(pos);

        if (!mc.player.getMainHandItem().is(Items.BUCKET))
            mc.player.getInventory().setSelectedSlot(emptyBucketSlot);
        hypixelInteractItem(r);
        hypixelShouldReceive = false;
    }

    private BlockPos hypixelFindScoopableWaterBlock() {
        if (mc.player == null) return null;

        Level level = mc.player.level();
        BlockPos playerBlockPos = mc.player.blockPosition();
        double maxReachDistance = mc.player.blockInteractionRange();
        int reach = (int) Math.ceil(maxReachDistance);
        List<BlockPos> possible = new ArrayList<>();
        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    BlockPos currentPos = playerBlockPos.offset(x, y, z);
                    FluidState fluidState = level.getFluidState(currentPos);
                    if (fluidState.isSourceOfType(Fluids.WATER)) {
                        possible.add(currentPos);
                    }
                }
            }
        }
        possible.removeIf(blockPos -> {
            Rot2f rotation = hypixelGetLookAtWaterBlock(blockPos);
            return hypixelRayCast(rotation, 4.5).getType() != HitResult.Type.BLOCK;
        });
        if (possible.isEmpty()) return null;
        return possible.getFirst();
    }

    private boolean hypixelNextTickWillLanding() {
        return !hypixelIsAirBlocksBelow(hypixelGetYMotion());
    }

    private boolean hypixelIsAirBlocksBelow(int high) {
        if (mc.player == null || mc.level == null) return false;

        List<HypixelPlaceData> possibleBoxes = new ArrayList<>();

        for (int x = -hypixelGetXMotion(); x <= hypixelGetXMotion(); x++) {
            for (int z = -hypixelGetZMotion(); z <= hypixelGetZMotion(); z++) {
                for (int y = 0; y <= high; y++) {
                    var pos = mc.player.blockPosition().offset(x, -y, z);
                    var state = mc.level.getBlockState(pos);

                    var bb = new AABB(pos).setMaxY(pos.getY() + 3);
                    if (bb.intersects(mc.player.getBoundingBox()) && !state.isAir()) {
                        possibleBoxes.add(new HypixelPlaceData(pos, bb));
                    }
                }
            }
        }

        return possibleBoxes.isEmpty();
    }

    private int hypixelGetXMotion() {
        if (mc.player == null) return -1;
        return (int) Math.ceil(Mth.square(mc.player.getDeltaMovement().x));
    }

    private int hypixelGetYMotion() {
        if (mc.player == null) return -1;
        return (int) Math.ceil(Mth.square(mc.player.getDeltaMovement().y));
    }

    private int hypixelGetZMotion() {
        if (mc.player == null) return -1;
        return (int) Math.ceil(Mth.square(mc.player.getDeltaMovement().z));
    }

    private boolean hypixelHasEmptyBucket() {
        if (mc.player == null) return false;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.BUCKET)) {
                return true;
            }
        }
        return false;
    }

    private boolean hypixelClientOnGround() {
        if (mc.player == null) return false;
        return hypixelPacketOnGround && mc.player.onGround();
    }

    private void hypixelInteractItem(Rot2f rotation) {
        Managers.ROTATION.setRotations(rotation, 180.0f, Priority.High);
        hypixelTargetRotation = rotation;
        hypixelInteractRequired = true;
    }

    private HypixelPlaceData hypixelFindBestPlacePos() {
        if (mc.player == null || mc.level == null) return null;

        List<HypixelPlaceData> possibleBoxes = new ArrayList<>();

        AABB playerBox = mc.player.getBoundingBox().move(mc.player.getDeltaMovement());

        for (int x = -hypixelGetXMotion(); x <= hypixelGetXMotion(); x++) {
            for (int z = -hypixelGetZMotion(); z <= hypixelGetZMotion(); z++) {
                for (int y = 0; y <= hypixelGetYMotion(); y++) {
                    var pos = mc.player.blockPosition().offset(x, -y, z);
                    var state = mc.level.getBlockState(pos);

                    var bb = new AABB(pos).setMaxY(pos.getY() + 3);
                    if (bb.intersects(playerBox) && !state.isAir()) {
                        possibleBoxes.add(new HypixelPlaceData(pos, bb));
                    }
                }
            }
        }

        if (possibleBoxes.isEmpty()) return null;

        possibleBoxes.sort(Comparator.comparingDouble(p -> p.pos.getY()));
        Collections.reverse(possibleBoxes);
        var best = possibleBoxes.getFirst();

        possibleBoxes.removeIf(p -> p.pos.getY() != best.pos().getY());

        possibleBoxes.sort(Comparator.comparingDouble(p -> Vec3.atLowerCornerOf(p.pos()).distanceToSqr(
                playerBox.getCenter().with(Direction.Axis.Y, playerBox.minY)
        )));
        possibleBoxes.sort(Comparator.comparing(p -> !p.bb.contains(mc.player.position())));
        return possibleBoxes.getFirst();
    }

    private Rot2f hypixelGetLookAtWaterBlock(BlockPos targetPos) {
        if (mc.player == null || targetPos == null) {
            return new Rot2f(0, 0);
        }

        return RotationUtils.calculate(Vec3.atCenterOf(targetPos));
    }

    private void hypixelPlaceWaterBucket(int waterBucketSlot) {
        if (waterBucketSlot == -1 || mc.player == null) {
            return;
        }
        hypixelOldSlot = mc.player.getInventory().getSelectedSlot();

        var best = hypixelFindBestPlacePos();
        hypixelLastData = best;

        if (best == null) return;

        var r = hypixelGetLookAtWaterBlock(best.pos());

        if (!mc.player.getMainHandItem().is(Items.WATER_BUCKET))
            mc.player.getInventory().setSelectedSlot(waterBucketSlot);
        hypixelInteractItem(r);
        hypixelShouldReceive = true;
    }

    private int hypixelFindSlot(Item item) {
        if (mc.player == null) return -1;
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && stack.getItem() == item) {
                slot = i;
            }
        }
        return slot;
    }

    private BlockHitResult hypixelRayCast(Rot2f rotation, double reach) {
        Vec3 eyesPos = mc.player.getEyePosition();
        Vec3 rotationVec = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        Vec3 endPos = eyesPos.add(rotationVec.x * reach, rotationVec.y * reach, rotationVec.z * reach);
        return mc.level.clip(new ClipContext(eyesPos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
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
        if (result == null || result.getType() == HitResult.Type.MISS) return true;
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
        placedWaterPos = null;
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

    private BlockPos getWaterPos() {
        BlockPos base = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos check = base.offset(x, y, z);
                    if (mc.level.getBlockState(check).getBlock() instanceof LiquidBlock) return check;
                }
            }
        }

        Vec3 velocity = mc.player.getDeltaMovement();
        Vec3 predicted = new Vec3(mc.player.getX() + velocity.x * 2, mc.player.getY() + velocity.y * 2, mc.player.getZ() + velocity.z * 2);
        BlockPos predictedBase = BlockPos.containing(predicted);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = -1; y <= 1; y++) {
                    BlockPos check = predictedBase.offset(x, y, z);
                    if (mc.level.getBlockState(check).getBlock() instanceof LiquidBlock) return check;
                }
            }
        }

        return null;
    }

    private boolean isFalling() {
        boolean falling = mc.player.fallDistance > 3.0 && !mc.player.onGround();
        if (mc.player.getDeltaMovement().y > 0) return false;
        return falling;
    }

    private record HypixelPlaceData(BlockPos pos, AABB bb) {
    }

}
