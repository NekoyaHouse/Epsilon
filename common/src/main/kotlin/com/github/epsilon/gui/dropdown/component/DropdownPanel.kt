package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree

interface DropdownPanel {

    val id: String

    fun startIntro()

    fun beginRenderFrame(frameId: Int) {
    }

    val introValue: Float

    fun drawBackground(scope: UiTree.Scope, textMetrics: UiTextMetrics)

    fun drawContent(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int)

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
