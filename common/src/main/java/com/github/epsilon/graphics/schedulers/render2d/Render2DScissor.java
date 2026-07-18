package com.github.epsilon.graphics.schedulers.render2d;

import java.util.Objects;

public record Render2DScissor(int x, int y, int width, int height) {

    public boolean visible() {
        return width > 0 && height > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Render2DScissor(int x1, int y1, int width1, int height1))) return false;
        return x == x1 && y == y1 && width == width1 && height == height1;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

}
