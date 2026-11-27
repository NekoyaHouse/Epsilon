package com.github.epsilon.gui.dropdown

import com.github.epsilon.kernel.render.animation.Easing

internal object DropdownEntranceMotion {
    fun progress(elapsedMs: Float, delayMs: Int, durationMs: Int): Float =
        ((elapsedMs - delayMs) / durationMs.coerceAtLeast(1)).coerceIn(0.0f, 1.0f)

    fun opacity(progress: Float): Float {
        val t = (progress / 0.45f).coerceIn(0.0f, 1.0f)
        return Easing.EASE_OUT_CUBIC.function.apply(t)
    }

    fun settle(progress: Float, bouncePercent: Int): Float {
        val t = progress.coerceIn(0.0f, 1.0f)
        val peak = 1.0f + bouncePercent.coerceIn(0, 30) / 100.0f
        return if (t < 0.62f) {
            val u = t / 0.62f
            peak * Easing.EASE_OUT_CUBIC.function.apply(u)
        } else {
            val u = (t - 0.62f) / 0.38f
            peak + (1.0f - peak) * Easing.SMOOTH_STEP.function.apply(u)
        }
    }
}
