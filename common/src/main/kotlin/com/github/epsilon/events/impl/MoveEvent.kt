package com.github.epsilon.events.impl

import com.github.epsilon.events.bus.Cancellable

class MoveEvent(var x: Double, var y: Double, var z: Double) : Cancellable()
