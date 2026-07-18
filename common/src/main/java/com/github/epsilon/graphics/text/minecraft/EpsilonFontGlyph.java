package com.github.epsilon.graphics.text.minecraft;

import com.github.epsilon.graphics.LuminRenderPipelines;
import com.github.epsilon.graphics.text.GlyphDescriptor;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.graphics.text.ttf.TtfGlyphAtlas;
import com.github.epsilon.modules.impl.ClientSetting;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public final class EpsilonFontGlyph implements BakedGlyph {

    private static final float SHADOW_OFFSET = 0.45f;
    private static final float BOLD_OFFSET = 0.45f;

    private static final Map<TtfGlyphAtlas, RenderType> AA_RENDER_TYPES = new IdentityHashMap<>();
    private static final Map<TtfGlyphAtlas, RenderType> NO_AA_RENDER_TYPES = new IdentityHashMap<>();

    private final int codepoint;
    private final TtfFontLoader font;
    private final @Nullable GlyphDescriptor descriptor;
    private final GlyphInfo info;

    private EpsilonFontGlyph(int codepoint, TtfFontLoader font, @Nullable GlyphDescriptor descriptor) {
        this.codepoint = codepoint;
        this.font = font;
        this.descriptor = descriptor;
        this.info = new EpsilonGlyphInfo(EpsilonFontMetrics.advance(codepoint, Style.EMPTY, font));
    }

    public static @Nullable EpsilonFontGlyph create(int codepoint) {
        TtfFontLoader font = EpsilonFontMetrics.font();
        if (font == null) {
            return null;
        }

        if (Character.isWhitespace(codepoint)) {
            return new EpsilonFontGlyph(codepoint, font, null);
        }
        if (codepoint < Character.MIN_CODE_POINT || codepoint > Character.MAX_CODE_POINT) {
            return null;
        }

        font.requestChars(new String(Character.toChars(codepoint)));
        GlyphDescriptor descriptor = font.getGlyph(codepoint);
        return descriptor != null ? new EpsilonFontGlyph(codepoint, font, descriptor) : null;
    }

    @Override
    public GlyphInfo info() {
        return this.info;
    }

    @Override
    public TextRenderable.@Nullable Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float boldOffset, float shadowOffset) {
        if (this.descriptor == null) {
            return null;
        }
        return new GlyphInstance(this, x, y, color, shadowColor, style, boldOffset, shadowOffset);
    }

    private RenderType renderType() {
        if (this.descriptor == null) {
            throw new IllegalStateException("Whitespace glyphs do not have render types");
        }
        Map<TtfGlyphAtlas, RenderType> renderTypes = ClientSetting.INSTANCE.fontAntiAliasing.getValue() ? AA_RENDER_TYPES : NO_AA_RENDER_TYPES;
        RenderPipeline pipeline = ClientSetting.INSTANCE.fontAntiAliasing.getValue()
                ? LuminRenderPipelines.TTF_FONT_AA
                : LuminRenderPipelines.TTF_FONT_NO_AA;
        String name = ClientSetting.INSTANCE.fontAntiAliasing.getValue() ? "epsilon_ttf_text_aa" : "epsilon_ttf_text_no_aa";
        return renderTypes.computeIfAbsent(this.descriptor.atlas(), atlas -> RenderType.create(
                name,
                RenderSetup.builder(pipeline)
                        .withTexture("Sampler0", atlas.getTextureId(), () -> atlas.getTexture().getSampler())
                        .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                        .createRenderSetup()
        ));
    }

    private float baselineY(float y) {
        return y + this.font.fontFile.pixelAscent * scale();
    }

    private float scale() {
        return EpsilonFontMetrics.minecraftScale(this.font);
    }

    private float left(float x, boolean bold, boolean italic) {
        if (this.descriptor == null) {
            return x;
        }
        float left = x + this.descriptor.xOffset() * scale();
        if (italic) left += Math.min(italicShearTop(yTop(0.0f)), italicShearBottom(yBottom(0.0f)));
        if (bold) left -= extraThickness(true);
        return left;
    }

    private static float extraThickness(boolean bold) {
        return bold ? 0.06f : 0.0f;
    }

    private float top(float y) {
        if (this.descriptor == null) {
            return y;
        }
        return yTop(y);
    }

    private float right(float x, boolean hasShadow, float shadowOffset, boolean bold, boolean italic) {
        if (this.descriptor == null) {
            return x + this.info.getAdvance(bold);
        }
        float right = x + this.descriptor.xOffset() * scale() + this.descriptor.width() * scale();
        if (hasShadow) right += shadowOffset;
        if (bold) right += extraThickness(true);
        if (italic) right += Math.max(italicShearTop(yTop(0.0f)), italicShearBottom(yBottom(0.0f)));
        return right;
    }

    private float bottom(float y, boolean hasShadow, float shadowOffset, boolean bold) {
        float bottom = yBottom(y);
        if (hasShadow) bottom += shadowOffset;
        if (bold) bottom += extraThickness(true);
        return bottom;
    }

    private float yTop(float y) {
        return baselineY(y) + this.descriptor.yOffset() * scale();
    }

    private float yBottom(float y) {
        return yTop(y) + this.descriptor.height() * scale();
    }

    private static float italicShearTop(float glyphTopRelativeToTextY) {
        return 1.0f - 0.25f * glyphTopRelativeToTextY;
    }

    private static float italicShearBottom(float glyphBottomRelativeToTextY) {
        return 1.0f - 0.25f * glyphBottomRelativeToTextY;
    }

    private void renderGlyph(Matrix4fc pose, VertexConsumer buffer, GlyphInstance instance, float offsetX, float offsetY, float z, int color, boolean bold) {
        if (this.descriptor == null) {
            return;
        }
        float x0 = instance.x + this.descriptor.xOffset() * scale() + offsetX;
        float x1 = x0 + this.descriptor.width() * scale();
        float y0 = top(instance.y) + offsetY;
        float y1 = y0 + this.descriptor.height() * scale();
        float extraThickness = extraThickness(bold);

        float shearTop = instance.style.isItalic() ? italicShearTop(y0 - instance.y) : 0.0f;
        float shearBottom = instance.style.isItalic() ? italicShearBottom(y1 - instance.y) : 0.0f;

        TtfGlyphAtlas.GlyphUV uv = this.descriptor.uv();
        buffer.addVertex(pose, x0 + shearTop - extraThickness, y0 - extraThickness, z).setUv(uv.u0(), uv.v0()).setColor(color);
        buffer.addVertex(pose, x0 + shearBottom - extraThickness, y1 + extraThickness, z).setUv(uv.u0(), uv.v1()).setColor(color);
        buffer.addVertex(pose, x1 + shearBottom + extraThickness, y1 + extraThickness, z).setUv(uv.u1(), uv.v1()).setColor(color);
        buffer.addVertex(pose, x1 + shearTop + extraThickness, y0 - extraThickness, z).setUv(uv.u1(), uv.v0()).setColor(color);
    }

    private record GlyphInstance(
            EpsilonFontGlyph glyph,
            float x,
            float y,
            int color,
            int shadowColor,
            Style style,
            float boldOffset,
            float shadowOffset
    ) implements TextRenderable.Styled, EpsilonTextRenderable {

        private boolean hasShadow() {
            return this.shadowColor != 0;
        }

        @Override
        public void render(Matrix4fc pose, VertexConsumer buffer, int packedLightCoords, boolean flat) {
            float depth;
            if (this.hasShadow()) {
                this.glyph.renderGlyph(pose, buffer, this, this.shadowOffset, this.shadowOffset, 0.0f, this.shadowColor, this.style.isBold());
                depth = flat ? 0.0f : Font.SHADOW_DEPTH;
            } else {
                depth = 0.0f;
            }

            this.glyph.renderGlyph(pose, buffer, this, 0.0f, 0.0f, depth, this.color, this.style.isBold());
            if (this.style.isBold()) {
                this.glyph.renderGlyph(pose, buffer, this, this.boldOffset, 0.0f, depth + (flat ? 0.0f : 0.001f), this.color, true);
            }
        }

        @Override
        public RenderType renderType(Font.DisplayMode displayMode) {
            return this.glyph.renderType();
        }

        @Override
        public GpuTextureView textureView() {
            if (this.glyph.descriptor == null) {
                throw new IllegalStateException("Whitespace glyphs do not have textures");
            }
            return this.glyph.descriptor.atlas().getTexture().getTextureView();
        }

        @Override
        public GpuSampler epsilon$sampler() {
            if (this.glyph.descriptor == null) {
                throw new IllegalStateException("Whitespace glyphs do not have samplers");
            }
            return this.glyph.descriptor.atlas().getTexture().getSampler();
        }

        @Override
        public RenderPipeline guiPipeline() {
            return ClientSetting.INSTANCE.fontAntiAliasing.getValue()
                    ? LuminRenderPipelines.TTF_FONT_AA
                    : LuminRenderPipelines.TTF_FONT_NO_AA;
        }

        @Override
        public float left() {
            return this.glyph.left(this.x, this.style.isBold(), this.style.isItalic());
        }

        @Override
        public float top() {
            return this.glyph.top(this.y);
        }

        @Override
        public float right() {
            return this.glyph.right(this.x, this.hasShadow(), this.shadowOffset, this.style.isBold(), this.style.isItalic());
        }

        @Override
        public float activeRight() {
            return this.x + this.glyph.info.getAdvance(this.style.isBold());
        }

        @Override
        public float bottom() {
            return this.glyph.bottom(this.y, this.hasShadow(), this.shadowOffset, this.style.isBold());
        }
    }

    private record EpsilonGlyphInfo(float advance) implements GlyphInfo {
        @Override
        public float getAdvance() {
            return this.advance;
        }

        @Override
        public float getBoldOffset() {
            return BOLD_OFFSET;
        }

        @Override
        public float getShadowOffset() {
            return SHADOW_OFFSET;
        }
    }
}
