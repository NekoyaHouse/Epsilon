package com.github.epsilon.gui.troll
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

class TrollScroll {

    private var position: Float = 0.0f
    private var velocity: Float = 0.0f
    private var lastUpdate: Long = 0L

    fun update(maximum: Float): Float {
        val now = System.nanoTime()
        val elapsed = if (lastUpdate == 0L) 0.0f else minOf((now - lastUpdate) / 100_000_000.0f, 1.0f)
        lastUpdate = now
        val decay = (0.25).pow(elapsed.toDouble()).toFloat()
        position += velocity * (decay - 1.0f) / ln(0.25).toFloat()
        velocity *= decay
        if (position < 0.0f || position > maximum) {
            val target = (position).coerceIn(0.0f, maximum)
            position += (target - position) * (1.0f - exp((-elapsed * 2.5f).toDouble()).toFloat())
            velocity += (target - position) * elapsed
        }
        position = (position).coerceIn(-36.0f, maximum + 36.0f)
        if (abs(velocity) < 0.01f && abs(position - (position).coerceIn(0.0f, maximum)) < 0.1f) {
            position = (position).coerceIn(0.0f, maximum)
        }
        return position
    }

    fun scroll(amount: Double) {
        velocity -= amount.toFloat() * 36.0f
    }

    fun stop() {
        velocity = 0.0f
        lastUpdate = 0L
    }

}
