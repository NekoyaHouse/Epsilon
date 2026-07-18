package com.github.epsilon.graphics;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

public class LuminVertexFormats {

    private static final int ROUND_INNER_RECT_ID = findNextId();
    private static final int ROUND_RADIUS_ID = findNextId(ROUND_INNER_RECT_ID + 1);
    private static final int ROUND_OUTLINE_WIDTH_ID = findNextId(ROUND_RADIUS_ID + 1);

    public static final VertexFormatElement ROUND_INNER_RECT = VertexFormatElement.register(ROUND_INNER_RECT_ID, 2, VertexFormatElement.Type.FLOAT, false, 4);
    public static final VertexFormatElement ROUND_RADIUS = VertexFormatElement.register(ROUND_RADIUS_ID, 4, VertexFormatElement.Type.FLOAT, false, 4);
    public static final VertexFormatElement ROUND_OUTLINE_WIDTH = VertexFormatElement.register(ROUND_OUTLINE_WIDTH_ID, 1, VertexFormatElement.Type.FLOAT, false, 1);

    public static final VertexFormat ROUND_RECT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("InnerRect", ROUND_INNER_RECT)
            .add("Radius", ROUND_RADIUS)
            .build();

    public static final VertexFormat ROUND_RECT_OUTLINE = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("InnerRect", ROUND_INNER_RECT)
            .add("Radius", ROUND_RADIUS)
            .add("OutlineWidth", ROUND_OUTLINE_WIDTH)
            .build();

    public static final VertexFormat TEXTURE = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("InnerRect", ROUND_INNER_RECT)
            .add("Radius", ROUND_RADIUS)
            .build();

    private static int findNextId() {
        return findNextId(0);
    }

    private static int findNextId(int start) {
        for (int i = Math.max(0, start); i < VertexFormatElement.MAX_COUNT; i++) {
            if (VertexFormatElement.byId(i) == null) {
                return i;
            }
        }
        throw new IllegalStateException("VertexFormatElement count limit exceeded");
    }

}
