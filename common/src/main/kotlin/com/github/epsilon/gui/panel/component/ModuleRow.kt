package com.github.epsilon.gui.panel.component

import com.github.epsilon.gui.lib.Layer
import com.github.epsilon.gui.lib.MarqueeText
import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.Toggle
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.panel.adapter.ModuleViewModel
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.utils.client.KeybindUtils

/**
 * 模块列表中的单行展示组件。
 *
 * 一行通常包含模块名称、副标题、快捷键提示以及启用开关。
 * 该类只保存命中区域与视图模型，绘制入口是 [ModuleRow] 扩展函数。
 */
class ModuleRow(private val moduleView: ModuleViewModel, private val rowBounds: UiRect) {

    private val toggleArea: UiRect = PanelElements.switchBounds(rowBounds)

    /**
     * 返回该行对应的模块视图模型。
     */
    fun getModule(): ModuleViewModel = moduleView

    /**
     * 返回整行的命中区域。
     */
    fun getBounds(): UiRect = rowBounds

    /**
     * 返回行内开关控件的命中区域。
     */
    fun getToggleBounds(): UiRect = toggleArea

    /**
     * 把开关区域转换成相对当前作用域原点的局部坐标。
     */
    internal fun localToggleBounds(origin: UiRect): UiRect = toggleArea.relativeTo(origin)

    /**
     * 判断快捷键文本是否溢出，需要跑马灯。
     */
    fun hasOverflowingKeybind(metrics: UiTextMetrics): Boolean {
        val keyScale = 0.6f
        val keyWidth = metrics.textWidth(formatKeybind(moduleView.module.keyBind), keyScale)
        return keyWidth > KEYBIND_CLIP_WIDTH + 0.5f
    }

    internal fun keybindText(): String = formatKeybind(moduleView.module.keyBind)

    internal fun viewModel(): ModuleViewModel = moduleView

    private fun formatKeybind(keyCode: Int): String = KeybindUtils.format(keyCode).uppercase()

    companion object {

        const val HEIGHT: Float = 34.0f

        const val KEYBIND_CLIP_WIDTH: Float = 40.0f

        const val KEYBIND_TOGGLE_GAP: Float = 8.0f

        internal fun marqueePhase(): Float {
            val period = 5000L
            val pause = 800L
            val t = System.currentTimeMillis() % period
            val travel = (period - pause * 2L) / 2L
            if (t < pause) {
                return 0.0f
            }
            if (t < pause + travel) {
                return (t - pause) / travel.toFloat()
            }
            if (t < pause + travel + pause) {
                return 1.0f
            }
            return 1.0f - (t - pause - travel - pause) / travel.toFloat()
        }

    }
}

/**
 * 绘制一行模块。
 *
 * 坐标是相对当前作用域局部的（调用方用 [com.github.epsilon.gui.lib.Positioned] 把行矩形推入），
 * 因此这里只依赖 [UiScope.width] / [UiScope.height] 与 [UiScope.metrics]。
 */
fun UiScope.ModuleRow(
    row: ModuleRow,
    hover: Float,
    selected: Float,
    toggle: Float,
    toggleHover: Float,
) {
    val moduleView = row.viewModel()
    val titleScale = 0.70f
    val subScale = 0.60f
    val keyScale = 0.60f
    val titleHeight = metrics.textHeight(titleScale)
    val subHeight = metrics.textHeight(subScale)
    val keyHeight = metrics.textHeight(keyScale)
    val lineGap = 2.0f
    val totalTextHeight = titleHeight + lineGap + subHeight
    val titleY = (height - totalTextHeight) / 2.0f
    val subY = titleY + titleHeight + lineGap
    val keyY = (height - keyHeight) / 2.0f
    val titleColor = MD3Theme.lerp(MD3Theme.TEXT_PRIMARY, MD3Theme.ON_PRIMARY_CONTAINER, selected)
    val subColor = MD3Theme.lerp(
        MD3Theme.TEXT_SECONDARY,
        MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 180),
        selected
    )
    val keyColor = if (MD3Theme.isLightTheme()) MD3Theme.TEXT_SECONDARY else MD3Theme.TEXT_MUTED
    val keybindText = row.keybindText()
    val keyWidth = metrics.textWidth(keybindText, keyScale)
    val localToggleBounds = row.localToggleBounds(origin)
    val clipRight = localToggleBounds.x - ModuleRow.KEYBIND_TOGGLE_GAP
    val clipWidth = minOf(keyWidth, ModuleRow.KEYBIND_CLIP_WIDTH)
    val clipX = clipRight - clipWidth
    val keybindClip = UiRect(clipX, keyY, clipWidth, keyHeight)

    RoundRect(0.0f, 0.0f, width, height, MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(hover))
    if (selected > 0.01f) {
        RoundRect(0.0f, 0.0f, width, height, MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.stateLayer(MD3Theme.PRIMARY, selected, 42))
    }

    Text(moduleView.displayName, PanelElements.ROW_LABEL_INSET, titleY, titleScale, titleColor)
    Text(moduleView.module.category!!.name, PanelElements.ROW_LABEL_INSET, subY, subScale, subColor)

    // 开关必须压在行表面之上：layer 由作用域携带，图元本身不再接收 layer 参数
    Layer(LayerOffset.Edge) {
        Toggle(localToggleBounds, toggle, toggleHover)
    }

    if (keyWidth <= ModuleRow.KEYBIND_CLIP_WIDTH + 0.5f) {
        Text(keybindText, clipRight - keyWidth, keyY, keyScale, keyColor)
    } else {
        val overflow = keyWidth - clipWidth
        val scrollOffset = -overflow * ModuleRow.marqueePhase()
        MarqueeText(keybindText, clipX + scrollOffset, keyY, keyScale, keyColor, keybindClip)
    }
}
