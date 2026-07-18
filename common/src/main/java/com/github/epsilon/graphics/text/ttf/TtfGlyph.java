package com.github.epsilon.graphics.text.ttf;

import java.nio.ByteBuffer;

public record TtfGlyph(
        ByteBuffer glyphData,
        int width,
        int height,
        int xOffset,
        int yOffset,
        int advance
) {
}
