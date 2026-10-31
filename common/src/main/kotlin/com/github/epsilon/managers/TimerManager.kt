package com.github.epsilon.managers

import com.github.epsilon.modules.impl.player.Timer

object TimerManager {

    var timer = 1f
    var lastTimer = 0f

    fun set(factor: Float) {
        var value = factor
        if (value < 0.1f) value = 0.1f
        timer = value
    }

    fun reset() {
        timer = getDefault()
        lastTimer = timer
    }

    fun tryReset() {
        if (lastTimer != getDefault()) {
            reset()
        }
    }

    fun get(): Float {
        return timer
    }

    fun getDefault(): Float {
        return if (Timer.isEnabled) Timer.multiplier.toFloat() else 1f
    }

}
