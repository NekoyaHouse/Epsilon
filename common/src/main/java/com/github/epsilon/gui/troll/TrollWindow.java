package com.github.epsilon.gui.troll;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public abstract class TrollWindow {

    private static final float RESIZE_MARGIN = 5.0f;

    protected final TrollScroll scroll = new TrollScroll();
    protected UiRect bounds = new UiRect(0, 0, 180, 300);
    protected UiRect viewport = bounds;
    protected float headerHeight;
    protected float scrollPosition;
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_QUART, 300L);
    private float manualWidth = -1.0f;
    private float manualHeight = -1.0f;
    private boolean dragging;
    private boolean resizing;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private UiRect resizeStartBounds = bounds;
    private double resizeStartMouseX;
    private double resizeStartMouseY;
    private double dragOffsetX;
    private double dragOffsetY;

    protected TrollWindow() {
        openAnimation.setStartValue(0.0f);
    }

    public abstract void layout(TextRenderer metrics, float screenWidth, float screenHeight);

    public abstract void draw(UiTree.Scope scope, TextRenderer metrics, int mouseX, int mouseY);

    public void startOpenAnimation() {
        openAnimation.setValue(0.0f);
        openAnimation.reset();
        openAnimation.run(1.0f);
    }

    public float getOpenProgress() {
        openAnimation.run(1.0f);
        return openAnimation.getValue();
    }

    public void position(float x, float y) {
        bounds = new UiRect(x, y, bounds.width(), bounds.height());
        updateViewport();
    }

    public void size(float width, float height, float screenWidth, float screenHeight) {
        float targetWidth = manualWidth > 0.0f ? manualWidth : width;
        float targetHeight = manualHeight > 0.0f ? manualHeight : height;
        targetWidth = Math.clamp(targetWidth, minimumWidth(), Math.max(1.0f, screenWidth));
        targetHeight = Math.clamp(targetHeight, minimumHeight(), Math.max(1.0f, screenHeight));

        float maxX = Math.max(0.0f, screenWidth - targetWidth);
        float maxY = allowHeaderOnly()
                ? Math.max(0.0f, screenHeight - Math.min(targetHeight, headerHeight))
                : Math.max(0.0f, screenHeight - targetHeight);
        bounds = new UiRect(
                Math.clamp(bounds.x(), 0.0f, maxX),
                Math.clamp(bounds.y(), 0.0f, maxY),
                targetWidth,
                targetHeight
        );
        updateViewport();
    }

    protected float minimumWidth() {
        return 80.0f;
    }

    protected float minimumHeight() {
        return Math.max(40.0f, headerHeight + 12.0f);
    }

    protected boolean resizable() {
        return true;
    }

    protected boolean allowHeaderOnly() {
        return false;
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) {
            return false;
        }

        ResizeEdge edge = resizable() ? resizeEdgeAt(event.x(), event.y()) : ResizeEdge.NONE;
        if (edge != ResizeEdge.NONE) {
            blurInput();
            resizing = true;
            resizeEdge = edge;
            resizeStartBounds = bounds;
            resizeStartMouseX = event.x();
            resizeStartMouseY = event.y();
            return true;
        }

        if (event.y() < bounds.y() + headerHeight) {
            blurInput();
            dragging = true;
            dragOffsetX = event.x() - bounds.x();
            dragOffsetY = event.y() - bounds.y();
            return true;
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        if (resizing) {
            resizeFrom(event.x(), event.y());
            return true;
        }
        if (!dragging) return false;
        position((float) (event.x() - dragOffsetX), (float) (event.y() - dragOffsetY));
        return true;
    }

    public void mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false;
            resizing = false;
            resizeEdge = ResizeEdge.NONE;
        }
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!viewport.contains(mouseX, mouseY)) return false;
        scroll.scroll(amount);
        return true;
    }

    public void blurInput() {
    }

    public void resetInput() {
        dragging = false;
        resizing = false;
        resizeEdge = ResizeEdge.NONE;
        scroll.stop();
        blurInput();
    }

    private void resizeFrom(double mouseX, double mouseY) {
        float dx = (float) (mouseX - resizeStartMouseX);
        float dy = (float) (mouseY - resizeStartMouseY);
        float left = resizeStartBounds.x();
        float top = resizeStartBounds.y();
        float right = resizeStartBounds.right();
        float bottom = resizeStartBounds.bottom();

        if (resizeEdge.hasLeft()) left += dx;
        if (resizeEdge.hasRight()) right += dx;
        if (resizeEdge.hasTop()) top += dy;
        if (resizeEdge.hasBottom()) bottom += dy;

        float screenWidth = Math.max(1.0f, LuminRenderSystem.getScaledWidth());
        float screenHeight = Math.max(1.0f, LuminRenderSystem.getScaledHeight());
        float minWidth = Math.min(minimumWidth(), screenWidth);
        float minHeight = Math.min(minimumHeight(), screenHeight);

        if (resizeEdge.hasLeft()) left = Math.min(left, right - minWidth);
        if (resizeEdge.hasRight()) right = Math.max(right, left + minWidth);
        if (resizeEdge.hasTop()) top = Math.min(top, bottom - minHeight);
        if (resizeEdge.hasBottom()) bottom = Math.max(bottom, top + minHeight);

        left = Math.max(0.0f, left);
        top = Math.max(0.0f, top);
        right = Math.min(screenWidth, right);
        bottom = Math.min(screenHeight, bottom);

        if (right - left < minWidth) {
            if (resizeEdge.hasLeft()) left = Math.max(0.0f, right - minWidth);
            else if (resizeEdge.hasRight()) right = Math.min(screenWidth, left + minWidth);
        }
        if (bottom - top < minHeight) {
            if (resizeEdge.hasTop()) top = Math.max(0.0f, bottom - minHeight);
            else if (resizeEdge.hasBottom()) bottom = Math.min(screenHeight, top + minHeight);
        }

        bounds = new UiRect(left, top, right - left, bottom - top);
        manualWidth = bounds.width();
        manualHeight = bounds.height();
        updateViewport();
    }

    private ResizeEdge resizeEdgeAt(double mouseX, double mouseY) {
        boolean left = mouseX <= bounds.x() + RESIZE_MARGIN;
        boolean right = mouseX >= bounds.right() - RESIZE_MARGIN;
        boolean top = mouseY <= bounds.y() + RESIZE_MARGIN;
        boolean bottom = mouseY >= bounds.bottom() - RESIZE_MARGIN;

        if (left && top) return ResizeEdge.TOP_LEFT;
        if (right && top) return ResizeEdge.TOP_RIGHT;
        if (left && bottom) return ResizeEdge.BOTTOM_LEFT;
        if (right && bottom) return ResizeEdge.BOTTOM_RIGHT;
        if (left) return ResizeEdge.LEFT;
        if (right) return ResizeEdge.RIGHT;
        if (top) return ResizeEdge.TOP;
        if (bottom) return ResizeEdge.BOTTOM;
        return ResizeEdge.NONE;
    }

    private void updateViewport() {
        viewport = new UiRect(
                bounds.x() + TrollTheme.xMargin(),
                bounds.y() + headerHeight,
                Math.max(1.0f, bounds.width() - TrollTheme.xMargin() * 2.0f),
                Math.max(0.0f, bounds.height() - headerHeight)
        );
    }

    private enum ResizeEdge {
        NONE,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        private boolean hasLeft() {
            return this == LEFT || this == TOP_LEFT || this == BOTTOM_LEFT;
        }

        private boolean hasRight() {
            return this == RIGHT || this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        private boolean hasTop() {
            return this == TOP || this == TOP_LEFT || this == TOP_RIGHT;
        }

        private boolean hasBottom() {
            return this == BOTTOM || this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
    }

}
