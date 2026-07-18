package com.github.epsilon.gui.lib.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;
import com.github.epsilon.graphics.schedulers.render2d.Render2DScissor;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTheme;
import com.github.epsilon.gui.lib.UiTree;

import java.awt.*;
import java.util.List;

/**
 * 将 {@link UiTree} 编译为 {@link Render2DScheduler} 命令。
 */
public final class LuminUiRenderer {

    private LuminUiRenderer() {
    }

    static void renderIntoLayeredBatch(UiTree tree, UiRenderBatch batch, int baseLayer) {
        renderNodesIntoLayeredBatch(tree.nodes(), batch, baseLayer, null);
    }

    private static void renderNodesIntoLayeredBatch(List<UiTree.UiNode> nodes, UiRenderBatch batch,
                                                    int layer, Render2DScissor activeScissor) {
        Render2DScheduler.LayerHandle handle = batch.absoluteLayer(layer);
        Render2DScissor previousScissor = applyScissor(handle, activeScissor);
        RenderTarget target = RenderTarget.forLayer(handle, batch.scheduler().textMetrics(), null, batch.theme());
        try {
            for (UiTree.UiNode node : nodes) {
                if (node instanceof UiTree.LayerNode(int childLayer, List<UiTree.UiNode> children)) {
                    renderNodesIntoLayeredBatch(children, batch, layer + childLayer, activeScissor);
                    continue;
                }
                if (node instanceof UiTree.LayeredNode(int childLayer, UiTree.UiNode child)) {
                    renderNodesIntoLayeredBatch(List.of(child), batch, layer + childLayer, activeScissor);
                    continue;
                }
                if (node instanceof UiTree.ScissorNode(UiRect clip, List<UiTree.UiNode> children)) {
                    renderNodesIntoLayeredBatch(children, batch, layer, intersect(activeScissor, toScissor(clip)));
                    continue;
                }
                renderNode(node, target);
            }
        } finally {
            restoreScissor(handle, activeScissor, previousScissor);
        }
    }

    private static void renderNodesIntoTarget(List<UiTree.UiNode> nodes, RenderTarget target,
                                              Render2DScissor activeScissor) {
        Render2DScissor previousScissor = applyScissor(target.layer(), activeScissor);
        try {
            for (UiTree.UiNode node : nodes) {
                if (node instanceof UiTree.LayerNode(int ignoredLayer, List<UiTree.UiNode> children)) {
                    renderNodesIntoTarget(children, target, activeScissor);
                    continue;
                }
                if (node instanceof UiTree.LayeredNode(int ignoredLayer, UiTree.UiNode child)) {
                    renderNodesIntoTarget(List.of(child), target, activeScissor);
                    continue;
                }
                if (node instanceof UiTree.ScissorNode(UiRect clip, List<UiTree.UiNode> children)) {
                    renderNodesIntoTarget(children, target, intersect(activeScissor, toScissor(clip)));
                    continue;
                }
                renderNode(node, target);
            }
        } finally {
            restoreScissor(target.layer(), activeScissor, previousScissor);
        }
    }

