package com.github.epsilon.utils.rotation

import com.github.epsilon.Constants
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.utils.safety.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape

object RotationUtils {

    /**
     * 选择最适合与目标方块交互的方向。
     *
     * @param pos 目标位置
     * @return 操作结果
     */
    context(nn: NonNullContext)
    fun getDirection(pos: BlockPos): Direction {
        val raycastSide = getNearestSide(pos) { direction -> canSeeWithContext(pos, direction) }
        if (raycastSide != null) {
            return raycastSide
        }
        val grimSide = getNearestSide(pos) { direction -> isGrimDirectionWithContext(pos, direction) }
        if (grimSide != null) {
            return grimSide
        }
        return Direction.UP
    }

    context(nn: NonNullContext)
    private fun getNearestSide(pos: BlockPos, predicate: (Direction) -> Boolean): Direction? {
        var side: Direction? = null
        var closestDistanceSq = Double.MAX_VALUE
        val eyePos = player.eyePosition

        for (direction in Direction.entries) {
            if (!predicate(direction)) continue

            val distanceSq = eyePos.distanceToSqr(Vec3.atCenterOf(pos.relative(direction)))
            if (distanceSq >= closestDistanceSq) continue

            side = direction
            closestDistanceSq = distanceSq
        }

        return side
    }

    /**
     * 判断玩家眼睛是否可直接看见指定方块面。
     *
     * @param pos  目标位置
     * @param side 方块面方向
     * @return 判断结果
     */
    context(nn: NonNullContext)
    fun canSee(pos: BlockPos, side: Direction): Boolean {
        return canSeeWithContext(pos, side)
    }

    context(nn: NonNullContext)
    private fun canSeeWithContext(pos: BlockPos, side: Direction): Boolean {
        val testVec = Vec3.atCenterOf(pos).relative(side, 0.5)
        val context = ClipContext(
            player.eyePosition, testVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        )
        val result = level.clip(context)
        return result.type == HitResult.Type.MISS
    }

    context(nn: NonNullContext)
    private fun getCollisionBox(pos: BlockPos): AABB {
        val shape: VoxelShape = level.getBlockState(pos).getCollisionShape(level, pos).move(pos)
        var collisionBox = AABB(pos)

        for (box in shape.toAabbs()) {
            collisionBox = collisionBox.intersect(box)
        }

        return collisionBox
    }

    /**
     * 判断指定方块面是否满足 Grim 方向检查。
     *
     * @param pos       目标位置
     * @param direction 方块面方向
     * @return 判断结果
     */
    context(nn: NonNullContext)
    fun isGrimDirection(pos: BlockPos, direction: Direction): Boolean {
        return isGrimDirectionWithContext(pos, direction)
    }

    context(nn: NonNullContext)
    private fun isGrimDirectionWithContext(pos: BlockPos, direction: Direction): Boolean {
        val collisionBox = getCollisionBox(pos)
        val eyePositions = AABB(
            player.x, player.y + 0.4, player.z,
            player.x, player.y + 1.62, player.z
        ).inflate(0.0002)
        if (eyePositions.intersects(collisionBox)) {
            return true
        }
        return !(when (direction) {
            Direction.NORTH -> eyePositions.minZ > collisionBox.minZ
            Direction.SOUTH -> eyePositions.maxZ < collisionBox.maxZ
            Direction.EAST -> eyePositions.maxX < collisionBox.maxX
            Direction.WEST -> eyePositions.minX > collisionBox.minX
            Direction.UP -> eyePositions.maxY < collisionBox.maxY
            Direction.DOWN -> eyePositions.minY > collisionBox.minY
        })
    }

    /**
     * 判断实体是否位于玩家水平视野范围内。
     *
     * @param entity 实体
     * @param fov    水平视野角度
     * @return 判断结果
     */
    context(nn: NonNullContext)
    fun isInFov(entity: Entity, fov: Float): Boolean {
        if (fov >= 360f) return true
        val yawDiff = Math.abs(Mth.wrapDegrees(getRotationsToEntity(entity).yaw - player.yRot))
        return yawDiff <= fov / 2.0
    }

