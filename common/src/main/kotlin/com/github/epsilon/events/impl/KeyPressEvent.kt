package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.client.input.KeyEvent

class KeyPressEvent(val keyEvent: KeyEvent, val action: Int) : Cancellable(), IEvent<KeyPressEvent>,
    IPosting by Companion {
    companion object : EventBus()

    fun getKey(): Int = keyEvent.key()

    fun getModifiers(): Int = keyEvent.modifiers()

}
