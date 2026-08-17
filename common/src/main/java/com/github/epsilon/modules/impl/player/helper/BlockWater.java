package com.github.epsilon.modules.impl.player.helper;

import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.Optional;

import static com.github.epsilon.Constants.mc;

public class BlockWater extends HelperBase {

    private static final int SEARCH_RADIUS = 5;
    private static final double SEARCH_RANGE_SQUARED = 4.5 * 4.5;

    private enum State {
        NONE,
        WATER,
        WATER_SUPPORT
    }

    private State state = State.NONE;
    private BlockPos targetPos;
    private PlacementData pendingPlacement;
    private Rot2f targetRotation;
    private int savedSlot = -1;

    public BlockWater() {
        super("Block Water");
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onMotion() {
        if (mc.player.isInWater()) {
            reset();
            return;
        }

        if (pendingPlacement != null) {
            if (!BlockUtils.canPlaceAt(pendingPlacement.blockPos().relative(pendingPlacement.direction()))) {
                targetPos = null;
                pendingPlacement = null;
                targetRotation = null;
                state = State.NONE;
                return;
            }
            if (!RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), pendingPlacement.blockPos(), pendingPlacement.direction())) {
                if (Helper.isRotationAtTarget(targetRotation)) reset();
                return;
            }
            placeBlock();
            pendingPlacement = null;
            if (state != State.WATER_SUPPORT) reset();
            return;
        }

        switch (state) {
            case NONE -> {
                findTargetPos();
                if (targetPos != null) state = State.WATER;
            }
            case WATER -> {
                if (!isTargetWater()) {
                    reset();
                    return;
                }
                if (mc.level.getBlockState(targetPos.below()).isAir() || !tryFindPlacement()) {
                    state = State.WATER_SUPPORT;
                }
            }
            case WATER_SUPPORT -> {
                if (!isTargetWater()) {
                    reset();
                    return;
                }
                BlockPos supportPos = targetPos.below();
                if (mc.level.getBlockState(supportPos).isSolid()) {
                    state = State.WATER;
                    tryFindPlacement();
                    return;
                }
                findSuitableFace(supportPos).ifPresentOrElse(data -> {
                    targetRotation = RotationUtils.calculate(data.hitVec());
                    pendingPlacement = data;
                }, this::reset);
            }
        }
    }

    private boolean tryFindPlacement() {
        Optional<PlacementData> placement = findSuitableFace(targetPos);
        if (placement.isEmpty()) {
            pendingPlacement = null;
            targetRotation = null;
            return false;
        }
        pendingPlacement = placement.get();
        targetRotation = RotationUtils.calculate(pendingPlacement.hitVec());
        return true;
    }

    private void placeBlock() {
        if (pendingPlacement == null) return;
        FindItemResult block = findBlock();
        if (!block.found()) {
            reset();
            return;
        }
        InteractionHand hand = block.getHand();
        if (block.isMainHand()) {
            if (savedSlot == -1) savedSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(block.slot());
        }
        BlockHitResult hit = new BlockHitResult(pendingPlacement.hitVec(), pendingPlacement.direction(), pendingPlacement.blockPos(), false);
        mc.gameMode.useItemOn(mc.player, hand, hit);
        mc.player.swing(hand);
    }

    @Override
    public void onRender() {
        if (state == State.NONE || targetPos == null) return;
        Render3DScheduler.INSTANCE.addFilledBox(new AABB(targetPos), new Color(0, 128, 255, 64));
        Render3DScheduler.INSTANCE.addOutlineBox(new AABB(targetPos), new Color(0, 128, 255, 190));
    }

    private void findTargetPos() {
        BlockPos playerPos = mc.player.blockPosition();
        Vec3 eyePos = mc.player.getEyePosition();
        targetPos = null;
        double closest = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos candidate = playerPos.offset(dx, dy, dz);
                    if (Helper.hasWaterPlacement(candidate) || !mc.level.getBlockState(candidate).is(Blocks.WATER) || !mc.level.getFluidState(candidate).isSource())
                        continue;
                    Vec3 candidateCenter = Vec3.atCenterOf(candidate);
                    double distance = candidateCenter.distanceToSqr(eyePos);
                    if (distance > SEARCH_RANGE_SQUARED) continue;
                    if (!BlockUtils.canPlaceAt(candidate) || !RaytraceUtils.canSeePointFrom(eyePos, candidateCenter))
                        continue;
                    if (distance < closest) {
                        closest = distance;
                        targetPos = candidate;
                    }
                }
            }
        }
    }

    private FindItemResult findBlock() {
        FindItemResult cobblestone = InvUtils.findInHotbar(Items.COBBLESTONE);
        if (cobblestone.found()) return cobblestone;
        return InvUtils.findInHotbar(stack -> {
            if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
            Block block = blockItem.getBlock();
            return block.defaultBlockState().isSolid();
        });
    }

    private Optional<PlacementData> findSuitableFace(BlockPos blockPos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = blockPos.relative(direction);
            if (mc.level.getBlockState(neighbor).isSolid() && !mc.level.getBlockState(neighbor).is(Blocks.WATER)) {
                Vec3 hitVec = Vec3.atCenterOf(neighbor).add(direction.getOpposite().getStepX() * 0.5, direction.getOpposite().getStepY() * 0.5, direction.getOpposite().getStepZ() * 0.5);
                if (BlockUtils.canPlaceAt(blockPos) && mc.player.getEyePosition().distanceToSqr(hitVec) <= SEARCH_RANGE_SQUARED && canSeeBlockFace(neighbor, direction.getOpposite())) {
                    return Optional.of(new PlacementData(neighbor, direction.getOpposite(), hitVec));
                }
            }
            if (direction != Direction.DOWN) continue;
            BlockPos below = neighbor;
            for (int i = 0; i < 8 && mc.level.getBlockState(below).is(Blocks.WATER); i++) {
                below = below.below();
            }
            if (!mc.level.getBlockState(below).isSolid() || mc.level.getBlockState(below).is(Blocks.WATER)) continue;
            Vec3 hitVec = Vec3.atCenterOf(below).add(0.0, 0.5, 0.0);
            if (BlockUtils.canPlaceAt(below.above()) && mc.player.getEyePosition().distanceToSqr(hitVec) <= SEARCH_RANGE_SQUARED && canSeeBlockFace(below, Direction.UP)) {
                return Optional.of(new PlacementData(below, Direction.UP, hitVec));
            }
        }
        return Optional.empty();
    }

    private boolean canSeeBlockFace(BlockPos blockPos, Direction direction) {
        Vec3 target = Vec3.atCenterOf(blockPos).add(direction.getStepX() * 0.49, direction.getStepY() * 0.49, direction.getStepZ() * 0.49);
        BlockHitResult hit = mc.level.clip(new ClipContext(mc.player.getEyePosition(), target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(blockPos) && hit.getLocation().distanceToSqr(target) < 0.25;
    }

    private boolean isTargetWater() {
        return targetPos != null && mc.level.getBlockState(targetPos).is(Blocks.WATER);
    }

    private void reset() {
        targetPos = null;
        pendingPlacement = null;
        targetRotation = null;
        state = State.NONE;
        if (savedSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(savedSlot);
        }
        savedSlot = -1;
    }

    @Override
    public boolean isActive() {
        return targetRotation != null;
    }

    @Override
    public Rot2f getTargetRotation() {
        return targetRotation;
    }

    private record PlacementData(BlockPos blockPos, Direction direction, Vec3 hitVec) {
    }

}
