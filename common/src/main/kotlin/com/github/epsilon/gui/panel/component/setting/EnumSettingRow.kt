package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.component.PanelElements
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent

class EnumSettingRow(private val enumSetting: EnumSetting<*>) : SettingRow<EnumSetting<*>>(enumSetting) {

    private val dropdownAnimation = Animation(Easing.EASE_OUT_CUBIC, 180L)
    private var dropdownOpen = false

    init {
        dropdownAnimation.startValue = 0.0f
    }

    fun setDropdownOpen(open: Boolean) {
        this.dropdownOpen = open
    }

    override fun hasActiveAnimation(): Boolean = !dropdownAnimation.isFinished

    override fun buildUi(
        scope: UiTree.Scope, guiGraphics: GuiGraphicsExtractor, textRenderer: TextRenderer,
        bounds: UiRect, hoverProgress: Float, mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        val supported = enumSetting.isCurrentValueSupported()
        val labelScale = 0.68f
        val labelY = (bounds.height - textRenderer.getHeight(labelScale)) / 2.0f
        val chipTextScale = 0.60f
        scope.roundRect(
            0.0f, 0.0f, bounds.width, bounds.height,
            MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(hoverProgress)
        )
        scope.text(
            enumSetting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale,
            if (supported) MD3Theme.TEXT_PRIMARY else MD3Theme.TEXT_MUTED
        )
        val chipBounds = getChipBounds(textRenderer, bounds).relativeTo(bounds)
        if (supported) {
            scope.chip(
                chipBounds, enumSetting.translatedValue, chipTextScale,
                MD3Theme.SECONDARY_CONTAINER, MD3Theme.ON_SECONDARY_CONTAINER, null, 0.58f, null
            )
        } else {
            scope.chip(
                chipBounds, enumSetting.translatedValue, chipTextScale,
                MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGHEST, 255), MD3Theme.TEXT_MUTED, null, 0.58f, null
            )
            LabelBadges.platformOnlyChip(scope, textRenderer, bounds, chipBounds)
        }

        val chevronProgress = scope.animate(dropdownAnimation, dropdownOpen)
        val chevronSize = 3.0f
        val chevronCenterX = chipBounds.right() - 7.5f
        val chevronCenterY = chipBounds.y + chipBounds.height / 2.0f
        scope.triangle(
            chevronCenterX, chevronCenterY, chevronSize, chevronProgress,
            if (supported) MD3Theme.ON_SECONDARY_CONTAINER else MD3Theme.TEXT_MUTED
        )
    }

    fun getChipBounds(textRenderer: TextRenderer, bounds: UiRect): UiRect = PanelElements.measureAssistChipBounds(
        textRenderer, bounds, enumSetting.translatedValue, 0.60f, 8.0f, 10.0f, 96.0f
    )

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean =
        bounds.contains(event.x, event.y) && event.button() == InputConstants.MOUSE_BUTTON_LEFT

}
