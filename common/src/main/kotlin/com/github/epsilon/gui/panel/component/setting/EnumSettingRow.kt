package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.gui.lib.Chip
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.Triangle
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.animate
import com.github.epsilon.gui.panel.component.PanelElements
import com.github.epsilon.gui.panel.component.RowSurface
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent

private const val CHIP_TEXT_SCALE = 0.60f
private const val CHIP_HORIZONTAL_PADDING = 8.0f
private const val CHIP_TRAILING_SLOT = 10.0f
private const val CHIP_MAX_WIDTH = 96.0f
private const val CHEVRON_SIZE = 3.0f
private const val CHEVRON_RIGHT_INSET = 7.5f

class EnumSettingRow(private val enumSetting: EnumSetting<*>) : SettingRow<EnumSetting<*>>(enumSetting) {

    // 绘制入口是与本类同文件的 [UiScope.EnumSettingRow]，动画与展开状态需要对该扩展函数可见。
    internal val dropdownAnimation = Animation(Easing.EASE_OUT_CUBIC, 180L)

    internal var dropdownOpen = false

    init {
        dropdownAnimation.startValue = 0.0f
    }

    fun setDropdownOpen(open: Boolean) {
        this.dropdownOpen = open
    }

    override fun hasActiveAnimation(): Boolean = !dropdownAnimation.isFinished

    /**
     * 枚举 chip 的绝对矩形，供弹窗定位使用。
     */
    fun getChipBounds(metrics: UiTextMetrics, bounds: UiRect): UiRect = PanelElements.measureAssistChipBounds(
        metrics, bounds, enumSetting.translatedValue, CHIP_TEXT_SCALE, CHIP_HORIZONTAL_PADDING, CHIP_TRAILING_SLOT,
        CHIP_MAX_WIDTH
    )

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean =
        bounds.contains(event.x, event.y) && event.button() == InputConstants.MOUSE_BUTTON_LEFT

}

/**
 * 绘制一行枚举设置；作用域原点即行左上角。
 */
fun UiScope.EnumSettingRow(row: EnumSettingRow, hoverProgress: Float) {
    val setting = row.getSetting()
    val supported = setting.isCurrentValueSupported()
    val labelScale = 0.68f
    val labelY = (height - metrics.textHeight(labelScale)) / 2.0f

    RowSurface(hoverProgress)
    Text(
        setting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale,
        if (supported) MD3Theme.TEXT_PRIMARY else MD3Theme.TEXT_MUTED
    )
    val chipBounds = PanelElements.measureLocalAssistChipBounds(
        metrics, width, height, setting.translatedValue,
        CHIP_TEXT_SCALE, CHIP_HORIZONTAL_PADDING, CHIP_TRAILING_SLOT, CHIP_MAX_WIDTH
    )
    if (supported) {
        Chip(
            chipBounds, setting.translatedValue, CHIP_TEXT_SCALE,
            MD3Theme.SECONDARY_CONTAINER, MD3Theme.ON_SECONDARY_CONTAINER, null, 0.58f, null
        )
    } else {
        Chip(
            chipBounds, setting.translatedValue, CHIP_TEXT_SCALE,
            MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGHEST, 255), MD3Theme.TEXT_MUTED, null, 0.58f, null
        )
        PlatformOnlyChip(chipBounds)
    }

    val chevronProgress = animate(row.dropdownAnimation, row.dropdownOpen)
    val chevronCenterX = chipBounds.right() - CHEVRON_RIGHT_INSET
    val chevronCenterY = chipBounds.y + chipBounds.height / 2.0f
    Triangle(
        chevronCenterX, chevronCenterY, CHEVRON_SIZE, chevronProgress,
        if (supported) MD3Theme.ON_SECONDARY_CONTAINER else MD3Theme.TEXT_MUTED
    )
}
