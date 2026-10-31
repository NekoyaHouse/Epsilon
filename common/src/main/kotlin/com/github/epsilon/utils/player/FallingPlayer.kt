package com.github.epsilon.utils.player

import com.github.epsilon.utils.safety.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * 玩家下落轨迹模拟器。
 *
 * 位置、速度与模拟状态为该类实例字段；只有“本地玩家是否疾跑 / 包围盒宽度 / 世界射线检测”
 * 需要客户端上下文，相关方法接收 [NonNullContext] 上下文参数，调用方需在 `NonNullContext`
 * 作用域内调用（`nonNullListener` / `runSafe` / `context(nn)`）。
 */
class FallingPlayer(
    var x: Double,
    var y: Double,
    var z: Double,
    private var motionX: Double,
    private var motionY: Double,
    private var motionZ: Double,
    private val yaw: Float,
    private val strafe: Float,
    private val forward: Float,
    private val jumpMovementFactor: Float,
) {

    /**
     * 创建玩家下落轨迹模拟器。
     *
     * @param player 玩家
     */
    constructor(player: Player) : this(
        player.x,
        player.y,
        player.z,
        player.deltaMovement.x,
        player.deltaMovement.y,
        player.deltaMovement.z,
        player.yRot,
        0.0F,
        0.0F,
        player.speed,
    )

    context(nn: NonNullContext)
    private fun calculateForTick() {
        var sr = strafe * 0.9800000190734863F
        var fw = forward * 0.9800000190734863F
        var movement = sr * sr + fw * fw

        if (movement >= 1.0E-4F) {
            movement = Mth.sqrt(movement)
            if (movement < 1.0F) {
                movement = 1.0F
            }

            var fixedJumpFactor = jumpMovementFactor
            if (nn.player.isSprinting) {
                fixedJumpFactor *= 1.3F
            }

            movement = fixedJumpFactor / movement
            sr *= movement
            fw *= movement

            val sin = Mth.sin((yaw * (Math.PI.toFloat() / 180.0F)).toDouble())
            val cos = Mth.cos((yaw * (Math.PI.toFloat() / 180.0F)).toDouble())
            motionX += sr * cos - fw * sin
            motionZ += fw * cos + sr * sin
        }

        motionY -= 0.08
        motionY *= 0.9800000190734863
        x += motionX
        y += motionY
        z += motionZ
        motionX *= 0.91
        motionZ *= 0.91
    }

    /**
     * 将下落轨迹向前模拟指定 tick 数。
     *
     * @param ticks 模拟的 tick 数
     */
    context(nn: NonNullContext)
    fun calculate(ticks: Int): FallingPlayer {
        for (i in 0 until ticks) {
            calculateForTick()
        }
        return this
    }

    /**
     * 向前模拟轨迹并查找最先落到的方块。
     *
     * @param ticks 模拟的 tick 数
     * @return 最先碰撞的方块位置；未碰撞时返回 null
     */
    context(nn: NonNullContext)
    fun findCollision(ticks: Int): BlockPos? {
        for (i in 0 until ticks) {
            val start = Vec3(x, y, z)
            calculateForTick()
            val end = Vec3(x, y, z)
            val halfWidth = nn.player.bbWidth / 2.0F

            raytrace(start, end)?.let { return it }
            raytrace(start.add(halfWidth.toDouble(), 0.0, halfWidth.toDouble()), end)?.let { return it }
            raytrace(start.add(-halfWidth.toDouble(), 0.0, halfWidth.toDouble()), end)?.let { return it }
            raytrace(start.add(halfWidth.toDouble(), 0.0, -halfWidth.toDouble()), end)?.let { return it }
            raytrace(start.add(-halfWidth.toDouble(), 0.0, -halfWidth.toDouble()), end)?.let { return it }
            raytrace(start.add(halfWidth.toDouble(), 0.0, (halfWidth / 2.0F).toDouble()), end)?.let { return it }
            raytrace(start.add(-halfWidth.toDouble(), 0.0, (halfWidth / 2.0F).toDouble()), end)?.let { return it }
            raytrace(start.add((halfWidth / 2.0F).toDouble(), 0.0, halfWidth.toDouble()), end)?.let { return it }
            raytrace(start.add((halfWidth / 2.0F).toDouble(), 0.0, -halfWidth.toDouble()), end)?.let { return it }
        }

        return null
    }

    context(nn: NonNullContext)
    private fun raytrace(start: Vec3, end: Vec3): BlockPos? {
        val result = nn.level.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                nn.player,
            )
        )

        if (result.type == HitResult.Type.BLOCK && result.direction == Direction.UP) {
            return result.blockPos
        }

        return null
    }

}