    /**
     * 计算从玩家视点朝向实体中心的旋转。
     *
     * @param entity 实体
     * @return 获取或计算得到的结果
     */
    context(nn: NonNullContext)
    fun getRotationsToEntity(entity: Entity): Rot2f {
        val eyePos = player.eyePosition
        val targetPos = entity.position().add(0.0, entity.bbHeight / 2.0, 0.0)
        val dx = targetPos.x - eyePos.x
        val dy = targetPos.y - eyePos.y
        val dz = targetPos.z - eyePos.z
        val dist = Math.sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(-Math.atan2(dx, dz)).toFloat()
        val pitch = Math.toDegrees(-Math.atan2(dy, dist)).toFloat()
        return Rot2f(yaw, Mth.clamp(pitch, -90f, 90f))
    }

    /**
     * 计算玩家眼睛到实体眼睛的距离。
     *
     * @param entity 实体
     * @return 获取或计算得到的结果
     */
    context(nn: NonNullContext)
    fun getEyeDistanceToEntity(entity: LivingEntity): Double {
        val eyePos = player.eyePosition
        val box = entity.boundingBox
        val dx = Math.max(box.minX - eyePos.x, Math.max(0.0, eyePos.x - box.maxX))
        val dy = Math.max(box.minY - eyePos.y, Math.max(0.0, eyePos.y - box.maxY))
        val dz = Math.max(box.minZ - eyePos.z, Math.max(0.0, eyePos.z - box.maxZ))
        return Math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param from 起始位置
     * @param to   目标位置
     * @return 计算得到的旋转角
     */
    fun calculate(from: Vec3, to: Vec3): Rot2f {
        val diff = to.subtract(from)
        val distance = Math.hypot(diff.x, diff.z)
        val yaw = Math.toDegrees(Mth.atan2(diff.z, diff.x)).toFloat() - 90.0f
        val pitch = (-Math.toDegrees(Mth.atan2(diff.y, distance))).toFloat()
        return Rot2f(yaw, pitch)
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param entity 实体
     * @return 计算得到的旋转角
     */
    context(nn: NonNullContext)
    fun calculate(entity: Entity): Rot2f {
        return calculate(
            entity.position().add(
                0.0,
                Mth.clamp(
                    player.y - entity.y + player.eyeHeight,
                    0.0,
                    (entity.boundingBox.maxY - entity.boundingBox.minY) * 0.9
                ),
                0.0
            )
        )
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param entity   实体
     * @param adaptive 是否在实体包围盒内自适应选择目标点
     * @param range    射线追踪或自适应选点距离
     * @return 计算得到的旋转角
     */
    context(nn: NonNullContext)
    fun calculate(entity: Entity, adaptive: Boolean, range: Double): Rot2f {
        val normalRotations = calculate(entity)

        val result = RaytraceUtils.raytrace(normalRotations, range, 0.0f)

        if (!adaptive || (result != null && result.type == HitResult.Type.ENTITY)) {
            return normalRotations
        }

        val bb = entity.boundingBox
        val minX = bb.minX
        val maxX = bb.maxX
        val minY = bb.minY
        val maxY = bb.maxY
        val minZ = bb.minZ
        val maxZ = bb.maxZ

        val basePos = entity.position()

        var yPercent = 1.0
        while (yPercent >= 0) {
            var xPercent = 1.0
            while (xPercent >= -0.5) {
                var zPercent = 1.0
                while (zPercent >= -0.5) {

                    val offsetX = (maxX - minX) * xPercent
                    val offsetY = (maxY - minY) * yPercent
                    val offsetZ = (maxZ - minZ) * zPercent

                    val targetPoint = basePos.add(offsetX, offsetY, offsetZ)

                    val adaptiveRotations = calculate(targetPoint)

                    val rayCastResult = RaytraceUtils.raytrace(adaptiveRotations, range, 0.0f)

                    if (rayCastResult != null && rayCastResult.type == HitResult.Type.ENTITY) {
                        return adaptiveRotations
                    }

                    zPercent -= 0.5
                }
                xPercent -= 0.5
            }
            yPercent -= 0.25 + Math.random() * 0.1
        }

        return normalRotations
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param to 目标位置
     * @return 计算得到的旋转角
     */
    context(nn: NonNullContext)
    fun calculate(to: BlockPos): Rot2f {
        return calculate(player.eyePosition, Vec3.atCenterOf(to))
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param to 目标位置
     * @return 计算得到的旋转角
     */
    context(nn: NonNullContext)
    fun calculate(to: Vec3): Rot2f {
        return calculate(player.eyePosition, to)
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param position  目标位置
     * @param direction 方块面方向
     * @return 计算得到的旋转角
     */
    context(nn: NonNullContext)
    fun calculate(position: Vec3, direction: Direction): Rot2f {
        var x = position.x + 0.5
        var y = position.y + 0.5
        var z = position.z + 0.5
        x += direction.stepX.toDouble() * 0.5
        y += direction.stepY.toDouble() * 0.5
        z += direction.stepZ.toDouble() * 0.5
        return calculate(Vec3(x, y, z))
    }

    /**
     * 计算朝向目标位置或实体的旋转。
     *
     * @param position  目标位置
     * @param direction 方块面方向
     * @return 计算得到的旋转角
     */
    context(nn: NonNullContext)
    fun calculate(position: BlockPos, direction: Direction): Rot2f {
        var x = position.x + 0.5
        var y = position.y + 0.5
        var z = position.z + 0.5
        x += direction.stepX.toDouble() * 0.5
        y += direction.stepY.toDouble() * 0.5
        z += direction.stepZ.toDouble() * 0.5
        return calculate(Vec3(x, y, z))
    }

    /**
     * 计算朝向目标位置的旋转。
     *
     * @param vec     目标位置
     * @param predict 是否按玩家速度预判
     * @return 计算得到的旋转角
     */
    context(nn: NonNullContext)
    fun calculate(vec: Vec3, predict: Boolean): Rot2f {
        var eyesPos = player.eyePosition

        if (predict) eyesPos = eyesPos.add(player.deltaMovement)

        val diffX = vec.x - eyesPos.x
        val diffY = vec.y - eyesPos.y
        val diffZ = vec.z - eyesPos.z

        val yaw = Mth.wrapDegrees(Math.toDegrees(Math.atan2(diffZ, diffX)).toFloat() - 90f)
        val pitch = Mth.wrapDegrees(
            -Math.toDegrees(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ))).toFloat()
        )

        return Rot2f(yaw, pitch)
    }

    /**
     * 按鼠标灵敏度步长量化目标旋转。
     *
     * @param rotation 旋转角
     * @return 操作结果
     */
    fun applySensitivityPatch(rotation: Rot2f): Rot2f {
        val previousRotation = Rot2f(
            RotationManager.INSTANCE.getLastRotation().yaw,
            RotationManager.INSTANCE.getLastRotation().pitch
        )
        val mouseSensitivity = (
            Constants.client.options.sensitivity().get() * (1 + Math.random() / 10000000) * 0.6f + 0.2f
        ).toFloat()
        val multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0f * 0.15
        val yaw = previousRotation.yaw +
            (Math.round((rotation.yaw - previousRotation.yaw) / multiplier) * multiplier).toFloat()
        val pitch = previousRotation.pitch +
            (Math.round((rotation.pitch - previousRotation.pitch) / multiplier) * multiplier).toFloat()
        return Rot2f(yaw, Mth.clamp(pitch, -90f, 90f))
    }

    /**
     * 按鼠标灵敏度步长量化目标旋转。
     *
     * @param rotation         旋转角
     * @param previousRotation 用于量化的基准旋转
     * @return 操作结果
     */
    fun applySensitivityPatch(rotation: Rot2f, previousRotation: Rot2f): Rot2f {
        val mouseSensitivity = (
            Constants.client.options.sensitivity().get() * (1 + Math.random() / 10000000) * 0.6f + 0.2f
        ).toFloat()
        val multiplier = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0f * 0.15
        val yaw = previousRotation.yaw +
            (Math.round((rotation.yaw - previousRotation.yaw) / multiplier) * multiplier).toFloat()
        val pitch = previousRotation.pitch +
            (Math.round((rotation.pitch - previousRotation.pitch) / multiplier) * multiplier).toFloat()
        return Rot2f(yaw, Mth.clamp(pitch, -90f, 90f))
    }

    /**
     * 将旋转角调整到最接近玩家当前角度的等价值。
     *
     * @param rotation 旋转角
     * @return 操作结果
     */
    fun relateToPlayerRotation(rotation: Rot2f): Rot2f {
        val rotationManager = RotationManager.INSTANCE
        val lastRotation = rotationManager.getLastRotation()
        val previousRotation = Rot2f(lastRotation.yaw, lastRotation.pitch)
        val yaw = previousRotation.yaw + Mth.wrapDegrees(rotation.yaw - previousRotation.yaw)
        val pitch = Mth.clamp(rotation.pitch, -90f, 90f)
        return Rot2f(yaw, pitch)
    }

    /**
     * 规范化旋转角并限制俯仰角范围。
     *
     * @param rotation 旋转角
     * @return 操作结果
     */
    context(nn: NonNullContext)
    fun resetRotation(rotation: Rot2f?): Rot2f? {
        val target = rotation ?: return null
        val yaw = target.yaw + Mth.wrapDegrees(player.yRot - target.yaw)
        val pitch = player.xRot
        return Rot2f(yaw, pitch)
    }

    /**
     * 以最大角速度将当前旋转移向目标旋转。
     *
     * @param targetRotation 目标旋转
     * @param speed          移动速度或最大旋转速度
     * @return 操作结果
     */
    fun move(targetRotation: Rot2f, speed: Double): Rot2f {
        return move(RotationManager.INSTANCE.lastRotations, targetRotation, speed)
    }

    /**
     * 以最大角速度将当前旋转移向目标旋转。
     *
     * @param lastRotation   当前或上一次旋转
     * @param targetRotation 目标旋转
     * @param speed          移动速度或最大旋转速度
     * @return 操作结果
     */
    fun move(lastRotation: Rot2f, targetRotation: Rot2f, speed: Double): Rot2f {
        if (speed != 0.0) {
            val deltaYaw = Mth.wrapDegrees(targetRotation.yaw - lastRotation.yaw).toDouble()
            val deltaPitch = (targetRotation.pitch - lastRotation.pitch).toDouble()

            val distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch)
            val distributionYaw = Math.abs(deltaYaw / distance)
            val distributionPitch = Math.abs(deltaPitch / distance)

            val maxYaw = speed * distributionYaw
            val maxPitch = speed * distributionPitch

            val moveYaw = Mth.clamp(deltaYaw, -maxYaw, maxYaw).toFloat()
            val movePitch = Mth.clamp(deltaPitch, -maxPitch, maxPitch).toFloat()

            return Rot2f(moveYaw, movePitch)
        }

        return Rot2f(0.0f, 0.0f)
    }

    /**
     * 使用限制后的角速度平滑旋转到目标。
     *
     * @param targetRotation 目标旋转
     * @param speed          移动速度或最大旋转速度
     * @return 操作结果
     */
    fun smooth(targetRotation: Rot2f, speed: Double): Rot2f {
        return smooth(RotationManager.INSTANCE.lastRotations, targetRotation, speed)
    }

    /**
     * 使用限制后的角速度平滑旋转到目标。
     *
     * @param lastRotation   当前或上一次旋转
     * @param targetRotation 目标旋转
     * @param speed          移动速度或最大旋转速度
     * @return 操作结果
     */
    fun smooth(lastRotation: Rot2f, targetRotation: Rot2f, speed: Double): Rot2f {
        var yaw = targetRotation.yaw
        var pitch = targetRotation.pitch
        val lastYaw = lastRotation.yaw
        val lastPitch = lastRotation.pitch

        if (speed != 0.0) {
            val move = move(targetRotation, speed)

            yaw = lastYaw + move.yaw
            pitch = lastPitch + move.pitch

            val iterations = (Constants.client.fps / 20f + Math.random() * 10).toInt()
            for (i in 1..iterations) {
                if (Math.abs(move.yaw) + Math.abs(move.pitch) > 0.0001) {
                    yaw += ((Math.random() - 0.5) / 1000).toFloat()
                    pitch -= (Math.random() / 200).toFloat()
                }

                /*
                 * 按鼠标灵敏度修正角度步长
                 */
                val rotations = Rot2f(yaw, pitch)
                val fixedRotations = applySensitivityPatch(rotations)

                /*
                 * 应用修正后的旋转
                 */
                yaw = fixedRotations.yaw
                pitch = fixedRotations.pitch
            }
        }

        return Rot2f(yaw, pitch)
    }

}
