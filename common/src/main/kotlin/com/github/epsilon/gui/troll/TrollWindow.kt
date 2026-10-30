package com.github.epsilon.gui.troll

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

abstract class TrollWindow protected constructor() {

    val scroll: TrollScroll = TrollScroll()
    var bounds: UiRect = UiRect(0.0f, 0.0f, 180.0f, 300.0f)
    var viewport: UiRect = bounds
    var headerHeight: Float = 0.0f
    protected var scrollPosition: Float = 0.0f
    private val openAnimation: Animation = Animation(Easing.EASE_OUT_QUART, 300L)
    private var manualWidth: Float = -1.0f
    private var manualHeight: Float = -1.0f
    private var dragging: Boolean = false
    private var resizing: Boolean = false
    private var resizeEdge: ResizeEdge = ResizeEdge.NONE
    private var resizeStartBounds: UiRect = bounds
    private var resizeStartMouseX: Double = 0.0
    private var resizeStartMouseY: Double = 0.0
    private var dragOffsetX: Double = 0.0
    private var dragOffsetY: Double = 0.0

    init {
        openAnimation.startValue = 0.0f
    }

    abstract fun layout(metrics: TextRenderer, screenWidth: Float, screenHeight: Float)

    abstract fun draw(scope: UiTree.Scope, metrics: TextRenderer, mouseX: Int, mouseY: Int)

    fun startOpenAnimation() {
        openAnimation.value = 0.0f
        openAnimation.reset()
        openAnimation.run(1.0f)
    }

    fun getOpenProgress(): Float {
        openAnimation.run(1.0f)
        return openAnimation.value
    }

    fun position(x: Float, y: Float) {
        bounds = UiRect(x, y, bounds.width, bounds.height)
        updateViewport()
    }

    fun size(width: Float, height: Float, screenWidth: Float, screenHeight: Float) {
        var targetWidth = if (manualWidth > 0.0f) manualWidth else width
        var targetHeight = if (manualHeight > 0.0f) manualHeight else height
        targetWidth = Math.clamp(targetWidth, minimumWidth(), Math.max(1.0f, screenWidth))
        targetHeight = Math.clamp(targetHeight, minimumHeight(), Math.max(1.0f, screenHeight))

        val maxX = Math.max(0.0f, screenWidth - targetWidth)
        val maxY = if (allowHeaderOnly()) {
            Math.max(0.0f, screenHeight - Math.min(targetHeight, headerHeight))
        } else {
            Math.max(0.0f, screenHeight - targetHeight)
        }
        bounds = UiRect(
            Math.clamp(bounds.x, 0.0f, maxX),
            Math.clamp(bounds.y, 0.0f, maxY),
            targetWidth,
            targetHeight
        )
        updateViewport()
    }

    protected open fun minimumWidth(): Float {
        return 80.0f
    }

    protected open fun minimumHeight(): Float {
        return Math.max(40.0f, headerHeight + 12.0f)
    }

    protected open fun resizable(): Boolean {
        return true
    }

    protected open fun allowHeaderOnly(): Boolean {
        return false
    }

