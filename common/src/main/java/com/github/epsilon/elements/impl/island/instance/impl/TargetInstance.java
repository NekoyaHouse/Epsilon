package com.github.epsilon.elements.impl.island.instance.impl;

import com.github.epsilon.elements.impl.island.IslandPalette;
import com.github.epsilon.elements.impl.island.instance.LandInstance;
import com.github.epsilon.elements.impl.island.pattern.LCPattern;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.HealthManager;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.awt.*;
import java.util.Locale;
import java.util.function.Supplier;

import static com.github.epsilon.Constants.mc;

public class TargetInstance extends LandInstance {

    private static final float PADDING = 8f;
    private static final float MIN_WIDTH = 160f;
    private static final float HEAD_SIZE = 36f;
    private static final float HEAD_GAP = 6f;
    private static final float ICON_SIZE = 16f;
    private static final float ICON_RIGHT_PADDING = 12f;
    private static final float ICON_TOP_OFFSET = 4f;
    private static final float NAME_ICON_GAP = 4f;
    private static final float NAME_SCALE = 1.25f;
    private static final float INFO_SCALE = 1.0f;
    private static final float HEALTH_SCALE = 0.9f;
    private static final float BAR_HEIGHT = 4f;
    private static final float HEALTH_ROW_GAP = 6f;
    private static final float HEALTH_TEXT_INSET = 10f;
    private static final float HEALTH_BAR_GAP = 5f;

    private final LivingEntity target;
    private final Supplier<TextRenderer> textRendererSupplier;
    private final TimerUtils timer = new TimerUtils();

    private float healthWidth;
    private boolean healthInitialized;

    public TargetInstance(LivingEntity target, Supplier<TextRenderer> textRendererSupplier, LCPattern pattern) {
        super(pattern, 3);
        this.target = target;
        this.textRendererSupplier = textRendererSupplier;
    }

    @Override
    public void update() {
        if (!checkPattern()) {
            timer.reset();
        }

        targetRadius = 0.5f;

        TextRenderer textRenderer = textRendererSupplier.get();
        float nameWidth = textRenderer.getWidth(target.getName().getString(), NAME_SCALE);
        float desired = PADDING + HEAD_SIZE + HEAD_GAP + nameWidth + NAME_ICON_GAP + ICON_SIZE + ICON_RIGHT_PADDING;
        float screenMaxWidth = LuminRenderSystem.getScaledWidthInt() - 50f;
        targetWidth = Mth.clamp(desired, MIN_WIDTH, Math.max(MIN_WIDTH, screenMaxWidth));
        float maxHealth = Math.max(1.0f, target.getMaxHealth() + Math.max(0.0f, target.getAbsorptionAmount()));
        float health = HealthManager.INSTANCE.getHealth(target);
        float healthRowHeight = Math.max(BAR_HEIGHT, textRenderer.getHeight(HEALTH_SCALE));
        targetHeight = PADDING * 2f + HEAD_SIZE + HEALTH_ROW_GAP + healthRowHeight;

        float healthTextWidth = textRenderer.getWidth(formatHealth(health), HEALTH_SCALE);
        float maxHealthTextWidth = textRenderer.getWidth(formatHealth(maxHealth), HEALTH_SCALE);
        float maxBarWidth = Math.max(1f, targetWidth - HEALTH_TEXT_INSET * 2f
                - healthTextWidth - maxHealthTextWidth - HEALTH_BAR_GAP * 2f);
        float currentBarWidth = Math.max(1f, maxBarWidth * (health / maxHealth));

        if (!healthInitialized) {
            healthWidth = currentBarWidth;
            healthInitialized = true;
        } else {
            healthWidth = moveTowards(healthWidth, currentBarWidth, maxBarWidth * 0.04f);
        }
    }

