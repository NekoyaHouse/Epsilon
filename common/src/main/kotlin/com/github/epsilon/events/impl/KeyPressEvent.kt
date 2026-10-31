package com.github.epsilon.events.impl

import com.github.epsilon.events.bus.Cancellable
import net.minecraft.client.input.KeyEvent

class KeyPressEvent(val keyEvent: KeyEvent, val action: Int) : Cancellable() {

    fun getKey(): Int = keyEvent.key()

    fun getModifiers(): Int = keyEvent.modifiers()

}
