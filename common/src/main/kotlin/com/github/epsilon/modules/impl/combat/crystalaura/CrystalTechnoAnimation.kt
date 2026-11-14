package com.github.epsilon.modules.impl.combat.crystalaura

import net.minecraft.world.phys.Vec3

internal class CrystalTechnoAnimation {
    private enum class Phase {
        Active,
        Waiting,
        Retracting,
        Outline,
        Hidden
    }

    private data class Visibility(val height: Double, val opacity: Double)

    private val movement = CrystalRenderAnimation()
    private var position: Vec3? = null
    private var phase = Phase.Hidden
    private var startedAt = 0L
    private var from = Visibility(0.0, 0.0)

    fun sample(
        target: Vec3?,
        now: Long,
        movingLength: Int,
        fadeLength: Int,
        hasPulses: Boolean
    ): CrystalRenderAnimation.Frame? {
        val current = visibility(now, fadeLength)
        if (target != null) {
            if (phase == Phase.Hidden) movement.clear()
            position = target
            if (phase != Phase.Active) {
                from = current
                startedAt = now
                phase = Phase.Active
            }
        } else if (phase == Phase.Active || phase == Phase.Waiting) {
            if (hasPulses) {
                phase = Phase.Waiting
            } else {
                from = current
                startedAt = now
                phase = if (current.height > 0.0) Phase.Retracting else Phase.Outline
            }
        }
        val visibility = visibility(now, fadeLength)
        val center = movement.sample(position, now, movingLength, 0)?.center ?: return null
        return if (visibility.opacity > 0.0) {
            CrystalRenderAnimation.Frame(center, visibility.opacity, visibility.height)
        } else null
    }

    fun clear() {
        movement.clear()
        position = null
        phase = Phase.Hidden
        startedAt = 0L
        from = Visibility(0.0, 0.0)
    }

    private fun visibility(now: Long, length: Int): Visibility {
        if (phase == Phase.Retracting && (length <= 0 || now - startedAt >= length)) {
            phase = Phase.Outline
            startedAt += length.coerceAtLeast(0)
            from = Visibility(0.0, from.opacity)
        }
        val progress = if (length <= 0) 1.0 else ((now - startedAt).toDouble() / length).coerceIn(0.0, 1.0)
        val eased = progress * progress * (3.0 - 2.0 * progress)
        return when (phase) {
            Phase.Active, Phase.Waiting -> Visibility(
                from.height + (1.0 - from.height) * eased,
                from.opacity + (1.0 - from.opacity) * eased
            )

            Phase.Retracting -> Visibility(from.height * (1.0 - eased), from.opacity)
            Phase.Outline -> {
                if (progress >= 1.0) phase = Phase.Hidden
                Visibility(0.0, from.opacity * (1.0 - eased))
            }

            Phase.Hidden -> Visibility(0.0, 0.0)
        }
    }
}
