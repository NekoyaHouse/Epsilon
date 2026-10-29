package com.github.epsilon.gui.troll;

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

    protected final TrollScroll scroll = new TrollScroll();
    protected UiRect bounds = new UiRect(0, 0, 180, 300);
    protected UiRect viewport = bounds;
    protected float headerHeight;
    protected float scrollPosition;
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_QUART, 300L);
    private boolean dragging;
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
    }

    public void size(float width, float height, float screenWidth, float screenHeight) {
        float maxX = Math.max(0.0f, screenWidth - width);
        float maxY = allowHeaderOnly()
                ? Math.max(0.0f, screenHeight - Math.min(height, headerHeight))
                : Math.max(0.0f, screenHeight - height);
        bounds = new UiRect(Math.clamp(bounds.x(), 0.0f, maxX), Math.clamp(bounds.y(), 0.0f, maxY), width, height);
        viewport = new UiRect(bounds.x() + TrollTheme.xMargin(), bounds.y() + headerHeight, Math.max(1.0f, width - TrollTheme.xMargin() * 2.0f), Math.max(0.0f, height - headerHeight));
    }

    protected boolean allowHeaderOnly() {
        return false;
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && bounds.contains(event.x(), event.y()) && event.y() < bounds.y() + headerHeight) {
            blurInput();
            dragging = true;
            dragOffsetX = event.x() - bounds.x();
            dragOffsetY = event.y() - bounds.y();
            return true;
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event) {
        if (!dragging || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        position((float) (event.x() - dragOffsetX), (float) (event.y() - dragOffsetY));
        return true;
    }

    public void mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) dragging = false;
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
        scroll.stop();
        blurInput();
    }

}
