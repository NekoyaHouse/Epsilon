package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.modules.impl.movement.Scaffold;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.animation.Easing;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.util.Mth;

import java.awt.*;
import java.util.function.Supplier;

public class ScaffoldBlock extends HudModule {

    public static final ScaffoldBlock INSTANCE = new ScaffoldBlock();

    private ScaffoldBlock() {
        super("Scaffold Block", 0f, 0f, 84f, 28f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final DoubleSetting cornerRadius = doubleSetting("Corner Radius", 5.0, 0.0, 20.0, 0.5);
    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(15, 15, 15, 150));
    private final ColorSetting textColor = colorSetting("Text Color", new Color(248, 249, 252, 245));
    private final ColorSetting textSecondary = colorSetting("Text Secondary", new Color(210, 214, 225, 170));

    private final BoolSetting drawShadow = boolSetting("Drop Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", DEFAULT_SHADOW_BLUR, MIN_SHADOW_BLUR, MAX_SHADOW_BLUR, SHADOW_BLUR_STEP, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", DEFAULT_SHADOW_COLOR, drawShadow::getValue);

    private final BoolSetting backgroundBlur = boolSetting("Background Blur", true);
    private final IntSetting blurStrength = intSetting("Blur Strength", 5, 1, 16, 1);

    private final BoolSetting smoothNumber = boolSetting("Smooth Number", true);
    private final DoubleSetting numberDelay = doubleSetting("Number Delay", 0.15, 0.0, 0.5, 0.01, smoothNumber::getValue);

    private static final String LABEL = "Blocks";
    private static final float BASE_HEIGHT = 24.0f;
    private static final float BASE_PAD_X = 7.0f;
    private static final float BASE_LABEL_GAP = 2.6f;

    private boolean initialized;
    private String previousCountText = "0";
    private String targetCountText = "0";
    private String pendingCountText = "0";
    private float numberAnimProgress = 1.0f;
    private float delayTimer;
    private float visibilityProgress;
    private long lastVisibilityUpdateMs;

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    protected void onEnable() {
        resetAnimation();
    }

    @Override
    protected void onDisable() {
        resetAnimation();
    }

    @Override
    public void render(DeltaTracker deltaTracker) {
        int blockCount = getDisplayBlockCount();
        boolean shouldShow = shouldShowHud(blockCount);
        updateVisibilityAnimation(shouldShow);

        AnimationState animation = createAnimationState();
        if (!shouldShow && animation.panelProgress() <= 0.01f) {
            resetAnimation();
            return;
        }

        if (shouldShow) {
            syncNumberAnimation(blockCount, deltaTracker);
        } else if (!initialized) {
            return;
        }

        TextRenderer textRenderer = textRendererSupplier.get();
        UiTree.Scope scope = renderScope();

        Layout layout = createLayout(textRenderer);
        drawBackground(scope, layout, animation);
        drawText(scope, textRenderer, layout, animation);
        setBounds(layout.totalWidth(), layout.height());
    }

    private boolean shouldShowHud(int liveBlockCount) {
        boolean preview = mc.screen instanceof HudEditorScreen;
        return preview || (Scaffold.INSTANCE.isEnabled() && liveBlockCount > 0);
    }

    private int getDisplayBlockCount() {
        int liveBlockCount = Math.max(0, Scaffold.INSTANCE.getBlockCount());
        if (mc.screen instanceof HudEditorScreen) {
            return Math.max(64, liveBlockCount);
        }
        return liveBlockCount;
    }

    private AnimationState createAnimationState() {
        float rawVisibility = Mth.clamp(visibilityProgress, 0.0f, 1.0f);
        float easedVisibility = (float) Math.pow(rawVisibility, 3.0);
        float panelProgress = Easing.EASE_OUT_SINE.getFunction().apply(easedVisibility);
        float contentProgress = Easing.EASE_OUT_SINE.getFunction().apply(Mth.clamp((rawVisibility - 0.08f) / 0.92f, 0.0f, 1.0f));
        float contentAlpha = contentProgress * contentProgress * (3.0f - 2.0f * contentProgress);
        return new AnimationState(panelProgress, contentProgress, contentAlpha);
    }

    private Layout createLayout(TextRenderer textRenderer) {
        float scaled = scale.getValue().floatValue();
        float height = BASE_HEIGHT * scaled;
        float radius = Math.min(cornerRadius.getValue().floatValue() * scaled, height / 2.0f);
        float padX = BASE_PAD_X * scaled;
        float labelScale = 0.62f * scaled;
        float labelGap = BASE_LABEL_GAP * scaled;
        float numberColumnWidth = getNumberColumnWidth(textRenderer, scaled);
        float labelWidth = textRenderer.getWidth(LABEL, labelScale);
        float totalWidth = padX * 2.0f + numberColumnWidth + labelGap + labelWidth;
        return new Layout(height, radius, padX, scaled, labelScale, labelGap, numberColumnWidth, labelWidth, totalWidth, this.x);
    }

    private void drawBackground(UiTree.Scope scope, Layout layout, AnimationState animation) {
        float animatedWidth = layout.totalWidth() * animation.panelProgress();
        float animatedX = Mth.lerp(animation.panelProgress(), layout.centerX(), layout.renderX());
        float animatedRadius = Math.min(layout.radius(), animatedWidth / 2.0f);

        if (backgroundBlur.getValue()) {
            BlurShader.INSTANCE.render(animatedX, this.y, animatedWidth, layout.height(), animatedRadius, blurStrength.getValue());
        }
        if (drawShadow.getValue()) {
            scope.shadow(animatedX, this.y, animatedWidth, layout.height(), animatedRadius, shadowBlur.getValue().floatValue(), withAlpha(shadowColor.getValue(), animation.contentAlpha()));
        }

        scope.roundRect(animatedX, this.y, animatedWidth, layout.height(), animatedRadius, withAlpha(backgroundColor.getValue(), animation.contentAlpha()));
    }

    private void drawText(UiTree.Scope scope, TextRenderer textRenderer, Layout layout, AnimationState animation) {
        float numberColumnX = layout.renderX() + layout.padX();
        float numberY = this.y + (layout.height() - textRenderer.getHeight(layout.numberScale())) / 2.0f;
        float animatedNumberColumnX = Mth.lerp(animation.contentProgress(), layout.centerX() - layout.numberColumnWidth() / 2.0f, numberColumnX);
        float animatedWidth = layout.totalWidth() * animation.panelProgress();
        float animatedX = Mth.lerp(animation.panelProgress(), layout.centerX(), layout.renderX());

        boolean requiresScissor = animation.panelProgress() < 1.0f
                || smoothNumber.getValue() && numberAnimProgress < 1.0f;
        scope.scissorIf(requiresScissor, new UiRect(animatedX, this.y, animatedWidth, layout.height()), clipped -> {
            if (smoothNumber.getValue()) {
                drawRollingNumber(clipped, textRenderer, layout.numberScale(), animatedNumberColumnX, layout.numberColumnWidth(), numberY, animation.contentAlpha());
            } else {
                clipped.text(targetCountText, animatedNumberColumnX, numberY, layout.numberScale(), withAlpha(textColor.getValue(), animation.contentAlpha()));
            }

            float labelX = numberColumnX + layout.numberColumnWidth() + layout.labelGap();
            float animatedLabelX = Mth.lerp(animation.contentProgress(), layout.centerX() - layout.labelWidth() / 2.0f, labelX);
            float labelY = this.y + (layout.height() - textRenderer.getHeight(layout.labelScale())) / 2.0f;
            clipped.text(LABEL, animatedLabelX, labelY, layout.labelScale(), withAlpha(textSecondary.getValue(), animation.contentAlpha()));
        });
    }

    private void syncNumberAnimation(int blockCount, DeltaTracker deltaTracker) {
        String nextText = Integer.toString(blockCount);
        if (!initialized) {
            previousCountText = nextText;
            targetCountText = nextText;
            pendingCountText = nextText;
            numberAnimProgress = 1.0f;
            delayTimer = 0.0f;
            initialized = true;
            return;
        }

        if (!smoothNumber.getValue()) {
            previousCountText = nextText;
            targetCountText = nextText;
            pendingCountText = nextText;
            numberAnimProgress = 1.0f;
            delayTimer = 0.0f;
            return;
        }

        float frameTime = deltaTracker == null ? 0.05f : deltaTracker.getGameTimeDeltaTicks() / 20.0f;
        if (!nextText.equals(targetCountText)) {
            pendingCountText = nextText;
            delayTimer += frameTime;
            if (delayTimer >= numberDelay.getValue().floatValue()) {
                previousCountText = targetCountText;
                targetCountText = pendingCountText;
                numberAnimProgress = 0.0f;
                delayTimer = 0.0f;
            }
        } else {
            delayTimer = 0.0f;
        }

        if (numberAnimProgress < 1.0f) {
            numberAnimProgress = Math.min(1.0f, numberAnimProgress + frameTime * 3.0f);
            if (numberAnimProgress >= 1.0f) {
                previousCountText = targetCountText;
            }
        }
    }

    private void updateVisibilityAnimation(boolean shouldShow) {
        long now = System.currentTimeMillis();
        if (lastVisibilityUpdateMs == 0L) {
            lastVisibilityUpdateMs = now;
        }

        float delta = Mth.clamp((now - lastVisibilityUpdateMs) / 220.0f, 0.0f, 1.0f);
        lastVisibilityUpdateMs = now;

        if (shouldShow) {
            visibilityProgress = Math.min(1.0f, visibilityProgress + delta);
        } else {
            visibilityProgress = Math.max(0.0f, visibilityProgress - delta);
        }
    }

    private float getNumberColumnWidth(TextRenderer textRenderer, float numberScale) {
        float width = Math.max(
                textRenderer.getWidth(previousCountText, numberScale),
                textRenderer.getWidth(targetCountText, numberScale)
        );
        return width + 4.0f * scale.getValue().floatValue();
    }

    private void drawRollingNumber(UiTree.Scope scope, TextRenderer textRenderer, float numberScale, float columnX, float columnWidth, float textY, float alphaMul) {
        String previous = previousCountText;
        String target = targetCountText;
        int maxLen = Math.max(previous.length(), target.length());
        float contentWidth = Math.max(
                textRenderer.getWidth(previous, numberScale),
                textRenderer.getWidth(target, numberScale)
        );
        float charX = columnX + Math.max(0.0f, (columnWidth - contentWidth) / 2.0f);
        float slideOffset = 4.0f * scale.getValue().floatValue();

        for (int i = 0; i < maxLen; i++) {
            char previousChar = i < previous.length() ? previous.charAt(i) : '\0';
            char targetChar = i < target.length() ? target.charAt(i) : '\0';

            float slotWidth = 0.0f;
            if (previousChar != '\0') {
                slotWidth = Math.max(slotWidth, textRenderer.getWidth(String.valueOf(previousChar), numberScale));
            }
            if (targetChar != '\0') {
                slotWidth = Math.max(slotWidth, textRenderer.getWidth(String.valueOf(targetChar), numberScale));
            }

            if (previousChar == targetChar) {
                if (targetChar != '\0') {
                    scope.text(String.valueOf(targetChar), charX, textY, numberScale, withAlpha(textColor.getValue(), alphaMul));
                }
            } else {
                float oldAlpha = 1.0f - numberAnimProgress;
                float newAlpha = numberAnimProgress;

                if (previousChar != '\0' && oldAlpha > 0.01f) {
                    scope.text(String.valueOf(previousChar), charX, textY - numberAnimProgress * slideOffset, numberScale, withAlpha(textColor.getValue(), oldAlpha * alphaMul));
                }
                if (targetChar != '\0' && newAlpha > 0.01f) {
                    scope.text(String.valueOf(targetChar), charX, textY + (1.0f - numberAnimProgress) * slideOffset, numberScale, withAlpha(textColor.getValue(), newAlpha * alphaMul));
                }
            }

            charX += slotWidth;
        }
    }

    private void resetAnimation() {
        initialized = false;
        previousCountText = "0";
        targetCountText = "0";
        pendingCountText = "0";
        numberAnimProgress = 1.0f;
        delayTimer = 0.0f;
        visibilityProgress = 0.0f;
        lastVisibilityUpdateMs = 0L;
    }

    private static Color withAlpha(Color color, float alphaMul) {
        int alpha = Mth.clamp((int) (color.getAlpha() * alphaMul), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private record AnimationState(float panelProgress, float contentProgress, float contentAlpha) {
    }

    private record Layout(float height, float radius, float padX, float numberScale, float labelScale, float labelGap,
                          float numberColumnWidth, float labelWidth, float totalWidth, float renderX) {
        private float centerX() {
            return renderX + totalWidth / 2.0f;
        }
    }

}
