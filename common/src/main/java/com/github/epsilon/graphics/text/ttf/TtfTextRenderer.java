package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.buffer.BufferUtils;
import com.github.epsilon.graphics.buffer.LuminRingBuffer;
import com.github.epsilon.graphics.text.GlyphDescriptor;
import com.github.epsilon.graphics.text.ITextRenderer;
import com.github.epsilon.graphics.text.TextGlitchEffect;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.render.ScissorUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.*;

public class TtfTextRenderer implements ITextRenderer {

    private static final float DEFAULT_SCALE = 0.35f;
    private static final float SPACING = 0f;
    private static final int STANDARD_STRIDE = 24;
    private static final int FONT_BLUR_STRIDE = 40;
    private static final int FONT_BLUR_UNIFORM_SIZE = 16;
    private static final float MAX_FONT_BLUR_RADIUS = 20.0f;
    private static final float FONT_BLUR_EXTRA_PADDING = 2.0f;
    private static final int LAYOUT_FLOATS_PER_GLYPH = 10;
    private static final int LAYOUT_CACHE_LIMIT = 256;
    private static final int WIDTH_CACHE_LIMIT = 256;
    private static final float SPACE_WIDTH = 3.0f;
    private final long bufferSize;

    public enum Mode {
        STANDARD,
        FONT_BLUR,
        GLITCH
    }

    private final Mode mode;
    private final int stride;
    private final long glyphBytes;

