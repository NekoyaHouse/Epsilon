package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.panel.component.RowSurface
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.settings.impl.ButtonSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

class ButtonSettingRow(private val buttonSetting: ButtonSetting) : SettingRow<ButtonSetting>(buttonSetting) {

    // 绘制入口是与本类同文件的 [UiScope.ButtonSettingRow]，动画状态需要对该扩展函数可见。
    internal val hoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 160L)

    init {
        hoverAnimation.startValue = 0.0f
    }

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (!bounds.contains(event.x, event.y) || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        buttonSetting.value.run()
        return true
    }

}

/**
 * 绘制一行按钮设置；作用域原点即行左上角。
 */
fun UiScope.ButtonSettingRow(row: ButtonSettingRow, hoverProgress: Float) {
    val setting = row.getSetting()
    val labelScale = 0.68f
    val labelY = (height - metrics.textHeight(labelScale)) / 2.0f
    row.hoverAnimation.run(hoverProgress)

    RowSurface(row.hoverAnimation.value)
    Text(setting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)
}