    open fun mouseClicked(event: MouseButtonEvent): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) {
            return false
        }

        val edge = if (resizable()) resizeEdgeAt(event.x(), event.y()) else ResizeEdge.NONE
        if (edge != ResizeEdge.NONE) {
            blurInput()
            resizing = true
            resizeEdge = edge
            resizeStartBounds = bounds
            resizeStartMouseX = event.x()
            resizeStartMouseY = event.y()
            return true
        }

        if (event.y() < bounds.y + headerHeight) {
            blurInput()
            dragging = true
            dragOffsetX = event.x() - bounds.x
            dragOffsetY = event.y() - bounds.y
            return true
        }
        return false
    }

    open fun mouseDragged(event: MouseButtonEvent): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false
        if (resizing) {
            resizeFrom(event.x(), event.y())
            return true
        }
        if (!dragging) return false
        position((event.x() - dragOffsetX).toFloat(), (event.y() - dragOffsetY).toFloat())
        return true
    }

    open fun mouseReleased(event: MouseButtonEvent) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false
            resizing = false
            resizeEdge = ResizeEdge.NONE
        }
    }

    open fun keyPressed(event: KeyEvent): Boolean {
        return false
    }

    open fun charTyped(event: CharacterEvent): Boolean {
        return false
    }

    open fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (!viewport.contains(mouseX, mouseY)) return false
        scroll.scroll(amount)
        return true
    }

    open fun blurInput() {
    }

    open fun resetInput() {
        dragging = false
        resizing = false
        resizeEdge = ResizeEdge.NONE
        scroll.stop()
        blurInput()
    }

    private fun resizeFrom(mouseX: Double, mouseY: Double) {
        val dx = (mouseX - resizeStartMouseX).toFloat()
        val dy = (mouseY - resizeStartMouseY).toFloat()
        var left = resizeStartBounds.x
        var top = resizeStartBounds.y
        var right = resizeStartBounds.right()
        var bottom = resizeStartBounds.bottom()

        if (resizeEdge.hasLeft()) left += dx
        if (resizeEdge.hasRight()) right += dx
        if (resizeEdge.hasTop()) top += dy
        if (resizeEdge.hasBottom()) bottom += dy

        val screenWidth = Math.max(1.0f, LuminRenderSystem.getScaledWidth())
        val screenHeight = Math.max(1.0f, LuminRenderSystem.getScaledHeight())
        val minWidth = Math.min(minimumWidth(), screenWidth)
        val minHeight = Math.min(minimumHeight(), screenHeight)

        if (resizeEdge.hasLeft()) left = Math.min(left, right - minWidth)
        if (resizeEdge.hasRight()) right = Math.max(right, left + minWidth)
        if (resizeEdge.hasTop()) top = Math.min(top, bottom - minHeight)
        if (resizeEdge.hasBottom()) bottom = Math.max(bottom, top + minHeight)

        left = Math.max(0.0f, left)
        top = Math.max(0.0f, top)
        right = Math.min(screenWidth, right)
        bottom = Math.min(screenHeight, bottom)

        if (right - left < minWidth) {
            if (resizeEdge.hasLeft()) left = Math.max(0.0f, right - minWidth)
            else if (resizeEdge.hasRight()) right = Math.min(screenWidth, left + minWidth)
        }
        if (bottom - top < minHeight) {
            if (resizeEdge.hasTop()) top = Math.max(0.0f, bottom - minHeight)
            else if (resizeEdge.hasBottom()) bottom = Math.min(screenHeight, top + minHeight)
        }

        bounds = UiRect(left, top, right - left, bottom - top)
        manualWidth = bounds.width
        manualHeight = bounds.height
        updateViewport()
    }

    private fun resizeEdgeAt(mouseX: Double, mouseY: Double): ResizeEdge {
        val left = mouseX <= bounds.x + RESIZE_MARGIN
        val right = mouseX >= bounds.right() - RESIZE_MARGIN
        val top = mouseY <= bounds.y + RESIZE_MARGIN
        val bottom = mouseY >= bounds.bottom() - RESIZE_MARGIN

        if (left && top) return ResizeEdge.TOP_LEFT
        if (right && top) return ResizeEdge.TOP_RIGHT
        if (left && bottom) return ResizeEdge.BOTTOM_LEFT
        if (right && bottom) return ResizeEdge.BOTTOM_RIGHT
        if (left) return ResizeEdge.LEFT
        if (right) return ResizeEdge.RIGHT
        if (top) return ResizeEdge.TOP
        if (bottom) return ResizeEdge.BOTTOM
        return ResizeEdge.NONE
    }

    private fun updateViewport() {
        viewport = UiRect(
            bounds.x + TrollTheme.xMargin(),
            bounds.y + headerHeight,
            Math.max(1.0f, bounds.width - TrollTheme.xMargin() * 2.0f),
            Math.max(0.0f, bounds.height - headerHeight)
        )
    }

    private enum class ResizeEdge {
        NONE,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        fun hasLeft(): Boolean {
            return this == LEFT || this == TOP_LEFT || this == BOTTOM_LEFT
        }

        fun hasRight(): Boolean {
            return this == RIGHT || this == TOP_RIGHT || this == BOTTOM_RIGHT
        }

        fun hasTop(): Boolean {
            return this == TOP || this == TOP_LEFT || this == TOP_RIGHT
        }

        fun hasBottom(): Boolean {
            return this == BOTTOM || this == BOTTOM_LEFT || this == BOTTOM_RIGHT
        }
    }

    companion object {
        private const val RESIZE_MARGIN = 5.0f
    }

}
