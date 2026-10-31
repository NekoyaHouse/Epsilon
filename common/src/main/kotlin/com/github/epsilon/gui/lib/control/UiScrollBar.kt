package com.github.epsilon.gui.lib.control

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTheme
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import java.util.*

/**
 * 轻量滚动条，统一提供绘制、命中测试和拖动值换算。
 *
 * 控件只接收通用坐标与 [UiTheme]，不持有 Panel 或 Dropdown 状态。
 */
class UiScrollBar(theme: UiTheme) {

    private val theme: UiTheme = Objects.requireNonNull(theme, "theme")
    private val hoverAnimation: Animation = Animation(Easing.EASE_OUT_CUBIC, theme.hoverAnimationDuration())
    private var dragging = false
    private var dragOffset = 0.0f

    fun isDragging(): Boolean {
        return dragging
    }

    fun draw(
        scope: UiTree.Scope, viewport: UiRect, scroll: Float, maxScroll: Float,
        contentHeight: Float, mouseX: Double, mouseY: Double
    ) {
        val geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight)
        if (geometry == null) {
            hoverAnimation.run(0.0f)
            return
        }
        hoverAnimation.run(if (geometry.trackContains(mouseX, mouseY) || dragging) 1.0f else 0.0f)
        draw(scope, geometry, hoverAnimation.value, theme)
    }

    fun mouseClicked(
        mouseX: Double, mouseY: Double, viewport: UiRect, scroll: Float,
        maxScroll: Float, contentHeight: Float
    ): Boolean {
        val geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight)
        if (geometry == null || !geometry.trackContains(mouseX, mouseY)) {
            return false
        }
        dragging = true
        dragOffset = if (geometry.thumbContains(mouseX, mouseY)) {
            mouseY.toFloat() - geometry.thumbY
        } else {
            geometry.thumbHeight / 2.0f
        }
        return true
    }

    fun mouseDragged(mouseY: Double, viewport: UiRect, maxScroll: Float, contentHeight: Float): Float {
        if (!dragging || maxScroll <= 0.0f) {
            return -1.0f
        }
        return scrollFromThumbTopY(mouseY.toFloat() - dragOffset, viewport, maxScroll, contentHeight)
    }

    fun mouseReleased(): Boolean {
        if (!dragging) {
            return false
        }
        dragging = false
        return true
    }

    fun reset() {
        dragging = false
        hoverAnimation.run(0.0f)
    }

    fun isHovered(
        mouseX: Double, mouseY: Double, viewport: UiRect, scroll: Float,
        maxScroll: Float, contentHeight: Float
    ): Boolean {
        val geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight)
        return geometry != null && geometry.trackContains(mouseX, mouseY)
    }

    data class Geometry(
        val thumbX: Float, val thumbY: Float, val thumbWidth: Float, val thumbHeight: Float,
        val trackX: Float, val trackY: Float, val trackWidth: Float, val trackHeight: Float
    ) {
        fun thumbContains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= trackX && mouseX <= trackX + trackWidth && mouseY >= thumbY && mouseY <= thumbY + thumbHeight
        }

        fun trackContains(mouseX: Double, mouseY: Double): Boolean {
            return mouseX >= trackX && mouseX <= trackX + trackWidth && mouseY >= trackY && mouseY <= trackY + trackHeight
        }
    }

    companion object {

        const val WIDTH = 3.5f
        const val RIGHT_INSET = 2.5f
        const val MIN_THUMB_HEIGHT = 10.0f
        const val HIT_WIDTH = 10.0f
        const val HOVER_WIDTH = 2.5f
        const val TOTAL_WIDTH = HIT_WIDTH

        fun draw(
            scope: UiTree.Scope, theme: UiTheme, viewport: UiRect, scroll: Float,
            maxScroll: Float, contentHeight: Float
        ) {
            val geometry = computeGeometry(viewport, scroll, maxScroll, contentHeight)
            if (geometry != null) {
                draw(scope, geometry, 0.0f, theme)
            }
        }

        fun computeGeometry(viewport: UiRect, scroll: Float, maxScroll: Float, contentHeight: Float): Geometry? {
            if (maxScroll <= 0.0f || contentHeight <= viewport.height) {
                return null
            }
            val trackHeight = viewport.height
            if (trackHeight <= 0.5f) {
                return null
            }
            val thumbHeight = (viewport.height / contentHeight * trackHeight).coerceIn(MIN_THUMB_HEIGHT, trackHeight)
            val thumbTravel = trackHeight - thumbHeight
            val scrollRatio = if (maxScroll > 0.0f) scroll / maxScroll else 0.0f
            val thumbY = viewport.y + thumbTravel * (scrollRatio).coerceIn(0.0f, 1.0f)
            val thumbX = viewport.right() - RIGHT_INSET
            val trackX = viewport.right() - HIT_WIDTH
            return Geometry(thumbX, thumbY, WIDTH, thumbHeight, trackX, viewport.y, HIT_WIDTH, trackHeight)
        }

        fun scrollFromThumbTopY(thumbTopY: Float, viewport: UiRect, maxScroll: Float, contentHeight: Float): Float {
            val geometry = computeGeometry(viewport, 0.0f, maxScroll, contentHeight)
            if (geometry == null) {
                return 0.0f
            }
            val thumbTravel = geometry.trackHeight - geometry.thumbHeight
            if (thumbTravel <= 0.0f) {
                return 0.0f
            }
            val ratio = (thumbTopY - geometry.trackY) / thumbTravel
            return (ratio).coerceIn(0.0f, 1.0f) * maxScroll
        }

        private fun draw(scope: UiTree.Scope, geometry: Geometry, hoverProgress: Float, theme: UiTheme) {
            val thumbWidth = geometry.thumbWidth + (HOVER_WIDTH - geometry.thumbWidth) * hoverProgress
            val thumbX = geometry.thumbX - (thumbWidth - geometry.thumbWidth) * 0.5f
            scope.roundRect(
                thumbX,
                geometry.thumbY,
                thumbWidth,
                geometry.thumbHeight,
                thumbWidth * 0.5f,
                theme.scrollBar(hoverProgress)
            )
        }

    }

}
