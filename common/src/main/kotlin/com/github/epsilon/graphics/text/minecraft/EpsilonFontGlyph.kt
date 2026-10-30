package com.github.epsilon.graphics.text.minecraft

import com.github.epsilon.graphics.LuminRenderPipelines
import com.github.epsilon.graphics.text.GlyphDescriptor
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.graphics.text.ttf.TtfGlyphAtlas
import com.github.epsilon.modules.impl.ClientSetting
import com.mojang.blaze3d.font.GlyphInfo
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.TextRenderable
import net.minecraft.client.gui.font.glyphs.BakedGlyph
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.network.chat.Style
import org.joml.Matrix4fc
import java.util.IdentityHashMap
import java.util.function.Supplier

// 原 Java 版本的 private static final 常量；放在文件级私有时，
// 嵌套 record 也能直接访问，不需要经过 companion 作用域。
private const val SHADOW_OFFSET = 0.45f
private const val BOLD_OFFSET = 0.45f

private fun extraThickness(bold: Boolean): Float {
    return if (bold) 0.06f else 0.0f
}

private fun italicShearTop(glyphTopRelativeToTextY: Float): Float {
    return 1.0f - 0.25f * glyphTopRelativeToTextY
}

private fun italicShearBottom(glyphBottomRelativeToTextY: Float): Float {
    return 1.0f - 0.25f * glyphBottomRelativeToTextY
}

