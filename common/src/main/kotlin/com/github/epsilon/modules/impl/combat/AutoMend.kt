package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rotation
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.interaction
import com.github.epsilon.utils.safety.player
import net.minecraft.world.item.Items

object AutoMend : Module("Auto Mend", Category.COMBAT) {

    private enum class SwitchMode {
        Normal,
        Silent,
    }

    private val switchMode: EnumSetting<SwitchMode> = enumSetting("Switch Mode", SwitchMode.Normal)
    private val swingHand by boolSetting("Swing Hand", false)

    private var shouldSwapBack = false

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            val result = InvUtils.findInHotbar(Items.EXPERIENCE_BOTTLE)
            if (!result.found()) return@nonNullHandler

            RotationManager.setRotations(Rotation(player.yRot, 90f), 180.0, Priority.High)

            InvUtils.swap(result.slot, true)

            val hand = result.getHand()
            interaction.useItem(player, hand)
            SwingMode.of(swingHand).interactSwing(hand)

            if (switchMode.`is`(SwitchMode.Silent)) {
                InvUtils.swapBack()
            } else {
                shouldSwapBack = true
            }
        }
    }

    override fun onEnable() {
        shouldSwapBack = false
    }

    override fun onDisable() {
        if (shouldSwapBack) {
            val nn = NonNullContext.current ?: return
            context(nn) { InvUtils.swapBack() }
        }
    }

}
