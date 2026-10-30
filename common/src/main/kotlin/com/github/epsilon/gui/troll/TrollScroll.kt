package com.github.epsilon.gui.troll

class TrollScroll {

    private var position: Float = 0.0f
    private var velocity: Float = 0.0f
    private var lastUpdate: Long = 0L

    fun update(maximum: Float): Float {
        val now = System.nanoTime()
        val elapsed = if (lastUpdate == 0L) 0.0f else Math.min((now - lastUpdate) / 100_000_000.0f, 1.0f)
        lastUpdate = now
        val decay = Math.pow(0.25, elapsed.toDouble()).toFloat()
        position += velocity * (decay - 1.0f) / Math.log(0.25).toFloat()
        velocity *= decay
        if (position < 0.0f || position > maximum) {
            val target = Math.clamp(position, 0.0f, maximum)
            position += (target - position) * (1.0f - Math.exp((-elapsed * 2.5f).toDouble()).toFloat())
            velocity += (target - position) * elapsed
        }
        position = Math.clamp(position, -36.0f, maximum + 36.0f)
        if (Math.abs(velocity) < 0.01f && Math.abs(position - Math.clamp(position, 0.0f, maximum)) < 0.1f) {
            position = Math.clamp(position, 0.0f, maximum)
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
