package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.LuminTexture;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.render.animation.Easing;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class TargetHUD extends HudModule {

    public static final TargetHUD INSTANCE = new TargetHUD();

    private TargetHUD() {
        super("Target HUD", 0f, 0f, 180f, 80f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 0.9, 0.5, 2.0, 0.1);
    private final DoubleSetting width = doubleSetting("Width", 150.0, 100.0, 300.0, 1.0);
    private final DoubleSetting height = doubleSetting("Height", 52.0, 30.0, 100.0, 1.0);
    private final DoubleSetting radius = doubleSetting("Radius", 5.0, 0.0, 20.0, 1.0);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 5.0, 1.0, 20.0, 1.0);
    private final DoubleSetting healthBarHeight = doubleSetting("Bar Height", 3.0, 2.0, 20.0, 1.0);
    private final DoubleSetting healthBarRadius = doubleSetting("Bar Radius", 1.2, 0.0, 15.0, 1.0);
    private final DoubleSetting nameSize = doubleSetting("Name Size", 10.5, 8.0, 18.0, 0.5);
    private final BoolSetting delayBar = boolSetting("Delay Bar", true);
    private final BoolSetting delayWait = boolSetting("Delay Wait", true, delayBar::getValue);
    private final DoubleSetting delayTime = doubleSetting("Delay Time", 250.0, 0.0, 500.0, 50.0, () -> delayBar.getValue() && delayWait.getValue());
    private final DoubleSetting delaySpeed = doubleSetting("Delay Speed", 2.0, 0.1, 10.0, 0.1, delayBar::getValue);
    private final BoolSetting barOutline = boolSetting("Bar Outline", true);
    private final DoubleSetting barOutlineWidth = doubleSetting("Bar Outline Width", 1.0, 0.5, 5.0, 0.5, barOutline::getValue);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 145));
    private final ColorSetting barBackgroundColor = colorSetting("Bar Background Color", new Color(255, 255, 255, 55));
    private final ColorSetting barFillColor = colorSetting("Bar Fill Color", new Color(255, 236, 248, 235));
    private final ColorSetting delayBarColor = colorSetting("Delay Bar Color", new Color(190, 190, 190, 100), delayBar::getValue);
    private final ColorSetting barOutlineColor = colorSetting("Bar Outline Color", new Color(255, 255, 255, 85), barOutline::getValue);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(255, 255, 255, 235));
    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", DEFAULT_SHADOW_BLUR, MIN_SHADOW_BLUR, MAX_SHADOW_BLUR, SHADOW_BLUR_STEP, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", DEFAULT_SHADOW_COLOR, drawShadow::getValue);

    private static final long VISIBILITY_ANIMATION_DURATION_MS = 300L;
    private static final float HEAD_DAMAGE_SCALE_FACTOR = 0.15f;
    private static final float EQUIPMENT_ITEM_SCALE = 0.85f;

    private int lastTargetId = Integer.MIN_VALUE;
    private float displayedHealth = 0.0f;
    private float delayedHealth = 0.0f;
    private float lastKnownHealth = -1.0f;
    private float lastKnownMaxHealth = 1.0f;
    private long lastDamageTimeMs = 0L;
    private LivingEntity renderedTarget;
    private float visibilityProgress = 0.0f;
    private long lastVisibilityUpdateMs = 0L;

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    public void render(DeltaTracker deltaTracker) {
        float panelScale = scale.getValue().floatValue();
        float panelWidth = width.getValue().floatValue() * panelScale;
        float panelHeight = height.getValue().floatValue() * panelScale;
        setBounds(panelWidth, panelHeight);

        float frameTime = deltaTracker == null ? 0.05f : deltaTracker.getGameTimeDeltaTicks() / 20.0f;
        LivingEntity target = updateRenderedTarget(resolveTarget());
        float animationScale = Easing.EASE_OUT_SINE.getFunction().apply(Mth.clamp(visibilityProgress, 0.0f, 1.0f));
        if (target == null || animationScale <= 0.01f) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        UiTree.Scope scope = renderScope();

        LivingEntity liveTarget = resolveTarget();
        float maxHealth = lastKnownMaxHealth;
        float healthPercent;
        if (liveTarget == target) {
            float health = Managers.HEALTH.getHealth(target);
            maxHealth = Math.max(1.0f, target.getMaxHealth() + Math.max(0.0f, target.getAbsorptionAmount()));
            lastKnownMaxHealth = maxHealth;
            healthPercent = updateAnimatedHealth(target, health, maxHealth, frameTime);
        } else {
            maxHealth = Math.max(1.0f, lastKnownMaxHealth);
            displayedHealth = Mth.clamp(displayedHealth, 0.0f, maxHealth);
            delayedHealth = Mth.clamp(delayedHealth, 0.0f, maxHealth);
            healthPercent = Mth.clamp(displayedHealth / maxHealth, 0.0f, 1.0f);
        }
        float delayHealthPercent = Mth.clamp(delayedHealth / maxHealth, 0.0f, 1.0f);

        float pad = 5.0f * panelScale;
        float cornerRadius = radius.getValue().floatValue() * panelScale;
        float barHeight = healthBarHeight.getValue().floatValue() * panelScale;
        float barRadius = healthBarRadius.getValue().floatValue() * panelScale;
        float barWidth = Math.max(1.0f, panelWidth - pad * 2.0f);
        float delayedBarWidth = Mth.clamp(barWidth, 0.0f, barWidth * delayHealthPercent);
        float filledBarWidth = Mth.clamp(barWidth, 0.0f, barWidth * healthPercent);

        float innerHeight = Math.max(1.0f, panelHeight - pad * 2.0f);
        float contentAreaHeight = Math.max(1.0f, innerHeight - pad - barHeight);
        float headSize = Math.min(contentAreaHeight, Math.max(26.0f * panelScale, panelHeight * 0.6f) * 1.05f);
        float textScale = Math.max(0.45f, nameSize.getValue().floatValue() / 14.0f) * panelScale;
        float textHeight = textRenderer.getHeight(textScale);
        float contentRowHeight = Math.max(headSize, textHeight);
        float contentBlockHeight = contentRowHeight + pad + barHeight;
        float contentStartY = this.y + pad + Math.max(0.0f, (innerHeight - contentBlockHeight) / 2.0f);
        float headY = contentStartY + (contentRowHeight - headSize) / 2.0f;
        float headX = this.x + pad;
        float barY = contentStartY + contentRowHeight + pad;

        float textStartX = headX + headSize + pad;

        String nameText = target.getName().getString();
        String healthText = String.format(Locale.ROOT, "%.1f", displayedHealth);

        float contentY = headY + 2.0f * panelScale;
        float healthTextWidth = textRenderer.getWidth(healthText, textScale);
        float healthTextX = this.x + panelWidth - pad - healthTextWidth;
        float equipmentY = contentY + textHeight + 2.8f * panelScale;
        float equipmentScale = EQUIPMENT_ITEM_SCALE * panelScale;
        float equipmentGap = 1.5f * panelScale;

        float centerX = this.x + panelWidth / 2.0f;
        float centerY = this.y + panelHeight / 2.0f;
        float scaledPanelX = Mth.lerp(animationScale, centerX, this.x);
        float scaledPanelY = Mth.lerp(animationScale, centerY, this.y);
        float scaledPanelWidth = panelWidth * animationScale;
        float scaledPanelHeight = panelHeight * animationScale;
        float scaledCornerRadius = cornerRadius * animationScale;
        float scaledBarHeight = barHeight * animationScale;
        float scaledBarRadius = barRadius * animationScale;
        float scaledBarOutlineWidth = barOutlineWidth.getValue().floatValue() * panelScale * animationScale;
        float scaledTextScale = textScale * animationScale;
        float scaledHeadRadius = headSize * 0.23f * animationScale;
        float scaledPadX = Mth.lerp(animationScale, centerX, this.x + pad);
        float scaledBarY = Mth.lerp(animationScale, centerY, barY);
        float scaledBarWidth = barWidth * animationScale;
        float scaledDelayedBarWidth = delayedBarWidth * animationScale;
        float scaledFilledBarWidth = filledBarWidth * animationScale;
        float scaledHeadX = Mth.lerp(animationScale, centerX, headX);
        float scaledHeadY = Mth.lerp(animationScale, centerY, headY);
        float scaledHeadSize = headSize * animationScale;
        float scaledTextStartX = Mth.lerp(animationScale, centerX, textStartX);
        float scaledContentY = Mth.lerp(animationScale, centerY, contentY);
        float scaledHealthTextX = Mth.lerp(animationScale, centerX, healthTextX);
        float scaledEquipmentX = Mth.lerp(animationScale, centerX, textStartX);
        float scaledEquipmentY = Mth.lerp(animationScale, centerY, equipmentY);
        float scaledEquipmentScale = equipmentScale * animationScale;
        float scaledEquipmentGap = equipmentGap * animationScale;
        float damageProgress = Easing.EASE_OUT_SINE.getFunction().apply(Mth.clamp(target.hurtTime / 10.0f, 0.0f, 1.0f));
        float headDamageScale = 1.0f - damageProgress * HEAD_DAMAGE_SCALE_FACTOR;
        float finalHeadSize = scaledHeadSize * headDamageScale;
        float finalHeadX = scaledHeadX + (scaledHeadSize - finalHeadSize) / 2.0f;
        float finalHeadY = scaledHeadY + (scaledHeadSize - finalHeadSize) / 2.0f;
        float finalHeadRadius = scaledHeadRadius * headDamageScale;
        Color headTintColor = withAlpha(tintColor(Color.WHITE, damageProgress), animationScale);

        BlurShader.INSTANCE.render(scaledPanelX, scaledPanelY, scaledPanelWidth, scaledPanelHeight, scaledCornerRadius, blurStrength.getValue().floatValue());

        if (drawShadow.getValue()) {
            scope.shadow(scaledPanelX, scaledPanelY, scaledPanelWidth, scaledPanelHeight, scaledCornerRadius, shadowBlur.getValue().floatValue() * animationScale, withAlpha(shadowColor.getValue(), animationScale));
        }

        scope.roundRect(scaledPanelX, scaledPanelY, scaledPanelWidth, scaledPanelHeight, scaledCornerRadius, withAlpha(backgroundColor.getValue(), animationScale));
        scope.roundRect(scaledPadX, scaledBarY, scaledBarWidth, scaledBarHeight, scaledBarRadius, withAlpha(barBackgroundColor.getValue(), animationScale));
        if (delayBar.getValue() && delayedHealth > displayedHealth) {
            scope.roundRect(scaledPadX, scaledBarY, scaledDelayedBarWidth, scaledBarHeight, scaledBarRadius, withAlpha(delayBarColor.getValue(), animationScale));
        }
        scope.roundRect(scaledPadX, scaledBarY, scaledFilledBarWidth, scaledBarHeight, scaledBarRadius, withAlpha(barFillColor.getValue(), animationScale));
        if (!(target instanceof AbstractClientPlayer)) {
            scope.roundRect(finalHeadX, finalHeadY, finalHeadSize, finalHeadSize, finalHeadRadius, withAlpha(tintColor(new Color(80, 80, 80, 200), damageProgress), animationScale));
        }

        if (barOutline.getValue() && scaledBarOutlineWidth > 0.0f) {
            scope.outline(
                    scaledPadX, scaledBarY, scaledBarWidth, scaledBarHeight, scaledBarRadius,
                    scaledBarOutlineWidth, withAlpha(barOutlineColor.getValue(), animationScale)
            );
        }

        if (target instanceof AbstractClientPlayer player) {
            AbstractTexture abstractTexture = mc.getTextureManager().getTexture(player.getSkin().body().texturePath());
            scope.playerHead(
                    new LuminTexture(abstractTexture.getTexture(), abstractTexture.getTextureView(), abstractTexture.getSampler()),
                    finalHeadX, finalHeadY, finalHeadSize, finalHeadRadius, headTintColor
            );
        }

        scope.text(nameText, scaledTextStartX, scaledContentY, scaledTextScale, withAlpha(textColor.getValue(), animationScale));
        scope.text(healthText, scaledHealthTextX, scaledContentY, scaledTextScale, withAlpha(textColor.getValue(), animationScale));
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        float panelScale = scale.getValue().floatValue();
        LivingEntity target = renderedTarget;
        if (target == null || visibilityProgress <= 0.01f) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        float panelWidth = width.getValue().floatValue() * panelScale;
        float panelHeight = height.getValue().floatValue() * panelScale;
        float animationScale = Easing.EASE_OUT_SINE.getFunction().apply(Mth.clamp(visibilityProgress, 0.0f, 1.0f));
        float pad = 5.0f * panelScale;
        float barHeight = healthBarHeight.getValue().floatValue() * panelScale;
        float innerHeight = Math.max(1.0f, panelHeight - pad * 2.0f);
        float contentAreaHeight = Math.max(1.0f, innerHeight - pad - barHeight);
        float headSize = Math.min(contentAreaHeight, Math.max(26.0f * panelScale, panelHeight * 0.6f) * 1.05f);
        float textScale = Math.max(0.45f, nameSize.getValue().floatValue() / 14.0f) * panelScale;
        float textHeight = textRenderer.getHeight(textScale);
        float contentRowHeight = Math.max(headSize, textHeight);
        float contentBlockHeight = contentRowHeight + pad + barHeight;
        float contentStartY = this.y + pad + Math.max(0.0f, (innerHeight - contentBlockHeight) / 2.0f);
        float headY = contentStartY + (contentRowHeight - headSize) / 2.0f;
        float headX = this.x + pad;
        float textStartX = headX + headSize + pad;
        float contentY = headY + 2.0f * panelScale;
        float equipmentY = contentY + textHeight + 2.8f * panelScale;
        float equipmentScale = EQUIPMENT_ITEM_SCALE * panelScale;
        float equipmentGap = 1.5f * panelScale;
        float centerX = this.x + panelWidth / 2.0f;
        float centerY = this.y + panelHeight / 2.0f;
        float scaledEquipmentX = Mth.lerp(animationScale, centerX, textStartX);
        float scaledEquipmentY = Mth.lerp(animationScale, centerY, equipmentY);
        float scaledEquipmentScale = equipmentScale * animationScale;
        float scaledEquipmentGap = equipmentGap * animationScale;

        renderEquipmentItems(graphics, target, scaledEquipmentX, scaledEquipmentY, scaledEquipmentScale, scaledEquipmentGap);
    }

    private void renderEquipmentItems(GuiGraphicsExtractor graphics, LivingEntity target, float startX, float y, float scale, float gap) {
        List<ItemStack> equipmentItems = new ArrayList<>(5);
        appendEquipmentItem(equipmentItems, target.getMainHandItem());
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.HEAD));
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.CHEST));
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.LEGS));
        appendEquipmentItem(equipmentItems, target.getItemBySlot(EquipmentSlot.FEET));

        if (equipmentItems.isEmpty()) {
            return;
        }

        float itemSize = 16.0f * scale;
        float itemX = startX;
        for (int i = 0; i < equipmentItems.size(); i++) {
            drawItem(graphics, target, equipmentItems.get(i), itemX, y, scale, target.getId() + i);
            itemX += itemSize + gap;
        }
    }

    private void appendEquipmentItem(List<ItemStack> items, ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(stack);
        }
    }

    private void drawItem(GuiGraphicsExtractor graphics, LivingEntity owner, ItemStack stack, float x, float y, float scale, int seed) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + scale, y + scale);
        graphics.pose().scale(scale, scale);
        graphics.item(owner, stack, 0, 0, seed);
        graphics.pose().popMatrix();
    }

    private LivingEntity resolveTarget() {
        LivingEntity target = KillAura.INSTANCE.target;
        if (isRenderableTarget(target)) {
            return target;
        }
        return mc.screen instanceof HudEditorScreen ? mc.player : null;
    }

    private boolean isRenderableTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isDeadOrDying();
    }

    private LivingEntity updateRenderedTarget(LivingEntity liveTarget) {
        long now = System.currentTimeMillis();
        if (lastVisibilityUpdateMs == 0L) {
            lastVisibilityUpdateMs = now;
        }

        float delta = Mth.clamp((now - lastVisibilityUpdateMs) / (float) VISIBILITY_ANIMATION_DURATION_MS, 0.0f, 1.0f);
        lastVisibilityUpdateMs = now;

        if (liveTarget != null) {
            renderedTarget = liveTarget;
            visibilityProgress = Math.min(1.0f, visibilityProgress + delta);
            return renderedTarget;
        }

        if (renderedTarget == null) {
            visibilityProgress = 0.0f;
            return null;
        }

        visibilityProgress = Math.max(0.0f, visibilityProgress - delta);
        if (visibilityProgress <= 0.01f) {
            renderedTarget = null;
            resetAnimatedState();
            return null;
        }
        return renderedTarget;
    }

    private Color tintColor(Color baseColor, float damageProgress) {
        int greenBlue = Mth.clamp(Math.round(255.0f - 155.0f * damageProgress), 100, 255);
        int red = Mth.clamp(Math.round(baseColor.getRed() + (255 - baseColor.getRed()) * damageProgress), 0, 255);
        int green = Mth.clamp(Math.round(baseColor.getGreen() * greenBlue / 255.0f), 0, 255);
        int blue = Mth.clamp(Math.round(baseColor.getBlue() * greenBlue / 255.0f), 0, 255);
        return new Color(red, green, blue, baseColor.getAlpha());
    }

    private Color withAlpha(Color color, float alphaScale) {
        int alpha = Mth.clamp(Math.round(color.getAlpha() * alphaScale), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private float updateAnimatedHealth(LivingEntity target, float currentHealth, float maxHealth, float frameTime) {
        int targetId = target.getId();
        if (targetId != lastTargetId) {
            lastTargetId = targetId;
            displayedHealth = currentHealth;
            delayedHealth = currentHealth;
            lastKnownHealth = currentHealth;
            lastKnownMaxHealth = maxHealth;
            lastDamageTimeMs = 0L;
        } else {
            if (lastKnownHealth >= 0.0f && currentHealth < lastKnownHealth) {
                lastDamageTimeMs = System.currentTimeMillis();
            }
            float speed = Mth.clamp(frameTime * 10.0f, 0.0f, 1.0f);
            displayedHealth = Mth.lerp(speed, displayedHealth, currentHealth);
            delayedHealth = updateDelayedHealth(currentHealth, frameTime);
            lastKnownHealth = currentHealth;
            lastKnownMaxHealth = maxHealth;
        }
        displayedHealth = Mth.clamp(displayedHealth, 0.0f, maxHealth);
        delayedHealth = Mth.clamp(delayedHealth, 0.0f, maxHealth);
        return Mth.clamp(displayedHealth / maxHealth, 0.0f, 1.0f);
    }

    private float updateDelayedHealth(float currentHealth, float frameTime) {
        if (!delayBar.getValue()) {
            return currentHealth;
        }
        if (currentHealth >= delayedHealth) {
            return currentHealth;
        }
        if (delayWait.getValue() && System.currentTimeMillis() - lastDamageTimeMs < delayTime.getValue().longValue()) {
            return delayedHealth;
        }
        float speed = Mth.clamp(frameTime * delaySpeed.getValue().floatValue() * 2.0f, 0.0f, 1.0f);
        return Mth.lerp(speed, delayedHealth, currentHealth);
    }

    private void resetAnimatedState() {
        lastTargetId = Integer.MIN_VALUE;
        displayedHealth = 0.0f;
        delayedHealth = 0.0f;
        lastKnownHealth = -1.0f;
        lastKnownMaxHealth = 1.0f;
        lastDamageTimeMs = 0L;
    }

}
