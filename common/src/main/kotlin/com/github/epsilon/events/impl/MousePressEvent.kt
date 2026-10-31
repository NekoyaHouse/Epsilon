package com.github.epsilon.events.impl

import com.github.epsilon.events.bus.Cancellable

class MousePressEvent(val button: Int, val action: Int, val modifiers: Int) : Cancellable()
