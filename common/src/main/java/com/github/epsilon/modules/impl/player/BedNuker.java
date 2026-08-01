package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BedNuker extends Module {

    public static final BedNuker INSTANCE = new BedNuker();

    private BedNuker() {
        super("Bed Nuker", Category.PLAYER);
    }

    private final DoubleSetting range = doubleSetting("Range", 4.5, 1.0, 6.0, 0.1);
    private final BoolSetting throughWalls = boolSetting("Through Walls", false);
    private final BoolSetting rotate = boolSetting("Rotate", true);
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 180, 1, 180, 1, rotate::getValue);
    private final BoolSetting swingHand = boolSetting("Swing Hand", true);

    private BlockPos currentTarget;
    private Direction currentFace;
    private boolean startedBreaking;
    private BlockPos trackedBedPos;
    private Direction trackedBedFace;

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (shouldPauseBreaking()) {
            clearBreakingState();
            return;
        }

        TargetData nextTarget = findTarget();
        if (nextTarget == null) {
            resetBreaking();
            return;
        }

        if (!nextTarget.pos.equals(currentTarget) || currentFace != nextTarget.face) {
            resetBreaking();
            currentTarget = nextTarget.pos;
            currentFace = nextTarget.face;
        }

        trackedBedPos = nextTarget.sourceBedPos;
        trackedBedFace = nextTarget.sourceBedFace;

        if (rotate.getValue()) {
            if (nextTarget.bedTarget) {
                Managers.ROTATION.setRotations(nextTarget.rotation, rotationSpeed.getValue(), candidate -> isBedHit(candidate, range.getValue()), Priority.Low);
            } else {
                Managers.ROTATION.setRotations(nextTarget.rotation, rotationSpeed.getValue(), Priority.Low);
            }
        }

        if (!startedBreaking) {
            mc.gameMode.startDestroyBlock(currentTarget, currentFace);
            startedBreaking = true;
        }

        mc.gameMode.continueDestroyBlock(currentTarget, currentFace);

        if (swingHand.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        } else {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }

        if (mc.level.getBlockState(currentTarget).isAir()) {
            resetBreaking();
        }
    }

    @Override
    public void onDisable() {
        resetBreaking();
        trackedBedPos = null;
        trackedBedFace = null;
    }

    private void resetBreaking() {
        if (startedBreaking && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
        clearBreakingState();
    }

    private void clearBreakingState() {
        currentTarget = null;
        currentFace = null;
        startedBreaking = false;
        trackedBedPos = null;
        trackedBedFace = null;
    }

    private boolean shouldPauseBreaking() {
        if (mc.options.keyAttack.isDown()) {
            return true;
        }

        Scaffold scaffold = Scaffold.INSTANCE;
        if (scaffold.isEnabled()) {
            return true;
        }

        KillAura killAura = KillAura.INSTANCE;
        return killAura.isEnabled() && killAura.target != null;
    }

    public BlockPos getCurrentTarget() {
        return currentTarget;
    }

    public boolean isBreakingTarget() {
        return startedBreaking && currentTarget != null;
    }

    private TargetData findTarget() {
        TargetData followUpTarget = getTrackedBedTarget();
        if (followUpTarget != null) {
            return followUpTarget;
        }

        double maxRange = range.getValue();
        double maxRangeSq = maxRange * maxRange;
        Vec3 eyes = mc.player.getEyePosition();
        BlockPos playerPos = mc.player.blockPosition();
        int radius = Mth.ceil(maxRange);
        TargetData best = null;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (!(mc.level.getBlockState(pos).getBlock() instanceof BedBlock)) {
                        continue;
                    }

                    if (eyes.distanceToSqr(Vec3.atCenterOf(pos)) > maxRangeSq) {
                        continue;
                    }

                    TargetData candidate = getTargetData(pos, maxRange, eyes);
                    if (candidate == null) {
                        continue;
                    }

                    if (best == null
                            || candidate.priority < best.priority
                            || (candidate.priority == best.priority && candidate.distanceSq < best.distanceSq)) {
                        best = candidate;
                    }
                }
            }
        }

        return best;
    }

    private TargetData getTrackedBedTarget() {
        if (trackedBedPos == null || trackedBedFace == null) {
            return null;
        }

        if (!(mc.level.getBlockState(trackedBedPos).getBlock() instanceof BedBlock)) {
            trackedBedPos = null;
            trackedBedFace = null;
            return null;
        }

        double maxRange = range.getValue();
        Vec3 eyes = mc.player.getEyePosition();
        TargetData target = getBedFaceTarget(trackedBedPos, trackedBedFace, maxRange, eyes);
        if (target != null && target.bedTarget) {
            return target;
        }

        return null;
    }

    private TargetData getTargetData(BlockPos bedPos, double maxRange, Vec3 eyes) {
        TargetData directTarget = getDirectBedTarget(bedPos, maxRange, eyes);
        if (directTarget != null) {
            return directTarget;
        }

        TargetData coveredFaceTarget = null;

        for (Direction bedFace : Direction.values()) {
            TargetData candidate = getBedFaceTarget(bedPos, bedFace, maxRange, eyes);
            if (candidate == null) {
                continue;
            }

            if (coveredFaceTarget == null || candidate.distanceSq < coveredFaceTarget.distanceSq) {
                coveredFaceTarget = candidate;
            }
        }

        return coveredFaceTarget;
    }

    private TargetData getDirectBedTarget(BlockPos bedPos, double maxRange, Vec3 eyes) {
        TargetData best = null;

        for (Direction face : Direction.values()) {
            if (face == Direction.DOWN) continue;

            BlockPos adjacentPos = bedPos.relative(face);
            if (!mc.level.getBlockState(adjacentPos).isAir()) {
                continue;
            }

            Vec3 aimPoint = getFaceAimPoint(bedPos, face);
            double distanceSq = eyes.distanceToSqr(aimPoint);
            if (distanceSq > maxRange * maxRange) {
                continue;
            }

            TargetData candidate = new TargetData(
                    bedPos,
                    face,
                    RotationUtils.calculate(aimPoint),
                    distanceSq,
                    0,
                    true,
                    bedPos,
                    face
            );

            if (best == null || candidate.distanceSq < best.distanceSq) {
                best = candidate;
            }
        }

        return best;
    }

    private TargetData getBedFaceTarget(BlockPos bedPos, Direction bedFace, double maxRange, Vec3 eyes) {
        BlockPos adjacentPos = bedPos.relative(bedFace);
        BlockState adjacentState = mc.level.getBlockState(adjacentPos);
        if (adjacentState.isAir()) {
            return null;
        }

        if (!throughWalls.getValue()) {
            Direction face = getClosestFace(adjacentPos, eyes);
            Rot2f blockerRotation = RotationUtils.calculate(adjacentPos, face);
            return new TargetData(
                    adjacentPos,
                    face,
                    blockerRotation,
                    eyes.distanceToSqr(Vec3.atCenterOf(adjacentPos)),
                    1,
                    false,
                    bedPos,
                    bedFace
            );
        }

        Direction face = getClosestFace(adjacentPos, eyes);
        Rot2f blockerRotation = RotationUtils.calculate(adjacentPos, face);
        return new TargetData(
                adjacentPos,
                face,
                blockerRotation,
                eyes.distanceToSqr(Vec3.atCenterOf(adjacentPos)),
                1,
                false,
                bedPos,
                bedFace
        );
    }

    private Vec3 getFaceAimPoint(BlockPos bedPos, Direction bedFace) {
        Vec3 center = Vec3.atCenterOf(bedPos);
        if (bedFace == Direction.UP) {
            return new Vec3(center.x, bedPos.getY() + 0.5625D, center.z);
        }

        return center.add(
                bedFace.getStepX() * 0.42D,
                -0.10D,
                bedFace.getStepZ() * 0.42D
        );
    }

    private Direction getClosestFace(BlockPos pos, Vec3 eyes) {
        Vec3 center = Vec3.atCenterOf(pos);
        Direction bestFace = Direction.UP;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            Vec3 facePoint = center.add(
                    direction.getStepX() * 0.5D,
                    direction.getStepY() * 0.5D,
                    direction.getStepZ() * 0.5D
            );
            double distance = eyes.distanceToSqr(facePoint);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestFace = direction;
            }
        }

        return bestFace;
    }

    private boolean isBedHit(Rot2f rotation, double rayRange) {
        HitResult result = RaytraceUtils.raytrace(rotation, rayRange);
        return result instanceof BlockHitResult blockHitResult && mc.level.getBlockState(blockHitResult.getBlockPos()).getBlock() instanceof BedBlock;
    }

    private record TargetData(
            BlockPos pos,
            Direction face,
            Rot2f rotation,
            double distanceSq,
            int priority,
            boolean bedTarget,
            BlockPos sourceBedPos,
            Direction sourceBedFace
    ) {
    }

}
