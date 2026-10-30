package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent

class KeybindSettingRow(private val keybindSetting: KeybindSetting) : SettingRow<KeybindSetting>(keybindSetting) {

    private val chipHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val focusAnimation = Animation(Easing.EASE_OUT_CUBIC, 150L)

    private var listeningState = false

    init {
        chipHoverAnimation.startValue = 0.0f
        focusAnimation.startValue = 0.0f
    }

    fun isListening(): Boolean = listeningState

    fun setListening(listening: Boolean) {
        this.listeningState = listening
    }

    override fun buildUi(
        scope: UiTree.Scope, guiGraphics: GuiGraphicsExtractor, textRenderer: TextRenderer,
        bounds: UiRect, hoverProgress: Float, mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        val labelScale = 0.68f
        val labelY = (bounds.height - textRenderer.getHeight(labelScale)) / 2.0f

        scope.roundRect(
            0.0f, 0.0f, bounds.width, bounds.height,
            MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(hoverProgress)
        )
        scope.text(keybindSetting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)

        val absoluteChipBounds = getChipBounds(bounds)
        val chipBounds = absoluteChipBounds.relativeTo(bounds)
        chipHoverAnimation.run(if (absoluteChipBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
        focusAnimation.run(if (listeningState) 1.0f else 0.0f)
        val chipHover = chipHoverAnimation.value
        val focusProgress = focusAnimation.value

        val radius = 8.0f

        if (focusProgress > 0.01f) {
            val haloInset = 1.5f * focusProgress
            scope.roundRect(
                chipBounds.x - haloInset, chipBounds.y - haloInset,
                chipBounds.width + haloInset * 2.0f, chipBounds.height + haloInset * 2.0f,
                radius + haloInset, MD3Theme.withAlpha(MD3Theme.PRIMARY, (28 * focusProgress).toInt())
            )
        }

        val background = MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, focusProgress)
        val foreground = MD3Theme.lerp(MD3Theme.ON_SECONDARY_CONTAINER, MD3Theme.ON_PRIMARY_CONTAINER, focusProgress)
        scope.roundRect(chipBounds.x, chipBounds.y, chipBounds.width, chipBounds.height, radius, background)

        if (chipHover > 0.01f) {
            val hoverAlpha = if (listeningState) 18 else 12
            scope.roundRect(
                chipBounds.x, chipBounds.y, chipBounds.width, chipBounds.height, radius,
                MD3Theme.withAlpha(foreground, (hoverAlpha * chipHover).toInt())
            )
        }

        val label = if (listeningState) "..." else formatKeybind(keybindSetting.value)
        val chipTextScale = 0.52f
        val textWidth = textRenderer.getWidth(label, chipTextScale)
        val textHeight = textRenderer.getHeight(chipTextScale)
        val textX = chipBounds.x + (chipBounds.width - textWidth) / 2.0f
        val textY = chipBounds.y + (chipBounds.height - textHeight) / 2.0f
        scope.text(label, textX, textY, chipTextScale, foreground)
    }

    fun getChipBounds(bounds: UiRect): UiRect {
        val chipWidth = 56.0f
        val chipHeight = 18.0f
        val chipX = bounds.right() - MD3Theme.ROW_TRAILING_INSET - chipWidth
        val chipY = bounds.y + (bounds.height - chipHeight) / 2.0f
        return UiRect(chipX, chipY, chipWidth, chipHeight)
    }

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (!bounds.contains(event.x, event.y) || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        val chipBounds = getChipBounds(bounds)
        return chipBounds.contains(event.x, event.y)
    }

    override fun hasActiveAnimation(): Boolean =
        !chipHoverAnimation.isFinished || !focusAnimation.isFinished

    private fun formatKeybind(keyCode: Int): String {
        if (keyCode == KeybindUtils.NONE) {
            return EpsilonTranslations.Keybind.NONE.getTranslatedName()
        }
        return KeybindUtils.format(keyCode)
    }

}
