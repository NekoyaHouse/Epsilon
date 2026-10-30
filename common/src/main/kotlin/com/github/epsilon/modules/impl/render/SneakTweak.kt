package com.github.epsilon.modules.impl.render

import com.github.epsilon.modules.Category
import com.github.epsilon.settings.getValue
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting

object SneakTweak : Module("Sneak Tweak", Category.RENDER) {

    private enum class SneakingEyeHeight {
        Default,
        Custom,
        Pre_1_14,
        Pre_1_9
    }

    private val smoothing by boolSetting("Smoothing", true)
    private val speedPercentage by intSetting("Speed Percentage", 100, 25, 300, 1, dependency = { smoothing })
    private val sneakingEyeHeight by enumSetting("Sneaking Eye Height", SneakingEyeHeight.Default, onChanged = { _ -> refreshPlayerDimensions() })
    private val customSneakingEyeHeight by doubleSetting(
        "Custom Sneaking Eye Height", 1.27, 0.0, 1.8, 0.01,
        dependency = { sneakingEyeHeight == SneakingEyeHeight.Custom }
    , onChanged = { _ -> refreshPlayerDimensions() })
    private val thirdPersonEyeHeight by boolSetting("Third Person Eye Height", false, onChanged = { _ -> refreshPlayerDimensions() })

    override fun onEnable() {
        refreshPlayerDimensions()
    }

    override fun onDisable() {
        refreshPlayerDimensions()
    }

    fun getCameraSmoothingModifier(defaultModifier: Float): Float {
        if (isEnabled && smoothing) {
            return defaultModifier * (speedPercentage / 100.0f)
        }
        return defaultModifier
    }

    fun shouldSnapCameraEyeHeight(): Boolean = isEnabled && !smoothing

    fun modifySneakingEyeHeight(height: Float): Float {
        if (mc.options.cameraType.isFirstPerson || thirdPersonEyeHeight) {
            return when (sneakingEyeHeight) {
                SneakingEyeHeight.Default -> height
                SneakingEyeHeight.Pre_1_14 -> 1.42f
                SneakingEyeHeight.Pre_1_9 -> 1.54f
                SneakingEyeHeight.Custom -> customSneakingEyeHeight.toFloat()
            }
        }
        return height
    }

    private fun refreshPlayerDimensions() {
        mc.player?.refreshDimensions()
    }

}
