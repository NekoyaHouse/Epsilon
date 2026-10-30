package com.github.epsilon.gui.panel.component

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.settings.Setting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent

/**
 * 设置行基类：负责单条设置的布局高度、UI 构建与输入事件分发。
 *
 * 具体行只覆盖自己关心的事件；默认实现保持“不消费输入、无动画”的语义。
 */
abstract class SettingRow<T : Setting<*>>(private val settingValue: T) : AutoCloseable {

    /** 供外部（列表控制器、状态同步）读取当前行绑定的设置。 */
    fun getSetting(): T = settingValue

    open fun getHeight(): Float = 28.0f

    open fun buildUi(
        scope: UiTree.Scope,
        guiGraphics: GuiGraphicsExtractor,
        textRenderer: TextRenderer,
        bounds: UiRect,
        hoverProgress: Float,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
    }

    open fun mouseClicked(bounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean = false

    open fun mouseReleased(bounds: UiRect, event: MouseButtonEvent): Boolean = false

    open fun mouseScrolled(
        bounds: UiRect, mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double,
    ): Boolean = false

    open fun keyPressed(event: KeyEvent): Boolean = false

    open fun charTyped(event: CharacterEvent): Boolean = false

    open fun preeditUpdated(event: PreeditEvent): Boolean = false

    open fun setFocused(focused: Boolean) {
    }

    open fun isFocused(): Boolean = false

    open fun hasActiveAnimation(): Boolean = false

    override fun close() {
    }

    companion object {

        /** 文本测量失败或尚未注入 renderer 时的兜底实例。 */
        val FALLBACK_TEXT_METRICS: TextRenderer = TextRenderer.create()

    }

}
