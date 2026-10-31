package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.client.gui.screens.Screen

class OpenScreenEvent(val screen: Screen?) : Cancellable(), IEvent<OpenScreenEvent>, IPosting by Companion {
    companion object : EventBus()
}
