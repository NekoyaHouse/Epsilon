package com.github.epsilon.utils.rotation;

import com.github.epsilon.managers.rotation.RotationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Predicate;

import static com.github.epsilon.Constants.mc;

public class RotationUtils {

    /**
     * 选择最适合与目标方块交互的方向。
     *
     * @param pos 目标位置
     * @return 操作结果
     */
    public static Direction getDirection(BlockPos pos) {
        Direction raycastSide = getNearestSide(pos, direction -> canSee(pos, direction));
        if (raycastSide != null) {
            return raycastSide;
        }
        Direction grimSide = getNearestSide(pos, direction -> isGrimDirection(pos, direction));
        if (grimSide != null) {
            return grimSide;
        }
        return Direction.UP;
    }

    private static Direction getNearestSide(BlockPos pos, Predicate<Direction> predicate) {
        Direction side = null;
        double closestDistanceSq = Double.MAX_VALUE;
        Vec3 eyePos = mc.player.getEyePosition();

        for (Direction direction : Direction.values()) {
            if (!predicate.test(direction)) continue;

            double distanceSq = eyePos.distanceToSqr(Vec3.atCenterOf(pos.relative(direction)));
            if (distanceSq >= closestDistanceSq) continue;

            side = direction;
            closestDistanceSq = distanceSq;
        }

        return side;
    }

    /**
     * 判断玩家眼睛是否可直接看见指定方块面。
     *
     * @param pos  目标位置
     * @param side 方块面方向
     * @return 判断结果
     */
    public static boolean canSee(BlockPos pos, Direction side) {
        Vec3 testVec = Vec3.atCenterOf(pos).relative(side, 0.5);
        ClipContext context = new ClipContext(mc.player.getEyePosition(), testVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        HitResult result = mc.level.clip(context);
        return result.getType() == HitResult.Type.MISS;
    }

    private static AABB getCollisionBox(BlockPos pos) {
        VoxelShape shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).move(pos);
        AABB collisionBox = new AABB(pos);

        for (AABB box : shape.toAabbs()) {
            collisionBox = collisionBox.intersect(box);
        }

        return collisionBox;
    }

    /**
     * 判断指定方块面是否满足 Grim 方向检查。
     *
     * @param pos       目标位置
     * @param direction 方块面方向
     * @return 判断结果
     */
    public static boolean isGrimDirection(BlockPos pos, Direction direction) {
        AABB collisionBox = getCollisionBox(pos);
        AABB eyePositions = new AABB(mc.player.getX(), mc.player.getY() + 0.4, mc.player.getZ(), mc.player.getX(), mc.player.getY() + 1.62, mc.player.getZ()).inflate(0.0002);
        if (eyePositions.intersects(collisionBox)) {
            return true;
        }
        return !switch (direction) {
            case NORTH -> eyePositions.minZ > collisionBox.minZ;
            case SOUTH -> eyePositions.maxZ < collisionBox.maxZ;
            case EAST -> eyePositions.maxX < collisionBox.maxX;
            case WEST -> eyePositions.minX > collisionBox.minX;
            case UP -> eyePositions.maxY < collisionBox.maxY;
            case DOWN -> eyePositions.minY > collisionBox.minY;
        };
    }

    /**
     * 判断实体是否位于玩家水平视野范围内。
     *
     * @param entity 实体
     * @param fov    水平视野角度
     * @return 判断结果
     */
    public static boolean isInFov(Entity entity, float fov) {
        if (fov >= 360) return true;
        float yawDiff = Math.abs(Mth.wrapDegrees(RotationUtils.getRotationsToEntity(entity).getYaw() - mc.player.getYRot()));
        return yawDiff <= fov / 2.0;
    }