    @Override
    public void draw(UiTree.Scope scope, float translateX, float translateY, float width, float height) {
        TextRenderer textRenderer = textRendererSupplier.get();

        float headX = PADDING;
        float headY = PADDING;
        float headRadius = HEAD_SIZE * 0.22f;

        scope.roundRect(headX, headY, HEAD_SIZE, HEAD_SIZE, headRadius, fade(new Color(0, 0, 0, 48)));

        if (target instanceof AbstractClientPlayer player) {
            Identifier skin = player.getSkin().body().texturePath();
            scope.playerHead(skin, headX, headY, HEAD_SIZE, headRadius, fade(Color.WHITE));
        }

        float textX = headX + HEAD_SIZE + HEAD_GAP;
        float iconX = width - ICON_SIZE - ICON_RIGHT_PADDING;
        String name = target.getName().getString();
        scope.text(name, textX, headY + 2f, NAME_SCALE, fade(IslandPalette.TEXT_PRIMARY));

        String distanceText = String.format(Locale.ROOT, "distance: %.1f", mc.player.distanceTo(target));
        float nameHeight = textRenderer.getHeight(NAME_SCALE);
        scope.text(distanceText, textX, headY + 2f + nameHeight + 3f, INFO_SCALE, fade(IslandPalette.TEXT_MUTED));

        drawTargetIcon(scope, iconX + ICON_SIZE * 0.5f, headY + ICON_TOP_OFFSET + ICON_SIZE * 0.5f);

        float maxHealth = Math.max(1.0f, target.getMaxHealth() + Math.max(0.0f, target.getAbsorptionAmount()));
        float health = HealthManager.INSTANCE.getHealth(target);

        String healthText = formatHealth(health);
        String maxHealthText = formatHealth(maxHealth);
        float healthTextHeight = textRenderer.getHeight(HEALTH_SCALE);
        float healthTextWidth = textRenderer.getWidth(healthText, HEALTH_SCALE);
        float maxHealthTextWidth = textRenderer.getWidth(maxHealthText, HEALTH_SCALE);
        float healthRowHeight = Math.max(BAR_HEIGHT, healthTextHeight);
        float healthRowY = headY + HEAD_SIZE + HEALTH_ROW_GAP;
        float healthY = healthRowY + (healthRowHeight - healthTextHeight) * 0.5f;

        float barX = HEALTH_TEXT_INSET + healthTextWidth + HEALTH_BAR_GAP;
        float barRight = width - HEALTH_TEXT_INSET - maxHealthTextWidth - HEALTH_BAR_GAP;
        float barWidth = Math.max(1f, barRight - barX);
        float barY = healthRowY + (healthRowHeight - BAR_HEIGHT) * 0.5f;
        float barRadius = BAR_HEIGHT * 0.5f;

        scope.roundRect(barX, barY, barWidth, BAR_HEIGHT, barRadius, fade(IslandPalette.TRACK));

        Color[] palette = {
                IslandPalette.ACCENT,
                IslandPalette.ACCENT_ALT,
                IslandPalette.SUCCESS
        };
        float filled = Mth.clamp(barWidth, 0f, healthWidth);
        if (filled > 0f) {
            scope.roundRectHorizontalGradient(barX, barY, filled, BAR_HEIGHT, barRadius,
                    fade(palette[0]), fade(palette[palette.length - 1]));
        }

        scope.text(healthText, HEALTH_TEXT_INSET, healthY, HEALTH_SCALE, fade(IslandPalette.TEXT_SECONDARY));
        scope.text(maxHealthText, width - HEALTH_TEXT_INSET - maxHealthTextWidth, healthY, HEALTH_SCALE, fade(IslandPalette.TEXT_SECONDARY));
    }

    private void drawTargetIcon(UiTree.Scope scope, float centerX, float centerY) {
        float radius = ICON_SIZE * 0.34f;
        Color color = fade(IslandPalette.ACCENT);
        scope.circle(centerX, centerY, radius, 1.4f, color);
        scope.roundRect(centerX - 1f, centerY - 1f, 2f, 2f, 1f, color);

        float tick = radius + 2.4f;
        scope.rect(centerX - 0.6f, centerY - tick, 1.2f, 2.2f, color);
        scope.rect(centerX - 0.6f, centerY + tick - 2.2f, 1.2f, 2.2f, color);
        scope.rect(centerX - tick, centerY - 0.6f, 2.2f, 1.2f, color);
        scope.rect(centerX + tick - 2.2f, centerY - 0.6f, 2.2f, 1.2f, color);
    }

    private static String formatHealth(float health) {
        return String.format(Locale.ROOT, "%.1f", health);
    }

    @Override
    public boolean isClosed() {
        return checkPattern() && timer.passedMillise(1200L);
    }

    public LivingEntity getTarget() {
        return target;
    }

    private static float moveTowards(float current, float target, float speed) {
        float difference = target - current;
        if (Math.abs(difference) <= speed) {
            return target;
        }
        return current + Math.signum(difference) * speed;
    }

}
