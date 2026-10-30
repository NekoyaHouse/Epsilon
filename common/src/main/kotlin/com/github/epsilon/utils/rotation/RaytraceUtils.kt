package com.github.epsilon.utils.rotation

import com.github.epsilon.utils.NonNullContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object RaytraceUtils {

    /**
     * 判断两点之间是否没有方块遮挡。
     *
     * @param eyes 射线起点
     * @param vec3 射线终点
     * @return 判断结果
     */
    fun canSeePointFrom(eyes: Vec3, vec3: Vec3): Boolean {
        val nn = NonNullContext.current ?: return false
        return nn.world.clip(
            ClipContext(eyes, vec3, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, nn.player)
        ).getType() == HitResult.Type.MISS
    }

    /**
     * 按指定旋转执行方块和实体射线追踪。
     *
     * @param rotation 旋转角
     * @param range    射线追踪或自适应选点距离
     * @return 操作结果
     */
    fun raytrace(rotation: Rot2f, range: Double): HitResult? {
        return raytrace(rotation, range, 0.0f)
    }

    /**
     * 按指定旋转执行方块和实体射线追踪。
     *
     * @param rotation 旋转角
     * @param range    射线追踪或自适应选点距离
     * @param expand   实体包围盒扩大量
     * @return 操作结果
     */
    fun raytrace(rotation: Rot2f, range: Double, expand: Float): HitResult? {
        val nn = NonNullContext.current ?: return null
        return raytrace(rotation, range, expand, nn.player)
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
    fun raytrace(rotation: Rot2f, range: Double, expand: Float, entity: Entity?): HitResult? {
        if (entity == null) return null
        val nn = NonNullContext.current ?: return null
        val world = nn.world

        val partialTicks = nn.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)

        val eyePos = entity.getEyePosition(partialTicks)
        val lookVec = Vec3.directionFromRotation(rotation.pitch, rotation.yaw)
        val endVec = eyePos.add(lookVec.scale(range))

        val objectMouseOver = world.clip(
            ClipContext(
                eyePos,
                endVec,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                entity
            )
        )

        var distToBlock = range
        if (objectMouseOver.getType() != HitResult.Type.MISS) {
            distToBlock = objectMouseOver.getLocation().distanceTo(eyePos)
        }

        val entitySearchEndVec = eyePos.add(lookVec.scale(range))

        var pointedEntity: Entity? = null
        var hitVec: Vec3? = null
        var currentDist = distToBlock

        val searchBox = entity.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0)

        val list = world.getEntities(entity, searchBox) { candidate -> !candidate.isSpectator() && candidate.isPickable() }

        for (candidate in list) {
            val collisionSize = candidate.getPickRadius() + expand
            val entityBox = candidate.getBoundingBox().inflate(collisionSize.toDouble())

            val intercept = entityBox.clip(eyePos, entitySearchEndVec)

            if (entityBox.contains(eyePos)) {
                if (currentDist >= 0.0) {
                    pointedEntity = candidate
                    hitVec = intercept.orElse(eyePos)
                    currentDist = 0.0
                }
            } else if (intercept.isPresent) {
                val interceptVec = intercept.get()
                val d3 = eyePos.distanceTo(interceptVec)

                if (d3 < currentDist || currentDist == 0.0) {
                    if (candidate.getRootVehicle() === entity.getRootVehicle()) {
                        if (currentDist == 0.0) {
                            pointedEntity = candidate
                            hitVec = interceptVec
                        }
                    } else {
                        pointedEntity = candidate
                        hitVec = interceptVec
                        currentDist = d3
                    }
                }
            }
        }

        if (pointedEntity != null && (currentDist < distToBlock || objectMouseOver.getType() == HitResult.Type.MISS)) {
            return EntityHitResult(pointedEntity, hitVec!!)
        }

        return objectMouseOver
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
    fun overBlock(rotation: Rot2f, dir: Direction, pos: BlockPos, strict: Boolean): Boolean {
        val nn = NonNullContext.current ?: return false
        val lookVec = Vec3.directionFromRotation(rotation.pitch, rotation.yaw)

        val eyePos = nn.player.getEyePosition(1.0f)
        val reach = 4.5
        val endVec = eyePos.add(lookVec.scale(reach))

        val result: BlockHitResult = nn.world.clip(
            ClipContext(
                eyePos,
                endVec,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                nn.player
            )
        )

        if (result.getType() == HitResult.Type.MISS) {
            return false
        }

        return result.getBlockPos() == pos && (!strict || result.getDirection() == dir)
    }

    /**
     * 判断指定旋转是否命中目标方块或指定方块面。
     *
     * @param rotation 旋转角
     * @param pos      目标位置
     * @param strict   是否要求命中指定方块面
     * @return 判断结果
     */
    fun overBlock(rotation: Rot2f, pos: BlockPos, strict: Boolean): Boolean {
        return overBlock(rotation, Direction.UP, pos, strict)
    }

    /**
     * 判断指定旋转是否命中目标方块或指定方块面。
     *
     * @param rotation 旋转角
     * @param pos      目标位置
     * @return 判断结果
     */
    fun overBlock(rotation: Rot2f, pos: BlockPos): Boolean {
        return overBlock(rotation, Direction.UP, pos, false)
    }

    /**
     * 判断指定旋转是否命中目标方块或指定方块面。
     *
     * @param rotation   旋转角
     * @param pos        目标位置
     * @param enumFacing 预期命中的方块面
     * @return 判断结果
     */
    fun overBlock(rotation: Rot2f, pos: BlockPos, enumFacing: Direction): Boolean {
        return overBlock(rotation, enumFacing, pos, true)
    }

}
