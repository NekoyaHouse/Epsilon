package com.github.epsilon.gui.lib.render

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import com.github.epsilon.graphics.schedulers.render2d.Render2DScissor
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTheme
import com.github.epsilon.gui.lib.UiTree
import java.awt.Color

/**
 * 将 [UiTree] 编译为 [Render2DScheduler] 命令。
 */
object LuminUiRenderer {

    internal fun renderIntoLayeredBatch(tree: UiTree, batch: UiRenderBatch, baseLayer: Int) {
        renderNodesIntoLayeredBatch(tree.nodes, batch, baseLayer, null)
    }

    private fun renderNodesIntoLayeredBatch(
        nodes: List<UiTree.UiNode>, batch: UiRenderBatch,
        layer: Int, activeScissor: Render2DScissor?
    ) {
        val handle = batch.absoluteLayer(layer)
        val previousScissor = applyScissor(handle, activeScissor)
        val target = RenderTarget.forLayer(handle, batch.scheduler().textMetrics(), null, batch.theme())
        try {
            for (node in nodes) {
                if (node is UiTree.LayerNode) {
                    renderNodesIntoLayeredBatch(node.children, batch, layer + node.layer, activeScissor)
                    continue
                }
                if (node is UiTree.LayeredNode) {
                    renderNodesIntoLayeredBatch(listOf(node.child), batch, layer + node.layer, activeScissor)
                    continue
                }
                if (node is UiTree.ScissorNode) {
                    renderNodesIntoLayeredBatch(node.children, batch, layer, intersect(activeScissor, toScissor(node.clip)))
                    continue
                }
                renderNode(node, target)
            }
        } finally {
            restoreScissor(handle, activeScissor, previousScissor)
        }
    }

    private fun renderNodesIntoTarget(
        nodes: List<UiTree.UiNode>, target: RenderTarget,
        activeScissor: Render2DScissor?
    ) {
        val previousScissor = applyScissor(target.layer, activeScissor)
        try {
            for (node in nodes) {
                if (node is UiTree.LayerNode) {
                    renderNodesIntoTarget(node.children, target, activeScissor)
                    continue
                }
                if (node is UiTree.LayeredNode) {
                    renderNodesIntoTarget(listOf(node.child), target, activeScissor)
                    continue
                }
                if (node is UiTree.ScissorNode) {
                    renderNodesIntoTarget(node.children, target, intersect(activeScissor, toScissor(node.clip)))
                    continue
                }
                renderNode(node, target)
            }
        } finally {
            restoreScissor(target.layer, activeScissor, previousScissor)
        }
    }

