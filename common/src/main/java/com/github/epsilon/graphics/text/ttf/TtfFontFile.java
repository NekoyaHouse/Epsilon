package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class TtfFontFile {

    private final ByteBuffer fontData;
    private final STBTTFontinfo fontInfo;

    private final int padding;

    public final float scale;
    public final int pixelAscent;
    public final int fontHeight;

    /** 缺字占位框（口字形）的尺寸比例：高度取 ascent 的 2/3，宽度与笔画由高度推导。 */
    private static final float FALLBACK_HEIGHT_RATIO = 2.0f / 3.0f;
    private static final float FALLBACK_WIDTH_RATIO = 0.72f;
    private static final float FALLBACK_STROKE_RATIO = 1.0f / 8.0f;
    /** 占位框位图四周外扩的像素数，保住墨迹边缘之外的一圈 SDF 过渡带。 */
    private static final int FALLBACK_PADDING = 2;

    public TtfFontFile(Identifier ttfFile, int totalHeight, int padding) {
        this(ResourceLocationUtils.loadResource(ttfFile), totalHeight, padding, ttfFile.toString());
    }

    public TtfFontFile(Path ttfFile, int totalHeight, int padding) {
        this(loadFontFile(ttfFile), totalHeight, padding, ttfFile.toString());
    }

    private TtfFontFile(ByteBuffer fontData, int totalHeight, int padding, String debugName) {
        this.fontData = fontData;

        fontInfo = STBTTFontinfo.create();

        if (!STBTruetype.stbtt_InitFont(fontInfo, fontData)) {
            MemoryUtil.memFree(fontData);
            throw new IllegalStateException("STB TrueType failed to load ttf font: " + debugName);
        }

        this.padding = padding;

        this.scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, totalHeight - padding * 2);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var ascentBuf = stack.callocInt(1);
            final var descentBuf = stack.callocInt(1);
            final var lineGapBuf = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascentBuf, descentBuf, lineGapBuf);

            final var ascent = ascentBuf.get();
            final var descent = descentBuf.get();
            final var lineGap = lineGapBuf.get();

            this.pixelAscent = (int) (ascent * scale);
            this.fontHeight = (int) ((ascent - descent + lineGap) * scale);
        }

