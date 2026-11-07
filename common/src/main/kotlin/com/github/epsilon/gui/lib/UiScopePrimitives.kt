package com.github.epsilon.gui.lib

import com.github.epsilon.graphics.LuminTexture
import com.github.epsilon.graphics.schedulers.render2d.Render2DTexture
import com.github.epsilon.graphics.text.TextGlitchEffect
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import java.awt.Color

/*
 * UiScope 图元扩展。
 *
 * 这些函数只做「转发到 UiTree.Scope」一件事：坐标保留局部语义（相对 UiScope.origin），
 * layer 由作用域携带，因此签名里既没有 layer 也没有 scope。命名遵循 Compose 规范：
 * 发射 UI 的函数使用 PascalCase。
 */

// ---------------------------------------------------------------------------
// 阴影
// ---------------------------------------------------------------------------

fun UiScope.Shadow(
    x: Float, y: Float, width: Float, height: Float,
    radius: Float, blurRadius: Float, color: Color,
) {
    tree.shadow(x, y, width, height, radius, blurRadius, color)
}

fun UiScope.Shadow(
    x: Float, y: Float, width: Float, height: Float,
    radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
    blurRadius: Float, color: Color,
) {
    tree.shadow(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, blurRadius, color)
}

/**
 * 分段阴影：一次提交多行矩形的阴影，避免逐行生成节点。
 *
 * [segmentRects] 是相对坐标下的 `x, y, width, height` 序列。
 */
fun UiScope.SegmentedShadow(
    x: Float, y: Float, width: Float, height: Float, radius: Float, blurRadius: Float, color: Color,
    segmentRects: FloatArray?, segmentRadii: FloatArray?, segmentColors: FloatArray?, segmentCount: Int,
) {
    tree.shadow(x, y, width, height, radius, blurRadius, color, segmentRects, segmentRadii, segmentColors, segmentCount)
}

// ---------------------------------------------------------------------------
// 圆角矩形
// ---------------------------------------------------------------------------

fun UiScope.RoundRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Color) {
    tree.roundRect(x, y, width, height, radius, color)
}

fun UiScope.RoundRect(
    x: Float, y: Float, width: Float, height: Float,
    radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
    color: Color,
) {
    tree.roundRect(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color)
}

fun UiScope.RoundRectGradient(
    x: Float, y: Float, width: Float, height: Float, radius: Float,
    topLeft: Color, bottomLeft: Color, bottomRight: Color, topRight: Color,
) {
    tree.roundRectGradient(x, y, width, height, radius, topLeft, bottomLeft, bottomRight, topRight)
}

fun UiScope.RoundRectGradient(
    x: Float, y: Float, width: Float, height: Float,
    radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
    topLeft: Color, bottomLeft: Color, bottomRight: Color, topRight: Color,
) {
    tree.roundRectGradient(
        x, y, width, height,
        radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
        topLeft, bottomLeft, bottomRight, topRight
    )
}

fun UiScope.RoundRectVerticalGradient(
    x: Float, y: Float, width: Float, height: Float, radius: Float, top: Color, bottom: Color,
) {
    tree.roundRectVerticalGradient(x, y, width, height, radius, top, bottom)
}

fun UiScope.RoundRectHorizontalGradient(
    x: Float, y: Float, width: Float, height: Float, radius: Float, left: Color, right: Color,
) {
    tree.roundRectHorizontalGradient(x, y, width, height, radius, left, right)
}

/** 铺满当前作用域的圆角填充，等价于 Compose 的 `Modifier.fillMaxSize().background()`。 */
fun UiScope.Fill(radius: Float, color: Color) {
    tree.roundRect(0.0f, 0.0f, width, height, radius, color)
}

/** 铺满当前作用域的直角填充。 */
fun UiScope.Fill(color: Color) {
    tree.rect(0.0f, 0.0f, width, height, color)
}

// ---------------------------------------------------------------------------
// 直角矩形
// ---------------------------------------------------------------------------

fun UiScope.Rect(x: Float, y: Float, width: Float, height: Float, color: Color) {
    tree.rect(x, y, width, height, color)
}

