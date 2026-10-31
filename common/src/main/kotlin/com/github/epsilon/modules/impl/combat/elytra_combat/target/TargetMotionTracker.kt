package com.github.epsilon.modules.impl.combat.elytra_combat.target

import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatWeaponController
import com.github.epsilon.utils.math.toDegrees
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.math.abs
import kotlin.math.acos

/**
 * 维护单个目标的最近位置历史，并提供预测和动作分类。
 *
 * 采样只在客户端 tick 执行，避免从网络线程访问实体或 Level。历史保留 30 个样本，
 * 目标切换时立即清空，防止把旧目标速度带入新目标。
 */
class TargetMotionTracker {

    private val samples = ArrayDeque<Sample>()
    private var trackedTarget: LivingEntity? = null

    @Volatile
    private var trackedTargetId: Int = -1

    @Volatile
    private var teleportPending: Boolean = false

    fun reset() {
        this.trackedTarget = null
        this.trackedTargetId = -1
        this.teleportPending = false
        this.samples.clear()
    }

    fun update(
        target: LivingEntity,
        tick: Int,
        mode: PredictorMode,
        ticksLater: Int,
        historyTicks: Int
    ): TargetSnapshot {
        if (target !== this.trackedTarget || this.teleportPending) {
            // 目标切换或传送后旧速度/相位不再可信，直接清空历史。
            this.trackedTarget = target
            this.trackedTargetId = target.id
            this.teleportPending = false
            this.samples.clear()
        }

        val position = target.position()
        // 同一 tick 重复调用时覆盖最后一个样本，避免历史中出现重复时间戳。
        val newest = this.samples.peekLast()
        if (newest == null || newest.tick != tick) {
            this.samples.addLast(Sample(position, tick))
        } else {
            this.samples.removeLast()
            this.samples.addLast(Sample(position, tick))
        }
        while (this.samples.size > MAX_HISTORY) {
            this.samples.removeFirst()
        }

        val predicted = predict(ticksLater, mode, historyTicks)
        // supported 通过向下探测 0.04 格判断目标是否站在方块/实体上。
        return TargetSnapshot(
            target,
            position,
            knownDeltaMovement(),
            predicted,
            classify(tick),
            target.onGround(),
            target.onGround() || !target.level().noBlockCollision(target, target.boundingBox.move(0.0, -0.04, 0.0)),
            CombatWeaponController.isUsingSpear(target),
        )
    }

    /**
     * 由网络线程标记目标传送；实际轨迹清理延迟到客户端 tick，避免跨线程访问 Entity。
     */
    fun noteTeleport(entityId: Int) {
        if (entityId == this.trackedTargetId) {
            this.teleportPending = true
        }
    }

    fun knownDeltaMovement(): Vec3 {
        // 使用最近两个不同 tick 的样本差计算速度，跳 tick 时按时间间隔归一化。
        if (this.samples.size < 2) {
            return Vec3.ZERO
        }

        val newest = this.samples.peekLast() ?: return Vec3.ZERO
        var second: Sample? = null
        var previous: Sample? = null
        for (sample in this.samples) {
            if (sample === newest) {
                second = previous
                break
            }
            previous = sample
        }
        val secondSample = second ?: return Vec3.ZERO
        if (newest.tick <= secondSample.tick) {
            return Vec3.ZERO
        }
        return newest.position.subtract(secondSample.position).scale(1.0 / (newest.tick - secondSample.tick))
    }

    fun predict(ticksLater: Int, mode: PredictorMode, historyTicks: Int): Vec3 {
        if (this.samples.isEmpty()) {
            return Vec3.ZERO
        }

        val window = recentSamples(historyTicks)
        // 样本不足或不需要外推时直接返回最新位置。
        if (window.size < 2 || ticksLater <= 0) {
            return window.last().position
        }

        return when (mode) {
            PredictorMode.Linear -> linearPrediction(window, ticksLater)
            PredictorMode.Quadratic -> quadraticPrediction(window, ticksLater)
            PredictorMode.NV -> nvPrediction(window, ticksLater)
        }
    }

    fun classify(currentTick: Int): TargetAction {
        // 分类只使用最近 3 个样本，避免旧轨迹干扰当前动作判断。
        if (this.samples.size < 2) {
            return TargetAction.CIRCLING
        }

        val window = recentSamples(3)
        val oldest = window.first()
        if (currentTick - oldest.tick > AFK_TICKS) {
            return TargetAction.AFK
        }
        if (window.size < 3) {
            return TargetAction.TOWARDS
        }

        val pos0 = window[0].position
        val pos1 = window[1].position
        val pos2 = window[2].position
        val dist01 = pos0.distanceTo(pos1)
        val dist12 = pos1.distanceTo(pos2)
        if (dist01 < 1.0E-6 && dist12 < 1.0E-6) {
            return TargetAction.AFK
        }
        if (dist01 < SLOW_DISTANCE && dist12 < SLOW_DISTANCE) {
            return TargetAction.SLOW_SPEED
        }

        val ab = pos1.subtract(pos0)
        val bc = pos2.subtract(pos1)
        // 两段位移夹角越大越接近绕圈；夹角小则再判断朝向/远离玩家。
        val denominator = ab.length() * bc.length()
        if (denominator < 1.0E-6) {
            return TargetAction.SLOW_SPEED
        }

        val dot = ab.dot(bc) / denominator
        val angle = (acos((dot).coerceIn(-1.0, 1.0))).toDegrees()
        if (angle >= STRAIGHT_ANGLE_DEGREES) {
            return TargetAction.CIRCLING
        }

        val localPlayer = mc.player ?: return TargetAction.TOWARDS
        val toPlayer = localPlayer.position().subtract(pos2)
        val moveDot = bc.normalize().dot(toPlayer.normalize())
        return if (moveDot > 0.0) TargetAction.TOWARDS else TargetAction.ESCAPING
    }

