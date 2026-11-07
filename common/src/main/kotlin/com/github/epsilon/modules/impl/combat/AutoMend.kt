package com.github.epsilon.modules.impl.combat

import com.github.epsilon.kernel.jvm.*
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.player.InvUtils.findInHotbar
import com.github.epsilon.utils.player.InvUtils.swap
import com.github.epsilon.utils.player.InvUtils.swapBack
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rotation
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
        onEnable {
            shouldSwapBack = false
        }

        onDisable {
            if (shouldSwapBack) {
                runSafe { swapBack() }
            }
            shouldSwapBack = false
        }

        onUpdate {
            val result = findInHotbar(Items.EXPERIENCE_BOTTLE)
            if (!result.found()) return@onUpdate

            RotationManager.setRotations(Rotation(player.yRot, 90f), 180.0, Priority.High)

            swap(result.slot, true)

            val hand = result.getHand()
            gameMode.useItem(player, hand)
            SwingMode.of(swingHand).interactSwing(hand)

            if (switchMode.`is`(SwitchMode.Silent)) {
                swapBack()
            } else {
                shouldSwapBack = true
            }
        }
    }

}
