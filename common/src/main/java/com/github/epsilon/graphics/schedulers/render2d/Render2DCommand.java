package com.github.epsilon.graphics.schedulers.render2d;

import com.github.epsilon.graphics.text.ttf.TtfFontLoader;

import java.awt.*;

sealed interface Render2DCommand permits Render2DCommand.Shadow, Render2DCommand.RoundRect,
        Render2DCommand.RoundRectOutline, Render2DCommand.Rect, Render2DCommand.Triangle,
        Render2DCommand.Texture, Render2DCommand.Text {

    int layer();

    long sequence();

    Render2DCommandKind kind();

    Render2DBounds bounds();

    Render2DScissor scissor();

    default Render2DBounds orderingBounds() {
        return bounds();
    }

    record Shadow(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                  float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                  float blurRadius, Color color) implements Render2DCommand {
        @Override
        public Render2DBounds orderingBounds() {
            float pad = Math.max(0.0f, blurRadius);
            return Render2DBounds.of(bounds.x() - pad, bounds.y() - pad,
                    bounds.width() + pad * 2.0f, bounds.height() + pad * 2.0f);
        }

        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.SHADOW;
        }
    }

    record RoundRect(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                     float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                     Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.ROUND_RECT;
        }
    }

    record RoundRectOutline(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                            float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                            float outlineWidth, Color color) implements Render2DCommand {
        @Override
        public Render2DBounds orderingBounds() {
            float pad = Math.max(0.0f, outlineWidth * 0.5f);
            return Render2DBounds.of(bounds.x() - pad, bounds.y() - pad,
                    bounds.width() + pad * 2.0f, bounds.height() + pad * 2.0f);
        }

        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.ROUND_RECT_OUTLINE;
        }
    }

    record Rect(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.RECT;
        }
    }

    record Triangle(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                    float centerX, float centerY, float size, float progress, Color color) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.TRIANGLE;
        }
    }

    record Texture(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                   Render2DTexture texture, float radiusTopLeft, float radiusTopRight,
                   float radiusBottomRight, float radiusBottomLeft, float u0, float v0, float u1, float v1,
                   Color color, float originX, float originY, float rotationDegrees) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.TEXTURE;
        }
    }

    record Text(int layer, long sequence, Render2DBounds bounds, Render2DScissor scissor,
                String text, float x, float y, float scale, Color color,
                TtfFontLoader fontLoader, float originX, float originY,
                float rotationDegrees) implements Render2DCommand {
        @Override
        public Render2DCommandKind kind() {
            return Render2DCommandKind.TEXT;
        }
    }

}
