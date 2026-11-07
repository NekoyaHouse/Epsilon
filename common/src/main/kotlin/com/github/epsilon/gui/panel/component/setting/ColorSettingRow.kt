package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.panel.component.RowSurface
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.ColorSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent

private const val SWATCH_SIZE = 12.0f
private const val SWATCH_RADIUS = 5.0f

class ColorSettingRow(private val colorSetting: ColorSetting) : SettingRow<ColorSetting>(colorSetting) {

    fun getSwatchBounds(bounds: UiRect): UiRect =
        localSwatchBounds(bounds.width, bounds.height).translate(bounds.x, bounds.y)

    /**
     * 色块的相对矩形；作用域原点即行左上角。
     */
    internal fun localSwatchBounds(rowWidth: Float, rowHeight: Float): UiRect = UiRect(
        rowWidth - MD3Theme.ROW_TRAILING_INSET - SWATCH_SIZE,
        (rowHeight - SWATCH_SIZE) / 2.0f,
        SWATCH_SIZE,
        SWATCH_SIZE
    )

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean =
        event.button() == InputConstants.MOUSE_BUTTON_LEFT && bounds.contains(event.x, event.y)

}

/**
 * 绘制一行颜色设置；作用域原点即行左上角。
 */
fun UiScope.ColorSettingRow(row: ColorSettingRow, hoverProgress: Float) {
    val setting = row.getSetting()
    val labelScale = 0.68f
    val labelY = (height - metrics.textHeight(labelScale)) / 2.0f

    RowSurface(hoverProgress)
    Text(setting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)

    val swatchBounds = row.localSwatchBounds(width, height)
    RoundRect(
        swatchBounds.x, swatchBounds.y, swatchBounds.width, swatchBounds.height,
        SWATCH_RADIUS, MD3Theme.SURFACE_CONTAINER_HIGHEST
    )
    RoundRect(
        swatchBounds.x, swatchBounds.y, swatchBounds.width, swatchBounds.height,
        SWATCH_RADIUS, setting.value
    )
    RoundRect(
        swatchBounds.x, swatchBounds.y, swatchBounds.width, swatchBounds.height,
        SWATCH_RADIUS, MD3Theme.withAlpha(MD3Theme.OUTLINE_SOFT, 58)
    )
}
