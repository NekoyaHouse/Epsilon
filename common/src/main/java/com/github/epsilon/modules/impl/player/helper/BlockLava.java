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

public class BlockLava extends HelperBase {

    private static final int SEARCH_RADIUS = 5;
    private static final double SEARCH_RANGE_SQUARED = 4.5 * 4.5;
    private static final double[] FACE_SAMPLE_OFFSETS = {0.0, -0.3, 0.3, -0.45, 0.45};

    private enum State {
        NONE,
        LAVA,
        LAVA_SUPPORT
    }

    private State state = State.NONE;
    private BlockPos targetPos;
    private PlacementData pendingPlacement;
    private Rot2f targetRotation;
    private int savedSlot = -1;

    public BlockLava() {
        super("Block Lava");
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
        if (mc.player.isOnFire() || mc.player.isInLava()) {
            reset();
            return;
        }

        if (pendingPlacement != null) {
            if (!BlockUtils.canPlaceAt(pendingPlacement.placementPos())) {
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
            if (state != State.LAVA_SUPPORT) reset();
            return;
        }

        switch (state) {
            case NONE -> {
                findTargetPos();
                if (targetPos != null) state = State.LAVA;
            }
            case LAVA -> {
                if (!isTargetLava()) {
                    reset();
                    return;
                }
                if (!tryFindPlacement()) {
                    state = State.LAVA_SUPPORT;
                }
            }
            case LAVA_SUPPORT -> {
                if (!isTargetLava()) {
                    reset();
                    return;
                }
                if (tryFindPlacement()) {
                    state = State.LAVA;
                    return;
                }
                if (pendingPlacement != null) return;
                findSupportPlacement(targetPos).ifPresentOrElse(data -> {
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

    private Optional<PlacementData> findSupportPlacement(BlockPos lavaPos) {
        for (Direction direction : Direction.values()) {
            BlockPos supportPos = lavaPos.relative(direction);
            Optional<PlacementData> placement = findSuitableFace(supportPos);
            if (placement.isPresent()) return placement;
        }
        return Optional.empty();
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
        Render3DScheduler.INSTANCE.addFilledBox(new AABB(targetPos), new Color(255, 165, 0, 64));
        Render3DScheduler.INSTANCE.addOutlineBox(new AABB(targetPos), new Color(255, 165, 0, 190));
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
                    if (Helper.hasLavaPlacement(candidate) || !mc.level.getBlockState(candidate).is(Blocks.LAVA) || !mc.level.getFluidState(candidate).isSource()) {
                        continue;
                    }
                    Vec3 candidateCenter = Vec3.atCenterOf(candidate);
                    double distance = candidateCenter.distanceToSqr(eyePos);
                    if (distance >= closest || distance > SEARCH_RANGE_SQUARED || !BlockUtils.canPlaceAt(candidate))
                        continue;
                    if (!hasPlacementFace(candidate)) continue;

                    closest = distance;
                    targetPos = candidate;
                }
            }
        }
    }

    private boolean hasPlacementFace(BlockPos lavaPos) {
        return findSuitableFace(lavaPos).isPresent() || findSupportPlacement(lavaPos).isPresent();
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
        if (!BlockUtils.canPlaceAt(blockPos)) return Optional.empty();

        Vec3 eyePos = mc.player.getEyePosition();
        PlacementData best = null;
        double closest = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = blockPos.relative(direction);
            if (!isSolidSupport(neighbor)) continue;

            PlacementData placement = createPlacement(neighbor, direction.getOpposite(), eyePos);
            if (placement == null) continue;

            double distance = eyePos.distanceToSqr(placement.hitVec());
            if (distance <= SEARCH_RANGE_SQUARED && distance < closest) {
                closest = distance;
                best = placement;
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isSolidSupport(BlockPos pos) {
        return mc.level.getBlockState(pos).isSolid() && !mc.level.getBlockState(pos).is(Blocks.LAVA);
    }

    private PlacementData createPlacement(BlockPos support, Direction face, Vec3 eyePos) {
        Vec3 hitVec = findFaceHitVec(support, face, eyePos);
        return hitVec == null ? null : new PlacementData(support, face, hitVec);
    }

    private Vec3 findFaceHitVec(BlockPos support, Direction face, Vec3 eyePos) {
        Vec3 center = Vec3.atCenterOf(support).add(
                face.getStepX() * 0.5,
                face.getStepY() * 0.5,
                face.getStepZ() * 0.5
        );

        for (double first : FACE_SAMPLE_OFFSETS) {
            for (double second : FACE_SAMPLE_OFFSETS) {
                Vec3 sample = switch (face.getAxis()) {
                    case X -> center.add(0.0, first, second);
                    case Y -> center.add(first, 0.0, second);
                    case Z -> center.add(first, second, 0.0);
                };
                Vec3 clipEnd = sample.subtract(
                        face.getStepX() * 0.01,
                        face.getStepY() * 0.01,
                        face.getStepZ() * 0.01
                );
                BlockHitResult hit = mc.level.clip(new ClipContext(
                        eyePos,
                        clipEnd,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        mc.player
                ));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support) && hit.getDirection() == face) {
                    return hit.getLocation();
                }
            }
        }
        return null;
    }

    private boolean isTargetLava() {
        return targetPos != null && mc.level.getBlockState(targetPos).is(Blocks.LAVA);
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
        private BlockPos placementPos() {
            return blockPos.relative(direction);
        }
    }

}
