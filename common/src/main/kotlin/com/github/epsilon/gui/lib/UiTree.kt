package com.github.epsilon.gui.lib

import com.github.epsilon.graphics.LuminTexture
import com.github.epsilon.graphics.schedulers.render2d.Render2DTexture
import com.github.epsilon.graphics.text.TextGlitchEffect
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.lib.render.LuminUiRenderer
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.utils.render.animation.Animation
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import java.awt.Color

/**
 * 与具体 Screen 无关的声明式 UI 节点树。
 *
 * 调用方通过 [build] 在一个 [Scope] 中描述本帧需要绘制的内容，
 * 然后再由 [LuminUiRenderer] 将树结构编译进调度器命令或视口缓冲。
 */
class UiTree private constructor(
    val nodes: List<UiNode>,
    val hasActiveAnimations: Boolean,
) {

    /**
     * UI DSL 的构建作用域。
     *
     * 作用域负责收集节点、传播动画活动状态，并在需要时把子树包装成 layer 或 viewport。
     */
    class Scope {

        private var nodes: MutableList<UiNode> = ArrayList()
        private var activeAnimations = false
        private val boundStack = ArrayList<UiRect>()

        init {
            boundStack.add(UiRect(0.0f, 0.0f, 0.0f, 0.0f))
        }

        /**
         * 向当前作用域追加单个节点，并在需要时绑定到相对 layer。
         *
         * 高频绘制上下文会大量调用单节点重载；这里直接包装 [LayeredNode]，
         * 避免为了一个图元额外创建临时 [Scope] 和子列表。
         */
        private fun addNode(layer: Int, node: UiNode) {
            if (layer == 0) {
                nodes.add(node)
            } else {
                nodes.add(LayeredNode(layer, node))
            }
        }

        /**
         * 生成当前作用域内容的不可变树快照。
         *
         * 长生命周期绘制上下文会复用同一个 [Scope] 收集整帧内容，
         * 快照用于把当帧节点稳定地交给批次编译。
         */
        fun snapshot(): UiTree {
            return UiTree(nodes.toList(), activeAnimations)
        }

        /**
         * 清空当前作用域，便于长生命周期的渲染门面复用。
         */
        fun clear() {
            nodes.clear()
            activeAnimations = false
            boundStack.clear()
            boundStack.add(UiRect(0.0f, 0.0f, 0.0f, 0.0f))
        }

        fun bound(): UiRect {
            return currentBound()
        }

        fun stack(bounds: UiRect): Stack {
            return Stack(resolveRect(bounds))
        }

        fun stack(x: Float, y: Float, width: Float, height: Float): Stack {
            return stack(UiRect(x, y, width, height))
        }

        fun push(bounds: UiRect, content: (Scope) -> Unit) {
            pushRelative(bounds, content)
        }

        fun push(x: Float, y: Float, content: (Scope) -> Unit) {
            pushRelative(x, y, content)
        }

        fun pushRelative(bounds: UiRect, content: (Scope) -> Unit) {
            withBound(resolveRect(bounds), content)
        }

        fun pushRelative(x: Float, y: Float, content: (Scope) -> Unit) {
            val current = currentBound()
            withBound(UiRect(current.x + x, current.y + y, 0.0f, 0.0f), content)
        }

        fun pushAbsolute(bounds: UiRect, content: (Scope) -> Unit) {
            withBound(bounds, content)
        }

        fun pushAbsolute(x: Float, y: Float, content: (Scope) -> Unit) {
            withBound(UiRect(x, y, 0.0f, 0.0f), content)
        }

        fun stackPush(bounds: UiRect) {
            stackPushRelative(bounds)
        }

        fun stackPush(x: Float, y: Float) {
            stackPushRelative(x, y)
        }

        fun stackPushRelative(bounds: UiRect) {
            boundStack.add(resolveRect(bounds))
        }

        fun stackPushRelative(x: Float, y: Float) {
            val current = currentBound()
            boundStack.add(UiRect(current.x + x, current.y + y, 0.0f, 0.0f))
        }

        fun stackPushAbsolute(bounds: UiRect) {
            boundStack.add(bounds)
        }

        fun stackPushAbsolute(x: Float, y: Float) {
            boundStack.add(UiRect(x, y, 0.0f, 0.0f))
        }

        fun stackPop() {
            if (boundStack.size <= 1) {
                throw IllegalStateException("Cannot pop the root UiTree bound.")
            }
            boundStack.removeAt(boundStack.size - 1)
        }

        private fun withBound(bounds: UiRect, content: (Scope) -> Unit) {
            boundStack.add(bounds)
            try {
                content(this)
            } finally {
                boundStack.removeAt(boundStack.size - 1)
            }
        }

        private fun currentBound(): UiRect {
            return boundStack[boundStack.size - 1]
        }

        private fun resolveX(x: Float): Float {
            return currentBound().x + x
        }

        private fun resolveY(y: Float): Float {
            return currentBound().y + y
        }

        private fun resolveRect(rect: UiRect): UiRect {
            return UiRect(resolveX(rect.x), resolveY(rect.y), rect.width, rect.height)
        }

        private fun resolveSegmentRects(segmentRects: FloatArray?): FloatArray? {
            if (segmentRects == null) {
                return null
            }
            val resolved = segmentRects.clone()
            val bound = currentBound()
            var i = 0
            while (i + 3 < resolved.size) {
                resolved[i] += bound.x
                resolved[i + 1] += bound.y
                i += 4
            }
            return resolved
        }

        private fun resolveButton(element: ButtonElement): ButtonElement {
            return ButtonElement(
                resolveRect(element.bounds), element.radius, element.background,
                element.label, element.labelScale, element.labelColor
            )
        }

        private fun resolveSwitch(element: SwitchElement): SwitchElement {
            return SwitchElement(resolveRect(element.bounds), element.toggleProgress, element.hoverProgress)
        }

        private fun resolveInput(element: InputElement): InputElement {
            return InputElement(
                resolveRect(element.bounds), element.focused, element.hoverProgress,
                element.focusRingProgress, element.focusRingColor, element.focusRingInset,
                element.textInset, element.text, element.textScale, element.textColor,
                element.selection, element.selectionColor,
                element.caretIndex, element.caretColor,
                element.trailingHint, element.trailingHintScale, element.trailingHintColor
            )
        }

        /**
         * 在一个相对层级中构建子树。
         *
         * 层级只影响支持 layer 的批次输出；旧的直接编译入口会把它当作普通分组处理。
         */
        fun layer(layer: Int, content: (Scope) -> Unit) {
            val capture = capture(content)
            activeAnimations = activeAnimations || capture.hasActiveAnimations
            nodes.add(LayerNode(layer, capture.nodes))
        }

        fun scissor(clip: UiRect, content: (Scope) -> Unit) {
            val resolvedClip = resolveRect(clip)
            val capture = capture(content)
            activeAnimations = activeAnimations || capture.hasActiveAnimations
            nodes.add(ScissorNode(resolvedClip, capture.nodes))
        }

        fun scissor(x: Float, y: Float, width: Float, height: Float, content: (Scope) -> Unit) {
            scissor(UiRect(x, y, width, height), content)
        }

        fun scissorIf(required: Boolean, clip: UiRect, content: (Scope) -> Unit) {
            if (required) {
                scissor(clip, content)
            } else {
                content(this)
            }
        }

        fun scissorIf(required: Boolean, x: Float, y: Float, width: Float, height: Float, content: (Scope) -> Unit) {
            scissorIf(required, UiRect(x, y, width, height), content)
        }

        /**
         * 推进一个布尔目标动画，并返回当前值。
         */
        fun animate(animation: Animation, target: Boolean): Float {
            return animate(animation, if (target) 1.0f else 0.0f)
        }

        /**
         * 推进一个浮点目标动画，并返回当前值。
         */
        fun animate(animation: Animation, target: Float): Float {
            animation.run(target)
            activeAnimations = activeAnimations || !animation.isFinished
            return animation.value
        }

        fun shadow(x: Float, y: Float, width: Float, height: Float, radius: Float, blurRadius: Float, color: Color) {
            nodes.add(
                ShadowNode(
                    resolveX(x), resolveY(y), width, height,
                    radius, radius, radius, radius, blurRadius, color
                )
            )
        }

        fun shadow(
            x: Float, y: Float, width: Float, height: Float, radius: Float, blurRadius: Float, color: Color,
            segmentRects: FloatArray?, segmentRadii: FloatArray?, segmentCount: Int
        ) {
            shadow(x, y, width, height, radius, blurRadius, color, segmentRects, segmentRadii, null, segmentCount)
        }

        fun shadow(
            x: Float, y: Float, width: Float, height: Float, radius: Float, blurRadius: Float, color: Color,
            segmentRects: FloatArray?, segmentRadii: FloatArray?, segmentColors: FloatArray?, segmentCount: Int
        ) {
            nodes.add(
                SegmentedShadowNode(
                    resolveX(x), resolveY(y), width, height,
                    radius, radius, radius, radius, blurRadius, color,
                    resolveSegmentRects(segmentRects), segmentRadii?.clone(),
                    segmentColors?.clone(), segmentCount
                )
            )
        }

        fun shadow(
            layer: Int,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            radius: Float,
            blurRadius: Float,
            color: Color
        ) {
            addNode(
                layer,
                ShadowNode(resolveX(x), resolveY(y), width, height, radius, radius, radius, radius, blurRadius, color)
            )
        }

        fun shadow(
            x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            blurRadius: Float, color: Color
        ) {
            nodes.add(
                ShadowNode(
                    resolveX(x), resolveY(y), width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, blurRadius, color
                )
            )
        }

        fun shadow(
            x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            blurRadius: Float, color: Color, segmentRects: FloatArray?, segmentRadii: FloatArray?, segmentCount: Int
        ) {
            shadow(
                x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                blurRadius, color, segmentRects, segmentRadii, null, segmentCount
            )
        }

        fun shadow(
            x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            blurRadius: Float, color: Color, segmentRects: FloatArray?, segmentRadii: FloatArray?,
            segmentColors: FloatArray?, segmentCount: Int
        ) {
            nodes.add(
                SegmentedShadowNode(
                    resolveX(x), resolveY(y), width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, blurRadius, color,
                    resolveSegmentRects(segmentRects), segmentRadii?.clone(),
                    segmentColors?.clone(), segmentCount
                )
            )
        }

        fun shadow(
            layer: Int, x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            blurRadius: Float, color: Color
        ) {
            addNode(
                layer,
                ShadowNode(
                    resolveX(x), resolveY(y), width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, blurRadius, color
                )
            )
        }

        fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Color) {
            nodes.add(RoundRectNode(resolveX(x), resolveY(y), width, height, radius, radius, radius, radius, color))
        }

        fun roundRect(layer: Int, x: Float, y: Float, width: Float, height: Float, radius: Float, color: Color) {
            addNode(
                layer,
                RoundRectNode(resolveX(x), resolveY(y), width, height, radius, radius, radius, radius, color)
            )
        }

        fun roundRect(
            x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            color: Color
        ) {
            nodes.add(
                RoundRectNode(
                    resolveX(x), resolveY(y), width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color
                )
            )
        }

        fun roundRect(
            layer: Int, x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            color: Color
        ) {
            addNode(
                layer,
                RoundRectNode(
                    resolveX(x), resolveY(y), width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color
                )
            )
        }

        fun roundRectGradient(
            x: Float, y: Float, width: Float, height: Float, radius: Float,
            topLeft: Color, bottomLeft: Color, bottomRight: Color, topRight: Color
        ) {
            roundRectGradient(
                x, y, width, height, radius, radius, radius, radius, topLeft, bottomLeft, bottomRight, topRight
            )
        }

        fun roundRectGradient(
            x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            topLeft: Color, bottomLeft: Color, bottomRight: Color, topRight: Color
        ) {
            nodes.add(
                RoundRectGradientNode(
                    resolveX(x), resolveY(y), width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    topLeft, bottomLeft, bottomRight, topRight
                )
            )
        }

        fun roundRectGradient(
            layer: Int, x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            topLeft: Color, bottomLeft: Color, bottomRight: Color, topRight: Color
        ) {
            addNode(
                layer,
                RoundRectGradientNode(
                    resolveX(x), resolveY(y), width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    topLeft, bottomLeft, bottomRight, topRight
                )
            )
        }

        fun roundRectVerticalGradient(
            x: Float, y: Float, width: Float, height: Float, radius: Float, top: Color, bottom: Color
        ) {
            roundRectGradient(x, y, width, height, radius, top, bottom, bottom, top)
        }

        fun roundRectHorizontalGradient(
            x: Float, y: Float, width: Float, height: Float, radius: Float, left: Color, right: Color
        ) {
            roundRectGradient(x, y, width, height, radius, left, left, right, right)
        }

        fun roundRectHorizontalGradient(
            layer: Int, x: Float, y: Float, width: Float, height: Float, radius: Float, left: Color, right: Color
        ) {
            roundRectGradient(layer, x, y, width, height, radius, radius, radius, radius, left, left, right, right)
        }

        fun rect(x: Float, y: Float, width: Float, height: Float, color: Color) {
            nodes.add(RectNode(resolveX(x), resolveY(y), width, height, color))
        }

        fun rect(layer: Int, x: Float, y: Float, width: Float, height: Float, color: Color) {
            addNode(layer, RectNode(resolveX(x), resolveY(y), width, height, color))
        }

        fun rectOutline(x: Float, y: Float, width: Float, height: Float, outlineWidth: Float, color: Color) {
            nodes.add(RectOutlineNode(resolveX(x), resolveY(y), width, height, outlineWidth, color))
        }

        fun rectOutline(
            layer: Int,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
            outlineWidth: Float,
            color: Color
        ) {
            addNode(layer, RectOutlineNode(resolveX(x), resolveY(y), width, height, outlineWidth, color))
        }

        fun rectGradient(
            x: Float, y: Float, width: Float, height: Float,
            topLeft: Color, bottomLeft: Color, bottomRight: Color, topRight: Color
        ) {
            nodes.add(
                RectGradientNode(
                    resolveX(x),
                    resolveY(y),
                    width,
                    height,
                    topLeft,
                    bottomLeft,
                    bottomRight,
                    topRight
                )
            )
        }

        fun rectGradient(
            layer: Int, x: Float, y: Float, width: Float, height: Float,
            topLeft: Color, bottomLeft: Color, bottomRight: Color, topRight: Color
        ) {
            addNode(
                layer,
                RectGradientNode(resolveX(x), resolveY(y), width, height, topLeft, bottomLeft, bottomRight, topRight)
            )
        }

        fun rectVerticalGradient(x: Float, y: Float, width: Float, height: Float, top: Color, bottom: Color) {
            rectGradient(x, y, width, height, top, bottom, bottom, top)
        }

        fun rectHorizontalGradient(x: Float, y: Float, width: Float, height: Float, left: Color, right: Color) {
            rectGradient(x, y, width, height, left, left, right, right)
        }

        fun outline(x: Float, y: Float, width: Float, height: Float, radius: Float, outlineWidth: Float, color: Color) {
            outline(x, y, width, height, radius, radius, radius, radius, outlineWidth, color)
        }

        fun outline(
            x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            outlineWidth: Float, color: Color
        ) {
            nodes.add(
                OutlineNode(
                    resolveX(x), resolveY(y), width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    outlineWidth, color
                )
            )
        }

        fun outline(
            layer: Int, x: Float, y: Float, width: Float, height: Float,
            radiusTopLeft: Float, radiusTopRight: Float, radiusBottomRight: Float, radiusBottomLeft: Float,
            outlineWidth: Float, color: Color
        ) {
            addNode(
                layer,
                OutlineNode(
                    resolveX(x), resolveY(y), width, height,
                    radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    outlineWidth, color
                )
            )
        }

        fun text(text: String, x: Float, y: Float, scale: Float, color: Color) {
            nodes.add(TextNode(text, resolveX(x), resolveY(y), scale, color, null))
        }

        fun text(layer: Int, text: String, x: Float, y: Float, scale: Float, color: Color) {
            addNode(layer, TextNode(text, resolveX(x), resolveY(y), scale, color, null))
        }

        fun text(text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?) {
            nodes.add(TextNode(text, resolveX(x), resolveY(y), scale, color, fontLoader))
        }

        fun text(layer: Int, text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?) {
            addNode(layer, TextNode(text, resolveX(x), resolveY(y), scale, color, fontLoader))
        }

        fun textWithShadow(text: String, x: Float, y: Float, scale: Float, color: Color) {
            textWithShadow(0, text, x, y, scale, color, null)
        }

        fun textWithShadow(layer: Int, text: String, x: Float, y: Float, scale: Float, color: Color) {
            textWithShadow(layer, text, x, y, scale, color, null)
        }

        fun textWithShadow(text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?) {
            textWithShadow(0, text, x, y, scale, color, fontLoader)
        }

        fun textWithShadow(
            layer: Int, text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?,
        ) {
            if (text.isEmpty() || color.alpha == 0) return
            val offset = TEXT_SHADOW_OFFSET * scale
            val shadow = Color(ARGB.scaleRGB(color.rgb, TEXT_SHADOW_RGB_SCALE), true)
            text(layer, text, x + offset, y + offset, scale, shadow, fontLoader)
            text(layer, text, x, y, scale, color, fontLoader)
        }

        fun gradientText(text: String, x: Float, y: Float, scale: Float, startColor: Color, endColor: Color) {
            nodes.add(GradientTextNode(text, resolveX(x), resolveY(y), scale, startColor, endColor, null))
        }

        fun gradientText(
            text: String, x: Float, y: Float, scale: Float, startColor: Color, endColor: Color,
            fontLoader: TtfFontLoader?
        ) {
            nodes.add(GradientTextNode(text, resolveX(x), resolveY(y), scale, startColor, endColor, fontLoader))
        }

        fun blurredText(
            text: String,
            x: Float,
            y: Float,
            scale: Float,
            blurRadius: Float,
            intensity: Int,
            color: Color
        ) {
            nodes.add(BlurTextNode(text, resolveX(x), resolveY(y), scale, color, blurRadius, intensity, null))
        }

        fun blurredText(
            text: String, x: Float, y: Float, scale: Float, color: Color, blurRadius: Float, intensity: Int,
            fontLoader: TtfFontLoader?
        ) {
            nodes.add(BlurTextNode(text, resolveX(x), resolveY(y), scale, color, blurRadius, intensity, fontLoader))
        }

        fun blurredText(
            layer: Int,
            text: String,
            x: Float,
            y: Float,
            scale: Float,
            color: Color,
            blurRadius: Float,
            intensity: Int
        ) {
            blurredText(layer, text, x, y, scale, color, blurRadius, intensity, null)
        }

        fun blurredText(
            layer: Int, text: String, x: Float, y: Float, scale: Float, color: Color, blurRadius: Float, intensity: Int,
            fontLoader: TtfFontLoader?
        ) {
            addNode(
                layer,
                BlurTextNode(text, resolveX(x), resolveY(y), scale, color, blurRadius, intensity, fontLoader)
            )
        }

        fun glitchText(text: String, x: Float, y: Float, scale: Float, color: Color, effect: TextGlitchEffect?) {
            nodes.add(GlitchTextNode(text, resolveX(x), resolveY(y), scale, color, effect, null))
        }

        fun glitchText(
            text: String, x: Float, y: Float, scale: Float, color: Color, effect: TextGlitchEffect?,
            fontLoader: TtfFontLoader?
        ) {
            nodes.add(GlitchTextNode(text, resolveX(x), resolveY(y), scale, color, effect, fontLoader))
        }

        fun rotatedText(
            text: String, x: Float, y: Float, scale: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float
        ) {
            nodes.add(
                RotatedTextNode(
                    text, resolveX(x), resolveY(y), scale, color, null,
                    resolveX(originX), resolveY(originY), rotationDegrees
                )
            )
        }

        fun rotatedText(
            layer: Int, text: String, x: Float, y: Float, scale: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float
        ) {
            addNode(
                layer,
                RotatedTextNode(
                    text, resolveX(x), resolveY(y), scale, color, null,
                    resolveX(originX), resolveY(originY), rotationDegrees
                )
            )
        }

        fun rotatedText(
            text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?,
            originX: Float, originY: Float, rotationDegrees: Float
        ) {
            nodes.add(
                RotatedTextNode(
                    text, resolveX(x), resolveY(y), scale, color, fontLoader,
                    resolveX(originX), resolveY(originY), rotationDegrees
                )
            )
        }

        fun rotatedText(
            layer: Int, text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?,
            originX: Float, originY: Float, rotationDegrees: Float
        ) {
            addNode(
                layer,
                RotatedTextNode(
                    text, resolveX(x), resolveY(y), scale, color, fontLoader,
                    resolveX(originX), resolveY(originY), rotationDegrees
                )
            )
        }

        /**
         * 添加一个带独立裁剪区域的文本节点（跑马灯）。
         */
        fun marqueeText(text: String, x: Float, y: Float, scale: Float, color: Color, clip: UiRect) {
            marqueeText(text, x, y, scale, color, null, clip)
        }

        fun marqueeText(
            text: String, x: Float, y: Float, scale: Float, color: Color, fontLoader: TtfFontLoader?, clip: UiRect
        ) {
            nodes.add(MarqueeTextNode(text, resolveX(x), resolveY(y), scale, color, fontLoader, resolveRect(clip)))
        }

        fun texture(
            texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color
        ) {
            roundedTexture(texture, x, y, width, height, 0.0f, u0, v0, u1, v1, color)
        }

        fun texture(
            texture: Identifier, x: Float, y: Float, width: Float, height: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color
        ) {
            texture(texture, x, y, width, height, u0, v0, u1, v1, color, false)
        }

        fun texture(
            texture: Identifier, x: Float, y: Float, width: Float, height: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color, linearFilter: Boolean
        ) {
            texture(Render2DTexture.IdentifierRef(texture, linearFilter), x, y, width, height, u0, v0, u1, v1, color)
        }

        fun texture(
            texture: LuminTexture, x: Float, y: Float, width: Float, height: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color
        ) {
            texture(Render2DTexture.LuminRef(texture), x, y, width, height, u0, v0, u1, v1, color)
        }

        fun rotatedTexture(
            texture: LuminTexture, x: Float, y: Float, width: Float, height: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float
        ) {
            rotatedRoundedTexture(
                texture, x, y, width, height, 0.0f, u0, v0, u1, v1, color,
                originX, originY, rotationDegrees
            )
        }

        fun rotatedRoundedTexture(
            texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
            radius: Float, u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float
        ) {
            rotatedRoundedTexture(
                texture, x, y, width, height, radius, radius, radius, radius,
                u0, v0, u1, v1, color, originX, originY, rotationDegrees
            )
        }

        fun rotatedRoundedTexture(
            texture: Identifier, x: Float, y: Float, width: Float, height: Float,
            radius: Float, u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float
        ) {
            rotatedRoundedTexture(
                texture,
                x,
                y,
                width,
                height,
                radius,
                u0,
                v0,
                u1,
                v1,
                color,
                originX,
                originY,
                rotationDegrees,
                false
            )
        }

        fun rotatedRoundedTexture(
            texture: Identifier, x: Float, y: Float, width: Float, height: Float,
            radius: Float, u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float, linearFilter: Boolean
        ) {
            rotatedRoundedTexture(
                Render2DTexture.IdentifierRef(texture, linearFilter),
                x, y, width, height, radius, u0, v0, u1, v1, color, originX, originY, rotationDegrees
            )
        }

        fun rotatedRoundedTexture(
            texture: LuminTexture, x: Float, y: Float, width: Float, height: Float,
            radius: Float, u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float
        ) {
            rotatedRoundedTexture(
                Render2DTexture.LuminRef(texture),
                x, y, width, height, radius, u0, v0, u1, v1, color, originX, originY, rotationDegrees
            )
        }

        fun rotatedRoundedTexture(
            texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
            topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color,
            originX: Float, originY: Float, rotationDegrees: Float
        ) {
            nodes.add(
                RotatedTextureNode(
                    texture, resolveX(x), resolveY(y), width, height,
                    topLeft, topRight, bottomRight, bottomLeft, u0, v0, u1, v1, color,
                    resolveX(originX), resolveY(originY), rotationDegrees
                )
            )
        }

        fun roundedTexture(
            texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float, radius: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color
        ) {
            roundedTexture(texture, x, y, width, height, radius, radius, radius, radius, u0, v0, u1, v1, color)
        }

        fun roundedTexture(
            texture: Identifier, x: Float, y: Float, width: Float, height: Float, radius: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color
        ) {
            roundedTexture(texture, x, y, width, height, radius, u0, v0, u1, v1, color, false)
        }

        fun roundedTexture(
            texture: Identifier, x: Float, y: Float, width: Float, height: Float, radius: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color, linearFilter: Boolean
        ) {
            roundedTexture(
                Render2DTexture.IdentifierRef(texture, linearFilter),
                x, y, width, height, radius, u0, v0, u1, v1, color
            )
        }

        fun roundedTexture(
            texture: LuminTexture, x: Float, y: Float, width: Float, height: Float, radius: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color
        ) {
            roundedTexture(Render2DTexture.LuminRef(texture), x, y, width, height, radius, u0, v0, u1, v1, color)
        }

        fun roundedTexture(
            texture: Render2DTexture, x: Float, y: Float, width: Float, height: Float,
            topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float,
            u0: Float, v0: Float, u1: Float, v1: Float, color: Color
        ) {
            nodes.add(
                TextureNode(
                    texture, resolveX(x), resolveY(y), width, height,
                    topLeft, topRight, bottomRight, bottomLeft, u0, v0, u1, v1, color
                )
            )
        }

        fun playerHead(texture: LuminTexture, x: Float, y: Float, size: Float, radius: Float, color: Color) {
            roundedTexture(
                texture,
                x,
                y,
                size,
                size,
                radius,
                8.0f / 64.0f,
                8.0f / 64.0f,
                16.0f / 64.0f,
                16.0f / 64.0f,
                color
            )
            roundedTexture(
                texture,
                x,
                y,
                size,
                size,
                radius,
                40.0f / 64.0f,
                8.0f / 64.0f,
                48.0f / 64.0f,
                16.0f / 64.0f,
                color
            )
        }

        fun playerHead(identifier: Identifier, x: Float, y: Float, size: Float, radius: Float, color: Color) {
            roundedTexture(
                identifier,
                x,
                y,
                size,
                size,
                radius,
                8.0f / 64.0f,
                8.0f / 64.0f,
                16.0f / 64.0f,
                16.0f / 64.0f,
                color
            )
            roundedTexture(
                identifier,
                x,
                y,
                size,
                size,
                radius,
                40.0f / 64.0f,
                8.0f / 64.0f,
                48.0f / 64.0f,
                16.0f / 64.0f,
                color
            )
        }

        fun button(
            x: Float, y: Float, width: Float, height: Float, radius: Float, background: Color,
            label: String, labelScale: Float, labelColor: Color
        ) {
            button(ButtonElement(UiRect(x, y, width, height), radius, background, label, labelScale, labelColor))
        }

        fun button(
            bounds: UiRect,
            radius: Float,
            background: Color,
            label: String,
            labelScale: Float,
            labelColor: Color
        ) {
            button(ButtonElement(bounds, radius, background, label, labelScale, labelColor))
        }

        fun button(element: ButtonElement) {
            val resolved = resolveButton(element)
            val bounds = resolved.bounds
            nodes.add(
                ButtonNode(
                    bounds.x, bounds.y, bounds.width, bounds.height, element.radius,
                    resolved.background, resolved.label, resolved.labelScale, resolved.labelColor
                )
            )
        }

        fun switchControl(bounds: UiRect, toggleProgress: Float, hoverProgress: Float) {
            toggleSwitch(SwitchElement(bounds, toggleProgress, hoverProgress))
        }

        fun toggle(bounds: UiRect, toggleProgress: Float, hoverProgress: Float) {
            toggleSwitch(SwitchElement(bounds, toggleProgress, hoverProgress))
        }

        fun toggleSwitch(element: SwitchElement) {
            val resolved = resolveSwitch(element)
            nodes.add(SwitchNode(resolved.bounds, resolved.toggleProgress, resolved.hoverProgress))
        }

        fun toggle(element: SwitchElement) {
            toggleSwitch(element)
        }

        fun filledField(bounds: UiRect, focused: Boolean, hoverProgress: Float) {
            input(
                InputElement(
                    bounds, focused, hoverProgress,
                    0.0f, Color(0, 0, 0, 0), 0.0f,
                    6.0f, null, 0.0f, Color(0, 0, 0, 0),
                    null, null,
                    null, null,
                    null, 0.0f, null
                )
            )
        }

        fun input(element: InputElement) {
            nodes.add(InputNode(resolveInput(element)))
        }

        fun input(
            bounds: UiRect, focused: Boolean, hoverProgress: Float,
            textInset: Float, text: String?, textScale: Float, textColor: Color,
            caretIndex: Int?, caretColor: Color?,
            trailingHint: String?, trailingHintScale: Float, trailingHintColor: Color?
        ) {
            input(
                InputElement(
                    bounds, focused, hoverProgress,
                    0.0f, Color(0, 0, 0, 0), 0.0f,
                    textInset, text, textScale, textColor,
                    null, null,
                    caretIndex, caretColor,
                    trailingHint, trailingHintScale, trailingHintColor
                )
            )
        }

        fun input(
            bounds: UiRect, focused: Boolean, hoverProgress: Float,
            focusRingProgress: Float, focusRingColor: Color, focusRingInset: Float,
            textInset: Float, text: String?, textScale: Float, textColor: Color,
            selection: SelectionRange?, selectionColor: Color?,
            caretIndex: Int?, caretColor: Color?,
            trailingHint: String?, trailingHintScale: Float, trailingHintColor: Color?
        ) {
            input(
                InputElement(
                    bounds, focused, hoverProgress,
                    focusRingProgress, focusRingColor, focusRingInset,
                    textInset, text, textScale, textColor,
                    selection, selectionColor,
                    caretIndex, caretColor,
                    trailingHint, trailingHintScale, trailingHintColor
                )
            )
        }

        fun assistChip(
            bounds: UiRect, label: String, textScale: Float, background: Color, foreground: Color,
            trailingIcon: String?, trailingIconScale: Float, trailingIconFont: TtfFontLoader?
        ) {
            nodes.add(
                AssistChipNode(
                    resolveRect(bounds), label, textScale, background, foreground,
                    trailingIcon, trailingIconScale, trailingIconFont
                )
            )
        }

        fun chip(
            bounds: UiRect, label: String, textScale: Float, background: Color, foreground: Color,
            trailingIcon: String?, trailingIconScale: Float, trailingIconFont: TtfFontLoader?
        ) {
            assistChip(
                bounds,
                label,
                textScale,
                background,
                foreground,
                trailingIcon,
                trailingIconScale,
                trailingIconFont
            )
        }

        fun segmentedControl(
            bounds: UiRect,
            leadingLabel: String,
            trailingLabel: String,
            progress: Float,
            hoverProgress: Float
        ) {
            nodes.add(SegmentedControlNode(resolveRect(bounds), leadingLabel, trailingLabel, progress, hoverProgress))
        }

        fun segmented(
            bounds: UiRect,
            leadingLabel: String,
            trailingLabel: String,
            progress: Float,
            hoverProgress: Float
        ) {
            segmentedControl(bounds, leadingLabel, trailingLabel, progress, hoverProgress)
        }

        fun iconButton(bounds: UiRect, label: String, scale: Float, tone: Color, hoverProgress: Float) {
            nodes.add(IconButtonNode(resolveRect(bounds), label, scale, tone, hoverProgress))
        }

        /**
         * 添加一个带阴影的弹窗卡片外壳节点。
         */
        fun popupCard(bounds: UiRect, radius: Float, blurRadius: Float, shadowColor: Color, surfaceColor: Color) {
            nodes.add(PopupCardNode(resolveRect(bounds), radius, blurRadius, shadowColor, surfaceColor))
        }

        /**
         * 添加一个滑条节点。
         */
        fun slider(
            bounds: UiRect, progress: Float, trackRadius: Float,
            trackColor: Color, activeEndInset: Float, activeMinWidth: Float, activeColor: Color,
            handleWidth: Float, handleHeight: Float, handleRadius: Float, handleColor: Color
        ) {
            nodes.add(
                SliderNode(
                    resolveRect(bounds), progress, trackRadius, trackColor, activeEndInset, activeMinWidth, activeColor,
                    handleWidth, handleHeight, handleRadius, handleColor
                )
            )
        }

        /**
         * 添加一个 chevron 三角箭头节点。
         */
        fun triangle(centerX: Float, centerY: Float, size: Float, progress: Float, color: Color) {
            nodes.add(TriangleNode(resolveX(centerX), resolveY(centerY), size, progress, color))
        }

        fun triangle(layer: Int, centerX: Float, centerY: Float, size: Float, progress: Float, color: Color) {
            addNode(layer, TriangleNode(resolveX(centerX), resolveY(centerY), size, progress, color))
        }

        /**
         * 添加一个描边圆环节点。
         */
        fun circle(centerX: Float, centerY: Float, radius: Float, strokeWidth: Float, color: Color) {
            arc(centerX, centerY, radius, strokeWidth, 0.0f, 360.0f, false, color)
        }

        /**
         * 添加一个描边圆弧节点。
         */
        fun arc(
            centerX: Float, centerY: Float, radius: Float, strokeWidth: Float,
            startDegrees: Float, sweepDegrees: Float, roundCap: Boolean, color: Color
        ) {
            nodes.add(
                ArcNode(
                    resolveX(centerX), resolveY(centerY), radius, strokeWidth,
                    startDegrees, sweepDegrees, roundCap, 0.0f, color, color, color
                )
            )
        }

        fun arc(
            layer: Int, centerX: Float, centerY: Float, radius: Float, strokeWidth: Float,
            startDegrees: Float, sweepDegrees: Float, roundCap: Boolean, color: Color
        ) {
            addNode(
                layer,
                ArcNode(
                    resolveX(centerX), resolveY(centerY), radius, strokeWidth,
                    startDegrees, sweepDegrees, roundCap, 0.0f, color, color, color
                )
            )
        }

        /**
         * 添加一条带三色循环扫描渐变的描边圆弧。
         */
        fun gradientArc(
            centerX: Float, centerY: Float, radius: Float, strokeWidth: Float,
            startDegrees: Float, sweepDegrees: Float, roundCap: Boolean,
            gradientRotationDegrees: Float, startColor: Color, middleColor: Color, endColor: Color
        ) {
            nodes.add(
                ArcNode(
                    resolveX(centerX), resolveY(centerY), radius, strokeWidth,
                    startDegrees, sweepDegrees, roundCap, gradientRotationDegrees,
                    startColor, middleColor, endColor
                )
            )
        }

        fun gradientArc(
            layer: Int, centerX: Float, centerY: Float, radius: Float, strokeWidth: Float,
            startDegrees: Float, sweepDegrees: Float, roundCap: Boolean,
            gradientRotationDegrees: Float, startColor: Color, middleColor: Color, endColor: Color
        ) {
            addNode(
                layer,
                ArcNode(
                    resolveX(centerX), resolveY(centerY), radius, strokeWidth,
                    startDegrees, sweepDegrees, roundCap, gradientRotationDegrees,
                    startColor, middleColor, endColor
                )
            )
        }

        /**
         * 在独立视口缓冲中构建一个可裁剪子树。
         */
        fun viewport(
            buffer: UiContentBuffer, viewport: UiRect,
            scroll: Float, maxScroll: Float, contentHeight: Float, content: (Scope) -> Unit
        ) {
            viewport(buffer, viewport, scroll, maxScroll, contentHeight, Int.MIN_VALUE, Int.MIN_VALUE, content)
        }

        fun viewport(
            buffer: UiContentBuffer, viewport: UiRect,
            scroll: Float, maxScroll: Float, contentHeight: Float,
            mouseX: Int, mouseY: Int, content: (Scope) -> Unit
        ) {
            val resolvedViewport = resolveRect(viewport)
            val capture = capture { scope ->
                scope.pushAbsolute(
                    UiRect(
                        resolvedViewport.x, resolvedViewport.y - scroll,
                        resolvedViewport.width, contentHeight
                    ),
                    content
                )
            }
            activeAnimations = activeAnimations || capture.hasActiveAnimations
            nodes.add(
                ViewportNode(
                    buffer, resolvedViewport, scroll, maxScroll, contentHeight,
                    mouseX, mouseY, capture.nodes
                )
            )
        }

        private fun capture(content: (Scope) -> Unit): CaptureResult {
            val parent = nodes
            val parentAnimations = activeAnimations
            val parentBoundDepth = boundStack.size
            // 子树构建期间临时切换收集列表，finally 中恢复父作用域，避免子节点泄露到父层。
            nodes = ArrayList()
            activeAnimations = false
            try {
                content(this)
                return CaptureResult(nodes.toList(), activeAnimations)
            } finally {
                nodes = parent
                activeAnimations = parentAnimations
                while (boundStack.size > parentBoundDepth) {
                    boundStack.removeAt(boundStack.size - 1)
                }
            }
        }

        /**
         * 当前作用域内的垂直自动布局栈。
         *
         * Stack 会按 item 高度推进游标，并可直接在每个 item 的矩形内下传同一个 [Scope]。
         */
        inner class Stack(private val bounds: UiRect) {
            private var cursor = bounds.y

            fun bounds(): UiRect {
                return bounds
            }

            fun item(height: Float): UiRect {
                val rect = UiRect(bounds.x, cursor, bounds.width, maxOf(0.0f, height))
                cursor += maxOf(0.0f, height)
                return rect
            }

            fun item(height: Float, gapAfter: Float): UiRect {
                val rect = item(height)
                gap(gapAfter)
                return rect
            }

            fun item(height: Float, content: (Scope) -> Unit) {
                val rect = item(height)
                pushAbsolute(rect, content)
            }

            fun item(height: Float, gapAfter: Float, content: (Scope) -> Unit) {
                val rect = item(height, gapAfter)
                pushAbsolute(rect, content)
            }

            fun item(height: Float, content: (UiRect, Scope) -> Unit) {
                val rect = item(height)
                pushAbsolute(rect) { scope -> content(rect, scope) }
            }

            fun item(height: Float, gapAfter: Float, content: (UiRect, Scope) -> Unit) {
                val rect = item(height, gapAfter)
                pushAbsolute(rect) { scope -> content(rect, scope) }
            }

            fun gap(gap: Float) {
                cursor += maxOf(0.0f, gap)
            }

            fun offset(xOffset: Float, yOffset: Float, widthOffset: Float, heightOffset: Float): UiRect {
                return UiRect(
                    bounds.x + xOffset,
                    cursor + yOffset,
                    maxOf(0.0f, bounds.width + widthOffset),
                    maxOf(0.0f, heightOffset)
                )
            }

            fun offset(
                xOffset: Float, yOffset: Float, widthOffset: Float, heightOffset: Float, content: (Scope) -> Unit
            ) {
                pushAbsolute(offset(xOffset, yOffset, widthOffset, heightOffset), content)
            }

            fun cursor(): Float {
                return cursor
            }
        }

        companion object {
            private const val TEXT_SHADOW_OFFSET = 0.703125f
            private const val TEXT_SHADOW_RGB_SCALE = 0.25f
        }
    }

    /**
     * 自动布局作用域。
     *
     * 该作用域负责在 row/column 中计算子元素位置，GUI 代码只需要声明间距、尺寸和内容。
     */
    class LayoutScope internal constructor(
        private val draw: Scope,
        private val bounds: UiRect,
    ) {

        fun bounds(): UiRect {
            return bounds
        }

        fun draw(): Scope {
            return draw
        }

        fun layer(layer: Int, content: (LayoutScope) -> Unit) {
            draw.layer(layer) { scope -> content(LayoutScope(scope, bounds)) }
        }

        fun box(insets: Insets, content: (LayoutScope) -> Unit) {
            content(LayoutScope(draw, insets.apply(bounds)))
        }

        fun column(gap: Float, content: (LinearScope) -> Unit) {
            val linear = LinearScope(draw, bounds, Axis.VERTICAL, gap)
            content(linear)
        }

        fun row(gap: Float, content: (LinearScope) -> Unit) {
            val linear = LinearScope(draw, bounds, Axis.HORIZONTAL, gap)
            content(linear)
        }

        fun roundRect(radius: Float, color: Color) {
            draw.roundRect(bounds.x, bounds.y, bounds.width, bounds.height, radius, color)
        }

        fun text(text: String, scale: Float, color: Color) {
            val y = bounds.y + (bounds.height - drawTextHeight(scale)) / 2.0f
            draw.text(text, bounds.x, y, scale, color)
        }

        fun text(text: String, scale: Float, color: Color, fontLoader: TtfFontLoader) {
            val y = bounds.y + (bounds.height - drawTextHeight(scale, fontLoader)) / 2.0f
            draw.text(text, bounds.x, y, scale, color, fontLoader)
        }

        private fun drawTextHeight(scale: Float): Float {
            return 9.0f * 0.27f * scale
        }

        private fun drawTextHeight(scale: Float, fontLoader: TtfFontLoader): Float {
            return fontLoader.fontFile.fontHeight * 0.27f * scale
        }
    }

    /**
     * row/column 的线性布局作用域。
     *
     * 固定尺寸和 fill 尺寸可以混用；fill 会使用当前容器尚未消费的剩余空间。
     */
    class LinearScope internal constructor(
        private val draw: Scope,
        private val bounds: UiRect,
        private val axis: Axis,
        private val gap: Float,
    ) {
        private var cursor = if (axis == Axis.VERTICAL) bounds.y else bounds.x

        fun item(size: Float, content: (LayoutScope) -> Unit) {
            val rect = nextRect(maxOf(0.0f, size))
            content(LayoutScope(draw, rect))
            cursor += size + gap
        }

        fun fill(content: (LayoutScope) -> Unit) {
            val end = if (axis == Axis.VERTICAL) bounds.bottom() else bounds.right()
            item(maxOf(0.0f, end - cursor), content)
        }

        fun spacer(size: Float) {
            cursor += maxOf(0.0f, size) + gap
        }

        private fun nextRect(size: Float): UiRect {
            if (axis == Axis.VERTICAL) {
                return UiRect(bounds.x, cursor, bounds.width, size)
            }
            return UiRect(cursor, bounds.y, size, bounds.height)
        }
    }

    enum class Axis {
        HORIZONTAL,
        VERTICAL
    }

    data class Insets(val left: Float, val top: Float, val right: Float, val bottom: Float) {

        fun apply(rect: UiRect): UiRect {
            return UiRect(
                rect.x + left,
                rect.y + top,
                maxOf(0.0f, rect.width - left - right),
                maxOf(0.0f, rect.height - top - bottom)
            )
        }

        companion object {

            fun all(value: Float): Insets {
                return Insets(value, value, value, value)
            }

            fun symmetric(horizontal: Float, vertical: Float): Insets {
                return Insets(horizontal, vertical, horizontal, vertical)
            }

        }
    }

    private data class CaptureResult(val nodes: List<UiNode>, val hasActiveAnimations: Boolean)

    /**
     * DSL 节点只描述本帧的绘制意图，不持有 renderer 状态。
     *
     * 编译阶段会按节点类型把它们分发到调度器命令或视口缓冲。
     */
    sealed interface UiNode

    /**
     * 按钮节点的语义描述。
     */
    data class ButtonElement(
        val bounds: UiRect, val radius: Float, val background: Color, val label: String, val labelScale: Float,
        val labelColor: Color,
    )

    /**
     * 开关节点的语义描述。
     */
    data class SwitchElement(val bounds: UiRect, val toggleProgress: Float, val hoverProgress: Float)

    /**
     * 输入框中文字选区的半开区间描述。
     */
    data class SelectionRange(val start: Int, val end: Int)

    /**
     * 输入框节点的完整语义描述。
     */
    data class InputElement(
        val bounds: UiRect, val focused: Boolean, val hoverProgress: Float,
        val focusRingProgress: Float, val focusRingColor: Color, val focusRingInset: Float,
        val textInset: Float, val text: String?, val textScale: Float, val textColor: Color,
        val selection: SelectionRange?, val selectionColor: Color?,
        val caretIndex: Int?, val caretColor: Color?,
        val trailingHint: String?, val trailingHintScale: Float,
        val trailingHintColor: Color?,
    )

    /**
     * 子树级 layer 偏移；适合一组节点整体提升或下沉。
     */
    data class LayerNode(val layer: Int, val children: List<UiNode>) : UiNode

    /**
     * 单节点 layer 包装；供高频图元入口使用，减少小列表分配。
     */
    data class LayeredNode(val layer: Int, val child: UiNode) : UiNode

    data class ScissorNode(val clip: UiRect, val children: List<UiNode>) : UiNode

    data class ShadowNode(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val radiusTopLeft: Float, val radiusTopRight: Float, val radiusBottomRight: Float, val radiusBottomLeft: Float,
        val blurRadius: Float, val color: Color,
    ) : UiNode

    data class SegmentedShadowNode(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val radiusTopLeft: Float, val radiusTopRight: Float,
        val radiusBottomRight: Float, val radiusBottomLeft: Float,
        val blurRadius: Float, val color: Color,
        val segmentRects: FloatArray?, val segmentRadii: FloatArray?, val segmentColors: FloatArray?,
        val segmentCount: Int,
    ) : UiNode

    data class RoundRectNode(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val radiusTopLeft: Float, val radiusTopRight: Float, val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val color: Color,
    ) : UiNode

    data class RoundRectGradientNode(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val radiusTopLeft: Float, val radiusTopRight: Float, val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val topLeft: Color, val bottomLeft: Color, val bottomRight: Color,
        val topRight: Color,
    ) : UiNode

    data class RectNode(val x: Float, val y: Float, val width: Float, val height: Float, val color: Color) : UiNode

    data class RectGradientNode(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val topLeft: Color, val bottomLeft: Color, val bottomRight: Color,
        val topRight: Color,
    ) : UiNode

    data class RectOutlineNode(
        val x: Float, val y: Float, val width: Float, val height: Float, val outlineWidth: Float,
        val color: Color,
    ) : UiNode

    data class OutlineNode(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val radiusTopLeft: Float, val radiusTopRight: Float, val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val outlineWidth: Float, val color: Color,
    ) : UiNode

    data class TextNode(
        val text: String, val x: Float, val y: Float, val scale: Float, val color: Color,
        val fontLoader: TtfFontLoader?,
    ) : UiNode

    data class GradientTextNode(
        val text: String, val x: Float, val y: Float, val scale: Float, val startColor: Color, val endColor: Color,
        val fontLoader: TtfFontLoader?,
    ) : UiNode

    data class BlurTextNode(
        val text: String, val x: Float, val y: Float, val scale: Float, val color: Color, val blurRadius: Float,
        val intensity: Int, val fontLoader: TtfFontLoader?,
    ) : UiNode

    data class GlitchTextNode(
        val text: String, val x: Float, val y: Float, val scale: Float, val color: Color,
        val effect: TextGlitchEffect?, val fontLoader: TtfFontLoader?,
    ) : UiNode

    data class RotatedTextNode(
        val text: String, val x: Float, val y: Float, val scale: Float, val color: Color,
        val fontLoader: TtfFontLoader?, val originX: Float, val originY: Float, val rotationDegrees: Float,
    ) : UiNode

    data class MarqueeTextNode(
        val text: String, val x: Float, val y: Float, val scale: Float, val color: Color,
        val fontLoader: TtfFontLoader?, val clip: UiRect,
    ) : UiNode

    data class TextureNode(
        val texture: Render2DTexture, val x: Float, val y: Float, val width: Float, val height: Float,
        val radiusTopLeft: Float, val radiusTopRight: Float, val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float, val color: Color,
    ) : UiNode

    data class RotatedTextureNode(
        val texture: Render2DTexture, val x: Float, val y: Float, val width: Float, val height: Float,
        val radiusTopLeft: Float, val radiusTopRight: Float, val radiusBottomRight: Float,
        val radiusBottomLeft: Float,
        val u0: Float, val v0: Float, val u1: Float, val v1: Float, val color: Color,
        val originX: Float, val originY: Float, val rotationDegrees: Float,
    ) : UiNode

    data class ButtonNode(
        val x: Float, val y: Float, val width: Float, val height: Float, val radius: Float, val background: Color,
        val label: String, val labelScale: Float, val labelColor: Color,
    ) : UiNode

    data class SwitchNode(val bounds: UiRect, val toggleProgress: Float, val hoverProgress: Float) : UiNode

    data class FilledFieldNode(val bounds: UiRect, val focused: Boolean, val hoverProgress: Float) : UiNode

    data class InputNode(val element: InputElement) : UiNode

    data class AssistChipNode(
        val bounds: UiRect, val label: String, val textScale: Float, val background: Color, val foreground: Color,
        val trailingIcon: String?, val trailingIconScale: Float, val trailingIconFont: TtfFontLoader?,
    ) : UiNode

    data class SegmentedControlNode(
        val bounds: UiRect, val leadingLabel: String, val trailingLabel: String, val progress: Float,
        val hoverProgress: Float,
    ) : UiNode

    data class IconButtonNode(
        val bounds: UiRect, val label: String, val scale: Float, val tone: Color,
        val hoverProgress: Float,
    ) : UiNode

    data class PopupCardNode(
        val bounds: UiRect, val radius: Float, val blurRadius: Float, val shadowColor: Color,
        val surfaceColor: Color,
    ) : UiNode

    data class SliderNode(
        val bounds: UiRect, val progress: Float, val trackRadius: Float, val trackColor: Color,
        val activeEndInset: Float, val activeMinWidth: Float, val activeColor: Color,
        val handleWidth: Float, val handleHeight: Float, val handleRadius: Float, val handleColor: Color,
    ) : UiNode

    data class TriangleNode(
        val centerX: Float, val centerY: Float, val size: Float, val progress: Float,
        val color: Color,
    ) : UiNode

    data class ArcNode(
        val centerX: Float, val centerY: Float, val radius: Float, val strokeWidth: Float,
        val startDegrees: Float, val sweepDegrees: Float, val roundCap: Boolean,
        val gradientRotationDegrees: Float,
        val startColor: Color, val middleColor: Color, val endColor: Color,
    ) : UiNode

    data class ViewportNode(
        val buffer: UiContentBuffer, val viewport: UiRect,
        val scroll: Float, val maxScroll: Float, val contentHeight: Float,
        val mouseX: Int, val mouseY: Int, val children: List<UiNode>,
    ) : UiNode

    companion object {

        /**
         * 构建一棵新的 UI 树。
         */
        fun build(content: (Scope) -> Unit): UiTree {
            val scope = Scope()
            content(scope)
            return scope.snapshot()
        }

        fun from(scope: Scope): UiTree {
            return scope.snapshot()
        }

        /**
         * 构建带自动布局的 UI 树。
         */
        fun layout(bounds: UiRect, content: (LayoutScope) -> Unit): UiTree {
            val scope = Scope()
            content(LayoutScope(scope, bounds))
            return scope.snapshot()
        }

    }

}