class EpsilonFontGlyph private constructor(
    codepoint: Int,
    private val font: TtfFontLoader,
    private val descriptor: GlyphDescriptor?
) : BakedGlyph {

    // record 组件名 info 与 BakedGlyph.info() 冲突，改名为 glyphInfo，方法名保持 info()。
    private val glyphInfo: GlyphInfo = EpsilonGlyphInfo(EpsilonFontMetrics.advance(codepoint, Style.EMPTY, font))

    override fun info(): GlyphInfo {
        return this.glyphInfo
    }

    override fun createGlyph(
        x: Float,
        y: Float,
        color: Int,
        shadowColor: Int,
        style: Style,
        boldOffset: Float,
        shadowOffset: Float
    ): TextRenderable.Styled? {
        if (this.descriptor == null) {
            return null
        }
        return GlyphInstance(this, x, y, color, shadowColor, style, boldOffset, shadowOffset)
    }

    private fun renderType(): RenderType {
        val descriptor = this.descriptor
            ?: throw IllegalStateException("Whitespace glyphs do not have render types")
        val antiAliasing = ClientSetting.INSTANCE.fontAntiAliasing.getValue()
        val renderTypes = if (antiAliasing) AA_RENDER_TYPES else NO_AA_RENDER_TYPES
        val pipeline = if (antiAliasing) LuminRenderPipelines.TTF_FONT_AA else LuminRenderPipelines.TTF_FONT_NO_AA
        val name = if (antiAliasing) "epsilon_ttf_text_aa" else "epsilon_ttf_text_no_aa"
        return renderTypes.getOrPut(descriptor.atlas) {
            RenderType.create(
                name,
                RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", descriptor.atlas.getTextureId(), Supplier { descriptor.atlas.getTexture().getSampler() })
                    .createRenderSetup()
            )
        }
    }

    private fun baselineY(y: Float): Float {
        return y + EpsilonFontMetrics.MINECRAFT_BASELINE
    }

    private fun scale(): Float {
        return EpsilonFontMetrics.minecraftScale(this.font)
    }

    private fun left(x: Float, bold: Boolean, italic: Boolean): Float {
        val descriptor = this.descriptor ?: return x
        var left = x + descriptor.xOffset * scale()
        if (italic) left += Math.min(italicShearTop(yTop(0.0f)), italicShearBottom(yBottom(0.0f)))
        if (bold) left -= extraThickness(true)
        return left
    }

    private fun top(y: Float): Float {
        if (this.descriptor == null) {
            return y
        }
        return yTop(y)
    }

    private fun right(x: Float, hasShadow: Boolean, shadowOffset: Float, bold: Boolean, italic: Boolean): Float {
        val descriptor = this.descriptor ?: return x + this.glyphInfo.getAdvance(bold)
        var right = x + descriptor.xOffset * scale() + descriptor.width * scale()
        if (hasShadow) right += shadowOffset
        if (bold) right += extraThickness(true)
        if (italic) right += Math.max(italicShearTop(yTop(0.0f)), italicShearBottom(yBottom(0.0f)))
        return right
    }

    private fun bottom(y: Float, hasShadow: Boolean, shadowOffset: Float, bold: Boolean): Float {
        var bottom = yBottom(y)
        if (hasShadow) bottom += shadowOffset
        if (bold) bottom += extraThickness(true)
        return bottom
    }

    private fun yTop(y: Float): Float {
        return baselineY(y) + this.descriptor!!.yOffset * scale()
    }

    private fun yBottom(y: Float): Float {
        return yTop(y) + this.descriptor!!.height * scale()
    }

    private fun renderGlyph(
        pose: Matrix4fc,
        buffer: VertexConsumer,
        instance: GlyphInstance,
        offsetX: Float,
        offsetY: Float,
        z: Float,
        color: Int,
        bold: Boolean
    ) {
        val descriptor = this.descriptor ?: return
        val x0 = instance.x + descriptor.xOffset * scale() + offsetX
        val x1 = x0 + descriptor.width * scale()
        val y0 = top(instance.y) + offsetY
        val y1 = y0 + descriptor.height * scale()
        val thickness = extraThickness(bold)

        val shearTop = if (instance.glyphStyle.isItalic) italicShearTop(y0 - instance.y) else 0.0f
        val shearBottom = if (instance.glyphStyle.isItalic) italicShearBottom(y1 - instance.y) else 0.0f

        val uv = descriptor.uv
        buffer.addVertex(pose, x0 + shearTop - thickness, y0 - thickness, z).setUv(uv.u0, uv.v0).setColor(color)
        buffer.addVertex(pose, x0 + shearBottom - thickness, y1 + thickness, z).setUv(uv.u0, uv.v1).setColor(color)
        buffer.addVertex(pose, x1 + shearBottom + thickness, y1 + thickness, z).setUv(uv.u1, uv.v1).setColor(color)
        buffer.addVertex(pose, x1 + shearTop + thickness, y0 - thickness, z).setUv(uv.u1, uv.v0).setColor(color)
    }

    // record 组件名 style 与 TextRenderable.Styled.style() 冲突，
    // 因此组件改名为 glyphStyle，并用 style() 显式实现接口方法。
    @JvmRecord
    private data class GlyphInstance(
        val glyph: EpsilonFontGlyph,
        val x: Float,
        val y: Float,
        val color: Int,
        val shadowColor: Int,
        val glyphStyle: Style,
        val boldOffset: Float,
        val shadowOffset: Float
    ) : TextRenderable.Styled, EpsilonTextRenderable {

        private fun hasShadow(): Boolean {
            return this.shadowColor != 0
        }

        override fun render(pose: Matrix4fc, buffer: VertexConsumer, packedLightCoords: Int, flat: Boolean) {
            val depth: Float
            if (this.hasShadow()) {
                this.glyph.renderGlyph(pose, buffer, this, this.shadowOffset, this.shadowOffset, 0.0f, this.shadowColor, this.glyphStyle.isBold)
                depth = if (flat) 0.0f else Font.SHADOW_DEPTH
            } else {
                depth = 0.0f
            }

            this.glyph.renderGlyph(pose, buffer, this, 0.0f, 0.0f, depth, this.color, this.glyphStyle.isBold)
            if (this.glyphStyle.isBold) {
                this.glyph.renderGlyph(pose, buffer, this, this.boldOffset, 0.0f, depth + (if (flat) 0.0f else 0.001f), this.color, true)
            }
        }

        override fun renderType(displayMode: Font.DisplayMode): RenderType {
            return this.glyph.renderType()
        }

        override fun textureView(): GpuTextureView {
            val descriptor = this.glyph.descriptor
                ?: throw IllegalStateException("Whitespace glyphs do not have textures")
            return descriptor.atlas.getTexture().getTextureView()
        }

        override fun `epsilon$sampler`(): GpuSampler {
            val descriptor = this.glyph.descriptor
                ?: throw IllegalStateException("Whitespace glyphs do not have samplers")
            return descriptor.atlas.getTexture().getSampler()
        }

        override fun guiPipeline(): RenderPipeline {
            return if (ClientSetting.INSTANCE.fontAntiAliasing.getValue()) {
                LuminRenderPipelines.TTF_FONT_AA
            } else {
                LuminRenderPipelines.TTF_FONT_NO_AA
            }
        }

        override fun style(): Style {
            return this.glyphStyle
        }

        override fun left(): Float {
            return this.glyph.left(this.x, this.glyphStyle.isBold, this.glyphStyle.isItalic)
        }

        override fun top(): Float {
            return this.glyph.top(this.y)
        }

        override fun right(): Float {
            return this.glyph.right(this.x, this.hasShadow(), this.shadowOffset, this.glyphStyle.isBold, this.glyphStyle.isItalic)
        }

        override fun activeRight(): Float {
            return this.x + this.glyph.glyphInfo.getAdvance(this.glyphStyle.isBold)
        }

        override fun bottom(): Float {
            return this.glyph.bottom(this.y, this.hasShadow(), this.shadowOffset, this.glyphStyle.isBold)
        }
    }

    @JvmRecord
    private data class EpsilonGlyphInfo(val advance: Float) : GlyphInfo {

        override fun getAdvance(): Float {
            return this.advance
        }

        override fun getBoldOffset(): Float {
            return BOLD_OFFSET
        }

        override fun getShadowOffset(): Float {
            return SHADOW_OFFSET
        }
    }

    companion object {

        private val AA_RENDER_TYPES: MutableMap<TtfGlyphAtlas, RenderType> = IdentityHashMap()
        private val NO_AA_RENDER_TYPES: MutableMap<TtfGlyphAtlas, RenderType> = IdentityHashMap()

        @JvmStatic
        fun create(codepoint: Int): EpsilonFontGlyph? {
            val font = EpsilonFontMetrics.font() ?: return null

            if (Character.isWhitespace(codepoint)) {
                return EpsilonFontGlyph(codepoint, font, null)
            }
            if (codepoint < Character.MIN_CODE_POINT || codepoint > Character.MAX_CODE_POINT) {
                return null
            }

            font.requestChars(String(Character.toChars(codepoint)))
            val descriptor = font.getGlyph(codepoint)
            return if (descriptor != null) EpsilonFontGlyph(codepoint, font, descriptor) else null
        }
    }
}