//        this.fontHeight = totalHeight - padding * 2;

    }

    private static ByteBuffer loadFontFile(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read font file: " + path, e);
        }
    }

    public synchronized TtfGlyph generateGlyph(char ch) {
        return generateGlyph((int) ch);
    }

    public synchronized TtfGlyph generateGlyph(int codepoint) {
        final var glyphIndex = STBTruetype.stbtt_FindGlyphIndex(fontInfo, codepoint);

        // (byte) 128 溢出为 -128，于是 pixelDistScale 为负：墨迹落在 128 以下（暗），外部落在 128 以上（亮），
        // 与 ttf_font_* 着色器的 1 - r 解释一致。改动这里的符号会让所有文字反相。
        byte onEdgeValue = (byte) 128;
        float pixelDistScale = (float) onEdgeValue / padding;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var width = stack.callocInt(1);
            final var height = stack.callocInt(1);
            final var xOff = stack.callocInt(1);
            final var yOff = stack.callocInt(1);

            ByteBuffer sdfPixels = STBTruetype.stbtt_GetGlyphSDF(
                    fontInfo,
                    scale,
                    glyphIndex,
                    padding,
                    onEdgeValue,
                    pixelDistScale,
                    width,
                    height,
                    xOff, yOff
            );

            int glyphWidth = width.get(0);
            int glyphHeight = height.get(0);
            ByteBuffer alphaPixels = MemoryUtil.memCalloc(Math.max(1, glyphWidth * glyphHeight));
            try (MemoryStack bitmapStack = MemoryStack.stackPush()) {
                final var bitmapWidth = bitmapStack.callocInt(1);
                final var bitmapHeight = bitmapStack.callocInt(1);
                final var bitmapX = bitmapStack.callocInt(1);
                final var bitmapY = bitmapStack.callocInt(1);
                ByteBuffer bitmap = STBTruetype.stbtt_GetGlyphBitmap(
                        fontInfo, scale, scale, glyphIndex,
                        bitmapWidth, bitmapHeight, bitmapX, bitmapY
                );
                try {
                    if (bitmap != null && glyphWidth > 0 && glyphHeight > 0) {
                        int copyWidth = Math.min(bitmapWidth.get(0), glyphWidth);
                        int copyHeight = Math.min(bitmapHeight.get(0), glyphHeight);
                        int dstX = bitmapX.get(0) - xOff.get(0);
                        int dstY = bitmapY.get(0) - yOff.get(0);
                        for (int row = 0; row < copyHeight; row++) {
                            int targetY = row + dstY;
                            if (targetY < 0 || targetY >= glyphHeight) continue;
                            for (int column = 0; column < copyWidth; column++) {
                                int targetX = column + dstX;
                                if (targetX >= 0 && targetX < glyphWidth) {
                                    alphaPixels.put(targetY * glyphWidth + targetX, bitmap.get(row * bitmapWidth.get(0) + column));
                                }
                            }
                        }
                    }
                } finally {
                    if (bitmap != null) {
                        STBTruetype.stbtt_FreeBitmap(bitmap);
                    }
                }
            }

            final var advance = stack.callocInt(1);
            final var lsb = stack.callocInt(1);
            STBTruetype.stbtt_GetGlyphHMetrics(fontInfo, glyphIndex, advance, lsb);

            return new TtfGlyph(sdfPixels, alphaPixels, glyphWidth, glyphHeight, xOff.get(), yOff.get(), (int) (advance.get() * scale));
        }
    }

    /**
     * 生成缺字占位字形：一个“口”字形方框，供字形尚未上传或字体缺失该字形时渲染。
     * <p>
     * 尺寸由字体 ascent 推导，与多数字体自带 {@code .notdef} 方框接近；位图四周外扩
     * {@code FALLBACK_PADDING} 像素，让墨迹边缘外仍有 SDF 过渡带。
     * <p>
     * SDF 极性必须与 {@link #generateGlyph(int)} 保持一致：那里的 {@code onEdgeValue} 是 byte 128
     * （即 -128），使 {@code pixelDistScale} 为负，于是墨迹（内部距离为正）落在 128 以下、
     * 外部落在 128 以上；着色器按 {@code 1 - r} 解释该纹理，墨迹才是可见部分。
     */
    public TtfGlyph generateFallbackGlyph() {
        int ascent = Math.max(pixelAscent, 1);
        int boxHeight = Math.max(3, Math.round(ascent * FALLBACK_HEIGHT_RATIO));
        int boxWidth = Math.max(3, Math.round(boxHeight * FALLBACK_WIDTH_RATIO));
        // 笔画不能太细（缩放后会消失），也不能填满方框（环会退化成实心块）。
        int stroke = Math.clamp(Math.round(boxHeight * FALLBACK_STROKE_RATIO), 1,
                Math.max(1, (Math.min(boxWidth, boxHeight) - 1) / 2));

        int bitmapWidth = boxWidth + FALLBACK_PADDING * 2;
        int bitmapHeight = boxHeight + FALLBACK_PADDING * 2;
        int xOffset = -FALLBACK_PADDING;
        int yOffset = -boxHeight - FALLBACK_PADDING;

        ByteBuffer sdfPixels = MemoryUtil.memAlloc(bitmapWidth * bitmapHeight);
        ByteBuffer alphaPixels = MemoryUtil.memCalloc(bitmapWidth * bitmapHeight);

        double halfWidth = boxWidth / 2.0;
        double halfHeight = boxHeight / 2.0;
        double centerX = halfWidth;
        double centerY = -halfHeight;
        double innerHalfWidth = Math.max(halfWidth - stroke, 0.0);
        double innerHalfHeight = Math.max(halfHeight - stroke, 0.0);
        double onEdgeValue = 128.0;
        double pixelDistScale = -onEdgeValue / padding;

        for (int row = 0; row < bitmapHeight; row++) {
            double localY = yOffset + row + 0.5;
            for (int column = 0; column < bitmapWidth; column++) {
                double localX = xOffset + column + 0.5;
                // ringDistance 是标准 SDF 约定（墨迹内为负），取负后才是 stb 在墨迹内为正的 min_dist。
                double distance = ringDistance(localX - centerX, localY - centerY,
                        halfWidth, halfHeight, innerHalfWidth, innerHalfHeight);
                int index = row * bitmapWidth + column;
                sdfPixels.put(index, (byte) (int) Math.clamp(onEdgeValue + pixelDistScale * -distance, 0.0, 255.0));
                alphaPixels.put(index, (byte) (int) (Math.clamp(0.5 - distance, 0.0, 1.0) * 255.0));
            }
        }

        // advance 只作兜底：TtfFontLoader 对外会换成目标字符自身的步进宽度。
        return new TtfGlyph(sdfPixels, alphaPixels, bitmapWidth, bitmapHeight, xOffset, yOffset, boxWidth + stroke);
    }

    /** 矩形环（“口”字框）的有符号距离，墨迹内为负。 */
    private static double ringDistance(double x, double y, double halfWidth, double halfHeight,
                                       double innerHalfWidth, double innerHalfHeight) {
        return Math.max(
                boxDistance(x, y, halfWidth, halfHeight),
                -boxDistance(x, y, innerHalfWidth, innerHalfHeight)
        );
    }

    /** 矩形有符号距离，内部为负。 */
    private static double boxDistance(double x, double y, double halfWidth, double halfHeight) {
        double qx = Math.abs(x) - halfWidth;
        double qy = Math.abs(y) - halfHeight;
        return Math.hypot(Math.max(qx, 0.0), Math.max(qy, 0.0)) + Math.min(Math.max(qx, qy), 0.0);
    }

    public synchronized int getAdvance(char ch) {
        return getAdvance((int) ch);
    }

    public synchronized int getAdvance(int codepoint) {
        final var glyphIndex = STBTruetype.stbtt_FindGlyphIndex(fontInfo, codepoint);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var advance = stack.callocInt(1);
            final var lsb = stack.callocInt(1);
            STBTruetype.stbtt_GetGlyphHMetrics(fontInfo, glyphIndex, advance, lsb);
            return (int) (advance.get() * scale);
        }
    }

    public synchronized float getVisualHeight(String sample) {
        if (sample == null || sample.isEmpty()) {
            return fontHeight;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final var x0 = stack.mallocInt(1);
            final var y0 = stack.mallocInt(1);
            final var x1 = stack.mallocInt(1);
            final var y1 = stack.mallocInt(1);
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;

            for (int i = 0; i < sample.length(); ) {
                int codepoint = sample.codePointAt(i);
                i += Character.charCount(codepoint);
                if (Character.isWhitespace(codepoint)) {
                    continue;
                }

                int glyphIndex = STBTruetype.stbtt_FindGlyphIndex(fontInfo, codepoint);
                if (glyphIndex == 0) {
                    continue;
                }

                STBTruetype.stbtt_GetGlyphBitmapBox(fontInfo, glyphIndex, scale, scale, x0, y0, x1, y1);
                if (y1.get(0) <= y0.get(0)) {
                    continue;
                }

                minY = Math.min(minY, y0.get(0));
                maxY = Math.max(maxY, y1.get(0));
            }

            return maxY > minY ? maxY - minY : fontHeight;
        }
    }

    public void destroy() {
        MemoryUtil.memFree(fontData);
    }

}
