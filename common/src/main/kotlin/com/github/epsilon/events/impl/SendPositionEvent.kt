package com.github.epsilon.events.impl

import com.github.epsilon.events.bus.Cancellable

class SendPositionEvent(
    var x: Double,
    var y: Double,
    var z: Double,
    var yaw: Float,
    var pitch: Float,
    var isOnGround: Boolean,
) : Cancellable()
