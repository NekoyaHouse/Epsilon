package com.github.epsilon.graphics.immediate;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;

public final class LuminTessellator {

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 1024;
    private static final LuminTessellator INSTANCE = new LuminTessellator(DEFAULT_BUFFER_SIZE);

    private final ByteBufferBuilder allocator;

    private LuminTessellator(int bufferSize) {
        this.allocator = new ByteBufferBuilder(bufferSize);
    }

    public static LuminTessellator getInstance() {
        return INSTANCE;
    }

    public BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        return new BufferBuilder(this.allocator, mode, format);
    }

}
