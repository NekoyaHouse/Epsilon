package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.lib.Positioned
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope

/**
 * Dropdown 控件基类。
 *
 * 命中测试始终使用屏幕绝对坐标（[x] / [y] / [width]）；绘制入口是 [UiScope] 扩展
 * [draw]，坐标相对 [drawInScope] 推入的组件矩形，因此子类只写局部坐标。
 */
abstract class Component {

    var x: Float = 0.0f
    var y: Float = 0.0f
    var width: Float = 0.0f

    abstract fun getHeight(): Float

    /** 在已定位到组件矩形的作用域内绘制；坐标相对组件左上角。 */
    abstract fun UiScope.draw(mouseX: Int, mouseY: Int)

    /**
     * 把组件摆到相对当前作用域的 [localBounds] 上并绘制。
     *
     * 同时缓存绝对位置供鼠标命中测试：局部坐标 + 作用域原点 = 屏幕绝对坐标。
     */
    fun drawInScope(scope: UiScope, mouseX: Int, mouseY: Int, localBounds: UiRect) {
        setPosition(scope.origin.x + localBounds.x, scope.origin.y + localBounds.y, localBounds.width)
        scope.Positioned(localBounds.x, localBounds.y, localBounds.width, localBounds.height) {
            draw(mouseX, mouseY)
        }
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
