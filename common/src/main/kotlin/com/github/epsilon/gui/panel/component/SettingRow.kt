package com.github.epsilon.gui.panel.component

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.Positioned
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.asUiTextMetrics
import com.github.epsilon.gui.panel.component.setting.BoolSettingRow
import com.github.epsilon.gui.panel.component.setting.ButtonSettingRow
import com.github.epsilon.gui.panel.component.setting.ColorSettingRow
import com.github.epsilon.gui.panel.component.setting.DoubleSettingRow
import com.github.epsilon.gui.panel.component.setting.EnumSettingRow
import com.github.epsilon.gui.panel.component.setting.IntSettingRow
import com.github.epsilon.gui.panel.component.setting.KeybindSettingRow
import com.github.epsilon.gui.panel.component.setting.RegistryListSettingRow
import com.github.epsilon.gui.panel.component.setting.StringListSettingRow
import com.github.epsilon.gui.panel.component.setting.StringSettingRow
import com.github.epsilon.settings.Setting
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent

/**
 * 设置行基类：负责单条设置的布局高度、输入事件分发与状态。
 *
 * 绘制入口不在基类里：每个具体行都在同文件提供 `fun UiScope.XxxSettingRow(...)` 扩展函数，
 * 作用域原点就是行左上角，因此绘制只使用局部坐标。统一由 [SettingRow] 扩展函数分派。
 *
 * 具体行只覆盖自己关心的事件；默认实现保持“不消费输入、无动画”的语义。
 */
abstract class SettingRow<T : Setting<*>>(private val settingValue: T) : AutoCloseable {

    /** 供外部（列表控制器、状态同步）读取当前行绑定的设置。 */
    fun getSetting(): T = settingValue

    open fun getHeight(): Float = 28.0f


    open fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean = false

    open fun mouseReleased(bounds: UiRect, event: MouseButtonEvent): Boolean = false

    open fun mouseScrolled(
        bounds: UiRect, mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double,
    ): Boolean = false

    open fun keyPressed(event: KeyEvent): Boolean = false

    open fun charTyped(event: CharacterEvent): Boolean = false

    open fun preeditUpdated(event: PreeditEvent): Boolean = false

    open fun cancelInput() {}

    open fun setFocused(focused: Boolean) {
    }

    open fun isFocused(): Boolean = false

    override fun close() {
    }

    companion object {

        /** 文本测量失败或尚未注入 renderer 时的兜底度量。 */
        val FALLBACK_TEXT_METRICS: UiTextMetrics = TextRenderer.create().asUiTextMetrics()

    }

}

/**
 * 按具体行类型分派绘制。
 *
 * @param absoluteBounds 行的绝对矩形，仅用于命中测试与弹窗定位；绘制位置由它相对
 *                       [UiScope.origin] 换算，行内坐标一律以行左上角为原点
 * @param hoverProgress  外部推进的行悬浮进度
 * @param mouseX         绝对鼠标 X（行内子控件命中测试用，可为 [Int.MIN_VALUE]）
 * @param mouseY         绝对鼠标 Y
 */
fun UiScope.SettingRow(
    row: SettingRow<*>,
    absoluteBounds: UiRect,
    hoverProgress: Float,
    mouseX: Int,
    mouseY: Int,
) {
    val local = absoluteBounds.relativeTo(origin)
    Positioned(local.x, local.y, local.width, local.height) {
        when (row) {
            is BoolSettingRow -> BoolSettingRow(row, hoverProgress)
            is ButtonSettingRow -> ButtonSettingRow(row, hoverProgress)
            is ColorSettingRow -> ColorSettingRow(row, hoverProgress)
            is DoubleSettingRow -> DoubleSettingRow(row, hoverProgress)
            is EnumSettingRow -> EnumSettingRow(row, hoverProgress)
            is IntSettingRow -> IntSettingRow(row, hoverProgress)
            is KeybindSettingRow -> KeybindSettingRow(row, hoverProgress, mouseX, mouseY)
            is RegistryListSettingRow -> RegistryListSettingRow(row, hoverProgress)
            is StringListSettingRow -> StringListSettingRow(row, hoverProgress)
            is StringSettingRow -> StringSettingRow(row, hoverProgress, mouseX, mouseY)
            else -> Unit
        }
    }
}
