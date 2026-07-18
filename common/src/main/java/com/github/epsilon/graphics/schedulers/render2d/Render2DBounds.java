package com.github.epsilon.graphics.schedulers.render2d;

public record Render2DBounds(float x, float y, float width, float height) {

    public static Render2DBounds of(float x, float y, float width, float height) {
        return new Render2DBounds(x, y, Math.max(0.0f, width), Math.max(0.0f, height));
    }

    public static Render2DBounds point(float x, float y) {
        return new Render2DBounds(x, y, 0.0f, 0.0f);
    }

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public float centerX() {
        return x + width * 0.5f;
    }

    public float centerY() {
        return y + height * 0.5f;
    }

    public boolean intersects(Render2DBounds other) {
        if (width <= 0.0f || height <= 0.0f || other.width <= 0.0f || other.height <= 0.0f) {
            return x >= other.x && x <= other.right() && y >= other.y && y <= other.bottom();
        }
        return right() > other.x && x < other.right() && bottom() > other.y && y < other.bottom();
    }

    public boolean contains(Render2DBounds other) {
        return other.x >= x && other.right() <= right() && other.y >= y && other.bottom() <= bottom();
    }

    public Render2DBounds include(Render2DBounds other) {
        float nx = Math.min(x, other.x);
        float ny = Math.min(y, other.y);
        float nr = Math.max(right(), other.right());
        float nb = Math.max(bottom(), other.bottom());
        return new Render2DBounds(nx, ny, Math.max(0.0f, nr - nx), Math.max(0.0f, nb - ny));
    }

}
