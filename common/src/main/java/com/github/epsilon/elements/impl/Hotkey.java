package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.render.ColorUtils;
import com.github.epsilon.utils.render.animation.Easing;
import com.google.common.base.Suppliers;
import me.sofurry.ClInitNative;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

@ClInitNative
public class Hotkey extends HudModule {

    public static final Hotkey INSTANCE = new Hotkey();

    private Hotkey() {
        super("Hotkey", 96.0f, ROW_HEIGHT);
    }

    private enum ColorMode {
        Single,
        Double
    }

    private enum ShadowColorMode {
        Black,
        Follow
    }

    private final EnumSetting<ColorMode> colorMode = enumSetting("Color Mode", ColorMode.Single);
    private final ColorSetting singleColor = colorSetting("Color", Color.WHITE, false, () -> colorMode.is(ColorMode.Single));
    private final ColorSetting doubleColor1 = colorSetting("Color 1", Color.WHITE, false, () -> colorMode.is(ColorMode.Double));
    private final ColorSetting doubleColor2 = colorSetting("Color 2", Color.RED, false, () -> colorMode.is(ColorMode.Double));
    private final EnumSetting<ShadowColorMode> shadowColorMode = enumSetting("Shadow Color", ShadowColorMode.Black);
    private final BoolSetting showSuffix = boolSetting("Show Suffix", true);

    private static final float ICON_SIZE = 16.0f;
    private static final float ROW_HEIGHT = 16.0f;
    private static final float ROW_GAP = 2.0f;
    private static final float TEXT_PADDING = 4.0f;
    private static final float ICON_TEXT_GAP = 2.0f;
    private static final float TEXT_SCALE = 0.65f;
    private static final float ICON_SCALE = 0.72f;
    private static final float BLUR_STRENGTH = 8.0f;
    private static final float CORNER_RADIUS = 3.0f;
    private static final float SHADOW_BLUR = 10.0f;
    private static final Color SHADOW_COLOR = new Color(0, 0, 0, 110);
    private static final Color SUFFIX_COLOR = new Color(170, 170, 170);
    private static final int ANIMATION_DURATION_MS = 200;