    private static void renderNode(UiTree.UiNode node, RenderTarget target) {
        Render2DScheduler.LayerHandle layer = target.layer();
        TextRenderer metrics = target.textMetrics();
        UiTheme theme = target.theme();

        if (node instanceof UiTree.ShadowNode(
                float x, float y, float width, float height, float topLeft, float topRight, float bottomRight,
                float bottomLeft, float blurRadius, Color color
        )) {
            layer.addShadow(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, blurRadius, color);
            return;
        }
        if (node instanceof UiTree.RoundRectNode(
                float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight,
                float radiusBottomRight, float radiusBottomLeft, Color color
        )) {
            layer.addRoundRect(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color);
            return;
        }
        if (node instanceof UiTree.RoundRectGradientNode(
                float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight,
                float radiusBottomRight, float radiusBottomLeft, Color topLeft, Color bottomLeft,
                Color bottomRight, Color topRight
        )) {
            layer.addRoundRectGradient(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    topLeft, bottomLeft, bottomRight, topRight);
            return;
        }
        if (node instanceof UiTree.RectNode(float x, float y, float width, float height, Color color)) {
            layer.addRect(x, y, width, height, color);
            return;
        }
        if (node instanceof UiTree.RectGradientNode(
                float x, float y, float width, float height, Color topLeft, Color bottomLeft,
                Color bottomRight, Color topRight
        )) {
            layer.addRectGradient(x, y, width, height, topLeft, bottomLeft, bottomRight, topRight);
            return;
        }
        if (node instanceof UiTree.RectOutlineNode(
                float x, float y, float width, float height, float outlineWidth, Color color
        )) {
            layer.addRectOutline(x, y, width, height, outlineWidth, color);
            return;
        }
        if (node instanceof UiTree.OutlineNode(
                float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight,
                float radiusBottomRight, float radiusBottomLeft, float outlineWidth, Color color
        )) {
            layer.addOutline(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    outlineWidth, color);
            return;
        }
        if (node instanceof UiTree.TextNode(
                String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader
        )) {
            layer.addText(text, x, y, scale, color, fontLoader);
            return;
        }
        if (node instanceof UiTree.RotatedTextNode(
                String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, float originX,
                float originY, float rotationDegrees
        )) {
            layer.addRotatedText(text, x, y, scale, color, fontLoader, originX, originY, rotationDegrees);
            return;
        }
        if (node instanceof UiTree.MarqueeTextNode(
                String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, UiRect clip
        )) {
            if (target.buffer() != null) {
                target.buffer().addMarqueeText(new UiContentBuffer.MarqueeTextDraw(text, x, y, scale, color, fontLoader, clip));
            } else {
                layer.addText(text, x, y, scale, color, fontLoader);
            }
            return;
        }
        if (node instanceof UiTree.TextureNode(
                var texture, float x, float y, float width, float height,
                float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                float u0, float v0, float u1, float v1, Color color
        )) {
            layer.addRoundedTexture(texture, x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    u0, v0, u1, v1, color);
            return;
        }
        if (node instanceof UiTree.RotatedTextureNode(
                var texture, float x, float y, float width, float height,
                float u0, float v0, float u1, float v1, Color color,
                float originX, float originY, float rotationDegrees
        )) {
            layer.addRotatedTexture(texture, x, y, width, height, u0, v0, u1, v1, color, originX, originY, rotationDegrees);
            return;
        }
        if (node instanceof UiTree.ButtonNode(
                float x, float y, float width, float height, float radius, Color background,
                String label, float labelScale, Color labelColor
        )) {
            renderButton(target, x, y, width, height, radius, background, label, labelScale, labelColor);
            return;
        }
        if (node instanceof UiTree.SwitchNode(
                UiRect bounds, float toggleProgress, float hoverProgress
        )) {
            renderSwitch(target, bounds, toggleProgress, hoverProgress);
            return;
        }
        if (node instanceof UiTree.FilledFieldNode(
                UiRect bounds, boolean focused, float hoverProgress
        )) {
            renderFilledField(target, bounds, focused, hoverProgress);
            return;
        }
        if (node instanceof UiTree.InputNode(UiTree.InputElement element)) {
            renderInput(target, element);
            return;
        }
        if (node instanceof UiTree.AssistChipNode(
                UiRect bounds, String label, float textScale, Color background, Color foreground,
                String trailingIcon, float trailingIconScale, TtfFontLoader trailingIconFont
        )) {
            layer.addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), theme.controlRadius(), background);
            float textY = bounds.y() + (bounds.height() - metrics.getHeight(textScale)) / 2.0f;
            layer.addText(label, bounds.x() + 8.0f, textY, textScale, foreground);
            if (trailingIcon != null && !trailingIcon.isEmpty() && trailingIconFont != null) {
                float iconWidth = metrics.getWidth(trailingIcon, trailingIconScale, trailingIconFont);
                float iconY = bounds.y() + (bounds.height() - metrics.getHeight(trailingIconScale, trailingIconFont)) / 2.0f;
                layer.addText(trailingIcon, bounds.right() - 8.0f - iconWidth, iconY, trailingIconScale, foreground, trailingIconFont);
            }
            return;
        }
        if (node instanceof UiTree.SegmentedControlNode(
                UiRect bounds, String leadingLabel, String trailingLabel, float progress, float hoverProgress
        )) {
            renderSegmentedControl(target, bounds, leadingLabel, trailingLabel, progress, hoverProgress);
            return;
        }
        if (node instanceof UiTree.IconButtonNode(
                UiRect bounds, String label, float scale, Color tone, float hoverProgress
        )) {
            layer.addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), bounds.height() / 2.0f,
                    theme.stateLayer(tone, hoverProgress, 32));
            Color labelColor = theme.lerp(theme.textMuted(), tone, hoverProgress);
            float labelWidth = metrics.getWidth(label, scale);
            float labelHeight = metrics.getHeight(scale);
            layer.addText(label,
                    bounds.x() + (bounds.width() - labelWidth) / 2.0f,
                    bounds.y() + (bounds.height() - labelHeight) / 2.0f,
                    scale,
                    labelColor);
            return;
        }
        if (node instanceof UiTree.PopupCardNode(
                UiRect bounds, float radius, float blurRadius, Color shadowColor, Color surfaceColor
        )) {
            renderPopupCard(target, bounds, radius, blurRadius, shadowColor, surfaceColor);
            return;
        }
        if (node instanceof UiTree.SliderNode(
                UiRect bounds, float progress, float trackRadius, Color trackColor, float activeEndInset,
                float activeMinWidth, Color activeColor, float handleWidth, float handleHeight, float handleRadius,
                Color handleColor
        )) {
            renderSlider(target, bounds, progress, trackRadius, trackColor, activeEndInset, activeMinWidth, activeColor,
                    handleWidth, handleHeight, handleRadius, handleColor);
            return;
        }
        if (node instanceof UiTree.TriangleNode(
                float centerX, float centerY, float size, float progress, Color color
        )) {
            layer.addChevronTriangle(centerX, centerY, size, progress, color);
            return;
        }
        if (node instanceof UiTree.ViewportNode(
                UiContentBuffer buffer, UiRect viewport, float scroll, float maxScroll,
                float contentHeight, int mouseX, int mouseY, List<UiTree.UiNode> children
        )) {
            buffer.beginViewport(viewport);
            renderNodesIntoTarget(children, RenderTarget.forContentBuffer(buffer), null);
            buffer.queueViewport(viewport, scroll, maxScroll, contentHeight, mouseX, mouseY);
        }
    }

    private static void renderButton(RenderTarget target, float x, float y, float width, float height, float radius,
                                     Color background, String label, float labelScale, Color labelColor) {
        Render2DScheduler.LayerHandle layer = target.layer();
        TextRenderer metrics = target.textMetrics();
        layer.addRoundRect(x, y, width, height, radius, background);
        float labelWidth = metrics.getWidth(label, labelScale);
        float labelHeight = metrics.getHeight(labelScale);
        layer.addText(label,
                x + (width - labelWidth) / 2.0f,
                y + (height - labelHeight) / 2.0f,
                labelScale,
                labelColor);
    }

    private static void renderPopupCard(RenderTarget target, UiRect bounds, float radius, float blurRadius,
                                        Color shadowColor, Color surfaceColor) {
        target.layer().addShadow(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, blurRadius, shadowColor);
        target.layer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius, surfaceColor);
    }

    private static void renderSlider(RenderTarget target, UiRect bounds, float progress, float trackRadius,
                                     Color trackColor, float activeEndInset, float activeMinWidth, Color activeColor,
                                     float handleWidth, float handleHeight, float handleRadius, Color handleColor) {
        Render2DScheduler.LayerHandle layer = target.layer();
        float clampedProgress = Math.clamp(progress, 0.0f, 1.0f);
        float safeHandleWidth = Math.max(1.0f, handleWidth);
        float handleX = bounds.x() + bounds.width() * clampedProgress - safeHandleWidth / 2.0f;
        float handleY = bounds.centerY() - handleHeight / 2.0f;
        float activeWidth = Math.max(activeMinWidth, bounds.width() * clampedProgress - activeEndInset);

        layer.addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), trackRadius, trackColor);
        if (activeWidth > 0.0f) {
            float clampedActiveWidth = Math.min(bounds.width(), activeWidth);
            layer.addRoundRect(bounds.x(), bounds.y(), clampedActiveWidth, bounds.height(), trackRadius, 1.0f, 1.0f, trackRadius, activeColor);
        }
        layer.addRoundRect(handleX, handleY, safeHandleWidth, handleHeight, handleRadius, handleColor);
    }

    private static void renderSegmentedControl(RenderTarget target, UiRect bounds, String leadingLabel,
                                               String trailingLabel, float progress, float hoverProgress) {
        Render2DScheduler.LayerHandle layer = target.layer();
        TextRenderer metrics = target.textMetrics();
        UiTheme theme = target.theme();
        float outerRadius = theme.controlRadius();
        float shellInset = 1.0f;
        float innerX = bounds.x() + shellInset;
        float innerY = bounds.y() + shellInset;
        float innerWidth = bounds.width() - shellInset * 2.0f;
        float innerHeight = bounds.height() - shellInset * 2.0f;
        float segmentWidth = innerWidth / 2.0f;
        float indicatorInset = 1.5f;
        float indicatorWidth = segmentWidth - indicatorInset * 2.0f;
        float indicatorX = innerX + indicatorInset + segmentWidth * progress;
        float indicatorY = innerY + indicatorInset;
        float indicatorHeight = innerHeight - indicatorInset * 2.0f;
        float indicatorRadius = Math.max(4.0f, outerRadius - 2.0f);
        float labelScale = 0.52f;
        float labelY = innerY + (innerHeight - metrics.getHeight(labelScale)) / 2.0f;
        Color inactiveLabel = theme.segmentedControlInactiveLabel();
        Color activeLabel = theme.segmentedControlActiveLabel();

        layer.addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), outerRadius, theme.outlineSoft());
        layer.addRoundRect(innerX, innerY, innerWidth, innerHeight, Math.max(outerRadius - shellInset, 1.0f), theme.segmentedControlSurface());
        if (hoverProgress > 0.01f) {
            layer.addRoundRect(innerX, innerY, innerWidth, innerHeight, Math.max(outerRadius - shellInset, 1.0f),
                    theme.stateLayer(theme.textPrimary(), hoverProgress, theme.light() ? 10 : 14));
        }
        layer.addRect(innerX + segmentWidth - 0.5f, innerY + 3.0f, 1.0f, innerHeight - 6.0f, theme.outlineSoft());
        layer.addRoundRect(indicatorX, indicatorY, indicatorWidth, indicatorHeight, indicatorRadius, theme.segmentedControlIndicator());
        float leadingWidth = metrics.getWidth(leadingLabel, labelScale);
        float trailingWidth = metrics.getWidth(trailingLabel, labelScale);
        layer.addText(leadingLabel, innerX + (segmentWidth - leadingWidth) / 2.0f, labelY, labelScale, theme.lerp(activeLabel, inactiveLabel, progress));
        layer.addText(trailingLabel, innerX + segmentWidth + (segmentWidth - trailingWidth) / 2.0f, labelY, labelScale, theme.lerp(inactiveLabel, activeLabel, progress));
    }

    private static void renderSwitch(RenderTarget target, UiRect bounds, float toggleProgress, float hoverProgress) {
        Render2DScheduler.LayerHandle layer = target.layer();
        UiTheme theme = target.theme();
        Color track = theme.switchTrack(toggleProgress);
        Color knob = theme.switchKnob(toggleProgress);
        Color outline = theme.switchTrackOutline(toggleProgress, hoverProgress);
        float clampedToggle = Math.clamp(toggleProgress, 0.0f, 1.0f);
        float knobSize = theme.switchHandleSizeOff()
                + (theme.switchHandleSizeOn() - theme.switchHandleSizeOff()) * clampedToggle;
        float stretchFactor = 4.0f * clampedToggle * (1.0f - clampedToggle);
        float knobWidth = knobSize + 3.5f * stretchFactor;
        float inset = theme.switchHandleInsetOff()
                + (theme.switchHandleInsetOn() - theme.switchHandleInsetOff()) * clampedToggle;
        float knobMinX = bounds.x() + inset + knobWidth / 2.0f;
        float knobMaxX = bounds.right() - inset - knobWidth / 2.0f;
        float knobCenterX = knobMinX + (knobMaxX - knobMinX) * toggleProgress;
        float knobCenterY = bounds.centerY();

        layer.addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), bounds.height() / 2.0f, track);
        if (outline.getAlpha() > 0) {
            layer.addOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    bounds.height() / 2.0f, theme.switchTrackOutlineWidth(toggleProgress), outline);
        }
        if (hoverProgress > 0.02f) {
            float haloSize = theme.switchStateLayerSize();
            float haloX = knobCenterX - haloSize / 2.0f;
            float haloY = knobCenterY - haloSize / 2.0f;
            layer.addRoundRect(haloX, haloY, haloSize, haloSize, haloSize / 2.0f,
                    theme.stateLayer(theme.textPrimary(), hoverProgress, 18));
        }
        layer.addRoundRect(knobCenterX - knobWidth / 2.0f, knobCenterY - knobSize / 2.0f,
                knobWidth, knobSize, knobSize / 2.0f, knob);
    }

    private static void renderFilledField(RenderTarget target, UiRect bounds, boolean focused, float hoverProgress) {
        UiTheme theme = target.theme();
        target.layer().addRoundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), theme.controlRadius(),
                theme.filledFieldSurface(focused, hoverProgress));
    }

    private static void renderInput(RenderTarget target, UiTree.InputElement element) {
        Render2DScheduler.LayerHandle layer = target.layer();
        TextRenderer metrics = target.textMetrics();
        UiTheme theme = target.theme();
        UiRect bounds = element.bounds();
        renderFilledField(target, bounds, element.focused(), element.hoverProgress());

        if (element.focusRingProgress() > 0.01f && element.focusRingInset() > 0.0f) {
            float inset = element.focusRingInset() * element.focusRingProgress();
            layer.addRoundRect(
                    bounds.x() - inset,
                    bounds.y() - inset,
                    bounds.width() + inset * 2.0f,
                    bounds.height() + inset * 2.0f,
                    theme.controlRadius() + inset,
                    theme.withAlpha(element.focusRingColor(), (int) (48 * element.focusRingProgress()))
            );
        }

        String text = element.text();
        if (text != null && !text.isEmpty()) {
            float textX = bounds.x() + element.textInset();
            float textY = bounds.y() + (bounds.height() - metrics.getHeight(element.textScale())) / 2.0f;

            UiTree.SelectionRange selection = element.selection();
            if (selection != null && element.selectionColor() != null) {
                int start = Math.clamp(selection.start(), 0, text.length());
                int end = Math.clamp(selection.end(), start, text.length());
                if (end > start) {
                    float selectionX = textX + metrics.getWidth(text.substring(0, start), element.textScale());
                    float selectionWidth = metrics.getWidth(text.substring(start, end), element.textScale());
                    layer.addRect(selectionX, bounds.y() + 3.0f, selectionWidth, bounds.height() - 6.0f, element.selectionColor());
                }
            }

            layer.addText(text, textX, textY, element.textScale(), element.textColor());

            if (element.caretIndex() != null && element.caretColor() != null) {
                int caretIndex = Math.clamp(element.caretIndex(), 0, text.length());
                float caretX = textX + metrics.getWidth(text.substring(0, caretIndex), element.textScale());
                layer.addRect(caretX, bounds.y() + 4.0f, 1.0f, bounds.height() - 8.0f, element.caretColor());
            }
        }

        if (element.trailingHint() != null && !element.trailingHint().isBlank() && element.trailingHintColor() != null) {
            float hintWidth = metrics.getWidth(element.trailingHint(), element.trailingHintScale());
            float hintY = bounds.y() + (bounds.height() - metrics.getHeight(element.trailingHintScale())) / 2.0f;
            float hintX = bounds.right() - element.textInset() - hintWidth;
            layer.addText(element.trailingHint(), hintX, hintY, element.trailingHintScale(), element.trailingHintColor());
        }
    }

    private static Render2DScissor applyScissor(Render2DScheduler.LayerHandle layer, Render2DScissor scissor) {
        if (scissor == null) {
            return null;
        }
        Render2DScissor previous = layer.scissor();
        Render2DScissor effective = intersect(previous, scissor);
        layer.setScissor(effective.x(), effective.y(), effective.width(), effective.height());
        return previous;
    }

    private static void restoreScissor(Render2DScheduler.LayerHandle layer, Render2DScissor activeScissor,
                                       Render2DScissor previousScissor) {
        if (activeScissor == null) {
            return;
        }
        if (previousScissor == null) {
            layer.clearScissor();
        } else {
            layer.setScissor(previousScissor.x(), previousScissor.y(), previousScissor.width(), previousScissor.height());
        }
    }

    private static Render2DScissor toScissor(UiRect rect) {
        LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(rect.x(), rect.y(), rect.width(), rect.height());
        return new Render2DScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
    }

    private static Render2DScissor intersect(Render2DScissor a, Render2DScissor b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        int x = Math.max(a.x(), b.x());
        int y = Math.max(a.y(), b.y());
        int right = Math.min(a.x() + a.width(), b.x() + b.width());
        int bottom = Math.min(a.y() + a.height(), b.y() + b.height());
        return new Render2DScissor(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    private record RenderTarget(Render2DScheduler.LayerHandle layer, TextRenderer textMetrics,
                                UiContentBuffer buffer, UiTheme theme) {
        private static RenderTarget forLayer(Render2DScheduler.LayerHandle layer, TextRenderer textMetrics,
                                             UiContentBuffer buffer, UiTheme theme) {
            return new RenderTarget(layer, textMetrics, buffer, theme);
        }

        private static RenderTarget forContentBuffer(UiContentBuffer buffer) {
            return new RenderTarget(buffer.contentLayer(), buffer.textMetrics(), buffer, buffer.theme());
        }
    }

}
