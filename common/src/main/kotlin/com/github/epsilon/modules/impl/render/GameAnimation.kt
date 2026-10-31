package com.github.epsilon.modules.impl.render

import com.github.epsilon.modules.Category
import com.github.epsilon.settings.getValue
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import kotlin.math.abs
import kotlin.math.roundToInt

object GameAnimation : Module("Game Animation", Category.RENDER) {

    private const val HOTBAR_RESET_DISTANCE = 240.0f
    private const val HOTBAR_ANIMATION_DURATION = 150L
    private const val INVENTORY_START_SCALE = 0.85f
    private const val INVENTORY_ANIMATION_DURATION = 180L

    private val hotbarAnimation = Animation(Easing.EASE_OUT_CUBIC, HOTBAR_ANIMATION_DURATION)
    private val inventoryAnimation = Animation(Easing.EASE_OUT_CUBIC, INVENTORY_ANIMATION_DURATION)
    private var lastHotbarTargetX = Float.NaN

    val hotbar by boolSetting("Hotbar", true)
    val inventory by boolSetting("Inventory", true)

    override fun onDisable() {
        resetHotbarAnimation()
        resetInventoryAnimation()
    }

    fun getHotbarSelectionX(vanillaX: Int): Int {
        if (!shouldAnimateHotbar()) {
            resetHotbarAnimation()
            return vanillaX
        }

        if (!lastHotbarTargetX.isFinite() || abs(hotbarAnimation.value - vanillaX) > HOTBAR_RESET_DISTANCE) {
            resetHotbarAnimation(vanillaX.toFloat())
            return vanillaX
        }

        lastHotbarTargetX = vanillaX.toFloat()
        hotbarAnimation.run(vanillaX.toFloat())
        return (hotbarAnimation.value).roundToInt()
    }

    fun beginInventoryAnimation() {
        if (!shouldAnimateInventory()) {
            resetInventoryAnimation()
            return
        }

        inventoryAnimation.startValue = INVENTORY_START_SCALE
        inventoryAnimation.reset()
    }

    fun getInventoryScale(): Float {
        if (!shouldAnimateInventory()) {
            resetInventoryAnimation()
            return 1.0f
        }

        inventoryAnimation.run(1.0f)
        return inventoryAnimation.value
    }

    fun getCurrentInventoryScale(): Float = if (shouldAnimateInventory()) inventoryAnimation.value else 1.0f

    private fun shouldAnimateHotbar(): Boolean = isEnabled && hotbar && !nullCheck()

    private fun shouldAnimateInventory(): Boolean = isEnabled && inventory && !nullCheck()

    private fun resetHotbarAnimation() {
        lastHotbarTargetX = Float.NaN
        hotbarAnimation.startValue = 0.0f
        hotbarAnimation.value = 0.0f
        hotbarAnimation.isFinished = true
    }

    private fun resetHotbarAnimation(x: Float) {
        lastHotbarTargetX = x
        hotbarAnimation.startValue = x
        hotbarAnimation.value = x
        hotbarAnimation.isFinished = true
    }

    private fun resetInventoryAnimation() {
        inventoryAnimation.startValue = 1.0f
        inventoryAnimation.value = 1.0f
        inventoryAnimation.isFinished = true
    }

}
