package com.github.epsilon.gui.panel.utils

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.control.UiScrollBar
import com.github.epsilon.gui.theme.EpsilonUiTheme

object ScrollBarUtils {

    /**
     * Total horizontal space the scrollbar occupies (width + padding on each side).
     */
    const val TOTAL_WIDTH = UiScrollBar.TOTAL_WIDTH

    fun draw(
        scope: com.github.epsilon.gui.lib.UiTree.Scope,
        viewport: UiRect,
        scroll: Float,
        maxScroll: Float,
        contentHeight: Float
    ) {
        UiScrollBar.draw(scope, EpsilonUiTheme, viewport, scroll, maxScroll, contentHeight)
    }

    /**
     * Geometry of the scrollbar thumb and its hit-test track area.
     */
    data class ThumbGeometry(
        val thumbX: Float, val thumbY: Float, val thumbWidth: Float, val thumbHeight: Float,
        val trackX: Float, val trackY: Float, val trackWidth: Float, val trackHeight: Float,
    ) {
        fun thumbContains(px: Double, py: Double): Boolean {
            return px >= thumbX && px <= thumbX + thumbWidth && py >= thumbY && py <= thumbY + thumbHeight
        }

        fun trackContains(px: Double, py: Double): Boolean {
            return px >= trackX && px <= trackX + trackWidth && py >= trackY && py <= trackY + trackHeight
        }
    }

    /**
     * Compute the thumb geometry for hit-testing.
     * Returns null if there is no scrollbar (maxScroll <= 0).
     */
    fun computeThumb(viewport: UiRect, scroll: Float, maxScroll: Float, contentHeight: Float): ThumbGeometry? {
        val geometry = UiScrollBar.computeGeometry(viewport, scroll, maxScroll, contentHeight) ?: return null
        return ThumbGeometry(
            geometry.thumbX, geometry.thumbY, geometry.thumbWidth, geometry.thumbHeight,
            geometry.trackX, geometry.trackY, geometry.trackWidth, geometry.trackHeight
        )
    }

    /**
     * Convert a thumb-top Y coordinate back to an absolute scroll value.
     */
    fun scrollFromMouseY(thumbTopY: Float, viewport: UiRect, maxScroll: Float, contentHeight: Float): Float {
        return UiScrollBar.scrollFromThumbTopY(thumbTopY, viewport, maxScroll, contentHeight)
    }

}
