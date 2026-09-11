package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.render.ColorUtils;
import com.github.epsilon.utils.render.animation.Easing;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class ModuleList extends HudModule {

    public static final ModuleList INSTANCE = new ModuleList();

    private ModuleList() {
        super("Module List", 4.0f, 16.0f, 96.0f, 20.0f);
    }

    private enum DisplayMode {
        Simple,
        Normal
    }

    private enum ColorMode {
        Single,
        Double,
        Rainbow,
        Fade
    }

    private final EnumSetting<DisplayMode> mode = enumSetting("Mode", DisplayMode.Normal);
    private final DoubleSetting horizontalPadding = doubleSetting("Horizontal Padding", 3.0, 0.0, 20.0, 0.5, () -> mode.is(DisplayMode.Normal));
    private final DoubleSetting verticalPadding = doubleSetting("Vertical Padding", 2.0, 0.0, 20.0, 0.5, () -> mode.is(DisplayMode.Normal));
    private final DoubleSetting lineWidth = doubleSetting("Line Width", 2.0, 0.0, 5.0, 0.5, () -> mode.is(DisplayMode.Normal));
    private final BoolSetting showSuffix = boolSetting("Show Suffix", true);
    private final DoubleSetting fontSize = doubleSetting("Font Size", 13.0, 8.0, 32.0, 1.0);
    private final BoolSetting textGlow = boolSetting("Text Glow", true);
    private final DoubleSetting glowRadius = doubleSetting("Glow Radius", 3.0, 0.1, 6.0, 0.1, textGlow::getValue);
    private final IntSetting glowIntensity = intSetting("Glow Intensity", 2, 1, 5, 1, textGlow::getValue);
    private final DoubleSetting sliderSpeed = doubleSetting("Slider Speed", 0.2, 0.01, 1.0, 0.01);
    private final BoolSetting background = boolSetting("Background", true, () -> mode.is(DisplayMode.Normal));
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(0, 0, 0, 50), () -> mode.is(DisplayMode.Normal) && background.getValue());
    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true, () -> mode.is(DisplayMode.Normal));
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 9.0, 2.0, 32.0, 1.0, () -> mode.is(DisplayMode.Normal) && drawShadow.getValue());
    private final EnumSetting<ColorMode> shadowColorMode = enumSetting("Shadow Color Mode", ColorMode.Single, () -> mode.is(DisplayMode.Normal) && drawShadow.getValue());
    private final DoubleSetting shadowGradientLength = doubleSetting("Shadow Gradient Length", 100.0, 10.0, 1000.0, 10.0, () -> mode.is(DisplayMode.Normal) && drawShadow.getValue() && (shadowColorMode.is(ColorMode.Double) || shadowColorMode.is(ColorMode.Fade)));
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(255, 255, 255, 90), () -> mode.is(DisplayMode.Normal) && drawShadow.getValue() && !shadowColorMode.is(ColorMode.Rainbow));
    private final ColorSetting shadowColor2 = colorSetting("Shadow Color 2", new Color(255, 0, 0, 90), () -> mode.is(DisplayMode.Normal) && drawShadow.getValue() && shadowColorMode.is(ColorMode.Double));
    private final EnumSetting<ColorMode> colorMode = enumSetting("Color Mode", ColorMode.Single);
    private final DoubleSetting doubleGradientLength = doubleSetting("Double Gradient Length", 100.0, 10.0, 1000.0, 10.0, () -> mode.is(DisplayMode.Normal) && (colorMode.is(ColorMode.Double) || colorMode.is(ColorMode.Fade)));
    private final DoubleSetting rainbowSpeed = doubleSetting("Rainbow Speed", 1.0, 0.1, 5.0, 0.1, this::usesRainbow);
    private final DoubleSetting rainbowSpread = doubleSetting("Rainbow Spread", 8.0, 0.0, 60.0, 1.0, this::usesRainbow);
    private final DoubleSetting rainbowHueOffset = doubleSetting("Rainbow Hue Offset", 0.0, 0.0, 360.0, 1.0, this::usesRainbow);
    private final DoubleSetting rainbowSaturation = doubleSetting("Rainbow Saturation", 0.5, 0.0, 1.0, 0.05, this::usesRainbow);
    private final DoubleSetting rainbowBrightness = doubleSetting("Rainbow Brightness", 1.0, 0.1, 1.0, 0.05, this::usesRainbow);
    private final IntSetting rainbowAlpha = intSetting("Rainbow Alpha", 255, 0, 255, 1, () -> colorMode.is(ColorMode.Rainbow));
    private final IntSetting shadowRainbowAlpha = intSetting("Shadow Rainbow Alpha", 90, 0, 255, 1, () -> mode.is(DisplayMode.Normal) && drawShadow.getValue() && shadowColorMode.is(ColorMode.Rainbow));
    private final BoolSetting blur = boolSetting("Blur", true, () -> mode.is(DisplayMode.Normal));
    private final IntSetting blurStrength = intSetting("Blur Strength", 8, 1, 20, 1, () -> mode.is(DisplayMode.Normal) && blur.getValue());
    private final ColorSetting themeColor1 = colorSetting("Theme Color 1", Color.WHITE, false, () -> !colorMode.is(ColorMode.Rainbow));
    private final ColorSetting themeColor2 = colorSetting("Theme Color 2", Color.RED, false, () -> colorMode.is(ColorMode.Double));

    private static final int MAX_SEGMENTS = 64;
    private static final float MIN_BOUNDS = 20.0f;
    private static final int MODULE_ANIMATION_DURATION_MS = 200;

    private final Map<Module, ToggleAnimation> moduleAnimations = new HashMap<>();
    private final Map<Module, Float> moduleYPositions = new HashMap<>();
    private List<Module> lastSortedModules = new ArrayList<>();

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    public void render(DeltaTracker deltaTracker) {
        TextRenderer textRenderer = textRendererSupplier.get();

        List<Module> modules = ModuleManager.INSTANCE.getModules();
        Set<Module> liveModules = new HashSet<>(modules);
        moduleAnimations.keySet().removeIf(module -> !liveModules.contains(module));
        moduleYPositions.keySet().removeIf(module -> !liveModules.contains(module));

        long now = System.currentTimeMillis();
        for (Module module : modules) {
            if (!isVisible(module)) continue;
            moduleAnimations.computeIfAbsent(module, ignored -> new ToggleAnimation(now)).update(true, now);
        }

        var iterator = moduleAnimations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Module, ToggleAnimation> entry = iterator.next();
            if (isVisible(entry.getKey())) continue;

            float progress = entry.getValue().update(false, now);
            if (progress <= 0.0f) {
                iterator.remove();
                moduleYPositions.remove(entry.getKey());
            }
        }

        if (mode.is(DisplayMode.Simple)) {
            renderSimple(textRenderer);
        } else {
            renderNormal(textRenderer);
        }
    }

    private void renderSimple(TextRenderer textRenderer) {
        float textScale = Math.max(0.1f, fontSize.getValue().floatValue() / 16.0f);
        List<RenderEntry> entries = collectRenderEntries(textRenderer, textScale, showSuffix.getValue());
        LayoutState layout = updateLayout(entries, textRenderer.getHeight(textScale) + 3.0f);

        float boundsWidth = Math.max(MIN_BOUNDS, maxDisplayWidth(entries));
        setBounds(boundsWidth, Math.max(MIN_BOUNDS, layout.height()));
        if (entries.isEmpty()) return;

        UiTree.Scope scope = renderScope();
        boolean rightSide = this.x + this.width * 0.5f > LuminRenderSystem.getScaledWidth() * 0.5f;

        for (int i = 0; i < entries.size(); i++) {
            RenderEntry entry = entries.get(i);
            float animation = entry.animation();
            if (animation <= 0.01f) continue;

            float slideOffset = entry.displayWidth() * (1.0f - animation);
            float textX = rightSide
                    ? this.x + boundsWidth - entry.displayWidth() + slideOffset
                    : this.x - slideOffset;
            float textY = this.y + layout.renderY()[i];
            Color color = withAlpha(resolveColor(i, 100L), animation);

            drawModuleText(scope, entry, textX, textY, textScale, color, animation);
        }
    }

    private void renderNormal(TextRenderer textRenderer) {
        float textScale = Math.max(0.1f, fontSize.getValue().floatValue() / 16.0f);
        float textHeight = textRenderer.getHeight(textScale);
        float horizontalPadding = this.horizontalPadding.getValue().floatValue();
        float lineHeight = textHeight + this.verticalPadding.getValue().floatValue() * 2.0f;

        List<RenderEntry> entries = collectRenderEntries(textRenderer, textScale, showSuffix.getValue());
        LayoutState layout = updateLayout(entries, lineHeight);
        float maxTextWidth = maxDisplayWidth(entries);
        float boundsWidth = Math.max(MIN_BOUNDS, maxTextWidth + horizontalPadding * 2.0f);
        setBounds(boundsWidth, Math.max(MIN_BOUNDS, layout.height()));
        if (entries.isEmpty()) return;

        boolean rightSide = this.x + this.width * 0.5f > LuminRenderSystem.getScaledWidth() * 0.5f;
        float baseTextX = this.x + horizontalPadding;
        long gradientStep = doubleGradientLength.getValue().longValue();
        List<NormalRow> rows = new ArrayList<>();
        int colorIndex = 0;

        for (int i = 0; i < entries.size(); i++) {
            RenderEntry entry = entries.get(i);
            float animation = entry.animation();
            if (animation <= 0.01f) continue;

            float backgroundWidth = entry.displayWidth() + horizontalPadding * 2.0f;
            float slideOffset = backgroundWidth * (1.0f - animation);
            float textX = rightSide ? baseTextX + maxTextWidth - entry.displayWidth() + slideOffset : baseTextX - slideOffset;
            float backgroundX = textX - horizontalPadding;
            float backgroundY = this.y + layout.renderY()[i];
            float backgroundHeight = lineHeight * animation;
            Color startColor = rows.isEmpty() ? resolveColor(colorIndex, gradientStep) : rows.getLast().endColor();
            Color endColor = resolveColor(++colorIndex, gradientStep);

            rows.add(new NormalRow(entry, textX, backgroundX, backgroundY, backgroundWidth, backgroundHeight, startColor, endColor));
        }

        List<SegmentBatch> segmentBatches = buildSegmentBatches(rows);
        if (blur.getValue()) renderBlur(segmentBatches);

        UiTree.Scope scope = renderScope();
        if (drawShadow.getValue()) renderShadow(scope, segmentBatches);

        for (NormalRow row : rows) {
            float animation = row.entry().animation();
            if (background.getValue()) {
                scope.rect(row.backgroundX(), row.backgroundY(), row.backgroundWidth(), row.backgroundHeight(), withAlpha(backgroundColor.getValue(), animation));
            }

            float textY = row.backgroundY() + (row.backgroundHeight() - textHeight) * 0.5f;
            drawModuleText(scope, row.entry(), row.textX(), textY, textScale, withAlpha(row.startColor(), animation), withAlpha(row.endColor(), animation), animation);
        }

        float lineWidth = this.lineWidth.getValue().floatValue() * textScale;
        for (int i = 0; i < rows.size(); i++) {
            NormalRow row = rows.get(i);
            float lineX = rightSide ? row.backgroundX() + row.backgroundWidth() : row.backgroundX() - lineWidth;
            float lineBottom = row.backgroundY() + row.backgroundHeight();
            if (i + 1 < rows.size()) {
                lineBottom = Math.max(lineBottom, rows.get(i + 1).backgroundY());
            }
            float bottomAnimation = i + 1 < rows.size() ? rows.get(i + 1).entry().animation() : row.entry().animation();
            scope.rectVerticalGradient(lineX, row.backgroundY(), lineWidth, Math.max(0.0f, lineBottom - row.backgroundY()), withAlpha(row.startColor(), row.entry().animation()), withAlpha(row.endColor(), bottomAnimation));
        }
    }

    private List<SegmentBatch> buildSegmentBatches(List<NormalRow> rows) {
        List<SegmentBatch> batches = new ArrayList<>((rows.size() + MAX_SEGMENTS - 1) / MAX_SEGMENTS);
        long gradientStep = shadowGradientLength.getValue().longValue();
        for (int offset = 0; offset < rows.size(); offset += MAX_SEGMENTS) {
            int count = Math.min(MAX_SEGMENTS, rows.size() - offset);
            float[] segmentRects = new float[count * 4];
            float[] segmentRadii = new float[count];
            Color[] segmentColors = new Color[count];
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;

            for (int i = 0; i < count; i++) {
                NormalRow row = rows.get(offset + i);
                int index = i * 4;
                segmentRects[index] = row.backgroundX();
                segmentRects[index + 1] = row.backgroundY();
                segmentRects[index + 2] = row.backgroundWidth();
                segmentRects[index + 3] = row.backgroundHeight();
                segmentColors[i] = resolveShadowColor(offset + i, gradientStep);

                minX = Math.min(minX, row.backgroundX());
                minY = Math.min(minY, row.backgroundY());
                maxX = Math.max(maxX, row.backgroundX() + row.backgroundWidth());
                maxY = Math.max(maxY, row.backgroundY() + row.backgroundHeight());
            }

            if (maxX > minX && maxY > minY) {
                batches.add(new SegmentBatch(minX, minY, maxX - minX, maxY - minY, segmentRects, segmentRadii, segmentColors, count));
            }
        }
        return batches;
    }

    private void renderBlur(List<SegmentBatch> batches) {
        for (SegmentBatch batch : batches) {
            BlurShader.INSTANCE.render(batch.x(), batch.y(), batch.width(), batch.height(), 0.0f, blurStrength.getValue(), batch.rects(), batch.radii(), batch.count());
        }
    }

    private void renderShadow(UiTree.Scope scope, List<SegmentBatch> batches) {
        float blurRadius = shadowBlur.getValue().floatValue();
        Color color = shadowColor.getValue();
        if (shadowColorMode.is(ColorMode.Rainbow)) {
            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), shadowRainbowAlpha.getValue());
        }
        for (SegmentBatch batch : batches) {
            float[] segmentColors = new float[batch.count() * 3];
            for (int i = 0; i < batch.count(); i++) {
                Color segmentColor = batch.colors()[i];
                int offset = i * 3;
                segmentColors[offset] = segmentColor.getRed() / 255.0f;
                segmentColors[offset + 1] = segmentColor.getGreen() / 255.0f;
                segmentColors[offset + 2] = segmentColor.getBlue() / 255.0f;
            }
            scope.shadow(batch.x(), batch.y(), batch.width(), batch.height(), 0.0f, blurRadius, color, batch.rects(), batch.radii(), segmentColors, batch.count());
        }
    }

    private List<RenderEntry> collectRenderEntries(TextRenderer textRenderer, float textScale, boolean showSuffix) {
        List<RenderEntry> entries = new ArrayList<>();
        for (Map.Entry<Module, ToggleAnimation> animationEntry : moduleAnimations.entrySet()) {
            float animation = animationEntry.getValue().progress();
            if (animation <= 0.001f) continue;

            Module module = animationEntry.getKey();
            String name = module.getTranslatedName();
            String info = showSuffix ? normalizeInfo(module.getInfo()) : "";
            float nameWidth = textRenderer.getWidth(name, textScale);
            float suffixWidth = info.isEmpty() ? 0.0f : textRenderer.getWidth(" " + info, textScale);
            entries.add(new RenderEntry(module, name, info, nameWidth, nameWidth + suffixWidth, animation));
        }

        entries.sort(Comparator.comparingDouble(RenderEntry::displayWidth).reversed());

        List<Module> sortedModules = new ArrayList<>(entries.size());
        for (RenderEntry entry : entries) {
            sortedModules.add(entry.module());
        }
        if (!sortedModules.equals(lastSortedModules)) {
            moduleYPositions.clear();
            lastSortedModules = sortedModules;
        }
        return entries;
    }

    private LayoutState updateLayout(List<RenderEntry> entries, float lineHeight) {
        float[] renderY = new float[entries.size()];
        float accumulatedY = 0.0f;
        float speed = sliderSpeed.getValue().floatValue();

        for (int i = 0; i < entries.size(); i++) {
            RenderEntry entry = entries.get(i);
            float targetY = accumulatedY;
            float currentY = moduleYPositions.getOrDefault(entry.module(), targetY);
            float difference = targetY - currentY;
            currentY = Math.abs(difference) > 0.1f ? currentY + difference * speed : targetY;
            moduleYPositions.put(entry.module(), currentY);
            renderY[i] = currentY;

            if (entry.animation() > 0.01f) {
                accumulatedY += lineHeight * entry.animation();
            }
        }

        return new LayoutState(renderY, accumulatedY);
    }

    private boolean isVisible(Module module) {
        return module.isEnabled() && !module.isHidden() && !module.getName().isEmpty();
    }

    private void drawModuleText(UiTree.Scope scope, RenderEntry entry, float x, float y, float textScale, Color nameColor, float animation) {
        drawModuleText(scope, entry, x, y, textScale, nameColor, nameColor, animation);
    }

    private void drawModuleText(UiTree.Scope scope, RenderEntry entry, float x, float y, float textScale, Color nameStartColor, Color nameEndColor, float animation) {
        float glowRadius = this.glowRadius.getValue().floatValue();
        int glowIntensity = this.glowIntensity.getValue();
        Color glowColor = ColorUtils.interpolateColor(nameStartColor, nameEndColor, 0.5f);
        if (textGlow.getValue()) {
            scope.blurredText(entry.name, x, y, textScale, glowRadius * textScale, glowIntensity, glowColor);
        }
        scope.gradientText(entry.name, x, y, textScale, nameStartColor, nameEndColor);
        if (!entry.info.isEmpty()) {
            String text = " " + entry.info;
            float textX = x + entry.nameWidth;
            Color color = withAlpha(new Color(170, 170, 170), animation);
            if (textGlow.getValue())
                scope.blurredText(text, textX, y, textScale, glowRadius * textScale, glowIntensity, color);
            scope.text(text, textX, y, textScale, color);
        }
    }

    private Color resolveColor(int index, long offsetStep) {
        return switch (colorMode.getValue()) {
            case Rainbow -> rainbowColor(index, rainbowAlpha.getValue());
            case Double -> animateColor(themeColor1.getValue(), themeColor2.getValue(), index * offsetStep);
            case Fade -> animateColor(themeColor1.getValue(), darker(themeColor1.getValue()), index * offsetStep);
            case Single -> themeColor1.getValue();
        };
    }

    private Color resolveShadowColor(int index, long offsetStep) {
        return switch (shadowColorMode.getValue()) {
            case Rainbow -> rainbowColor(index, shadowRainbowAlpha.getValue());
            case Double -> animateColor(shadowColor.getValue(), shadowColor2.getValue(), index * offsetStep);
            case Fade -> animateColor(shadowColor.getValue(), darker(shadowColor.getValue()), index * offsetStep);
            case Single -> shadowColor.getValue();
        };
    }

    public Color getThemeColor(int index, long offsetStep) {
        return resolveColor(index, offsetStep);
    }

    private boolean usesRainbow() {
        return colorMode.is(ColorMode.Rainbow) || mode.is(DisplayMode.Normal) && drawShadow.getValue() && shadowColorMode.is(ColorMode.Rainbow);
    }

    private Color rainbowColor(int index, int alpha) {
        double hueDegrees = System.currentTimeMillis() * 0.1 * rainbowSpeed.getValue() + rainbowHueOffset.getValue() + index * rainbowSpread.getValue();
        int rgb = Color.HSBtoRGB((float) ((hueDegrees % 360.0) / 360.0), rainbowSaturation.getValue().floatValue(), rainbowBrightness.getValue().floatValue());
        return new Color(rgb >>> 16 & 0xFF, rgb >>> 8 & 0xFF, rgb & 0xFF, Mth.clamp(alpha, 0, 255));
    }

    private Color animateColor(Color start, Color end, long offset) {
        double progress = ((System.currentTimeMillis() + offset) % 4000L) / 2000.0;
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0;
        }
        return ColorUtils.interpolateColor(start, end, (float) progress);
    }

    private Color darker(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2] * 0.6f);
        return new Color(rgb);
    }

    private Color withAlpha(Color color, float multiplier) {
        int alpha = Mth.clamp((int) (color.getAlpha() * Mth.clamp(multiplier, 0.0f, 1.0f)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private float maxDisplayWidth(List<RenderEntry> entries) {
        float width = 0.0f;
        for (RenderEntry entry : entries) {
            width = Math.max(width, entry.displayWidth());
        }
        return width;
    }

    private String normalizeInfo(String info) {
        return info == null || info.isBlank() ? "" : info;
    }

    private static class ToggleAnimation {
        private boolean target;
        private float progress;
        private long startedAt;

        private ToggleAnimation(long now) {
            target = true;
            startedAt = now;
        }

        private float update(boolean target, long now) {
            if (this.target != target) {
                long elapsed = Mth.clamp(now - startedAt, 0L, MODULE_ANIMATION_DURATION_MS);
                this.target = target;
                this.startedAt = now - (MODULE_ANIMATION_DURATION_MS - elapsed);
            }

            float elapsed = Mth.clamp((now - startedAt) / (float) MODULE_ANIMATION_DURATION_MS, 0.0f, 1.0f);
            float eased = Easing.EASE_IN_OUT_QUAD.getFunction().apply(elapsed);
            progress = target ? eased : 1.0f - eased;
            return progress;
        }

        private float progress() {
            return progress;
        }
    }

    private record RenderEntry(
            Module module, String name, String info, float nameWidth, float displayWidth, float animation
    ) {
    }

    private record LayoutState(float[] renderY, float height) {
    }

    private record NormalRow(
            RenderEntry entry, float textX, float backgroundX, float backgroundY, float backgroundWidth,
            float backgroundHeight, Color startColor, Color endColor
    ) {
    }

    private record SegmentBatch(
            float x, float y, float width, float height, float[] rects, float[] radii, Color[] colors, int count
    ) {
    }

}
