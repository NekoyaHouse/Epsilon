package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.lib.Positioned
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope

/**
 * Dropdown 控件基类。
 *
 * 命中测试始终使用屏幕绝对坐标（[x] / [y] / [width]）；绘制入口是 [UiScope] 扩展
 * [draw]，坐标相对 [drawInScope] 推入的组件矩形，因此子类只写局部坐标。
 *
 * 控件同时承担 recompose 缓存的责任：面板用 [cacheRevision] 作为 `LuminUIItem` 的版本，
 * 静止时版本稳定，动画或输入进行中每帧变化，从而只重建真正变化的控件。
 */
abstract class Component {

    var x: Float = 0.0f
    var y: Float = 0.0f
    var width: Float = 0.0f

    abstract fun getHeight(): Float

    /**
     * 控件当前是否有正在播放的动画，或存在必须逐帧重绘的状态（如输入框光标闪烁）。
     *
     * 返回 `true` 时 [cacheRevision] 会叠加时间戳，迫使持有的 `LuminUIItem` 每帧重建。
     */
    open fun hasActiveAnimations(): Boolean = false

    /**
     * 控件节点的缓存版本。
     *
     * 静止时必须是稳定值（否则逐项缓存永远不命中）；动画/输入进行中返回变化的值。
     */
    open fun cacheRevision(): Long {
        return if (hasActiveAnimations()) System.nanoTime() else 0L
    }

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

/**
 * 计算逐项缓存的失效版本。
 *
 * 鼠标坐标必须参与版本：控件内部的悬停区域（滑条、取色板、枚举选项、键位按钮等）
 * 只在绘制时按当前鼠标重新判定，若版本不随鼠标变化，缓存会重放旧的高亮状态。
 * 动画进行中的逐帧变化由 [base] 负责（叠加时间戳）。
 *
 * @param base   控件自身状态版本（见 [Component.cacheRevision]）
 * @param origin 当前作用域原点；节点里烘焙的是绝对坐标，作用域移动后必须重建
 * @param bounds 该项相对 [origin] 的矩形；布局变化必须并入版本
 */
internal fun dropdownItemRevision(base: Long, origin: UiRect, bounds: UiRect, mouseX: Int, mouseY: Int): Long {
    var revision = base
    revision = revision * 31L + origin.hashCode()
    revision = revision * 31L + bounds.hashCode()
    revision = revision * 31L + mouseX
    revision = revision * 31L + mouseY
    return revision
}

/** [dropdownItemRevision] 的控件重载。 */
internal fun dropdownItemRevision(
    component: Component, origin: UiRect, bounds: UiRect, mouseX: Int, mouseY: Int,
): Long {
    return dropdownItemRevision(component.cacheRevision(), origin, bounds, mouseX, mouseY)
}
