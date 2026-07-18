package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;

public abstract class Component {

    protected float x;
    protected float y;
    protected float width;

    public abstract float getHeight();

    public abstract void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY);

    public final void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, UiRect bounds) {
        // 组件只缓存本帧命中区域，位置由 UiTree/stack 在调用侧统一计算。
        scope.pushAbsolute(bounds, child -> drawInScope(child, textMetrics, mouseX, mouseY, bounds));
    }

    public final void drawInScope(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY,
                                  UiRect bounds) {
        setPosition(bounds.x(), bounds.y(), bounds.width());
        draw(scope, textMetrics, mouseX, mouseY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(String typedText) {
        return false;
    }

    public void setPosition(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    protected boolean isHovered(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    protected boolean isHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, x, y, width, getHeight());
    }

    protected float absoluteX(float localX) {
        return x + localX;
    }

    protected float absoluteY(float localY) {
        return y + localY;
    }

}
