package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.Locale;
import java.util.function.Supplier;

public class BPS extends HudModule {

    public static final BPS INSTANCE = new BPS();

    private BPS() {
        super("BPS HUD", 0f, 0f, 158f, 68f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 4.0, 0.0, 14.0, 0.5);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 150));
    private final ColorSetting accentColor = colorSetting("Accent Color", new Color(130, 180, 255, 255));
    private final ColorSetting textColor = colorSetting("Text Color", new Color(248, 249, 252, 245));
    private final ColorSetting textSecondary = colorSetting("Text Secondary", new Color(210, 214, 225, 165));
    private final ColorSetting textMuted = colorSetting("Text Muted", new Color(180, 184, 194, 145));

    private final DoubleSetting graphHeight = doubleSetting("Graph Height", 28.0, 20.0, 60.0, 1.0);
    private final ColorSetting graphBgColor = colorSetting("Graph Background", new Color(255, 255, 255, 10));
    private final ColorSetting graphMidlineColor = colorSetting("Graph Midline", new Color(255, 255, 255, 10));
    private final ColorSetting graphLineColor = colorSetting("Graph Line Color", new Color(130, 180, 255, 235));
    private final ColorSetting graphGlowColor = colorSetting("Graph Glow Color", new Color(130, 180, 255, 45));

    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", DEFAULT_SHADOW_BLUR, MIN_SHADOW_BLUR, MAX_SHADOW_BLUR, SHADOW_BLUR_STEP, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", DEFAULT_SHADOW_COLOR, drawShadow::getValue);

    private final BoolSetting backgroundBlur = boolSetting("Background Blur", true);
    private final IntSetting blurStrength = intSetting("Blur Strength", 5, 1, 16, 1);

    private final BoolSetting smoothNumber = boolSetting("Smooth Number", true);
    private final DoubleSetting numberDelay = doubleSetting("Number Delay", 0.15, 0.0, 0.5, 0.01, smoothNumber::getValue);

    private static final int GRAPH_SIZE = 72;
    private final float[] graphValues = new float[GRAPH_SIZE];
    private int graphIndex;
    private double lastX;
    private double lastZ;
    private long lastUpdateTime;
    private float currentBps;
    private float animatedBps;
    private float highestBps;
    private boolean initialized;

    private String previousBpsText = "0.0";
    private String targetBpsText = "0.0";
    private String pendingBpsText = "0.0";
    private float numberAnimProgress = 1.0f;
    private float delayTimer;

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    protected void onEnable() {
        resetBps();
    }

    @Override
    protected void onDisable() {
        resetBps();
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        if (nullCheck()) return;

        updateBps();

        TextRenderer textRenderer = textRendererSupplier.get();
        UiTree.Scope scope = renderScope();

        float s = scale.getValue().floatValue();
        float radius = cornerRadius.getValue().floatValue() * s;

        float panelW = 148f * s;
        float panelH = 58f * s;

        if (backgroundBlur.getValue()) {
            BlurShader.INSTANCE.render(this.x, this.y, panelW, panelH, radius, blurStrength.getValue());
        }

        if (drawShadow.getValue()) {
            scope.shadow(this.x, this.y, panelW, panelH, radius, shadowBlur.getValue().floatValue(), shadowColor.getValue());
        }

        scope.roundRect(this.x, this.y, panelW, panelH, radius, backgroundColor.getValue());

        float dotSize = 5f * s;
        float dotRadius = dotSize / 2f;
        scope.roundRect(this.x + 8f * s, this.y + 9f * s, dotSize, dotSize, dotRadius, accentColor.getValue());

        float titleScale = 0.62f * s;
        scope.text("Movement", this.x + 18f * s, this.y + 7f * s, titleScale, textColor.getValue());

        String bpsText = formatBps(animatedBps);
        float numberScale = 1.08f * s;
        float unitScale = 0.6f * s;
        float peakScale = 0.46f * s;

        if (smoothNumber.getValue()) {
            float frameTime = deltaTracker == null ? 0.05f : deltaTracker.getGameTimeDeltaTicks() / 20.0f;
            if (!bpsText.equals(targetBpsText)) {
                pendingBpsText = bpsText;
                delayTimer += frameTime;
                if (delayTimer >= numberDelay.getValue().floatValue()) {
                    previousBpsText = targetBpsText;
                    targetBpsText = pendingBpsText;
                    numberAnimProgress = 0f;
                    delayTimer = 0f;
                }
            } else {
                delayTimer = 0f;
            }
            if (numberAnimProgress < 1f) {
                numberAnimProgress = Math.min(1f, numberAnimProgress + frameTime * 3f);
                if (numberAnimProgress >= 1f) {
                    previousBpsText = targetBpsText;
                }
            }
            drawRollingNumber(scope, textRenderer, numberScale, s);
            float targetWidth = textRenderer.getWidth(targetBpsText, numberScale);
            scope.text("bps", this.x + 9f * s + targetWidth + 3f * s, this.y + 28f * s, unitScale, textSecondary.getValue());
        } else {
            scope.text(bpsText, this.x + 9f * s, this.y + 23f * s, numberScale, textColor.getValue());
            float numberWidth = textRenderer.getWidth(bpsText, numberScale);
            scope.text("bps", this.x + 9f * s + numberWidth + 3f * s, this.y + 28f * s, unitScale, textSecondary.getValue());
        }

        String peakText = "Maximum " + formatBps(highestBps);
        scope.text(peakText, this.x + 9f * s, this.y + 44f * s, peakScale, textMuted.getValue());

        float graphX = this.x + 62f * s;
        float graphY = this.y + 15f * s;
        float graphW = 78f * s;
        float graphH = graphHeight.getValue().floatValue() * s;
        drawGraph(scope, graphX, graphY, graphW, graphH, s);

        setBounds(panelW, panelH);
    }

    private void drawGraph(UiTree.Scope scope, float x, float y, float w, float h, float s) {
        float graphRadius = 4f * s;

        scope.roundRect(x, y, w, h, graphRadius, graphBgColor.getValue());

        float paddingX = 5f * s;
        float paddingY = 3f * s;
        float innerX = x + paddingX;
        float innerY = y + paddingY;
        float innerW = w - paddingX * 2f;
        float innerH = h - paddingY * 2f;

        if (innerW <= 0f || innerH <= 0f) return;

        float baseline = innerY + innerH;
        float usableH = innerH * 0.88f;

        float refLineY = baseline - usableH * 0.22f;
        float refThickness = Math.max(0.45f * s, 0.5f);
        scope.roundRect(x + 4f * s, refLineY - refThickness / 2f, w - 8f * s, refThickness, refThickness / 2f, graphMidlineColor.getValue());

        float max = getGraphMax();
        if (max < 0.1f) max = 1f;

        Color lineColor = graphLineColor.getValue();
        Color glowColor = graphGlowColor.getValue();

        float step = innerW / (GRAPH_SIZE - 1);
        float barWidth = Math.max(step * 0.75f, 0.7f * s);
        float glowBarWidth = Math.max(barWidth + 1.8f * s, 1.2f * s);
        float barRadius = Math.min(s, barWidth / 2f);
        float glowBarRadius = Math.min(1.5f * s, glowBarWidth / 2f);

        for (int i = 0; i < GRAPH_SIZE; i++) {
            int dataIndex = (graphIndex + i) % GRAPH_SIZE;
            float value = graphValues[dataIndex];
            float normalized = Mth.clamp(value / max, 0f, 1f);

            float wave = (float) Math.sin(i * 0.75f) * 0.04f;
            float finalValue = Math.max(0f, Math.min(1f, normalized + wave * normalized));

            float barH = finalValue * usableH;
            if (barH < 0.01f) continue;

            float barX = innerX + i * step - barWidth / 2f;
            float glowX = innerX + i * step - glowBarWidth / 2f;

            float age = i / (float) GRAPH_SIZE;
            float glowAlpha = 0.10f + age * 0.24f;
            float mainAlpha = 0.30f + age * 0.62f;

            scope.roundRect(glowX, baseline - barH, glowBarWidth, barH, glowBarRadius, withAlpha(glowColor, glowAlpha));
            scope.roundRect(barX, baseline - barH, barWidth, barH, barRadius, withAlpha(lineColor, mainAlpha));
        }

        float latestValue = graphValues[(graphIndex - 1 + GRAPH_SIZE) % GRAPH_SIZE];
        float latestNormalized = Mth.clamp(latestValue / max, 0f, 1f);
        float latestY = baseline - latestNormalized * usableH;
        float latestX = innerX + innerW;
        float pulse = 0.6f + latestNormalized * 1.8f;
        float dotR = (2.0f + pulse) * s;

        scope.roundRect(latestX - dotR, latestY - dotR, dotR * 2f, dotR * 2f, dotR, withAlpha(glowColor, 0.24f + latestNormalized * 0.22f));
        scope.roundRect(latestX - 1.55f * s, latestY - 1.55f * s, 3.1f * s, 3.1f * s, 1.55f * s, lineColor);
    }

    private void updateBps() {
        long now = System.currentTimeMillis();

        double x = mc.player.getX();
        double z = mc.player.getZ();

        if (!initialized) {
            initialized = true;
            lastX = x;
            lastZ = z;
            lastUpdateTime = now;
            currentBps = 0f;
            animatedBps = 0f;
            highestBps = 0f;
            pushGraphValue(0f);
            return;
        }

        long diff = now - lastUpdateTime;
        if (diff <= 0L) return;

        if (diff < 70L) {
            animatedBps = Mth.lerp(0.16f, animatedBps, currentBps);
            return;
        }

        double dx = x - lastX;
        double dz = z - lastZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        float rawBps = (float) (distance / (diff / 1000.0));

        if (rawBps > 80f || mc.player.isDeadOrDying()) {
            rawBps = 0f;
            lastX = x;
            lastZ = z;
            lastUpdateTime = now;
        }

        currentBps = Mth.lerp(0.55f, currentBps, rawBps);
        animatedBps = Mth.lerp(0.25f, animatedBps, currentBps);

        if (currentBps > highestBps && currentBps < 80f) {
            highestBps = currentBps;
        }

        pushGraphValue(currentBps);

        lastX = x;
        lastZ = z;
        lastUpdateTime = now;
    }

    private void pushGraphValue(float value) {
        value = Math.max(0f, value);
        value = Math.min(value, 26f);

        int prevIndex = (graphIndex - 1 + GRAPH_SIZE) % GRAPH_SIZE;
        float previous = graphValues[prevIndex];

        float speed = value > previous ? 0.68f : 0.34f;
        float smoothed = Mth.lerp(speed, previous, value);

        graphValues[graphIndex] = smoothed;
        graphIndex = (graphIndex + 1) % GRAPH_SIZE;
    }

    private float getGraphMax() {
        float max = 0f;
        for (float value : graphValues) {
            if (value > max) max = value;
        }
        return Math.max(max, 8f);
    }

    private void drawRollingNumber(UiTree.Scope scope, TextRenderer textRenderer, float numberScale, float s) {
        float progress = numberAnimProgress;
        String prev = previousBpsText;
        String target = targetBpsText;

        int maxLen = Math.max(prev.length(), target.length());
        float charX = this.x + 9f * s;
        float baseY = this.y + 23f * s;
        float slideOffset = 4f * s;

        for (int i = 0; i < maxLen; i++) {
            char prevChar = i < prev.length() ? prev.charAt(i) : '\0';
            char targetChar = i < target.length() ? target.charAt(i) : '\0';

            float slotWidth = 0f;
            if (prevChar != '\0') {
                slotWidth = Math.max(slotWidth, textRenderer.getWidth(String.valueOf(prevChar), numberScale));
            }
            if (targetChar != '\0') {
                slotWidth = Math.max(slotWidth, textRenderer.getWidth(String.valueOf(targetChar), numberScale));
            }

            if (prevChar == targetChar) {
                if (targetChar != '\0') {
                    scope.text(String.valueOf(targetChar), charX, baseY, numberScale, textColor.getValue());
                }
            } else {
                float oldAlpha = 1f - progress;
                float newAlpha = progress;

                if (prevChar != '\0' && oldAlpha > 0.01f) {
                    scope.text(String.valueOf(prevChar), charX, baseY - progress * slideOffset, numberScale, withAlpha(textColor.getValue(), oldAlpha));
                }
                if (targetChar != '\0' && newAlpha > 0.01f) {
                    scope.text(String.valueOf(targetChar), charX, baseY + (1f - progress) * slideOffset, numberScale, withAlpha(textColor.getValue(), newAlpha));
                }
            }

            charX += slotWidth;
        }
    }

    private void resetBps() {
        initialized = false;
        lastX = 0;
        lastZ = 0;
        lastUpdateTime = 0L;
        currentBps = 0f;
        animatedBps = 0f;
        highestBps = 0f;
        graphIndex = 0;
        for (int i = 0; i < graphValues.length; i++) {
            graphValues[i] = 0f;
        }
        previousBpsText = "0.0";
        targetBpsText = "0.0";
        numberAnimProgress = 1.0f;
        pendingBpsText = "0.0";
        delayTimer = 0f;
    }

    private static String formatBps(float bps) {
        if (bps < 0.05f) return "0.0";
        return String.format(Locale.ROOT, "%.1f", bps);
    }

    private static Color withAlpha(Color color, float alphaMul) {
        int a = Mth.clamp((int) (color.getAlpha() * alphaMul), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

}
