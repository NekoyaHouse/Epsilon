package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class Potions extends HudModule {

    public static final Potions INSTANCE = new Potions();

    private Potions() {
        super("Potions", 0f, 0f, 160f, 60f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.5, 0.05);
    private final DoubleSetting nameScale = doubleSetting("Name Text Scale", 0.78, 0.5, 1.5, 0.05);
    private final DoubleSetting durationScale = doubleSetting("Duration Text Scale", 0.62, 0.4, 1.5, 0.05);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 4.0, 0.0, 14.0, 0.5);
    private final DoubleSetting animSpeed = doubleSetting("Animation Speed", 10.0, 1.0, 20.0, 0.5);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 145));
    private final BoolSetting showIcon = boolSetting("Show Icon", true);
    private final BoolSetting showPill = boolSetting("Show Progress Bar", true);
    private final BoolSetting tintNameWithEffect = boolSetting("Tint Name With Effect Color", true);
    private final BoolSetting hideAmbient = boolSetting("Hide Ambient", false);

    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", DEFAULT_SHADOW_BLUR, MIN_SHADOW_BLUR, MAX_SHADOW_BLUR, SHADOW_BLUR_STEP, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", DEFAULT_SHADOW_COLOR, drawShadow::getValue);

    private final BoolSetting backgroundBlur = boolSetting("Background Blur", true);
    private final IntSetting blurStrength = intSetting("Blur Strength", 5, 1, 16, 1);

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    private static final float ROW_HEIGHT = 22.0f;
    private static final float ROW_SPACING = 2.0f;
    private static final float INNER_PADDING_X = 6.0f;
    private static final float INNER_PADDING_Y = 4.0f;
    private static final float ICON_SIZE = 14.0f;
    private static final float ICON_TEXT_GAP = 6.0f;
    private static final float PILL_WIDTH = 3.0f;
    private static final float PILL_GAP = 6.0f;
    private static final float NAME_DURATION_GAP = 1.0f;

    private final Map<Holder<MobEffect>, Float> alphaMap = new HashMap<>();
    private final Map<Holder<MobEffect>, Integer> maxDurationMap = new HashMap<>();

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        List<EffectInfo> items = collectItems(deltaTracker);
        if (items.isEmpty()) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        var scope = renderScope();

        float scale = this.scale.getValue().floatValue();
        float rowHeight = ROW_HEIGHT * scale;
        float spacing = ROW_SPACING * scale;
        float padX = INNER_PADDING_X * scale;
        float padY = INNER_PADDING_Y * scale;
        float pillWidth = PILL_WIDTH * scale;
        float pillRadius = pillWidth / 2.0f;
        float iconSize = ICON_SIZE * scale;
        float iconTextGap = ICON_TEXT_GAP * scale;
        float radius = cornerRadius.getValue().floatValue() * scale;
        float nameRenderScale = nameScale.getValue().floatValue() * scale;
        float durationRenderScale = durationScale.getValue().floatValue() * scale;

        HorizontalAnchor hAnchor = getHorizontalAnchor();

        float currentY = this.y;
        boolean first = true;

        float maxWidth = 0f;
        float totalHeight = 0f;

        for (EffectInfo info : items) {
            if (info.alpha <= 0.001f) continue;

            float alpha = Mth.clamp(info.alpha, 0f, 1f);

            // Track bounds
            if (info.totalWidth > maxWidth) maxWidth = info.totalWidth;
            totalHeight += (rowHeight + (first ? 0f : spacing)) * alpha;

            // Update render position
            if (!first) currentY += spacing * alpha;
            first = false;

            float rowWidth = info.totalWidth;
            float rowX = computeRowX(rowWidth, hAnchor);

            if (backgroundBlur.getValue()) {
                BlurShader.INSTANCE.render(rowX, currentY, rowWidth, rowHeight, radius, blurStrength.getValue());
            }

            if (drawShadow.getValue()) {
                scope.shadow(rowX, currentY, rowWidth, rowHeight, radius, shadowBlur.getValue().floatValue(), withAlpha(shadowColor.getValue(), alpha));
            }

            scope.roundRect(rowX, currentY, rowWidth, rowHeight, radius, withAlpha(backgroundColor.getValue(), alpha));

            float cursorX = rowX + padX;

            if (showIcon.getValue() && info.iconTexture != null) {
                float iconY = currentY + (rowHeight - iconSize) / 2.0f;
                scope.texture(info.iconTexture, cursorX, iconY, iconSize, iconSize, 0f, 0f, 1f, 1f, new Color(255, 255, 255, (int) (255 * alpha)));
                cursorX += iconSize + iconTextGap;
            }

            float nameTextHeight = textRenderer.getHeight(nameRenderScale);
            float durationTextHeight = textRenderer.getHeight(durationRenderScale);
            float gap = NAME_DURATION_GAP * scale;
            float textBlockHeight = nameTextHeight + durationTextHeight + gap;
            float nameY = currentY + (rowHeight - textBlockHeight) / 2.0f;
            float durationY = nameY + nameTextHeight + gap;

            Color nameColor = tintNameWithEffect.getValue() ? withAlpha(brighten(info.effectColor, 1.15f), alpha) : new Color(255, 255, 255, (int) (235 * alpha));
            Color durationColor = new Color(180, 180, 180, (int) (220 * alpha));

            scope.text(info.name, cursorX, nameY, nameRenderScale, nameColor);
            scope.text(info.duration, cursorX, durationY, durationRenderScale, durationColor);

            if (showPill.getValue()) {
                float pillX = rowX + rowWidth - padX - pillWidth;
                float pillH = rowHeight - padY * 2.0f;
                float pillY = currentY + padY;

                Color pillTrack = new Color(0, 0, 0, (int) (140 * alpha));
                scope.roundRect(pillX, pillY, pillWidth, pillH, pillRadius, pillTrack);

                float fillH = pillH * info.progress;
                if (fillH > 0.5f) {
                    float fillY = pillY + (pillH - fillH);
                    boolean fullFill = fillH >= pillH - 0.5f;
                    float topRadius = fullFill ? pillRadius : 0f;
                    Color top = withAlpha(brighten(info.effectColor, 1.3f), alpha);
                    Color bottom = withAlpha(info.effectColor, alpha);
                    scope.roundRectGradient(pillX, fillY, pillWidth, fillH, topRadius, topRadius, pillRadius, pillRadius, top, bottom, bottom, top);
                }
            }

            currentY += rowHeight * alpha;
        }

        setBounds(maxWidth, totalHeight);
    }

    private float computeRowX(float rowWidth, HorizontalAnchor hAnchor) {
        return switch (hAnchor) {
            case Right -> this.x + this.width - rowWidth;
            case Center -> this.x + (this.width - rowWidth) / 2.0f;
            default -> this.x;
        };
    }

    private List<EffectInfo> collectItems(DeltaTracker delta) {
        Collection<MobEffectInstance> active = mc.player.getActiveEffects();

        float frameTime = delta == null ? 0.05f : delta.getGameTimeDeltaTicks() / 20.0f;
        float speed = animSpeed.getValue().floatValue();

        Set<Holder<MobEffect>> stillActive = new HashSet<>();
        Map<Holder<MobEffect>, MobEffectInstance> instanceMap = new HashMap<>();

        for (MobEffectInstance inst : active) {
            if (hideAmbient.getValue() && inst.isAmbient()) continue;
            Holder<MobEffect> holder = inst.getEffect();
            stillActive.add(holder);
            instanceMap.put(holder, inst);

            float current = alphaMap.getOrDefault(holder, 0f);
            current = Mth.lerp(speed * frameTime, current, 1f);
            alphaMap.put(holder, current);

            int dur = inst.getDuration();
            if (dur > 0) {
                int prevMax = maxDurationMap.getOrDefault(holder, 0);
                if (dur > prevMax) maxDurationMap.put(holder, dur);
            }
        }

        Iterator<Map.Entry<Holder<MobEffect>, Float>> it = alphaMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Holder<MobEffect>, Float> e = it.next();
            if (!stillActive.contains(e.getKey())) {
                float current = Mth.lerp(speed * frameTime, e.getValue(), 0f);
                if (current < 0.001f) {
                    it.remove();
                    maxDurationMap.remove(e.getKey());
                } else {
                    e.setValue(current);
                }
            }
        }

        if (alphaMap.isEmpty()) return List.of();

        TextRenderer textRenderer = textRendererSupplier.get();
        float s = scale.getValue().floatValue();
        float nameRenderScale = nameScale.getValue().floatValue() * s;
        float durationRenderScale = durationScale.getValue().floatValue() * s;
        float padX = INNER_PADDING_X * s;
        float iconSize = ICON_SIZE * s;
        float iconTextGap = ICON_TEXT_GAP * s;
        float pillWidth = PILL_WIDTH * s;
        float pillGap = PILL_GAP * s;
        boolean showIconValue = showIcon.getValue();
        boolean showPillValue = showPill.getValue();

        List<EffectInfo> list = new ArrayList<>(alphaMap.size());
        for (Map.Entry<Holder<MobEffect>, Float> e : alphaMap.entrySet()) {
            Holder<MobEffect> holder = e.getKey();
            float alpha = e.getValue();
            MobEffectInstance inst = instanceMap.get(holder);
            int amplifier = inst != null ? inst.getAmplifier() : 0;
            int duration = inst != null ? inst.getDuration() : 0;
            boolean infinite = inst != null && inst.isInfiniteDuration();

            String displayName = holder.value().getDisplayName().getString() + romanLevel(amplifier);
            String durationStr = infinite ? "∞" : formatDuration(duration);

            int rgb = holder.value().getColor() & 0xFFFFFF;
            Color effectColor = new Color(rgb | 0xFF000000, true);

            float nameWidth = textRenderer.getWidth(displayName, nameRenderScale);
            float durationWidth = textRenderer.getWidth(durationStr, durationRenderScale);
            float textWidth = Math.max(nameWidth, durationWidth);

            float total = padX;
            if (showIconValue) total += iconSize + iconTextGap;
            total += textWidth;
            if (showPillValue) total += pillGap + pillWidth;
            total += padX;

            int maxDur = maxDurationMap.getOrDefault(holder, Math.max(duration, 1));
            float progress = infinite ? 1.0f : Mth.clamp((float) duration / Math.max(1, maxDur), 0f, 1f);

            Identifier icon = getIconTexture(holder);

            list.add(new EffectInfo(displayName, durationStr, alpha, effectColor, total, progress, icon));
        }

        list.sort(Comparator.comparing(a -> a.name));
        return list;
    }

    private static Identifier getIconTexture(Holder<MobEffect> holder) {
        return holder.unwrapKey()
                .map(ResourceKey::identifier)
                .map(id -> Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/mob_effect/" + id.getPath() + ".png"))
                .orElse(null);
    }

    private static String formatDuration(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private static String romanLevel(int amplifier) {
        int level = amplifier + 1;
        if (level <= 1) return "";
        return " " + switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(level);
        };
    }

    private static Color withAlpha(Color color, float alphaMul) {
        int a = Mth.clamp((int) (color.getAlpha() * alphaMul), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    private static Color brighten(Color color, float factor) {
        int r = Mth.clamp((int) (color.getRed() * factor), 0, 255);
        int g = Mth.clamp((int) (color.getGreen() * factor), 0, 255);
        int b = Mth.clamp((int) (color.getBlue() * factor), 0, 255);
        return new Color(r, g, b, color.getAlpha());
    }

    private record EffectInfo(String name, String duration, float alpha, Color effectColor, float totalWidth,
                              float progress, Identifier iconTexture) {
    }

}