    private final Map<Module, ToggleAnimation> moduleAnimations = new HashMap<>();
    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    public void render(DeltaTracker deltaTracker) {
        TextRenderer textRenderer = textRendererSupplier.get();
        List<Module> modules = ModuleManager.INSTANCE.getModules();
        Set<Module> liveModules = new HashSet<>(modules);
        moduleAnimations.keySet().removeIf(module -> !liveModules.contains(module));

        long now = System.currentTimeMillis();
        for (Module module : modules) {
            if (!isVisible(module)) continue;
            moduleAnimations.computeIfAbsent(module, ignored -> new ToggleAnimation(now)).update(true, now);
        }

        var iterator = moduleAnimations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Module, ToggleAnimation> entry = iterator.next();
            if (isVisible(entry.getKey())) continue;
            if (entry.getValue().update(false, now) <= 0.0f) {
                iterator.remove();
            }
        }

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<Module, ToggleAnimation> animationEntry : moduleAnimations.entrySet()) {
            float animation = animationEntry.getValue().progress();
            if (animation <= 0.001f) continue;

            Module module = animationEntry.getKey();
            Category category = module.getCategory();
            if (category == null || module.getName().isEmpty()) continue;

            String name = module.getTranslatedName();
            String info = showSuffix.getValue() ? normalizeInfo(module.getInfo()) : "";
            float nameWidth = textRenderer.getWidth(name, TEXT_SCALE);
            float suffixWidth = info.isEmpty() ? 0.0f : textRenderer.getWidth(" " + info, TEXT_SCALE);
            entries.add(new Entry(module, name, info, category.icon, nameWidth, nameWidth + suffixWidth, animation));
        }
        entries.sort(Comparator.comparingDouble(Entry::textWidth).reversed());

        float maxRowWidth = 0.0f;
        float contentHeight = 0.0f;
        for (Entry entry : entries) {
            maxRowWidth = Math.max(maxRowWidth, ICON_SIZE + ICON_TEXT_GAP + entry.textWidth() + TEXT_PADDING * 2.0f);
            contentHeight += (ROW_HEIGHT + ROW_GAP) * entry.animation();
        }
        if (!entries.isEmpty()) {
            contentHeight -= ROW_GAP * entries.getLast().animation();
        }
        setBounds(Math.max(ICON_SIZE, maxRowWidth), Math.max(ROW_HEIGHT, contentHeight));
        if (entries.isEmpty()) return;

        float rowY = this.y;
        for (Entry entry : entries) {
            float animation = entry.animation();
            float rowWidth = ICON_SIZE + ICON_TEXT_GAP + entry.textWidth() + TEXT_PADDING * 2.0f;
            float rowX = this.x - rowWidth * (1.0f - animation);
            float animatedHeight = ROW_HEIGHT * animation;
            float animatedRadius = Math.min(CORNER_RADIUS, animatedHeight * 0.5f);
            float textX = rowX + ICON_SIZE + ICON_TEXT_GAP + TEXT_PADDING;
            float textBackgroundX = textX - TEXT_PADDING;
            float textBackgroundWidth = entry.textWidth() + TEXT_PADDING * 2.0f;

            BlurShader.INSTANCE.render(rowX, rowY, ICON_SIZE, animatedHeight, animatedRadius, BLUR_STRENGTH);
            BlurShader.INSTANCE.render(textBackgroundX, rowY, textBackgroundWidth, animatedHeight, animatedRadius, BLUR_STRENGTH);
            rowY += (ROW_HEIGHT + ROW_GAP) * animation;
        }

        UiTree.Scope scope = renderScope();
        rowY = this.y;
        int colorIndex = 0;
        for (Entry entry : entries) {
            float animation = entry.animation();
            float rowWidth = ICON_SIZE + ICON_TEXT_GAP + entry.textWidth() + TEXT_PADDING * 2.0f;
            float rowX = this.x - rowWidth * (1.0f - animation);
            float animatedHeight = ROW_HEIGHT * animation;
            float animatedRadius = Math.min(CORNER_RADIUS, animatedHeight * 0.5f);
            float textBackgroundX = rowX + ICON_SIZE + ICON_TEXT_GAP;
            float textBackgroundWidth = entry.textWidth() + TEXT_PADDING * 2.0f;
            Color rowColor = resolveTextColor(colorIndex++);
            Color shadowColor = resolveShadowColor(rowColor, animation);
            scope.shadow(rowX, rowY, ICON_SIZE, animatedHeight, animatedRadius, SHADOW_BLUR, shadowColor);
            scope.shadow(textBackgroundX, rowY, textBackgroundWidth, animatedHeight, animatedRadius, SHADOW_BLUR, shadowColor);

            float iconHeight = textRenderer.getHeight(ICON_SCALE, StaticFontLoader.ICONS);
            float iconWidth = textRenderer.getWidth(entry.icon(), ICON_SCALE, StaticFontLoader.ICONS);
            Color textColor = withAlpha(rowColor, animation);
            scope.text(entry.icon(), rowX + (ICON_SIZE - iconWidth) * 0.5f, rowY + (animatedHeight - iconHeight) * 0.5f, ICON_SCALE, textColor, StaticFontLoader.ICONS);
            scope.text(entry.name(), textBackgroundX + TEXT_PADDING, rowY + (animatedHeight - textRenderer.getHeight(TEXT_SCALE)) * 0.5f, TEXT_SCALE, textColor);
            if (!entry.info().isEmpty()) {
                scope.text(" " + entry.info(), textBackgroundX + TEXT_PADDING + entry.nameWidth(), rowY + (animatedHeight - textRenderer.getHeight(TEXT_SCALE)) * 0.5f, TEXT_SCALE, withAlpha(SUFFIX_COLOR, animation));
            }
            rowY += (ROW_HEIGHT + ROW_GAP) * animation;
        }
    }

    private Color resolveTextColor(int index) {
        if (colorMode.is(ColorMode.Single)) {
            return singleColor.getValue();
        }
        return animateColor(doubleColor1.getValue(), doubleColor2.getValue(), index * 100L);
    }

    private Color resolveShadowColor(Color textColor, float animation) {
        Color color = shadowColorMode.is(ShadowColorMode.Follow)
                ? new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), SHADOW_COLOR.getAlpha())
                : SHADOW_COLOR;
        return withAlpha(color, animation);
    }

    private Color animateColor(Color start, Color end, long offset) {
        double progress = ((System.currentTimeMillis() + offset) % 4000L) / 2000.0;
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0;
        }
        return ColorUtils.interpolateColor(start, end, (float) progress);
    }

    private boolean isVisible(Module module) {
        return module.isEnabled() && !module.isHidden() && module.getCategory() != null && !module.getName().isEmpty();
    }

    private String normalizeInfo(String info) {
        return info == null || info.isBlank() ? "" : info;
    }

    private Color withAlpha(Color color, float multiplier) {
        int alpha = Mth.clamp((int) (color.getAlpha() * Mth.clamp(multiplier, 0.0f, 1.0f)), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static final class ToggleAnimation {
        private boolean target;
        private float progress;
        private long startedAt;

        private ToggleAnimation(long now) {
            target = true;
            startedAt = now;
        }

        private float update(boolean target, long now) {
            if (this.target != target) {
                long elapsed = Mth.clamp(now - startedAt, 0L, ANIMATION_DURATION_MS);
                this.target = target;
                this.startedAt = now - (ANIMATION_DURATION_MS - elapsed);
            }

            float elapsed = Mth.clamp((now - startedAt) / (float) ANIMATION_DURATION_MS, 0.0f, 1.0f);
            float eased = Easing.EASE_IN_OUT_QUAD.getFunction().apply(elapsed);
            progress = target ? eased : 1.0f - eased;
            return progress;
        }

        private float progress() {
            return progress;
        }
    }

    private record Entry(Module module, String name, String info, String icon, float nameWidth, float textWidth,
                         float animation) {
    }

}
