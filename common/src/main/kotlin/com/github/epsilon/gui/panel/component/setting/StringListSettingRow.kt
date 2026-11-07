package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.lib.Chip
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.panel.component.PanelElements
import com.github.epsilon.gui.panel.component.RowSurface
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.StringListSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent

private const val CHIP_TEXT_SCALE = 0.58f
private const val CHIP_HORIZONTAL_PADDING = 8.0f
private const val CHIP_TRAILING_SLOT = 12.0f
private const val CHIP_MAX_WIDTH = 94.0f

class StringListSettingRow(private val stringListSetting: StringListSetting) :
    SettingRow<StringListSetting>(stringListSetting) {

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean =
        bounds.contains(event.x, event.y) && event.button() == InputConstants.MOUSE_BUTTON_LEFT

}

/**
 * 绘制一行字符串列表设置；作用域原点即行左上角，所有坐标都是行局部坐标。
 */
fun UiScope.StringListSettingRow(row: StringListSettingRow, hoverProgress: Float) {
    val setting = row.getSetting()
    val labelScale = 0.68f
    val labelY = (height - metrics.textHeight(labelScale)) / 2.0f
    val summary = setting.size().toString() + EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName()

    RowSurface(hoverProgress)
    Text(setting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)

    val chipBounds = PanelElements.measureLocalAssistChipBounds(
        metrics, width, height, summary, CHIP_TEXT_SCALE, CHIP_HORIZONTAL_PADDING, CHIP_TRAILING_SLOT, CHIP_MAX_WIDTH
    )
    Chip(
        chipBounds, summary, CHIP_TEXT_SCALE, MD3Theme.SECONDARY_CONTAINER, MD3Theme.ON_SECONDARY_CONTAINER,
        IconChars.ADD, 0.58f, StaticFontLoader.ICONS
    )
}
