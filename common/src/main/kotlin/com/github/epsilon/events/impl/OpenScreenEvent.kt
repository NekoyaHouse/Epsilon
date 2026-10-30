package com.github.epsilon.events.impl

import com.github.epsilon.events.bus.Cancellable
import net.minecraft.client.gui.screens.Screen

class OpenScreenEvent(val screen: Screen) : Cancellable()
