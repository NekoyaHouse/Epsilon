package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.Toggle
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.animate
import com.github.epsilon.gui.panel.component.PanelElements
import com.github.epsilon.gui.panel.component.RowSurface
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.screen.PlatformNoticeScreen
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent

class BoolSettingRow(private val boolSetting: BoolSetting) : SettingRow<BoolSetting>(boolSetting) {

    // 绘制入口是与本类同文件的 [UiScope.BoolSettingRow]，动画状态需要对该扩展函数可见。
    internal val hoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 160L)

    internal val toggleAnimation = Animation(Easing.EASE_OUT_ELASTIC, 620L)

    init {
        hoverAnimation.startValue = 0.0f
        toggleAnimation.startValue = if (boolSetting.value) 1.0f else 0.0f
    }

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (!bounds.contains(event.x, event.y) || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        if (!boolSetting.isSupported()) {
            PlatformNoticeScreen.show(boolSetting)
            return true
        }
        boolSetting.value = !boolSetting.value
        SoundManager.playInUi(if (boolSetting.value) SoundKey.SETTINGS_OPEN else SoundKey.SETTINGS_CLOSE)
        return true
    }

}

/**
 * 绘制一行布尔设置；作用域原点即行左上角。
 */
fun UiScope.BoolSettingRow(row: BoolSettingRow, hoverProgress: Float) {
    val setting = row.getSetting()
    val supported = setting.isSupported()
    val labelScale = 0.68f
    val labelY = (height - metrics.textHeight(labelScale)) / 2.0f
    val animatedHover = animate(row.hoverAnimation, if (supported) hoverProgress else 0.0f)
    val toggleProgress = animate(row.toggleAnimation, setting.value)
    val switchBounds = PanelElements.localSwitchBounds(width, height)

    RowSurface(if (supported) animatedHover else 0.0f)
    Text(
        setting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale,
        if (supported) MD3Theme.TEXT_PRIMARY else MD3Theme.TEXT_MUTED
    )
    if (!supported) {
        PlatformOnlyChip(switchBounds, setting.unsupportedLabel)
    }
    Toggle(switchBounds, if (supported) toggleProgress else 0.0f, animatedHover)
}
