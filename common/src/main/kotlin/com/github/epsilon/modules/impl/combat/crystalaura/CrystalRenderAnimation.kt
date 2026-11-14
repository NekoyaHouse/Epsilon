package com.github.epsilon.modules.impl.combat.crystalaura

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** 渲染线程按单调时钟采样；重定向从当前插值位置继续，重复目标不重启动画。 */
internal class CrystalRenderAnimation {
    data class Frame(val center: Vec3, val scale: Double) {
        val box: AABB
            get() {
                val half = scale * 0.5
                return AABB(center.x - half, center.y - half, center.z - half,
                    center.x + half, center.y + half, center.z + half)
            }
    }

    private var target: Vec3? = null
    private var from: Vec3? = null
    private var to: Vec3? = null
    private var movedAt = 0L
    private var fadedAt = 0L
    private var fromScale = 0.0

    fun sample(target: Vec3?, now: Long, movingLength: Int, fadeLength: Int): Frame? {
        if (target != this.target) {
            val scale = scale(now, fadeLength)
            if (target != null) {
                // 完全消失后直接在新目标淡入，避免从上一次战斗的位置飞过来。
                from = if (scale > 0.0) position(now, movingLength) ?: target else target
                to = target
                movedAt = now
            }
            if ((target == null) != (this.target == null)) {
                fromScale = scale
                fadedAt = now
            }
            this.target = target
        }
        val center = position(now, movingLength) ?: return null
        val scale = scale(now, fadeLength)
        return if (scale > 0.0) Frame(center, scale) else null
    }

    fun clear() {
        target = null
        from = null
        to = null
        movedAt = 0L
        fadedAt = 0L
        fromScale = 0.0
    }

    private fun position(now: Long, length: Int): Vec3? {
        val from = from ?: return null
        val to = to ?: return null
        val remaining = 1.0 - progress(now, movedAt, length)
        return from.lerp(to, 1.0 - remaining * remaining * remaining * remaining)
    }

    private fun scale(now: Long, length: Int): Double {
        val progress = progress(now, fadedAt, length)
        return if (target != null) {
            val remaining = 1.0 - progress
            fromScale + (1.0 - fromScale) * (1.0 - remaining * remaining * remaining)
        } else {
            fromScale * (1.0 - progress * progress * progress)
        }
    }

    private fun progress(now: Long, start: Long, length: Int): Double =
        if (length <= 0) 1.0 else ((now - start).toDouble() / length).coerceIn(0.0, 1.0)
}
