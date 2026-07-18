package com.github.epsilon.gui.panel.utils;

import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.control.UiScrollBar;
import com.github.epsilon.gui.theme.EpsilonUiTheme;

public class ScrollBarDragState {

    private final UiScrollBar scrollBar = new UiScrollBar(EpsilonUiTheme.INSTANCE);

    public boolean isDragging() {
        return scrollBar.isDragging();
    }

    public boolean mouseClicked(double mouseX, double mouseY, UiRect viewport,
                                float scroll, float maxScroll) {
        if (maxScroll <= 0.0f) {
            return false;
        }
        float contentHeight = maxScroll + viewport.height();
        return scrollBar.mouseClicked(mouseX, mouseY, viewport, scroll, maxScroll, contentHeight);
    }

    public float mouseDragged(double mouseY, UiRect viewport, float maxScroll) {
        if (!scrollBar.isDragging() || maxScroll <= 0.0f) {
            return -1.0f;
        }
        float contentHeight = maxScroll + viewport.height();
        return scrollBar.mouseDragged(mouseY, viewport, maxScroll, contentHeight);
    }

    public boolean mouseReleased() {
        return scrollBar.mouseReleased();
    }

    public void reset() {
        scrollBar.reset();
    }
}