fun UiScope.RectOutline(x: Float, y: Float, width: Float, height: Float, outlineWidth: Float, color: Color) {
    tree.rectOutline(x, y, width, height, outlineWidth, color)
}

fun UiScope.RectGradient(
    x: Float, y: Float, width: Float, height: Float,
    topLeft: Color, bottomLeft: Color, bottomRight: Color, topRight: Color,
) {
    tree.rectGradient(x, y, width, height, topLeft, bottomLeft, bottomRight, topRight)
}

fun UiScope.RectVerticalGradient(x: Float, y: Float, width: Float, height: Float, top: Color, bottom: Color) {
    tree.rectVerticalGradient(x, y, width, height, top, bottom)
}

fun UiScope.RectHorizontalGradient(x: Float, y: Float, width: Float, height: Float, left: Color, right: Color) {
    tree.rectHorizontalGradient(x, y, width, height, left, right)
}

// ---------------------------------------------------------------------------
// 描边
// ---------------------------------------------------------------------------

fun UiScope.Outline(
    x: Float, y: Float, width: Float, height: Float, radius: Float, outlineWidth: Float, color: Color,
) {
    tree.outline(x, y, width, height, radius, outlineWidth, color)
}

fun UiScope.Outline(
    x: Float, y: Float, width: Float, height: Float,
    radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
    outlineWidth: Float, color: Color,
) {
    tree.outline(
        x, y, width, height,
        radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
        outlineWidth, color
    )
}

// ---------------------------------------------------------------------------
// 文本
// ---------------------------------------------------------------------------

fun UiScope.Text(text: String, x: Float, y: Float, scale: Float, color: Color) {
    tree.text(text, x, y, scale, color)
}

fun UiScope.Text(text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?) {
    tree.text(text, x, y, scale, color, fontLoader)
}

fun UiScope.TextWithShadow(text: String, x: Float, y: Float, scale: Float, color: Color) {
    tree.textWithShadow(text, x, y, scale, color)
}

fun UiScope.TextWithShadow(
    text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?,
) {
    tree.textWithShadow(text, x, y, scale, color, fontLoader)
}

fun UiScope.GradientText(text: String, x: Float, y: Float, scale: Float, startColor: Color, endColor: Color) {
    tree.gradientText(text, x, y, scale, startColor, endColor)
}

fun UiScope.GradientText(
    text: String, x: Float, y: Float, scale: Float, startColor: Color, endColor: Color,
    fontLoader: TtfFontLoader?,
) {
    tree.gradientText(text, x, y, scale, startColor, endColor, fontLoader)
}

fun UiScope.BlurredText(
    text: String, x: Float, y: Float, scale: Float, color: Color, blurRadius: Float, intensity: Int,
) {
    // 注意：UiTree 中无 fontLoader 的重载参数顺序是 (blurRadius, intensity, color)
    tree.blurredText(text, x, y, scale, blurRadius, intensity, color)
}

fun UiScope.BlurredText(
    text: String, x: Float, y: Float, scale: Float, color: Color, blurRadius: Float, intensity: Int,
    fontLoader: TtfFontLoader?,
) {
    tree.blurredText(text, x, y, scale, color, blurRadius, intensity, fontLoader)
}

fun UiScope.GlitchText(text: String, x: Float, y: Float, scale: Float, color: Color, effect: TextGlitchEffect?) {
    tree.glitchText(text, x, y, scale, color, effect)
}

fun UiScope.GlitchText(
    text: String, x: Float, y: Float, scale: Float, color: Color, effect: TextGlitchEffect?,
    fontLoader: TtfFontLoader?,
) {
    tree.glitchText(text, x, y, scale, color, effect, fontLoader)
}

fun UiScope.RotatedText(
    text: String, x: Float, y: Float, scale: Float, color: Color,
    originX: Float, originY: Float, rotationDegrees: Float,
) {
    tree.rotatedText(text, x, y, scale, color, originX, originY, rotationDegrees)
}

/** 旋转纹理，旋转原点同样是相对坐标。 */
fun UiScope.RotatedTexture(
    texture: LuminTexture, x: Float, y: Float, width: Float, height: Float,
    u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
    originX: Float, originY: Float, rotationDegrees: Float,
) {
    tree.rotatedTexture(texture, x, y, width, height, u0, v0, u1, v1, color, originX, originY, rotationDegrees)
}

