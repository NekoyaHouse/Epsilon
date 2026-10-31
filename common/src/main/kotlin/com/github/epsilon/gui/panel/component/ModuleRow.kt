package com.github.epsilon.gui.panel.component

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.adapter.ModuleViewModel
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.utils.client.KeybindUtils

/**
 * 模块列表中的单行展示组件。
 *
 * 一行通常包含模块名称、副标题、快捷键提示以及启用开关。
 * 该类会把自身内容写入外部 DSL 作用域。
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
     * 将模块行内容写入外部 UI DSL 作用域。
     *
     * @param scope               目标 DSL 作用域
     * @param textRenderer        用于测量文本尺寸的 renderer
     * @param hoverProgress       行悬停进度
     * @param selectedProgress    行选中进度
     * @param toggleProgress      开关进度
     * @param toggleHoverProgress 开关悬停进度
     */
    fun buildUi(
        scope: UiTree.Scope,
        textRenderer: TextRenderer,
        hoverProgress: Float,
        selectedProgress: Float,
        toggleProgress: Float,
        toggleHoverProgress: Float,
    ) {
        val titleScale = 0.70f
        val subScale = 0.60f
        val keyScale = 0.6f
        val titleHeight = textRenderer.getHeight(titleScale)
        val subHeight = textRenderer.getHeight(subScale)
        val keyHeight = textRenderer.getHeight(keyScale)
        val lineGap = 2.0f
        val totalTextHeight = titleHeight + lineGap + subHeight
        val titleY = (rowBounds.height - totalTextHeight) / 2.0f
        val subY = titleY + titleHeight + lineGap
        val keyY = (rowBounds.height - keyHeight) / 2.0f
        val titleColor = MD3Theme.lerp(MD3Theme.TEXT_PRIMARY, MD3Theme.ON_PRIMARY_CONTAINER, selectedProgress)
        val subColor = MD3Theme.lerp(
            MD3Theme.TEXT_SECONDARY,
            MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 180),
            selectedProgress
        )
        val keyColor = if (MD3Theme.isLightTheme()) MD3Theme.TEXT_SECONDARY else MD3Theme.TEXT_MUTED
        val keybindText = formatKeybind(moduleView.module.keyBind)
        val keyWidth = textRenderer.getWidth(keybindText, keyScale)
        val localToggleBounds = toggleArea.relativeTo(rowBounds)
        val clipRight = localToggleBounds.x - KEYBIND_TOGGLE_GAP
        val clipWidth = minOf(keyWidth, KEYBIND_CLIP_WIDTH)
        val clipX = clipRight - clipWidth
        val keybindClip = UiRect(clipX, keyY, clipWidth, keyHeight)

        scope.roundRect(
            0.0f, 0.0f, rowBounds.width, rowBounds.height,
            MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.rowSurface(hoverProgress)
        )
        if (selectedProgress > 0.01f) {
            scope.roundRect(
                0.0f, 0.0f, rowBounds.width, rowBounds.height, MD3Theme.CARD_RADIUS.toFloat(),
                MD3Theme.stateLayer(MD3Theme.PRIMARY, selectedProgress, 42)
            )
        }

        scope.text(moduleView.displayName, PanelElements.ROW_LABEL_INSET, titleY, titleScale, titleColor)
        scope.text(moduleView.module.category!!.name, PanelElements.ROW_LABEL_INSET, subY, subScale, subColor)
        scope.toggle(localToggleBounds, toggleProgress, toggleHoverProgress)

        if (keyWidth <= KEYBIND_CLIP_WIDTH + 0.5f) {
            scope.text(keybindText, clipRight - keyWidth, keyY, keyScale, keyColor)
        } else {
            val overflow = keyWidth - clipWidth
            val scrollOffset = -overflow * marqueePhase()
            scope.marqueeText(keybindText, clipX + scrollOffset, keyY, keyScale, keyColor, keybindClip)
        }
    }

    fun hasOverflowingKeybind(textRenderer: TextRenderer): Boolean {
        val keyScale = 0.6f
        val keyWidth = textRenderer.getWidth(formatKeybind(moduleView.module.keyBind), keyScale)
        return keyWidth > KEYBIND_CLIP_WIDTH + 0.5f
    }

    private fun formatKeybind(keyCode: Int): String = KeybindUtils.format(keyCode).uppercase()

    companion object {

        const val HEIGHT: Float = 34.0f

        const val KEYBIND_CLIP_WIDTH: Float = 40.0f

        const val KEYBIND_TOGGLE_GAP: Float = 8.0f

        private fun marqueePhase(): Float {
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
