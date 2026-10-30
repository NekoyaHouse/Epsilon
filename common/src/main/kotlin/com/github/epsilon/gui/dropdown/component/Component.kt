package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree

abstract class Component {

    var x: Float = 0.0f
    var y: Float = 0.0f
    var width: Float = 0.0f

    abstract fun getHeight(): Float

    abstract fun draw(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int)

    fun draw(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int, bounds: UiRect) {
        // 组件只缓存本帧命中区域，位置由 UiTree/stack 在调用侧统一计算。
        scope.pushAbsolute(bounds) { child -> drawInScope(child, textMetrics, mouseX, mouseY, bounds) }
    }

    fun drawInScope(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int, bounds: UiRect) {
        setPosition(bounds.x, bounds.y, bounds.width)
        draw(scope, textMetrics, mouseX, mouseY)
    }

    open fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return false
    }

    open fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return false
    }

    open fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        return false
    }

    open fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return false
    }

    open fun charTyped(typedText: String): Boolean {
        return false
    }

    open fun setPosition(x: Float, y: Float, width: Float) {
        this.x = x
        this.y = y
        this.width = width
    }

    protected fun isHovered(mouseX: Double, mouseY: Double, x: Float, y: Float, w: Float, h: Float): Boolean {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
    }

    protected fun isHovered(mouseX: Double, mouseY: Double): Boolean {
        return isHovered(mouseX, mouseY, x, y, width, getHeight())
    }

    protected fun absoluteX(localX: Float): Float {
        return x + localX
    }

    protected fun absoluteY(localY: Float): Float {
        return y + localY
    }

}
