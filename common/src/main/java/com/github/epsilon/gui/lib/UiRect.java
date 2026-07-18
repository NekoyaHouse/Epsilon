package com.github.epsilon.gui.lib;

/**
 * 与具体屏幕和渲染后端无关的矩形值对象。
 */
public record UiRect(float x, float y, float width, float height) {

    public UiRect {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)) {
            throw new IllegalArgumentException("UI rectangle values must be finite");
        }
    }

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public float centerX() {
        return x + width / 2.0f;
    }

    public float centerY() {
        return y + height / 2.0f;
    }

    public boolean contains(double px, double py) {
        return px >= x && px <= right() && py >= y && py <= bottom();
    }

    public UiRect inset(float amount) {
        return inset(amount, amount);
    }

    public UiRect inset(float horizontal, float vertical) {
        return new UiRect(x + horizontal, y + vertical,
                Math.max(0.0f, width - horizontal * 2.0f),
                Math.max(0.0f, height - vertical * 2.0f));
    }

    public UiRect atOrigin() {
        return new UiRect(0.0f, 0.0f, width, height);
    }

    public UiRect relativeTo(UiRect origin) {
        return new UiRect(x - origin.x(), y - origin.y(), width, height);
    }

    public UiRect translate(float deltaX, float deltaY) {
        return new UiRect(x + deltaX, y + deltaY, width, height);
    }

    public UiRect intersect(UiRect other) {
        float intersectionX = Math.max(x, other.x());
        float intersectionY = Math.max(y, other.y());
        float intersectionRight = Math.min(right(), other.right());
        float intersectionBottom = Math.min(bottom(), other.bottom());
        if (intersectionRight <= intersectionX || intersectionBottom <= intersectionY) {
            return null;
        }
        return new UiRect(intersectionX, intersectionY,
                intersectionRight - intersectionX, intersectionBottom - intersectionY);
    }
}
