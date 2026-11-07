package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.lib.InputRegion
import com.github.epsilon.gui.lib.Positioned
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope

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
            InputRegion(key = this@Component, bounds = UiRect(0f, 0f, width, inputHeight()),
                onPress = { event, _ -> mouseClicked(event.x(), event.y(), event.button()) },
                onPreviewPress = { event, _ -> previewMouseClicked(event.button()) },
                onDrag = { event, _, _ -> mouseDragged(event.x(), event.y()) },
                onRelease = { event -> mouseReleased(event.x(), event.y(), event.button()) },
                onCancel = ::cancelInput,
                onKey = { keyPressed(it.key(), it.keycode(), it.modifiers()) },
                onText = { charTyped(it.codepointAsString()) }, onBlur = ::blurInput)
            draw(mouseX, mouseY)
        }
    }

    open fun inputHeight(): Float = getHeight()
    open fun previewMouseClicked(button: Int): Boolean = false
    open fun mouseDragged(mouseX: Double, mouseY: Double): Boolean = false
    open fun cancelInput() {}
    open fun blurInput() {}

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
        return UiRect(x, y, w, h).contains(mouseX, mouseY)
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
