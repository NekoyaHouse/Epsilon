package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.lib.UiScope

interface DropdownPanel {

    val id: String

    fun startIntro()

    fun beginRenderFrame(frameId: Int) {
    }

    val introValue: Float

    /**
     * 绘制面板背景与标题栏。
     *
     * 作用域通过 context 参数传入：调用方在 `UiRenderBatch.Content(bounds) { }` 等
     * [UiScope] 作用域内直接写 `panel.drawBackground()`，坐标相对作用域原点。
     */
    context(scope: UiScope)
    fun drawBackground()

    /**
     * 绘制面板内容区（含滚动条）。
     *
     * 命中测试使用屏幕绝对坐标，因此 [mouseX] / [mouseY] 保持绝对坐标不变。
     */
    context(scope: UiScope)
    fun drawContent(mouseX: Int, mouseY: Int)

    val contentClipY: Float

    val contentClipHeight: Float

    fun requiresContentScissor(): Boolean

    val panelHeight: Float

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean

    fun onGlobalMouseClicked(mouseX: Double, mouseY: Double, button: Int) {
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean

    fun mouseDragged(mouseX: Double, mouseY: Double): Boolean

    fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean

    fun charTyped(typedText: String): Boolean

    fun hasActiveInput(): Boolean

    fun setPosition(x: Float, y: Float)

    fun updateMaxPanelHeight(maxPanelHeight: Float)

    val x: Float

    val y: Float

    val width: Float

    var isOpened: Boolean

    var isVisible: Boolean

}
