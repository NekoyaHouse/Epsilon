package com.github.epsilon.modules.impl.player

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.KeyPressEvent
import com.github.epsilon.events.impl.MousePressEvent
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.safety.NonNullContext
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult

object KeyFriend : Module("Key Friend", Category.PLAYER) {

    private val activateKey by keybindSetting(
        "Activate Key",
        KeybindUtils.encodeMouseButton(InputConstants.MOUSE_BUTTON_MIDDLE)
    )

    init {
        nonNullHandler<KeyPressEvent>(EventPriority.HIGHEST) { event ->
            if (handleInput(event.getKey(), event.action)) {
                event.cancel()
            }
        }

        nonNullHandler<MousePressEvent>(EventPriority.HIGHEST) { event ->
            if (handleInput(KeybindUtils.encodeMouseButton(event.button), event.action)) {
                event.cancel()
            }
        }
    }

    private fun handleInput(key: Int, action: Int): Boolean {
        if (NonNullContext.current == null) return false
        if (mc.gui.screen() != null || key != activateKey || action != InputConstants.PRESS) return false

        val hitResult = mc.hitResult
        if (hitResult !is EntityHitResult) return false

        val target = hitResult.entity
        if (target !is Player) return false

        val playerName = target.gameProfile.name()
        if (FriendManager.isFriend(playerName)) {
            FriendManager.removeFriend(playerName)
            NotificationManager.error(
                translatedName,
                EpsilonTranslations.component(EpsilonTranslations.Notifications.KEY_FRIEND_REMOVED, playerName).string
            )
        } else {
            FriendManager.addFriend(playerName)
            NotificationManager.success(
                translatedName,
                EpsilonTranslations.component(EpsilonTranslations.Notifications.KEY_FRIEND_ADDED, playerName).string
            )
        }
        return true
    }

}