    private fun renderNode(node: UiTree.UiNode, target: RenderTarget) {
        val layer = target.layer
        val metrics = target.textMetrics
        val theme = target.theme

        when (node) {
            is UiTree.ShadowNode -> {
                layer.addShadow(
                    node.x, node.y, node.width, node.height,
                    node.radiusTopLeft, node.radiusTopRight, node.radiusBottomRight, node.radiusBottomLeft,
                    node.blurRadius, node.color
                )
            }

            is UiTree.SegmentedShadowNode -> {
                layer.addShadow(
                    node.x, node.y, node.width, node.height,
                    node.radiusTopLeft, node.radiusTopRight, node.radiusBottomRight, node.radiusBottomLeft,
                    node.blurRadius, node.color,
                    node.segmentRects!!, node.segmentRadii!!, node.segmentColors, node.segmentCount
                )
            }

            is UiTree.RoundRectNode -> {
                layer.addRoundRect(
                    node.x, node.y, node.width, node.height,
                    node.radiusTopLeft, node.radiusTopRight, node.radiusBottomRight, node.radiusBottomLeft, node.color
                )
            }

            is UiTree.RoundRectGradientNode -> {
                layer.addRoundRectGradient(
                    node.x, node.y, node.width, node.height,
                    node.radiusTopLeft, node.radiusTopRight, node.radiusBottomRight, node.radiusBottomLeft,
                    node.topLeft, node.bottomLeft, node.bottomRight, node.topRight
                )
            }

            is UiTree.RectNode -> {
                layer.addRect(node.x, node.y, node.width, node.height, node.color)
            }

            is UiTree.RectGradientNode -> {
                layer.addRectGradient(
                    node.x, node.y, node.width, node.height,
                    node.topLeft, node.bottomLeft, node.bottomRight, node.topRight
                )
            }

            is UiTree.RectOutlineNode -> {
                layer.addRectOutline(node.x, node.y, node.width, node.height, node.outlineWidth, node.color)
            }

            is UiTree.OutlineNode -> {
                layer.addOutline(
                    node.x, node.y, node.width, node.height,
                    node.radiusTopLeft, node.radiusTopRight, node.radiusBottomRight, node.radiusBottomLeft,
                    node.outlineWidth, node.color
                )
            }

            is UiTree.TextNode -> {
                layer.addText(node.text, node.x, node.y, node.scale, node.color, node.fontLoader)
            }

            is UiTree.GradientTextNode -> {
                layer.addGradientText(
                    node.text, node.x, node.y, node.scale, node.startColor, node.endColor, node.fontLoader
                )
            }

            is UiTree.BlurTextNode -> {
                layer.addBlurredText(
                    node.text, node.x, node.y, node.scale, node.color, node.blurRadius, node.intensity, node.fontLoader
                )
            }

            is UiTree.GlitchTextNode -> {
                layer.addGlitchText(node.text, node.x, node.y, node.scale, node.color, node.effect, node.fontLoader)
            }

            is UiTree.RotatedTextNode -> {
                layer.addRotatedText(
                    node.text, node.x, node.y, node.scale, node.color, node.fontLoader,
                    node.originX, node.originY, node.rotationDegrees
                )
            }

            is UiTree.MarqueeTextNode -> {
                val buffer = target.buffer
                if (buffer != null) {
                    buffer.addMarqueeText(
                        UiContentBuffer.MarqueeTextDraw(
                            node.text, node.x, node.y, node.scale, node.color, node.fontLoader, node.clip
                        )
                    )
                } else {
                    layer.addText(node.text, node.x, node.y, node.scale, node.color, node.fontLoader)
                }
            }

            is UiTree.TextureNode -> {
                layer.addRoundedTexture(
                    node.texture, node.x, node.y, node.width, node.height,
                    node.radiusTopLeft, node.radiusTopRight, node.radiusBottomRight, node.radiusBottomLeft,
                    node.u0, node.v0, node.u1, node.v1, node.color
                )
            }

            is UiTree.RotatedTextureNode -> {
                layer.addRotatedRoundedTexture(
                    node.texture, node.x, node.y, node.width, node.height,
                    node.radiusTopLeft, node.radiusTopRight, node.radiusBottomRight, node.radiusBottomLeft,
                    node.u0, node.v0, node.u1, node.v1, node.color,
                    node.originX, node.originY, node.rotationDegrees
                )
            }

            is UiTree.ButtonNode -> {
                renderButton(
                    target, node.x, node.y, node.width, node.height, node.radius, node.background,
                    node.label, node.labelScale, node.labelColor
                )
            }

            is UiTree.SwitchNode -> {
                renderSwitch(target, node.bounds, node.toggleProgress, node.hoverProgress)
            }

            is UiTree.FilledFieldNode -> {
                renderFilledField(target, node.bounds, node.focused, node.hoverProgress)
            }

            is UiTree.InputNode -> {
                renderInput(target, node.element)
            }

            is UiTree.AssistChipNode -> {
                val bounds = node.bounds
                val trailingIcon = node.trailingIcon
                val trailingIconFont = node.trailingIconFont
                layer.addRoundRect(
                    bounds.x, bounds.y, bounds.width, bounds.height, theme.controlRadius(), node.background
                )
                val textY = bounds.y + (bounds.height - metrics.getHeight(node.textScale)) / 2.0f
                layer.addText(node.label, bounds.x + 8.0f, textY, node.textScale, node.foreground)
                if (trailingIcon != null && !trailingIcon.isEmpty() && trailingIconFont != null) {
                    val iconWidth = metrics.getWidth(trailingIcon, node.trailingIconScale, trailingIconFont)
                    val iconY = bounds.y + (bounds.height - metrics.getHeight(node.trailingIconScale, trailingIconFont)) / 2.0f
                    layer.addText(
                        trailingIcon, bounds.right() - 8.0f - iconWidth, iconY,
                        node.trailingIconScale, node.foreground, trailingIconFont
                    )
                }
            }

            is UiTree.SegmentedControlNode -> {
                renderSegmentedControl(
                    target, node.bounds, node.leadingLabel, node.trailingLabel,
                    node.progress, node.hoverProgress
                )
            }

            is UiTree.IconButtonNode -> {
                val bounds = node.bounds
                layer.addRoundRect(
                    bounds.x, bounds.y, bounds.width, bounds.height, bounds.height / 2.0f,
                    theme.stateLayer(node.tone, node.hoverProgress, 32)
                )
                val labelColor = theme.lerp(theme.textMuted(), node.tone, node.hoverProgress)
                val labelWidth = metrics.getWidth(node.label, node.scale)
                val labelHeight = metrics.getHeight(node.scale)
                layer.addText(
                    node.label,
                    bounds.x + (bounds.width - labelWidth) / 2.0f,
                    bounds.y + (bounds.height - labelHeight) / 2.0f,
                    node.scale,
                    labelColor
                )
            }

            is UiTree.PopupCardNode -> {
                renderPopupCard(target, node.bounds, node.radius, node.blurRadius, node.shadowColor, node.surfaceColor)
            }

            is UiTree.SliderNode -> {
                renderSlider(
                    target, node.bounds, node.progress, node.trackRadius, node.trackColor, node.activeEndInset,
                    node.activeMinWidth, node.activeColor, node.handleWidth, node.handleHeight, node.handleRadius,
                    node.handleColor
                )
            }

            is UiTree.TriangleNode -> {
                layer.addChevronTriangle(node.centerX, node.centerY, node.size, node.progress, node.color)
            }

            is UiTree.ArcNode -> {
                layer.addGradientArc(
                    node.centerX, node.centerY, node.radius, node.strokeWidth, node.startDegrees, node.sweepDegrees,
                    node.roundCap, node.gradientRotationDegrees, node.startColor, node.middleColor, node.endColor
                )
            }

            is UiTree.ViewportNode -> {
                node.buffer.beginViewport(node.viewport)
                renderNodesIntoTarget(node.children, RenderTarget.forContentBuffer(node.buffer), null)
                node.buffer.queueViewport(
                    node.viewport, node.scroll, node.maxScroll, node.contentHeight, node.mouseX, node.mouseY
                )
            }

            else -> {
                // Layer/Layered/Scissor 节点在遍历阶段处理，到这里说明是不可达的组合节点。
            }
        }
    }

