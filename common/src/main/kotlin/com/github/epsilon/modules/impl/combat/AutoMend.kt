package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
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

            RotationManager.INSTANCE.setRotations(Rot2f(player.yRot, 90f), 180.0, Priority.High)

            InvUtils.swap(result.slot, true)

            val hand = result.getHand()
            interaction.useItem(player, hand)
            if (swingHand) {
                PlayerUtils.swingHand(hand)
            }

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