    /**
     * 计算从玩家视点朝向实体中心的旋转。
     *
     * @param entity 实体
     * @return 获取或计算得到的结果
     */
    public static Rot2f getRotationsToEntity(Entity entity) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 targetPos = entity.position().add(0, entity.getBbHeight() / 2.0, 0);
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dist));
        return new Rot2f(yaw, Mth.clamp(pitch, -90, 90));
    }

    /**
     * 计算玩家眼睛到实体眼睛的距离。
     *
     * @param entity 实体
     * @return 获取或计算得到的结果
     */
    public static double getEyeDistanceToEntity(LivingEntity entity) {
        Vec3 eyePos = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        double dx = Math.max(box.minX - eyePos.x, Math.max(0, eyePos.x - box.maxX));
        double dy = Math.max(box.minY - eyePos.y, Math.max(0, eyePos.y - box.maxY));
        double dz = Math.max(box.minZ - eyePos.z, Math.max(0, eyePos.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param from 起始位置
     * @param to   目标位置
     * @return 计算得到的旋转角
     */
    public static Rot2f calculate(final Vec3 from, final Vec3 to) {
        final Vec3 diff = to.subtract(from);
        final double distance = Math.hypot(diff.x, diff.z);
        final float yaw = (float) Math.toDegrees(Mth.atan2(diff.z, diff.x)) - 90.0F;
        final float pitch = (float) -Math.toDegrees(Mth.atan2(diff.y, distance));
        return new Rot2f(yaw, pitch);
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param entity 实体
     * @return 计算得到的旋转角
     */
    public static Rot2f calculate(final Entity entity) {
        return calculate(entity.position().add(0, Mth.clamp(
                mc.player.getY() - entity.getY() + mc.player.getEyeHeight(),
                0.0,
                (entity.getBoundingBox().maxY - entity.getBoundingBox().minY) * 0.9
        ), 0));
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param entity   实体
     * @param adaptive 是否在实体包围盒内自适应选择目标点
     * @param range    射线追踪或自适应选点距离
     * @return 计算得到的旋转角
     */
    public static Rot2f calculate(final Entity entity, final boolean adaptive, final double range) {
        Rot2f normalRotations = RotationUtils.calculate(entity);

        HitResult result = RaytraceUtils.raytrace(normalRotations, range, 0.0f);

        if (!adaptive || (result != null && result.getType() == HitResult.Type.ENTITY)) {
            return normalRotations;
        }

        AABB bb = entity.getBoundingBox();
        double minX = bb.minX;
        double maxX = bb.maxX;
        double minY = bb.minY;
        double maxY = bb.maxY;
        double minZ = bb.minZ;
        double maxZ = bb.maxZ;

        Vec3 basePos = entity.position();

        for (double yPercent = 1; yPercent >= 0; yPercent -= 0.25 + Math.random() * 0.1) {
            for (double xPercent = 1; xPercent >= -0.5; xPercent -= 0.5) {
                for (double zPercent = 1; zPercent >= -0.5; zPercent -= 0.5) {

                    double offsetX = (maxX - minX) * xPercent;
                    double offsetY = (maxY - minY) * yPercent;
                    double offsetZ = (maxZ - minZ) * zPercent;

                    Vec3 targetPoint = basePos.add(offsetX, offsetY, offsetZ);

                    Rot2f adaptiveRotations = RotationUtils.calculate(targetPoint);

                    HitResult rayCastResult = RaytraceUtils.raytrace(adaptiveRotations, range, 0.0f);

                    if (rayCastResult != null && rayCastResult.getType() == HitResult.Type.ENTITY) {
                        return adaptiveRotations;
                    }
                }
            }
        }

        return normalRotations;
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param to 目标位置
     * @return 计算得到的旋转角
     */
    public static Rot2f calculate(BlockPos to) {
        return calculate(mc.player.getEyePosition(), Vec3.atCenterOf(to));
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param to 目标位置
     * @return 计算得到的旋转角
     */
    public static Rot2f calculate(Vec3 to) {
        return calculate(mc.player.getEyePosition(), to);
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param position  目标位置
     * @param direction 方块面方向
     * @return 计算得到的旋转角
     */
    public static Rot2f calculate(Vec3 position, Direction direction) {
        double x = position.x + 0.5;
        double y = position.y + 0.5;
        double z = position.z + 0.5;
        x += (double) direction.getStepX() * 0.5;
        y += (double) direction.getStepY() * 0.5;
        z += (double) direction.getStepZ() * 0.5;
        return calculate(new Vec3(x, y, z));
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param position  目标位置
     * @param direction 方块面方向
     * @return 计算得到的旋转角
     */
    public static Rot2f calculate(BlockPos position, Direction direction) {
        double x = position.getX() + 0.5;
        double y = position.getY() + 0.5;
        double z = position.getZ() + 0.5;
        x += (double) direction.getStepX() * 0.5;
        y += (double) direction.getStepY() * 0.5;
        z += (double) direction.getStepZ() * 0.5;
        return calculate(new Vec3(x, y, z));
    }

    public static Rot2f calculate(Vec3 vec, boolean predict) {
        Vec3 eyesPos = mc.player.getEyePosition();

        if (predict) eyesPos = eyesPos.add(mc.player.getDeltaMovement());

        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;

        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90f);
        float pitch = Mth.wrapDegrees(-(float) Math.toDegrees(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ))));

        return new Rot2f(yaw, pitch);
    }

    /**
     * 按鼠标灵敏度步长量化目标旋转。
     *
     * @param rotation 旋转角
     * @return 操作结果
     */
    public static Rot2f applySensitivityPatch(Rot2f rotation) {
        Rot2f previousRotation = new Rot2f(RotationManager.INSTANCE.getLastRotation().getYaw(), RotationManager.INSTANCE.getLastRotation().getPitch());
        float mouseSensitivity = (float) (mc.options.sensitivity().get() * (1 + Math.random() / 10000000) * 0.6F + 0.2F);
        double multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F * 0.15D;
        float yaw = previousRotation.getYaw() + (float) (Math.round((rotation.getYaw() - previousRotation.getYaw()) / multiplier) * multiplier);
        float pitch = previousRotation.getPitch() + (float) (Math.round((rotation.getPitch() - previousRotation.getPitch()) / multiplier) * multiplier);
        return new Rot2f(yaw, Mth.clamp(pitch, -90, 90));
    }

    /**
     * 按鼠标灵敏度步长量化目标旋转。
     *
     * @param rotation         旋转角
     * @param previousRotation 用于量化的基准旋转
     * @return 操作结果
     */
    public static Rot2f applySensitivityPatch(Rot2f rotation, Rot2f previousRotation) {
        float mouseSensitivity = (float) (mc.options.sensitivity().get() * (1 + Math.random() / 10000000) * 0.6F + 0.2F);
        double multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F * 0.15D;
        float yaw = previousRotation.getYaw() + (float) (Math.round((rotation.getYaw() - previousRotation.getYaw()) / multiplier) * multiplier);
        float pitch = previousRotation.getPitch() + (float) (Math.round((rotation.getPitch() - previousRotation.getPitch()) / multiplier) * multiplier);
        return new Rot2f(yaw, Mth.clamp(pitch, -90, 90));
    }

    /**
     * 将旋转角调整到最接近玩家当前角度的等价值。
     *
     * @param rotation 旋转角
     * @return 操作结果
     */
    public static Rot2f relateToPlayerRotation(Rot2f rotation) {
        RotationManager rotationManager = RotationManager.INSTANCE;
        Rot2f previousRotation = new Rot2f(rotationManager.getLastRotation().getYaw(), rotationManager.getLastRotation().getPitch());
        float yaw = previousRotation.getYaw() + Mth.wrapDegrees(rotation.getYaw() - previousRotation.getYaw());
        float pitch = Mth.clamp(rotation.getPitch(), -90, 90);
        return new Rot2f(yaw, pitch);
    }

    /**
     * 规范化旋转角并限制俯仰角范围。
     *
     * @param rotation 旋转角
     * @return 操作结果
     */
    public static Rot2f resetRotation(final Rot2f rotation) {
        if (rotation == null) return null;
        final float yaw = rotation.getYaw() + Mth.wrapDegrees(mc.player.getYRot() - rotation.getYaw());
        final float pitch = mc.player.getXRot();
        return new Rot2f(yaw, pitch);
    }

    /**
     * 以最大角速度将当前旋转移向目标旋转。
     *
     * @param targetRotation 目标旋转
     * @param speed          移动速度或最大旋转速度
     * @return 操作结果
     */
    public static Rot2f move(Rot2f targetRotation, double speed) {
        return move(RotationManager.INSTANCE.lastRotations, targetRotation, speed);
    }

    /**
     * 以最大角速度将当前旋转移向目标旋转。
     *
     * @param lastRotation   当前或上一次旋转
     * @param targetRotation 目标旋转
     * @param speed          移动速度或最大旋转速度
     * @return 操作结果
     */
    public static Rot2f move(Rot2f lastRotation, Rot2f targetRotation, double speed) {
        if (speed != 0) {
            double deltaYaw = Mth.wrapDegrees(targetRotation.getYaw() - lastRotation.getYaw());
            double deltaPitch = (targetRotation.getPitch() - lastRotation.getPitch());

            double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
            double distributionYaw = Math.abs(deltaYaw / distance);
            double distributionPitch = Math.abs(deltaPitch / distance);

            double maxYaw = speed * distributionYaw;
            double maxPitch = speed * distributionPitch;

            float moveYaw = (float) Mth.clamp(deltaYaw, -maxYaw, maxYaw);
            float movePitch = (float) Mth.clamp(deltaPitch, -maxPitch, maxPitch);

            return new Rot2f(moveYaw, movePitch);
        }

        return new Rot2f(0, 0);
    }

    /**
     * 使用限制后的角速度平滑旋转到目标。
     *
     * @param targetRotation 目标旋转
     * @param speed          移动速度或最大旋转速度
     * @return 操作结果
     */
    public static Rot2f smooth(final Rot2f targetRotation, final double speed) {
        return smooth(RotationManager.INSTANCE.lastRotations, targetRotation, speed);
    }

    /**
     * 使用限制后的角速度平滑旋转到目标。
     *
     * @param lastRotation   当前或上一次旋转
     * @param targetRotation 目标旋转
     * @param speed          移动速度或最大旋转速度
     * @return 操作结果
     */
    public static Rot2f smooth(final Rot2f lastRotation, final Rot2f targetRotation, final double speed) {
        float yaw = targetRotation.getYaw();
        float pitch = targetRotation.getPitch();
        final float lastYaw = lastRotation.getYaw();
        final float lastPitch = lastRotation.getPitch();

        if (speed != 0) {
            Rot2f move = move(targetRotation, speed);

            yaw = lastYaw + move.getYaw();
            pitch = lastPitch + move.getPitch();

            for (int i = 1; i <= (int) (mc.getFps() / 20f + Math.random() * 10); ++i) {
                if (Math.abs(move.getYaw()) + Math.abs(move.getPitch()) > 0.0001) {
                    yaw += (float) ((Math.random() - 0.5) / 1000);
                    pitch -= (float) (Math.random() / 200);
                }

                /*
                 * 按鼠标灵敏度修正角度步长
                 */
                Rot2f rotations = new Rot2f(yaw, pitch);
                Rot2f fixedRotations = applySensitivityPatch(rotations);

                /*
                 * 应用修正后的旋转
                 */
                yaw = fixedRotations.getYaw();
                pitch = fixedRotations.getPitch();
            }
        }

        return new Rot2f(yaw, pitch);
    }

}
