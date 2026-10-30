package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.component.PanelElements
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.StringListSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent

class StringListSettingRow(private val stringListSetting: StringListSetting) :
    SettingRow<StringListSetting>(stringListSetting) {

    override fun buildUi(
        scope: UiTree.Scope, guiGraphics: GuiGraphicsExtractor, textRenderer: TextRenderer,
        bounds: UiRect, hoverProgress: Float, mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        val labelScale = 0.68f
        val labelY = (bounds.height - textRenderer.getHeight(labelScale)) / 2.0f
        val summary = stringListSetting.size().toString() + EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName()
        val chipTextScale = 0.58f

        scope.roundRect(
            0.0f, 0.0f, bounds.width, bounds.height,
            MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(hoverProgress)
        )
        scope.text(stringListSetting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)

        val chipBounds = PanelElements.measureAssistChipBounds(
            textRenderer, bounds, summary, chipTextScale, 8.0f, 12.0f, 94.0f
        ).relativeTo(bounds)
        scope.chip(
            chipBounds, summary, chipTextScale, MD3Theme.SECONDARY_CONTAINER, MD3Theme.ON_SECONDARY_CONTAINER,
            IconChars.ADD, 0.58f, StaticFontLoader.ICONS
        )
    }

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean =
        bounds.contains(event.x, event.y) && event.button() == InputConstants.MOUSE_BUTTON_LEFT

}
