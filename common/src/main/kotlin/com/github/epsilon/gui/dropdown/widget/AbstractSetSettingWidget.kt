package com.github.epsilon.gui.dropdown.widget

import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.settings.Setting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import java.awt.Color

/**
 * 集合类型 Setting 的下拉菜单基类。
 *
 * 统一了所有 *ListSetting / *SetSetting 类型 Widget 的渲染与交互逻辑，
 * 子类只需提供翻译关键字和弹窗打开动作。
 */
abstract class AbstractSetSettingWidget<S : Setting<*>>(setting: S) : SettingWidget<S>(setting) {

    private val hoverAnim: Animation = Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER)

    /**
     * 集合当前元素数量
     */
    protected abstract fun elementCount(): Int

    /**
     * 按钮标签翻译组件（如 "3 items" 中的 "items"）
     */
    protected abstract fun labelText(): String

    /**
     * 点击按钮时执行的弹窗打开动作
     */
    protected abstract fun openPopup()

    override fun getHeight(): Float {
        return DropdownTheme.SETTING_HEIGHT - 1.0f + FIELD_HEIGHT
    }

    override fun draw(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int) {
        val hovered = isFieldHovered(mouseX.toDouble(), mouseY.toDouble())
        hoverAnim.run(if (hovered) 1.0f else 0.0f)

        scope.text(
            setting.displayName, DropdownTheme.SETTING_PADDING_X, 1.0f,
            DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel()
        )

        // 渲染使用本地坐标
        val fieldX = DropdownTheme.SETTING_PADDING_X
        val fieldY = DropdownTheme.SETTING_HEIGHT - 1.0f
        val fieldW = getFieldWidth()
        val hover = hoverAnim.value
        val background: Color = MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, hover * 0.6f)
        val outline: Color = MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.OUTLINE, 90), MD3Theme.PRIMARY, hover)
        val label = elementCount().toString() + labelText()
        val labelScale = 0.50f
        val iconScale = 0.54f
        val labelY = centeredTextY(textMetrics, fieldY, FIELD_HEIGHT, labelScale)
        val iconY = centeredTextY(textMetrics, fieldY, FIELD_HEIGHT, iconScale)

        scope.roundRect(fieldX, fieldY, fieldW, FIELD_HEIGHT, DropdownTheme.INPUT_RADIUS, background)
        scope.outline(fieldX, fieldY, fieldW, FIELD_HEIGHT, DropdownTheme.INPUT_RADIUS, 0.7f, outline)
        scope.text(label, fieldX + 6.0f, labelY, labelScale, MD3Theme.ON_SECONDARY_CONTAINER)
        scope.text("+", fieldX + fieldW - 12.0f, iconY, iconScale, MD3Theme.ON_SECONDARY_CONTAINER)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !isFieldHovered(mouseX, mouseY)) return false
        openPopup()
        SoundManager.playInUi(SoundKey.SETTINGS_OPEN)
        DropdownScreen.react(ReisaDropdownCompanion.Action.PANEL_OPEN)
        return true
    }

    /**
     * hit-test 使用绝对坐标
     */
    protected fun isFieldHovered(mouseX: Double, mouseY: Double): Boolean {
        return isHovered(mouseX, mouseY, getFieldX(), getFieldY(), getFieldWidth(), FIELD_HEIGHT)
    }

    protected fun getFieldX(): Float {
        return absoluteX(DropdownTheme.SETTING_PADDING_X)
    }

    protected fun getFieldY(): Float {
        return absoluteY(DropdownTheme.SETTING_HEIGHT - 1.0f)
    }

    protected fun getFieldWidth(): Float {
        return width - DropdownTheme.SETTING_PADDING_X * 2.0f
    }

    private fun centeredTextY(textMetrics: UiTextMetrics, boxY: Float, boxHeight: Float, scale: Float): Float {
        return boxY + (boxHeight - textMetrics.textHeight(scale)) * 0.5f
    }

    companion object {
        private const val FIELD_HEIGHT = 14.0f
    }

}