    private final Map<BatchKey, Batch> batches = new LinkedHashMap<>();
    // 缓存与 scale 无关的布局数据，绘制时只做平移和缩放。
    private final Map<LayoutKey, TextLayout> layoutCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LayoutKey, TextLayout> eldest) {
            return size() > LAYOUT_CACHE_LIMIT;
        }
    };
    // 宽度测量调用很密集，单独缓存避免为了布局缓存而请求/上传字形。
    private final Map<LayoutKey, Float> widthCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LayoutKey, Float> eldest) {
            return size() > WIDTH_CACHE_LIMIT;
        }
    };

    private boolean scissorEnabled = false;
    private int scissorX, scissorY, scissorW, scissorH;
    private GpuBufferSlice sharedDynamicUniforms;
    private int sharedMaxIndexCount;

    public TtfTextRenderer(long bufferSize) {
        this(bufferSize, Mode.STANDARD);
    }

    public TtfTextRenderer(long bufferSize, boolean fontBlur) {
        this(bufferSize, fontBlur ? Mode.FONT_BLUR : Mode.STANDARD);
    }

    public TtfTextRenderer(long bufferSize, Mode mode) {
        this.bufferSize = bufferSize;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.stride = mode == Mode.STANDARD ? STANDARD_STRIDE : FONT_BLUR_STRIDE;
        this.glyphBytes = stride * 4L;
    }

    public TtfTextRenderer() {
        this(64 * 1024);
    }

    @Override
    public void addText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
        if (text.isEmpty() || color.getAlpha() == 0) return;

        fontLoader.prepareChars(text);
        emitLayout(layoutFor(text, fontLoader), x, y, scale, ARGB.toABGR(color.getRGB()), 0.0f);
    }

    @Override
    public void addBlurredText(String text, float x, float y, float scale, Color color,
                               float blurRadius, TtfFontLoader fontLoader) {
        if (mode != Mode.FONT_BLUR) {
            throw new IllegalStateException("Font blur text requires TextRenderer.createFontBlur().");
        }
        if (text.isEmpty() || color.getAlpha() == 0 || !Float.isFinite(blurRadius) || blurRadius <= 0.0f) return;

        fontLoader.prepareChars(text);
        float clampedBlur = Math.clamp(blurRadius, 0.0f, MAX_FONT_BLUR_RADIUS);
        emitLayout(layoutFor(text, fontLoader), x, y, scale, ARGB.toABGR(color.getRGB()), clampedBlur);
    }

    @Override
    public void addGlitchText(String text, float x, float y, float scale, Color color,
                              TextGlitchEffect effect, TtfFontLoader fontLoader) {
        if (mode != Mode.GLITCH) {
            throw new IllegalStateException("Glitch text requires TextRenderer.createGlitch().");
        }
        if (text.isEmpty() || color.getAlpha() == 0 || effect == null) return;

        fontLoader.prepareChars(text);
        emitGlitchLayout(layoutFor(text, fontLoader), x, y, scale, ARGB.toABGR(color.getRGB()), effect);
    }

    @Override
    public void addRotatedText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, float originX, float originY, float rotationDegrees) {
        if (text.isEmpty() || color.getAlpha() == 0) return;

        fontLoader.prepareChars(text);
        emitRotatedLayout(layoutFor(text, fontLoader), x, y, scale, ARGB.toABGR(color.getRGB()), originX, originY, rotationDegrees);
    }

    @Override
    public void addGradientText(String text, float x, float y, float scale, Color startColor, Color endColor, TtfFontLoader fontLoader) {
        if (text.isEmpty() || startColor.getAlpha() == 0 && endColor.getAlpha() == 0) return;

        fontLoader.prepareChars(text);
        TextLayout layout = layoutFor(text, fontLoader);
        float totalWidth = layout.complete ? layout.width : baseWidth(text, fontLoader);
        emitGradientLayout(layout, x, y, scale, totalWidth, startColor.getRGB(), endColor.getRGB());
    }

    private TextLayout layoutFor(String text, TtfFontLoader fontLoader) {
        long revision = fontLoader.getGlyphRevision();
        long atlasRevision = fontLoader.getAtlasRevision();
        LayoutKey key = new LayoutKey(fontLoader, text, fontLoader.getRenderScale());
        TextLayout cached = layoutCache.get(key);
        // 完整布局可跨无关 glyph 加载复用；未完成布局等待 glyphRevision 变化后重建。
        if (cached != null && cached.atlasRevision == atlasRevision && (cached.complete || cached.glyphRevision == revision)) {
            return cached;
        }

        TextLayout layout = buildLayout(text, fontLoader, revision, atlasRevision);
        layoutCache.put(key, layout);
        return layout;
    }

    private TextLayout buildLayout(String text, TtfFontLoader fontLoader, long revision, long atlasRevision) {
        // 坐标统一存 DEFAULT_SCALE 下的基础值，emit 时再乘调用方传入的 scale。
        Map<TtfGlyphAtlas, LayoutRunBuilder> runBuilders = new LinkedHashMap<>();
        float xOffset = 0.0f;
        float yOffset = 0.0f;
        float maxLine = 0.0f;
        float fontScale = fontLoader.getRenderScale();
        float scaledFont = DEFAULT_SCALE * fontScale;
        float ascent = fontLoader.fontFile.pixelAscent * scaledFont;
        float lineHeight = fontLoader.fontFile.fontHeight * scaledFont;
        float spaceWidth = SPACE_WIDTH * fontScale;
        boolean complete = true;
        int glyphCount = 0;

        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            if (codepoint == ' ') {
                xOffset += spaceWidth;
                continue;
            }
            if (codepoint == '\n') {
                maxLine = Math.max(maxLine, xOffset);
                xOffset = 0.0f;
                yOffset += lineHeight;
                continue;
            }

            GlyphDescriptor glyph = fontLoader.getGlyph(codepoint);
            if (glyph == null) {
                // 字形尚未上传（或字体缺失该字形）时用口字形占位框顶上；占位字形同样写入 atlas，
                // complete 保持 false，真实字形上传推进 glyphRevision 后会重建布局换回真实字形。
                complete = false;
                glyph = hasNoInk(codepoint) ? null : fontLoader.getFallbackGlyph(codepoint);
                if (glyph == null) {
                    continue;
                }
            }

            float x1 = xOffset + glyph.xOffset() * scaledFont;
            float x2 = x1 + glyph.width() * scaledFont;
            float y1 = yOffset + ascent + glyph.yOffset() * scaledFont;
            float y2 = y1 + glyph.height() * scaledFont;
            float advance = glyph.advance() * scaledFont + SPACING;

            runBuilders.computeIfAbsent(glyph.atlas(), LayoutRunBuilder::new).add(x1, y1, x2, y2, glyph.uv(), xOffset, xOffset + glyph.advance() * scaledFont);

            xOffset += advance;
            glyphCount++;
        }

        maxLine = Math.max(maxLine, xOffset);

        LayoutRun[] runs = new LayoutRun[runBuilders.size()];
        int index = 0;
        for (LayoutRunBuilder builder : runBuilders.values()) {
            runs[index++] = builder.build();
        }

        return new TextLayout(runs, glyphCount, maxLine, complete, revision, atlasRevision);
    }

    /** 空白、控制、格式与代理码位本身没有墨迹，缺字时不画占位框。 */
    private static boolean hasNoInk(int codepoint) {
        int type = Character.getType(codepoint);
        return Character.isWhitespace(codepoint)
                || type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.SURROGATE;
    }

    private void emitLayout(TextLayout layout, float x, float y, float scale, int argb, float blurRadius) {
        if (layout.glyphCount == 0) return;

        for (LayoutRun run : layout.runs) {
            Batch batch = batchFor(run.atlas, blurRadius);
            long p = batch.beginWrite(run.glyphCount);
            float[] data = run.data;
            for (int i = 0; i < run.glyphCount; i++) {
                if (mode == Mode.FONT_BLUR) {
                    writeFontBlurGlyph(p, x, y, scale, data, i * LAYOUT_FLOATS_PER_GLYPH, argb, blurRadius);
                } else {
                    writeGlyph(p, x, y, scale, data, i * LAYOUT_FLOATS_PER_GLYPH, argb, argb);
                }
                p += glyphBytes;
            }
        }
    }

    private void emitGlitchLayout(TextLayout layout, float x, float y, float scale, int argb, TextGlitchEffect effect) {
        if (layout.glyphCount == 0) return;

        float padding = effect.requiredPadding();
        for (LayoutRun run : layout.runs) {
            Batch batch = batchFor(run.atlas, 0.0f, effect);
            long p = batch.beginWrite(run.glyphCount);
            float[] data = run.data;
            for (int i = 0; i < run.glyphCount; i++) {
                writeEffectGlyph(p, x, y, scale, data, i * LAYOUT_FLOATS_PER_GLYPH, argb, padding);
                p += glyphBytes;
            }
        }
    }

    private void emitRotatedLayout(TextLayout layout, float x, float y, float scale, int argb, float originX, float originY, float rotationDegrees) {
        if (layout.glyphCount == 0) return;

        float radians = (float) Math.toRadians(rotationDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        for (LayoutRun run : layout.runs) {
            Batch batch = batchFor(run.atlas, 0.0f);
            long p = batch.beginWrite(run.glyphCount);
            float[] data = run.data;
            for (int i = 0; i < run.glyphCount; i++) {
                writeRotatedGlyph(p, x, y, scale, data, i * LAYOUT_FLOATS_PER_GLYPH, argb, argb, originX, originY, cos, sin);
                p += glyphBytes;
            }
        }
    }

    private void emitGradientLayout(TextLayout layout, float x, float y, float scale, float width, int startArgb, int endArgb) {
        if (layout.glyphCount == 0) return;

        float totalWidth = Math.max(width, 1.0f);
        for (LayoutRun run : layout.runs) {
            Batch batch = batchFor(run.atlas, 0.0f);
            long p = batch.beginWrite(run.glyphCount);
            float[] data = run.data;
            for (int i = 0; i < run.glyphCount; i++) {
                int base = i * LAYOUT_FLOATS_PER_GLYPH;
                int leftArgb = ARGB.toABGR(ARGB.srgbLerp(clamp01(data[base + 8] / totalWidth), startArgb, endArgb));
                int rightArgb = ARGB.toABGR(ARGB.srgbLerp(clamp01(data[base + 9] / totalWidth), startArgb, endArgb));
                writeGlyph(p, x, y, scale, data, base, leftArgb, rightArgb);
                p += glyphBytes;
            }
        }
    }

    private Batch batchFor(TtfGlyphAtlas atlas, float blurRadius) {
        return batchFor(atlas, blurRadius, null);
    }

    private Batch batchFor(TtfGlyphAtlas atlas, float blurRadius, TextGlitchEffect glitchEffect) {
        BatchKey key = new BatchKey(atlas, blurRadius, glitchEffect);
        return batches.computeIfAbsent(key, k -> new Batch(
                new LuminRingBuffer(bufferSize, GpuBuffer.USAGE_VERTEX), atlas, blurRadius, glitchEffect
        ));
    }

    private static void writeGlyph(long p, float x, float y, float scale, float[] data, int base, int leftArgb, int rightArgb) {
        float x1 = x + data[base] * scale;
        float y1 = y + data[base + 1] * scale;
        float x2 = x + data[base + 2] * scale;
        float y2 = y + data[base + 3] * scale;
        float u0 = data[base + 4];
        float v0 = data[base + 5];
        float u1 = data[base + 6];
        float v1 = data[base + 7];

        BufferUtils.writeUvRectToAddr(p, x1, y1, u0, v0, leftArgb);
        BufferUtils.writeUvRectToAddr(p + STANDARD_STRIDE, x1, y2, u0, v1, leftArgb);
        BufferUtils.writeUvRectToAddr(p + STANDARD_STRIDE * 2L, x2, y2, u1, v1, rightArgb);
        BufferUtils.writeUvRectToAddr(p + STANDARD_STRIDE * 3L, x2, y1, u1, v0, rightArgb);
    }

    private static void writeFontBlurGlyph(long p, float x, float y, float scale, float[] data, int base,
                                           int argb, float blurRadius) {
        writeEffectGlyph(p, x, y, scale, data, base, argb, blurRadius + FONT_BLUR_EXTRA_PADDING);
    }

    private static void writeEffectGlyph(long p, float x, float y, float scale, float[] data, int base,
                                         int argb, float padding) {
        float x1 = x + data[base] * scale;
        float y1 = y + data[base + 1] * scale;
        float x2 = x + data[base + 2] * scale;
        float y2 = y + data[base + 3] * scale;
        float u0 = data[base + 4];
        float v0 = data[base + 5];
        float u1 = data[base + 6];
        float v1 = data[base + 7];
        float uPadding = padding * (u1 - u0) / Math.max(x2 - x1, 0.0001f);
        float vPadding = padding * (v1 - v0) / Math.max(y2 - y1, 0.0001f);

        writeFontBlurVertex(p, x1 - padding, y1 - padding, u0 - uPadding, v0 - vPadding,
                argb, u0, v0, u1, v1);
        writeFontBlurVertex(p + FONT_BLUR_STRIDE, x1 - padding, y2 + padding, u0 - uPadding, v1 + vPadding,
                argb, u0, v0, u1, v1);
        writeFontBlurVertex(p + FONT_BLUR_STRIDE * 2L, x2 + padding, y2 + padding, u1 + uPadding, v1 + vPadding,
                argb, u0, v0, u1, v1);
        writeFontBlurVertex(p + FONT_BLUR_STRIDE * 3L, x2 + padding, y1 - padding, u1 + uPadding, v0 - vPadding,
                argb, u0, v0, u1, v1);
    }

    private static void writeFontBlurVertex(long p, float x, float y, float u, float v, int color,
                                            float u0, float v0, float u1, float v1) {
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4, y);
        MemoryUtil.memPutFloat(p + 8, 0.0f);
        MemoryUtil.memPutFloat(p + 12, u);
        MemoryUtil.memPutFloat(p + 16, v);
        MemoryUtil.memPutInt(p + 20, color);
        MemoryUtil.memPutFloat(p + 24, u0);
        MemoryUtil.memPutFloat(p + 28, v0);
        MemoryUtil.memPutFloat(p + 32, u1);
        MemoryUtil.memPutFloat(p + 36, v1);
    }

    private static void writeRotatedGlyph(long p, float x, float y, float scale, float[] data, int base, int leftArgb, int rightArgb, float originX, float originY, float cos, float sin) {
        float x1 = x + data[base] * scale;
        float y1 = y + data[base + 1] * scale;
        float x2 = x + data[base + 2] * scale;
        float y2 = y + data[base + 3] * scale;
        float u0 = data[base + 4];
        float v0 = data[base + 5];
        float u1 = data[base + 6];
        float v1 = data[base + 7];

        writeRotatedVertex(p, x1, y1, u0, v0, leftArgb, originX, originY, cos, sin);
        writeRotatedVertex(p + STANDARD_STRIDE, x1, y2, u0, v1, leftArgb, originX, originY, cos, sin);
        writeRotatedVertex(p + STANDARD_STRIDE * 2L, x2, y2, u1, v1, rightArgb, originX, originY, cos, sin);
        writeRotatedVertex(p + STANDARD_STRIDE * 3L, x2, y1, u1, v0, rightArgb, originX, originY, cos, sin);
    }

    private static void writeRotatedVertex(long p, float x, float y, float u, float v, int color, float originX, float originY, float cos, float sin) {
        float dx = x - originX;
        float dy = y - originY;
        float rx = originX + dx * cos - dy * sin;
        float ry = originY + dx * sin + dy * cos;
        BufferUtils.writeUvRectToAddr(p, rx, ry, u, v, color);
    }

    private float baseWidth(String text, TtfFontLoader fontLoader) {
        LayoutKey key = new LayoutKey(fontLoader, text, fontLoader.getRenderScale());
        Float cached = widthCache.get(key);
        if (cached != null) {
            return cached;
        }

        float maxLine = 0.0f;
        float currentLine = 0.0f;
        float fontScale = fontLoader.getRenderScale();
        float scaledFont = DEFAULT_SCALE * fontScale;
        float spaceWidth = SPACE_WIDTH * fontScale;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            if (codepoint == ' ') {
                currentLine += spaceWidth;
            } else if (codepoint == '\n') {
                maxLine = Math.max(maxLine, currentLine);
                currentLine = 0.0f;
            } else {
                currentLine += fontLoader.getAdvance(codepoint) * scaledFont + SPACING;
            }
        }

        float width = Math.max(maxLine, currentLine);
        widthCache.put(key, width);
        return width;
    }

    private static float clamp01(float value) {
        return Math.clamp(value, 0.0f, 1.0f);
    }

    @Override
    public void draw() {
        if (batches.isEmpty()) return;

        LuminRenderSystem.applyOrthoProjection();

        GpuTextureView colorView = LuminRenderSystem.resolveColorView();
        GpuTextureView depthView = LuminRenderSystem.resolveDepthView();
        if (colorView == null) return;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return;

        int maxIndexCount = prepareTextBatches();
        if (maxIndexCount == 0) return;

        GpuBufferSlice dynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform();
        GpuBuffer ibo = LuminRenderSystem.getQuadIndexBuffer(maxIndexCount);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Lumin TTF Draws",
                colorView, Optional.empty(),
                depthView, OptionalDouble.empty())
        ) {
            pass.setPipeline(pipeline());
            if (scissorEnabled) {
                ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH);
            }

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicUniforms);
            pass.setIndexBuffer(ibo, LuminRenderSystem.getQuadIndexType());

            drawPrepared(pass);
        }
    }

    @Override
    public boolean prepareSharedDraw() {
        sharedDynamicUniforms = null;
        sharedMaxIndexCount = 0;
        if (batches.isEmpty()) return false;
        if (scissorEnabled && !ScissorUtils.isVisible(scissorW, scissorH)) return false;

        sharedMaxIndexCount = prepareTextBatches();
        if (sharedMaxIndexCount == 0) return false;

        LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount);
        sharedDynamicUniforms = LuminRenderSystem.writeDefaultGuiTransform();
        return sharedDynamicUniforms != null;
    }

    @Override
    public void draw(RenderPass pass) {
        if (sharedDynamicUniforms == null || sharedMaxIndexCount == 0) return;

        GpuBuffer ibo = LuminRenderSystem.getQuadIndexBuffer(sharedMaxIndexCount);
        pass.setIndexBuffer(ibo, LuminRenderSystem.getQuadIndexType());
        pass.setUniform("DynamicTransforms", sharedDynamicUniforms);
        drawPrepared(pass);
    }

    private int prepareTextBatches() {
        int maxIndexCount = 0;
        for (Batch batch : batches.values()) {
            if (batch.offsetInAtlas == 0) continue;
            if (batch.buffer.isMapped()) {
                batch.buffer.unmap();
                batch.mappedAddress = 0L;
            }

            int vertexCount = (int) (batch.offsetInAtlas / stride);
            maxIndexCount = Math.max(maxIndexCount, (vertexCount / 4) * 6);
            if (mode == Mode.FONT_BLUR) {
                batch.blurUniforms = LuminRenderSystem.writeDynamicUniform(
                        "font_blur_uniforms",
                        "Lumin Font Blur UBO",
                        FONT_BLUR_UNIFORM_SIZE,
                        16,
                        new FontBlurUniforms(batch.blurRadius)
                );
            } else if (mode == Mode.GLITCH) {
                batch.glitchUniforms = LuminRenderSystem.writeDynamicUniform(
                        "font_glitch_uniforms",
                        "Lumin Font Glitch UBO",
                        64,
                        16,
                        new GlitchUniforms(batch.glitchEffect, glitchTime())
                );
            }
        }
        return maxIndexCount;
    }

    private void drawPrepared(RenderPass pass) {
        if (scissorEnabled) {
            if (!ScissorUtils.enableScissor(pass, scissorX, scissorY, scissorW, scissorH)) {
                return;
            }
        } else {
            pass.disableScissor();
        }

        // 不同 atlas 共享同一字体 pipeline，在同一个 pass 内只切换纹理并连续 draw。
        for (Batch batch : batches.values()) {
            TtfGlyphAtlas atlas = batch.atlas;

            if (batch.offsetInAtlas == 0) continue;

            int vertexCount = (int) (batch.offsetInAtlas / stride);
            int indexCount = (vertexCount / 4) * 6;

            pass.setVertexBuffer(0, batch.buffer.getGpuBuffer().slice());
            LuminTexture fontTexture = mode == Mode.STANDARD ? atlas.getTexture() : atlas.getAlphaTexture();
            pass.bindTexture("Sampler0", fontTexture.getTextureView(), fontTexture.getSampler());
            if (mode == Mode.FONT_BLUR) {
                pass.setUniform("FontBlurUniforms", batch.blurUniforms);
            } else if (mode == Mode.GLITCH) {
                pass.setUniform("GlitchData", batch.glitchUniforms);
            }

            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        }
    }

    @Override
    public void clear() {
        for (Batch batch : batches.values()) {
            if (batch.offsetInAtlas > 0) {
                if (batch.buffer.isMapped()) {
                    batch.buffer.unmap();
                    batch.mappedAddress = 0L;
                }
                batch.buffer.rotate();
            }
            batch.offsetInAtlas = 0;
            batch.blurUniforms = null;
            batch.glitchUniforms = null;
        }
        sharedDynamicUniforms = null;
        sharedMaxIndexCount = 0;
    }

    @Override
    public void close() {
        clear();
        for (Batch batch : batches.values()) {
            batch.buffer.close();
        }
        batches.clear();
        layoutCache.clear();
        widthCache.clear();
    }

    @Override
    public float getHeight(float scale, TtfFontLoader fontLoader) {
        return fontLoader.fontFile.fontHeight * DEFAULT_SCALE * fontLoader.getRenderScale() * scale;
    }

    @Override
    public float getWidth(String text, float scale, TtfFontLoader fontLoader) {
        if (text.isEmpty()) return 0.0f;
        return baseWidth(text, fontLoader) * scale;
    }

    @Override
    public void setScissor(int x, int y, int width, int height) {
        LuminRenderSystem.ScissorRect scissor = ScissorUtils.clampFramebufferScissor(x, y, width, height);
        scissorEnabled = true;
        scissorX = scissor.x();
        scissorY = scissor.y();
        scissorW = scissor.width();
        scissorH = scissor.height();
    }

    @Override
    public void clearScissor() {
        scissorEnabled = false;
    }

    private final class Batch {
        final LuminRingBuffer buffer;
        final TtfGlyphAtlas atlas;
        final float blurRadius;
        final TextGlitchEffect glitchEffect;
        long offsetInAtlas = 0;
        long mappedAddress = 0L;
        GpuBufferSlice blurUniforms;
        GpuBufferSlice glitchUniforms;

        private Batch(LuminRingBuffer buffer, TtfGlyphAtlas atlas, float blurRadius, TextGlitchEffect glitchEffect) {
            this.buffer = buffer;
            this.atlas = atlas;
            this.blurRadius = blurRadius;
            this.glitchEffect = glitchEffect;
        }

        private long beginWrite(int glyphCount) {
            long start = offsetInAtlas;
            long requiredBytes = start + glyphCount * glyphBytes;
            buffer.ensureCapacity(requiredBytes);

            // 同一 atlas run 只映射一次，后续 glyph 直接顺序写入 mapped memory。
            if (!buffer.isMapped()) {
                buffer.tryMap();
            }
            // 扩容会解除旧映射并创建新的映射，必须在扩容后重新获取地址。
            mappedAddress = MemoryUtil.memAddress(buffer.getMappedBuffer());

            offsetInAtlas = requiredBytes;
            return mappedAddress + start;
        }
    }

    private record BatchKey(TtfGlyphAtlas atlas, float blurRadius, TextGlitchEffect glitchEffect) {
    }

    private record FontBlurUniforms(float blurRadius) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer).putVec4(blurRadius, 0.0f, 0.0f, 0.0f);
        }
    }

    private record GlitchUniforms(TextGlitchEffect effect, float time) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Color color = effect.neonColor();
            Std140Builder.intoBuffer(buffer)
                    .putVec4(effect.chromaticX(), effect.chromaticY(), effect.glowRadius(), effect.glowIntensity())
                    .putVec4(effect.sliceHeight(), effect.sliceAmount(), effect.glitchStrength(), effect.scanlineStrength())
                    .putVec4(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, effect.noiseStrength())
                    .putVec4(time, 0.0f, 0.0f, 0.0f);
        }
    }

    private RenderPipeline pipeline() {
        if (mode == Mode.FONT_BLUR) {
            return LuminRenderPipelines.TTF_FONT_BLUR;
        } else if (mode == Mode.GLITCH) {
            return LuminRenderPipelines.TTF_FONT_GLITCH;
        } else {
            return ClientSetting.INSTANCE.fontAntiAliasing.getValue() ? LuminRenderPipelines.TTF_FONT_AA : LuminRenderPipelines.TTF_FONT_NO_AA;
        }
    }

    private static float glitchTime() {
        return (System.nanoTime() % 1_024_000_000_000L) / 1_000_000_000.0f;
    }

    private record LayoutKey(TtfFontLoader fontLoader, String text, float renderScale) {
    }

    private static final class TextLayout {
        final LayoutRun[] runs;
        final int glyphCount;
        final float width;
        final boolean complete;
        final long glyphRevision;
        final long atlasRevision;

        private TextLayout(LayoutRun[] runs, int glyphCount, float width, boolean complete, long glyphRevision, long atlasRevision) {
            this.runs = runs;
            this.glyphCount = glyphCount;
            this.width = width;
            this.complete = complete;
            this.glyphRevision = glyphRevision;
            this.atlasRevision = atlasRevision;
        }
    }

    private static final class LayoutRun {
        final TtfGlyphAtlas atlas;
        final float[] data;
        final int glyphCount;

        private LayoutRun(TtfGlyphAtlas atlas, float[] data, int glyphCount) {
            this.atlas = atlas;
            this.data = data;
            this.glyphCount = glyphCount;
        }
    }

    private static final class LayoutRunBuilder {
        private final TtfGlyphAtlas atlas;
        private float[] data = new float[LAYOUT_FLOATS_PER_GLYPH * 16];
        private int glyphCount;

        private LayoutRunBuilder(TtfGlyphAtlas atlas) {
            this.atlas = atlas;
        }

        private void add(float x1, float y1, float x2, float y2, TtfGlyphAtlas.GlyphUV uv, float gradientLeft, float gradientRight) {
            ensureCapacity(glyphCount + 1);
            int base = glyphCount * LAYOUT_FLOATS_PER_GLYPH;
            data[base] = x1;
            data[base + 1] = y1;
            data[base + 2] = x2;
            data[base + 3] = y2;
            data[base + 4] = uv.u0();
            data[base + 5] = uv.v0();
            data[base + 6] = uv.u1();
            data[base + 7] = uv.v1();
            data[base + 8] = gradientLeft;
            data[base + 9] = gradientRight;
            glyphCount++;
        }

        private void ensureCapacity(int targetGlyphCount) {
            int required = targetGlyphCount * LAYOUT_FLOATS_PER_GLYPH;
            if (required <= data.length) {
                return;
            }
            data = Arrays.copyOf(data, Math.max(required, data.length * 2));
        }

        private LayoutRun build() {
            int used = glyphCount * LAYOUT_FLOATS_PER_GLYPH;
            return new LayoutRun(atlas, used == data.length ? data : Arrays.copyOf(data, used), glyphCount);
        }
    }

}