    private fun renderButton(
        target: RenderTarget, x: Float, y: Float, width: Float, height: Float, radius: Float,
        background: Color, label: String, labelScale: Float, labelColor: Color
    ) {
        val layer = target.layer
        val metrics = target.textMetrics
        layer.addRoundRect(x, y, width, height, radius, background)
        val labelWidth = metrics.getWidth(label, labelScale)
        val labelHeight = metrics.getHeight(labelScale)
        layer.addText(
            label,
            x + (width - labelWidth) / 2.0f,
            y + (height - labelHeight) / 2.0f,
            labelScale,
            labelColor
        )
    }

    private fun renderPopupCard(
        target: RenderTarget, bounds: UiRect, radius: Float, blurRadius: Float,
        shadowColor: Color, surfaceColor: Color
    ) {
        target.layer.addShadow(bounds.x, bounds.y, bounds.width, bounds.height, radius, blurRadius, shadowColor)
        target.layer.addRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, radius, surfaceColor)
    }

    private fun renderSlider(
        target: RenderTarget, bounds: UiRect, progress: Float, trackRadius: Float,
        trackColor: Color, activeEndInset: Float, activeMinWidth: Float, activeColor: Color,
        handleWidth: Float, handleHeight: Float, handleRadius: Float, handleColor: Color
    ) {
        val layer = target.layer
        val clampedProgress = Math.clamp(progress, 0.0f, 1.0f)
        val safeHandleWidth = Math.max(1.0f, handleWidth)
        val handleX = bounds.x + bounds.width * clampedProgress - safeHandleWidth / 2.0f
        val handleY = bounds.centerY() - handleHeight / 2.0f
        val activeWidth = Math.max(activeMinWidth, bounds.width * clampedProgress - activeEndInset)

        layer.addRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, trackRadius, trackColor)
        if (activeWidth > 0.0f) {
            val clampedActiveWidth = Math.min(bounds.width, activeWidth)
            layer.addRoundRect(
                bounds.x, bounds.y, clampedActiveWidth, bounds.height, trackRadius,
                1.0f, 1.0f, trackRadius, activeColor
            )
        }
        layer.addRoundRect(handleX, handleY, safeHandleWidth, handleHeight, handleRadius, handleColor)
    }

    private fun renderSegmentedControl(
        target: RenderTarget, bounds: UiRect, leadingLabel: String,
        trailingLabel: String, progress: Float, hoverProgress: Float
    ) {
        val layer = target.layer
        val metrics = target.textMetrics
        val theme = target.theme
        val outerRadius = theme.controlRadius()
        val shellInset = 1.0f
        val innerX = bounds.x + shellInset
        val innerY = bounds.y + shellInset
        val innerWidth = bounds.width - shellInset * 2.0f
        val innerHeight = bounds.height - shellInset * 2.0f
        val segmentWidth = innerWidth / 2.0f
        val indicatorInset = 1.5f
        val indicatorWidth = segmentWidth - indicatorInset * 2.0f
        val indicatorX = innerX + indicatorInset + segmentWidth * progress
        val indicatorY = innerY + indicatorInset
        val indicatorHeight = innerHeight - indicatorInset * 2.0f
        val indicatorRadius = Math.max(4.0f, outerRadius - 2.0f)
        val labelScale = 0.52f
        val labelY = innerY + (innerHeight - metrics.getHeight(labelScale)) / 2.0f
        val inactiveLabel = theme.segmentedControlInactiveLabel()
        val activeLabel = theme.segmentedControlActiveLabel()

        layer.addRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, outerRadius, theme.outlineSoft())
        layer.addRoundRect(
            innerX, innerY, innerWidth, innerHeight, Math.max(outerRadius - shellInset, 1.0f),
            theme.segmentedControlSurface()
        )
        if (hoverProgress > 0.01f) {
            layer.addRoundRect(
                innerX, innerY, innerWidth, innerHeight, Math.max(outerRadius - shellInset, 1.0f),
                theme.stateLayer(theme.textPrimary(), hoverProgress, if (theme.light()) 10 else 14)
            )
        }
        layer.addRect(innerX + segmentWidth - 0.5f, innerY + 3.0f, 1.0f, innerHeight - 6.0f, theme.outlineSoft())
        layer.addRoundRect(
            indicatorX, indicatorY, indicatorWidth, indicatorHeight, indicatorRadius,
            theme.segmentedControlIndicator()
        )
        val leadingWidth = metrics.getWidth(leadingLabel, labelScale)
        val trailingWidth = metrics.getWidth(trailingLabel, labelScale)
        layer.addText(
            leadingLabel, innerX + (segmentWidth - leadingWidth) / 2.0f, labelY, labelScale,
            theme.lerp(activeLabel, inactiveLabel, progress)
        )
        layer.addText(
            trailingLabel, innerX + segmentWidth + (segmentWidth - trailingWidth) / 2.0f, labelY, labelScale,
            theme.lerp(inactiveLabel, activeLabel, progress)
        )
    }

    private fun renderSwitch(target: RenderTarget, bounds: UiRect, toggleProgress: Float, hoverProgress: Float) {
        val layer = target.layer
        val theme = target.theme
        val track = theme.switchTrack(toggleProgress)
        val knob = theme.switchKnob(toggleProgress)
        val outline = theme.switchTrackOutline(toggleProgress, hoverProgress)
        val clampedToggle = Math.clamp(toggleProgress, 0.0f, 1.0f)
        val knobSize = theme.switchHandleSizeOff() +
                (theme.switchHandleSizeOn() - theme.switchHandleSizeOff()) * clampedToggle
        val stretchFactor = 4.0f * clampedToggle * (1.0f - clampedToggle)
        val knobWidth = knobSize + 3.5f * stretchFactor
        val inset = theme.switchHandleInsetOff() +
                (theme.switchHandleInsetOn() - theme.switchHandleInsetOff()) * clampedToggle
        val knobMinX = bounds.x + inset + knobWidth / 2.0f
        val knobMaxX = bounds.right() - inset - knobWidth / 2.0f
        val knobCenterX = knobMinX + (knobMaxX - knobMinX) * toggleProgress
        val knobCenterY = bounds.centerY()

        layer.addRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, bounds.height / 2.0f, track)
        if (outline.alpha > 0) {
            layer.addOutline(
                bounds.x, bounds.y, bounds.width, bounds.height,
                bounds.height / 2.0f, theme.switchTrackOutlineWidth(toggleProgress), outline
            )
        }
        if (hoverProgress > 0.02f) {
            val haloSize = theme.switchStateLayerSize()
            val haloX = knobCenterX - haloSize / 2.0f
            val haloY = knobCenterY - haloSize / 2.0f
            layer.addRoundRect(
                haloX, haloY, haloSize, haloSize, haloSize / 2.0f,
                theme.stateLayer(theme.textPrimary(), hoverProgress, 18)
            )
        }
        layer.addRoundRect(
            knobCenterX - knobWidth / 2.0f, knobCenterY - knobSize / 2.0f,
            knobWidth, knobSize, knobSize / 2.0f, knob
        )
    }

    private fun renderFilledField(target: RenderTarget, bounds: UiRect, focused: Boolean, hoverProgress: Float) {
        val theme = target.theme
        target.layer.addRoundRect(
            bounds.x, bounds.y, bounds.width, bounds.height, theme.controlRadius(),
            theme.filledFieldSurface(focused, hoverProgress)
        )
    }

    private fun renderInput(target: RenderTarget, element: UiTree.InputElement) {
        val layer = target.layer
        val metrics = target.textMetrics
        val theme = target.theme
        val bounds = element.bounds
        renderFilledField(target, bounds, element.focused, element.hoverProgress)

        if (element.focusRingProgress > 0.01f && element.focusRingInset > 0.0f) {
            val inset = element.focusRingInset * element.focusRingProgress
            layer.addRoundRect(
                bounds.x - inset,
                bounds.y - inset,
                bounds.width + inset * 2.0f,
                bounds.height + inset * 2.0f,
                theme.controlRadius() + inset,
                theme.withAlpha(element.focusRingColor, (48 * element.focusRingProgress).toInt())
            )
        }

        val text = element.text
        if (text != null) {
            val textX = bounds.x + element.textInset
            val textY = bounds.y + (bounds.height - metrics.getHeight(element.textScale)) / 2.0f

            if (!text.isEmpty()) {
                val selection = element.selection
                val selectionColor = element.selectionColor
                if (selection != null && selectionColor != null) {
                    val start = Math.clamp(selection.start.toLong(), 0, text.length)
                    val end = Math.clamp(selection.end.toLong(), start, text.length)
                    if (end > start) {
                        val selectionX = textX + metrics.getWidth(text.substring(0, start), element.textScale)
                        val selectionWidth = metrics.getWidth(text.substring(start, end), element.textScale)
                        layer.addRect(selectionX, bounds.y + 3.0f, selectionWidth, bounds.height - 6.0f, selectionColor)
                    }
                }

                layer.addText(text, textX, textY, element.textScale, element.textColor)
            }

            val caretIndex = element.caretIndex
            val caretColor = element.caretColor
            if (caretIndex != null && caretColor != null) {
                val safeCaretIndex = Math.clamp(caretIndex.toLong(), 0, text.length)
                val caretX = textX + metrics.getWidth(text.substring(0, safeCaretIndex), element.textScale)
                layer.addRect(caretX, bounds.y + 4.0f, 1.0f, bounds.height - 8.0f, caretColor)
            }
        }

        val trailingHint = element.trailingHint
        val trailingHintColor = element.trailingHintColor
        if (trailingHint != null && !trailingHint.isBlank() && trailingHintColor != null) {
            val hintWidth = metrics.getWidth(trailingHint, element.trailingHintScale)
            val hintY = bounds.y + (bounds.height - metrics.getHeight(element.trailingHintScale)) / 2.0f
            val hintX = bounds.right() - element.textInset - hintWidth
            layer.addText(trailingHint, hintX, hintY, element.trailingHintScale, trailingHintColor)
        }
    }

    private fun applyScissor(layer: Render2DScheduler.LayerHandle, scissor: Render2DScissor?): Render2DScissor? {
        if (scissor == null) {
            return null
        }
        val previous = layer.scissor()
        val effective = intersect(previous, scissor)
        layer.setScissor(effective!!.x, effective.y, effective.width, effective.height)
        return previous
    }

    private fun restoreScissor(
        layer: Render2DScheduler.LayerHandle, activeScissor: Render2DScissor?, previousScissor: Render2DScissor?
    ) {
        if (activeScissor == null) {
            return
        }
        if (previousScissor == null) {
            layer.clearScissor()
        } else {
            layer.setScissor(previousScissor.x, previousScissor.y, previousScissor.width, previousScissor.height)
        }
    }

    private fun toScissor(rect: UiRect): Render2DScissor {
        val scissor = LuminRenderSystem.toFramebufferScissor(rect.x, rect.y, rect.width, rect.height)
        return Render2DScissor(scissor.x, scissor.y, scissor.width, scissor.height)
    }

    private fun intersect(a: Render2DScissor?, b: Render2DScissor?): Render2DScissor? {
        if (a == null) {
            return b
        }
        if (b == null) {
            return a
        }
        val x = Math.max(a.x, b.x)
        val y = Math.max(a.y, b.y)
        val right = Math.min(a.x + a.width, b.x + b.width)
        val bottom = Math.min(a.y + a.height, b.y + b.height)
        return Render2DScissor(x, y, Math.max(0, right - x), Math.max(0, bottom - y))
    }

    private data class RenderTarget(
        val layer: Render2DScheduler.LayerHandle,
        val textMetrics: TextRenderer,
        val buffer: UiContentBuffer?,
        val theme: UiTheme,
    ) {
        companion object {

            fun forLayer(
                layer: Render2DScheduler.LayerHandle, textMetrics: TextRenderer,
                buffer: UiContentBuffer?, theme: UiTheme
            ): RenderTarget {
                return RenderTarget(layer, textMetrics, buffer, theme)
            }

            fun forContentBuffer(buffer: UiContentBuffer): RenderTarget {
                return RenderTarget(buffer.contentLayer(), buffer.textMetrics(), buffer, buffer.theme())
            }

        }
    }

}
