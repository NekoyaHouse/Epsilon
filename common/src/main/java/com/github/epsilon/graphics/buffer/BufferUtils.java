package com.github.epsilon.graphics.buffer;

import org.lwjgl.system.MemoryUtil;

public class BufferUtils {

    public static void writeUvRectToAddr(long p, float x, float y, float u, float v, int color) {
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4, y);
        MemoryUtil.memPutFloat(p + 8, 0.0f);
        MemoryUtil.memPutFloat(p + 12, u);
        MemoryUtil.memPutFloat(p + 16, v);
        MemoryUtil.memPutInt(p + 20, color);
    }

}