/** 旋转圆角纹理，旋转原点同样是相对坐标。 */
fun UiScope.RotatedRoundedTexture(
    texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float, radius: Float,
    u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
    originX: Float, originY: Float, rotationDegrees: Float,
) {
    tree.rotatedRoundedTexture(
        texture, x, y, width, height, radius, u0, v0, u1, v1, color,
        originX, originY, rotationDegrees
    )
}

fun UiScope.MarqueeText(text: String, x: Float, y: Float, scale: Float, color: Color, clip: UiRect) {
    tree.marqueeText(text, x, y, scale, color, clip)
}

fun UiScope.MarqueeText(
    text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?, clip: UiRect,
) {
    tree.marqueeText(text, x, y, scale, color, fontLoader, clip)
}

// ---------------------------------------------------------------------------
// 纹理
// ---------------------------------------------------------------------------

fun UiScope.Texture(
    texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
    u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
) {
    tree.texture(texture, x, y, width, height, u0, v0, u1, v1, color)
}

fun UiScope.Texture(
    texture: LuminTexture, x: Float, y: Float, width: Float, height: Float,
    u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
) {
    tree.texture(texture, x, y, width, height, u0, v0, u1, v1, color)
}

fun UiScope.Texture(
    texture: net.minecraft.resources.Identifier, x: Float, y: Float, width: Float, height: Float,
    u0: Float, v0: Float, u1: Float, v1: Float, color: Color, linearFilter: Boolean = false,
) {
    tree.texture(texture, x, y, width, height, u0, v0, u1, v1, color, linearFilter)
}

fun UiScope.RoundedTexture(
    texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float, radius: Float,
    u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
) {
    tree.roundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color)
}

fun UiScope.RoundedTexture(
    texture: LuminTexture, x: Float, y: Float, width: Float, height: Float, radius: Float,
    u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
) {
    tree.roundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color)
}

fun UiScope.RoundedTexture(
    texture: net.minecraft.resources.Identifier, x: Float, y: Float, width: Float, height: Float, radius: Float,
    u0: Float, v0: Float, u1: Float, v1: Float, color: Color, linearFilter: Boolean = false,
) {
    tree.roundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color, linearFilter)
}

fun UiScope.RoundedTexture(
    texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
    radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
    u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
) {
    tree.roundedTexture(
        texture, x, y, width, height,
        radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
        u0, v0, u1, v1, color
    )
}

fun UiScope.PlayerHead(
    texture: LuminTexture, x: Float, y: Float, size: Float, radius: Float, color: Color,
) {
    tree.playerHead(texture, x, y, size, radius, color)
}

fun UiScope.PlayerHead(
    identifier: net.minecraft.resources.Identifier, x: Float, y: Float, size: Float, radius: Float, color: Color,
) {
    tree.playerHead(identifier, x, y, size, radius, color)
}

// ---------------------------------------------------------------------------
// 基础图元
// ---------------------------------------------------------------------------

fun UiScope.Triangle(centerX: Float, centerY: Float, size: Float, progress: Float, color: Color) {
    tree.triangle(centerX, centerY, size, progress, color)
}

fun UiScope.Circle(centerX: Float, centerY: Float, radius: Float, strokeWidth: Float, color: Color) {
    tree.circle(centerX, centerY, radius, strokeWidth, color)
}

fun UiScope.Arc(
    centerX: Float, centerY: Float, radius: Float, strokeWidth: Float,
    startDegrees: Float, sweepDegrees: Float, roundCap: Boolean, color: Color,
) {
    tree.arc(centerX, centerY, radius, strokeWidth, startDegrees, sweepDegrees, roundCap, color)
}

fun UiScope.GradientArc(
    centerX: Float, centerY: Float, radius: Float, strokeWidth: Float,
    startDegrees: Float, sweepDegrees: Float, roundCap: Boolean,
    gradientRotationDegrees: Float, startColor: Color, middleColor: Color, endColor: Color,
) {
    tree.gradientArc(
        centerX, centerY, radius, strokeWidth, startDegrees, sweepDegrees, roundCap,
        gradientRotationDegrees, startColor, middleColor, endColor
    )
}
