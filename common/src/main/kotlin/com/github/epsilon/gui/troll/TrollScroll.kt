package com.github.epsilon.gui.troll

import com.github.epsilon.gui.lib.state.LuminFrameClock
import com.github.epsilon.gui.lib.state.LuminFrameTask
import com.github.epsilon.gui.lib.state.LuminRuntime
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

class TrollScroll : LuminFrameTask, AutoCloseable {

    private var position: Float by mutableStateOf(0.0f)
    private var maximum = 0.0f
    private var clock: LuminFrameClock? = null
    private var velocity: Float = 0.0f
    private var lastUpdate: Long = 0L

    fun update(maximum: Float): Float {
        this.maximum = maximum
        val nextClock = LuminRuntime.activeRoot?.clock
        if (clock !== nextClock) {
            clock?.unregister(this)
            clock = nextClock
            lastUpdate = 0L
        }
        if (abs(velocity) >= 0.01f || position < 0.0f || position > maximum) clock?.register(this)
        return position
    }

    override fun advance(nowNanos: Long): Boolean {
        val elapsed = if (lastUpdate == 0L) 0.0f else minOf((nowNanos - lastUpdate) / 100_000_000.0f, 1.0f)
        lastUpdate = nowNanos
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
        val active = abs(velocity) >= 0.01f || abs(position - position.coerceIn(0.0f, maximum)) >= 0.1f
        if (!active) {
            velocity = 0.0f
            lastUpdate = 0L
        }
        return active
    }

    fun scroll(amount: Double) {
        velocity -= amount.toFloat() * 36.0f
        clock?.register(this)
    }

    fun reset() {
        clock?.unregister(this)
        position = 0.0f
        velocity = 0.0f
        lastUpdate = 0L
    }

    fun stop() {
        clock?.unregister(this)
        velocity = 0.0f
        lastUpdate = 0L
    }

    override fun close() { stop(); clock = null }
}
