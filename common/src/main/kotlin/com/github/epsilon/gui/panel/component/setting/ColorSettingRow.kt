package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.ColorSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent

class ColorSettingRow(private val colorSetting: ColorSetting) : SettingRow<ColorSetting>(colorSetting) {

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
        scope.text(colorSetting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)
        val swatchBounds = getSwatchBounds(bounds).relativeTo(bounds)
        scope.roundRect(
            swatchBounds.x, swatchBounds.y, swatchBounds.width, swatchBounds.height,
            5.0f, MD3Theme.SURFACE_CONTAINER_HIGHEST
        )
        scope.roundRect(
            swatchBounds.x, swatchBounds.y, swatchBounds.width, swatchBounds.height,
            5.0f, colorSetting.value
        )
        scope.roundRect(
            swatchBounds.x, swatchBounds.y, swatchBounds.width, swatchBounds.height,
            5.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE_SOFT, 58)
        )
    }

    fun getSwatchBounds(bounds: UiRect): UiRect {
        val swatchX = bounds.right() - MD3Theme.ROW_TRAILING_INSET - 12.0f
        val swatchY = bounds.y + (bounds.height - 12.0f) / 2.0f
        return UiRect(swatchX, swatchY, 12.0f, 12.0f)
    }

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean =
        event.button() == InputConstants.MOUSE_BUTTON_LEFT && bounds.contains(event.x, event.y)

}