    private fun recentSamples(count: Int): List<Sample> {
        val size = minOf(maxOf(1, count), this.samples.size)
        val result = ArrayList<Sample>(size)
        val skip = this.samples.size - size
        var index = 0
        for (sample in this.samples) {
            if (index++ >= skip) {
                result.add(sample)
            }
        }
        return result
    }

    private data class Sample(val position: Vec3, val tick: Int)

    companion object {

        // 分类阈值：20 tick 无位移视为 AFK，连续两段位移小于 0.75 视为慢速，转向超过 60 度视为绕圈。
        private const val MAX_HISTORY = 30
        private const val AFK_TICKS = 20
        private const val SLOW_DISTANCE = 0.75
        private const val STRAIGHT_ANGLE_DEGREES = 60.0

        /**
         * 对所有可用样本做最小二乘线性回归，并以外推到未来 tick。
         */
        private fun linearPrediction(samples: List<Sample>, ticksLater: Int): Vec3 {
            val newest = samples.last()
            var meanTick = 0.0
            var meanPos = Vec3.ZERO
            for (sample in samples) {
                meanTick += sample.tick
                meanPos = meanPos.add(sample.position)
            }
            meanTick /= samples.size
            meanPos = meanPos.scale(1.0 / samples.size)

            var variance = 0.0
            var covariance = Vec3.ZERO
            for (sample in samples) {
                val dt = sample.tick - meanTick
                variance += dt * dt
                covariance = covariance.add(sample.position.subtract(meanPos).scale(dt))
            }
            if (variance < 1.0E-6) {
                return newest.position
            }

            val slope = covariance.scale(1.0 / variance)
            val futureTick = newest.tick + ticksLater
            return meanPos.add(slope.scale(futureTick - meanTick))
        }

        /**
         * 三次样本不足时退化为线性；足够时拟合 x=a*t^2+b*t+c。
         */
        private fun quadraticPrediction(samples: List<Sample>, ticksLater: Int): Vec3 {
            if (samples.size < 3) {
                return linearPrediction(samples, ticksLater)
            }

            val newest = samples.last()
            val t = DoubleArray(samples.size)
            val sx = DoubleArray(samples.size)
            val sy = DoubleArray(samples.size)
            val sz = DoubleArray(samples.size)
            for (i in samples.indices) {
                val sample = samples[i]
                t[i] = (sample.tick - newest.tick).toDouble()
                sx[i] = sample.position.x
                sy[i] = sample.position.y
                sz[i] = sample.position.z
            }

            val x = polynomial2(t, sx)
            val y = polynomial2(t, sy)
            val z = polynomial2(t, sz)
            val future = ticksLater.toDouble()
            return Vec3(
                x[0] * future * future + x[1] * future + x[2],
                y[0] * future * future + y[1] * future + y[2],
                z[0] * future * future + z[1] * future + z[2],
            )
        }

        /**
         * Slimefun 的 NV 模型：对相邻速度差做指数加权平均，再线性外推。
         */
        private fun nvPrediction(samples: List<Sample>, ticksLater: Int): Vec3 {
            var weighted = Vec3.ZERO
            var previous: Vec3? = null
            for (sample in samples) {
                val previousPos = previous
                if (previousPos != null) {
                    weighted = weighted.add(sample.position.subtract(previousPos)).scale(0.5)
                }
                previous = sample.position
            }
            return samples.last().position.add(weighted.scale(ticksLater.toDouble()))
        }

        private fun polynomial2(t: DoubleArray, values: DoubleArray): DoubleArray {
            // 高斯消元拟合二次多项式；奇异矩阵退化为常量预测。
            val st0 = t.size.toDouble()
            var st1 = 0.0
            var st2 = 0.0
            var st3 = 0.0
            var st4 = 0.0
            var sv0 = 0.0
            var sv1 = 0.0
            var sv2 = 0.0
            for (i in t.indices) {
                val ti = t[i]
                val ti2 = ti * ti
                st1 += ti
                st2 += ti2
                st3 += ti2 * ti
                st4 += ti2 * ti2
                sv0 += values[i]
                sv1 += ti * values[i]
                sv2 += ti2 * values[i]
            }

            val matrix = arrayOf(
                doubleArrayOf(st4, st3, st2, sv2),
                doubleArrayOf(st3, st2, st1, sv1),
                doubleArrayOf(st2, st1, st0, sv0),
            )
            for (column in 0 until 3) {
                var pivot = column
                for (row in column + 1 until 3) {
                    if (abs(matrix[row][column]) > abs(matrix[pivot][column])) {
                        pivot = row
                    }
                }
                val swap = matrix[column]
                matrix[column] = matrix[pivot]
                matrix[pivot] = swap
                val divisor = matrix[column][column]
                if (abs(divisor) < 1.0E-9) {
                    return doubleArrayOf(0.0, 0.0, values[values.size - 1])
                }
                for (i in column until 4) {
                    matrix[column][i] /= divisor
                }
                for (row in 0 until 3) {
                    if (row == column) {
                        continue
                    }
                    val factor = matrix[row][column]
                    for (i in column until 4) {
                        matrix[row][i] -= factor * matrix[column][i]
                    }
                }
            }
            return doubleArrayOf(matrix[0][3], matrix[1][3], matrix[2][3])
        }

    }

}
