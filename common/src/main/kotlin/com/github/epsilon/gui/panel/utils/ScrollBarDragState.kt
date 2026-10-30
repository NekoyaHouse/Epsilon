package com.github.epsilon.gui.panel.utils

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.control.UiScrollBar
import com.github.epsilon.gui.theme.EpsilonUiTheme

class ScrollBarDragState {

    private val scrollBar = UiScrollBar(EpsilonUiTheme)

    fun isDragging(): Boolean {
        return scrollBar.isDragging()
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, viewport: UiRect, scroll: Float, maxScroll: Float): Boolean {
        if (maxScroll <= 0.0f) {
            return false
        }
        val contentHeight = maxScroll + viewport.height
        return scrollBar.mouseClicked(mouseX, mouseY, viewport, scroll, maxScroll, contentHeight)
    }

    fun mouseDragged(mouseY: Double, viewport: UiRect, maxScroll: Float): Float {
        if (!scrollBar.isDragging() || maxScroll <= 0.0f) {
            return -1.0f
        }
        val contentHeight = maxScroll + viewport.height
        return scrollBar.mouseDragged(mouseY, viewport, maxScroll, contentHeight)
    }

    fun mouseReleased(): Boolean {
        return scrollBar.mouseReleased()
    }

    fun reset() {
        scrollBar.reset()
    }

}
