package com.github.epsilon.utils.rotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

import java.util.List;
import java.util.Optional;

import static com.github.epsilon.Constants.mc;

public class RaytraceUtils {

    /**
     * 判断两点之间是否没有方块遮挡。
     *
     * @param eyes 射线起点
     * @param vec3 射线终点
     * @return 判断结果
     */
    public static boolean canSeePointFrom(Vec3 eyes, Vec3 vec3) {
        return mc.level.clip(new ClipContext(eyes, vec3, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    /**
     * 按指定旋转执行方块和实体射线追踪。
     *
     * @param rotation 旋转角
     * @param range    射线追踪或自适应选点距离
     * @return 操作结果
     */
    public static HitResult raytrace(Rot2f rotation, double range) {
        return raytrace(rotation, range, 0);
    }

    /**
     * 按指定旋转执行方块和实体射线追踪。
     *
     * @param rotation 旋转角
     * @param range    射线追踪或自适应选点距离
     * @param expand   实体包围盒扩大量
     * @return 操作结果
     */
    public static HitResult raytrace(Rot2f rotation, double range, float expand) {
        return raytrace(rotation, range, expand, mc.player);
    }

    /**
     * 按指定旋转执行方块和实体射线追踪。
     *
     * @param rotation 旋转角
     * @param range    射线追踪或自适应选点距离
     * @param expand   实体包围盒扩大量
     * @param entity   实体
     * @return 操作结果
     */
    public static HitResult raytrace(Rot2f rotation, double range, float expand, Entity entity) {
        if (mc.level == null || entity == null) return null;

        float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Vec3 eyePos = entity.getEyePosition(partialTicks);
        Vec3 lookVec = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());
        Vec3 endVec = eyePos.add(lookVec.scale(range));

        HitResult objectMouseOver = mc.level.clip(new ClipContext(
                eyePos,
                endVec,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                entity
        ));

        double distToBlock = range;
        if (objectMouseOver.getType() != HitResult.Type.MISS) {
            distToBlock = objectMouseOver.getLocation().distanceTo(eyePos);
        }

        Vec3 entitySearchEndVec = eyePos.add(lookVec.scale(range));

        Entity pointedEntity = null;
        Vec3 hitVec = null;
        double currentDist = distToBlock;

        AABB searchBox = entity.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0);

        List<Entity> list = mc.level.getEntities(entity, searchBox, e -> !e.isSpectator() && e.isPickable());

        for (Entity candidate : list) {
            float collisionSize = candidate.getPickRadius() + expand;
            AABB entityBox = candidate.getBoundingBox().inflate(collisionSize);

            Optional<Vec3> intercept = entityBox.clip(eyePos, entitySearchEndVec);

            if (entityBox.contains(eyePos)) {
                if (currentDist >= 0.0) {
                    pointedEntity = candidate;
                    hitVec = intercept.orElse(eyePos);
                    currentDist = 0.0;
                }
            } else if (intercept.isPresent()) {
                Vec3 interceptVec = intercept.get();
                double d3 = eyePos.distanceTo(interceptVec);

                if (d3 < currentDist || currentDist == 0.0) {
                    if (candidate.getRootVehicle() == entity.getRootVehicle()) {
                        if (currentDist == 0.0) {
                            pointedEntity = candidate;
                            hitVec = interceptVec;
                        }
                    } else {
                        pointedEntity = candidate;
                        hitVec = interceptVec;
                        currentDist = d3;
                    }
                }
            }
        }

        if (pointedEntity != null && (currentDist < distToBlock || objectMouseOver.getType() == HitResult.Type.MISS)) {
            return new EntityHitResult(pointedEntity, hitVec);
        }

        return objectMouseOver;
    }

    /**
     * 判断指定旋转是否命中目标方块或指定方块面。
     *
     * @param rotation 旋转角
     * @param dir      预期命中的方块面
     * @param pos      目标位置
     * @param strict   是否要求命中指定方块面
     * @return 判断结果
     */
    public static boolean overBlock(Rot2f rotation, Direction dir, BlockPos pos, boolean strict) {
        Vec3 lookVec = Vec3.directionFromRotation(rotation.getPitch(), rotation.getYaw());

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        double reach = 4.5;
        Vec3 endVec = eyePos.add(lookVec.scale(reach));

        BlockHitResult result = mc.level.clip(new ClipContext(
                eyePos,
                endVec,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if (result.getType() == HitResult.Type.MISS) {
            return false;
        }

        return result.getBlockPos().equals(pos) && (!strict || result.getDirection() == dir);
    }

    /**
     * 判断指定旋转是否命中目标方块或指定方块面。
     *
     * @param rotation 旋转角
     * @param pos      目标位置
     * @param strict   是否要求命中指定方块面
     * @return 判断结果
     */
    public static boolean overBlock(Rot2f rotation, BlockPos pos, boolean strict) {
        return overBlock(rotation, Direction.UP, pos, strict);
    }

    /**
     * 判断指定旋转是否命中目标方块或指定方块面。
     *
     * @param rotation 旋转角
     * @param pos      目标位置
     * @return 判断结果
     */
    public static boolean overBlock(Rot2f rotation, BlockPos pos) {
        return overBlock(rotation, Direction.UP, pos, false);
    }

    /**
     * 判断指定旋转是否命中目标方块或指定方块面。
     *
     * @param rotation   旋转角
     * @param pos        目标位置
     * @param enumFacing 预期命中的方块面
     * @return 判断结果
     */
    public static boolean overBlock(Rot2f rotation, BlockPos pos, Direction enumFacing) {
        return overBlock(rotation, enumFacing, pos, true);
    }

}
