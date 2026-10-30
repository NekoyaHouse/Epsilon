package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.ChatScreen

object GUIMove : Module("GUI Move", Category.MOVEMENT) {

    private val sneakValue by boolSetting("Sneak", false)

    init {
        handler<KeyboardInputEvent>(EventPriority.HIGHEST) { event ->
            if (mc.gui.screen() == null || mc.gui.screen() is ChatScreen) return@handler

            val up = isKeyDown(mc.options.keyUp)
            val down = isKeyDown(mc.options.keyDown)
            val left = isKeyDown(mc.options.keyLeft)
            val right = isKeyDown(mc.options.keyRight)
            val jump = isKeyDown(mc.options.keyJump)
            val sneak = isKeyDown(mc.options.keyShift)
            val sprint = isKeyDown(mc.options.keySprint)

            val forward = if (up == down) 0.0f else if (up) 1.0f else -1.0f
            val strafe = if (left == right) 0.0f else if (left) 1.0f else -1.0f

            event.forward = forward
            event.strafe = strafe
            event.isJump = jump
            if (sneakValue) event.isSneak = sneak
            event.isSprint = sprint
        }
    }

    private fun isKeyDown(mapping: KeyMapping): Boolean {
        return InputConstants.isKeyDown(mapping.defaultKey.value)
    }

}
