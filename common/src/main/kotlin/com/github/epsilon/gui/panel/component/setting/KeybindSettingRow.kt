package com.github.epsilon.gui.panel.component.setting

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.panel.component.RowSurface
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

private const val CHIP_WIDTH = 56.0f
private const val CHIP_HEIGHT = 18.0f
private const val CHIP_TEXT_SCALE = 0.52f
private const val CHIP_RADIUS = 8.0f

class KeybindSettingRow(private val keybindSetting: KeybindSetting) : SettingRow<KeybindSetting>(keybindSetting) {

    // 绘制入口是与本类同文件的 [UiScope.KeybindSettingRow]，动画状态需要对该扩展函数可见。
    internal val chipHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)

    internal val focusAnimation = Animation(Easing.EASE_OUT_CUBIC, 150L)

    private var listeningState by mutableStateOf(false)

    init {
        chipHoverAnimation.startValue = 0.0f
        focusAnimation.startValue = 0.0f
    }

    fun isListening(): Boolean = listeningState

    fun setListening(listening: Boolean) {
        this.listeningState = listening
    }

    override fun setFocused(focused: Boolean) {
        if (!focused) listeningState = false
    }

    fun bindMouse(event: MouseButtonEvent): Boolean {
        if (!listeningState) return false
        keybindSetting.value = KeybindUtils.encodeMouseButton(event.button())
        listeningState = false
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!listeningState) return false
        if (!event.isEscape) keybindSetting.value = when (event.key()) {
            InputConstants.KEY_BACKSPACE, InputConstants.KEY_DELETE -> KeybindUtils.NONE
            else -> event.key()
        }
        listeningState = false
        return true
    }

    /**
     * chip 的绝对矩形，供命中测试与监听状态切换使用。
     */
    fun getChipBounds(bounds: UiRect): UiRect =
        localChipBounds(bounds.width, bounds.height).translate(bounds.x, bounds.y)

    /**
     * chip 的行局部矩形；作用域原点即行左上角。
     */
    internal fun localChipBounds(rowWidth: Float, rowHeight: Float): UiRect = UiRect(
        rowWidth - MD3Theme.ROW_TRAILING_INSET - CHIP_WIDTH,
        (rowHeight - CHIP_HEIGHT) / 2.0f,
        CHIP_WIDTH,
        CHIP_HEIGHT
    )

    override fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (!bounds.contains(event.x, event.y) || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        val chipBounds = getChipBounds(bounds)
        if (!chipBounds.contains(event.x, event.y)) return false
        listeningState = true
        return true
    }

    internal fun formatKeybind(keyCode: Int): String {
        if (keyCode == KeybindUtils.NONE) {
            return EpsilonTranslations.Keybind.NONE.getTranslatedName()
        }
        return KeybindUtils.format(keyCode)
    }

}

/**
 * 绘制一行快捷键设置；作用域原点即行左上角，所有坐标都是行局部坐标。
 */
fun UiScope.KeybindSettingRow(row: KeybindSettingRow, hoverProgress: Float, mouseX: Int, mouseY: Int) {
    val setting = row.getSetting()
    val labelScale = 0.68f
    val labelY = (height - metrics.textHeight(labelScale)) / 2.0f

    RowSurface(hoverProgress)
    Text(setting.displayName, MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY)

    val chipBounds = row.localChipBounds(width, height)
    // 子控件命中测试需要绝对坐标：作用域原点 + 行局部矩形
    val absoluteChipBounds = chipBounds.translate(origin.x, origin.y)
    row.chipHoverAnimation.run(if (absoluteChipBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
    row.focusAnimation.run(if (row.isListening()) 1.0f else 0.0f)
    val chipHover = row.chipHoverAnimation.value
    val focusProgress = row.focusAnimation.value

    if (focusProgress > 0.01f) {
        val haloInset = 1.5f * focusProgress
        RoundRect(
            chipBounds.x - haloInset, chipBounds.y - haloInset,
            chipBounds.width + haloInset * 2.0f, chipBounds.height + haloInset * 2.0f,
            CHIP_RADIUS + haloInset, MD3Theme.withAlpha(MD3Theme.PRIMARY, (28 * focusProgress).toInt())
        )
    }

    val background = MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, focusProgress)
    val foreground = MD3Theme.lerp(MD3Theme.ON_SECONDARY_CONTAINER, MD3Theme.ON_PRIMARY_CONTAINER, focusProgress)
    RoundRect(chipBounds.x, chipBounds.y, chipBounds.width, chipBounds.height, CHIP_RADIUS, background)

    if (chipHover > 0.01f) {
        val hoverAlpha = if (row.isListening()) 18 else 12
        RoundRect(
            chipBounds.x, chipBounds.y, chipBounds.width, chipBounds.height, CHIP_RADIUS,
            MD3Theme.withAlpha(foreground, (hoverAlpha * chipHover).toInt())
        )
    }

    val label = if (row.isListening()) "..." else row.formatKeybind(setting.value)
    val textWidth = metrics.textWidth(label, CHIP_TEXT_SCALE)
    val textHeight = metrics.textHeight(CHIP_TEXT_SCALE)
    val textX = chipBounds.x + (chipBounds.width - textWidth) / 2.0f
    val textY = chipBounds.y + (chipBounds.height - textHeight) / 2.0f
    Text(label, textX, textY, CHIP_TEXT_SCALE, foreground)
}
