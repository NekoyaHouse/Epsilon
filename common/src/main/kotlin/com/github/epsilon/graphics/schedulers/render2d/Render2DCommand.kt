package com.github.epsilon.graphics.schedulers.render2d

import com.github.epsilon.graphics.text.TextGlitchEffect
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import java.awt.Color

sealed interface Render2DCommand {

    val layer: Int

    val sequence: Long

    val bounds: Render2DBounds

    val scissor: Render2DScissor?

    fun kind(): Render2DCommandKind

    fun orderingBounds(): Render2DBounds = bounds

    @JvmRecord
    data class Shadow(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val radiusTopLeft: Float,
        val radiusTopRight: Float,
        val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val blurRadius: Float,
        val color: Color,
    ) : Render2DCommand {

        override fun orderingBounds(): Render2DBounds {
            val pad = Math.max(0.0f, blurRadius)
            return Render2DBounds.of(
                bounds.x - pad, bounds.y - pad,
                bounds.width + pad * 2.0f, bounds.height + pad * 2.0f
            )
        }

        override fun kind(): Render2DCommandKind = Render2DCommandKind.SHADOW

    }

    @JvmRecord
    data class SegmentedShadow(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val radiusTopLeft: Float,
        val radiusTopRight: Float,
        val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val blurRadius: Float,
        val color: Color,
        val segmentRects: FloatArray,
        val segmentRadii: FloatArray,
        val segmentColors: FloatArray?,
        val segmentCount: Int,
    ) : Render2DCommand {

        override fun orderingBounds(): Render2DBounds {
            val pad = Math.max(0.0f, blurRadius)
            return Render2DBounds.of(
                bounds.x - pad, bounds.y - pad,
                bounds.width + pad * 2.0f, bounds.height + pad * 2.0f
            )
        }

        override fun kind(): Render2DCommandKind = Render2DCommandKind.SHADOW

    }

    @JvmRecord
    data class RoundRect(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val radiusTopLeft: Float,
        val radiusTopRight: Float,
        val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val topLeft: Color,
        val bottomLeft: Color,
        val bottomRight: Color,
        val topRight: Color,
    ) : Render2DCommand {

        override fun kind(): Render2DCommandKind = Render2DCommandKind.ROUND_RECT

    }

    @JvmRecord
    data class RoundRectOutline(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val radiusTopLeft: Float,
        val radiusTopRight: Float,
        val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val outlineWidth: Float,
        val color: Color,
    ) : Render2DCommand {

        override fun orderingBounds(): Render2DBounds {
            val pad = Math.max(0.0f, outlineWidth * 0.5f)
            return Render2DBounds.of(
                bounds.x - pad, bounds.y - pad,
                bounds.width + pad * 2.0f, bounds.height + pad * 2.0f
            )
        }

        override fun kind(): Render2DCommandKind = Render2DCommandKind.ROUND_RECT_OUTLINE

    }

    @JvmRecord
    data class Rect(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val topLeft: Color,
        val bottomLeft: Color,
        val bottomRight: Color,
        val topRight: Color,
    ) : Render2DCommand {

        override fun kind(): Render2DCommandKind = Render2DCommandKind.RECT

    }

    @JvmRecord
    data class Triangle(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val centerX: Float,
        val centerY: Float,
        val size: Float,
        val progress: Float,
        val color: Color,
    ) : Render2DCommand {

        override fun kind(): Render2DCommandKind = Render2DCommandKind.TRIANGLE

    }

    @JvmRecord
    data class Arc(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val strokeWidth: Float,
        val startDegrees: Float,
        val sweepDegrees: Float,
        val roundCap: Boolean,
        val gradientRotationDegrees: Float,
        val startColor: Color,
        val middleColor: Color,
        val endColor: Color,
    ) : Render2DCommand {

        override fun kind(): Render2DCommandKind = Render2DCommandKind.ARC

    }

    @JvmRecord
    data class Texture(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val texture: Render2DTexture,
        val radiusTopLeft: Float,
        val radiusTopRight: Float,
        val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
        val color: Color,
        val originX: Float,
        val originY: Float,
        val rotationDegrees: Float,
    ) : Render2DCommand {

        override fun kind(): Render2DCommandKind = Render2DCommandKind.TEXTURE

    }

    @JvmRecord
    data class Text(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val text: String,
        val x: Float,
        val y: Float,
        val scale: Float,
        val color: Color,
        val fontLoader: TtfFontLoader?,
        val originX: Float,
        val originY: Float,
        val rotationDegrees: Float,
    ) : Render2DCommand {

        override fun kind(): Render2DCommandKind = Render2DCommandKind.TEXT

    }

    @JvmRecord
    data class GradientText(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val text: String,
        val x: Float,
        val y: Float,
        val scale: Float,
        val startColor: Color,
        val endColor: Color,
        val fontLoader: TtfFontLoader?,
    ) : Render2DCommand {

        override fun kind(): Render2DCommandKind = Render2DCommandKind.TEXT

    }

    @JvmRecord
    data class BlurText(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val text: String,
        val x: Float,
        val y: Float,
        val scale: Float,
        val color: Color,
        val blurRadius: Float,
        val intensity: Int,
        val fontLoader: TtfFontLoader?,
    ) : Render2DCommand {

        override fun kind(): Render2DCommandKind = Render2DCommandKind.BLUR_TEXT

    }

    @JvmRecord
    data class GlitchText(
        override val layer: Int,
        override val sequence: Long,
        override val bounds: Render2DBounds,
        override val scissor: Render2DScissor?,
        val text: String,
        val x: Float,
        val y: Float,
        val scale: Float,
        val color: Color,
        val effect: TextGlitchEffect,
        val fontLoader: TtfFontLoader?,
    ) : Render2DCommand {

        override fun kind(): Render2DCommandKind = Render2DCommandKind.GLITCH_TEXT

    }

}
