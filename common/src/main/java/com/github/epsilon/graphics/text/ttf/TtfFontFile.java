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

            final var advance = stack.callocInt(1);
            final var lsb = stack.callocInt(1);
            STBTruetype.stbtt_GetGlyphHMetrics(fontInfo, glyphIndex, advance, lsb);

            return new TtfGlyph(sdfPixels, width.get(), height.get(), xOff.get(), yOff.get(), (int) (advance.get() * scale));
        }
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
