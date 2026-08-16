package com.github.epsilon.elements.impl.notification.style;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.elements.impl.ModuleList;
import com.github.epsilon.elements.impl.notification.Notification;
import com.github.epsilon.elements.impl.notification.Notifications;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.animation.Easing;

import java.awt.*;
import java.util.List;

/**
 * The compact translucent notification style from Kiwi's Akarin HUD.
 */
public class AkarinNotification {

    private static final float MIN_WIDTH = 110.0f;
    private static final float CARD_HEIGHT = 29.0f;
    private static final float CARD_RADIUS = 6.0f;
    private static final float TEXT_X = 6.0f;
    private static final float RIGHT_PADDING = 6.0f;
    private static final float STRIPE_WIDTH = 1.5f;
    private static final float ENTRY_GAP = 5.0f;
    private static final float SLIDE_MARGIN = 10.0f;
    private static final long TRANSITION_DURATION = 200L;

    private final Notifications owner;
    private final BoolSetting backgroundBlur;
    private final DoubleSetting blurStrength;
    private final IntSetting backgroundAlpha;
    private final DoubleSetting radius;
    private final IntSetting displayTime;

    public AkarinNotification(Notifications owner) {
        this.owner = owner;
        Setting.Dependency active = () -> owner.isStyle(Notifications.Style.Akarin);
        backgroundBlur = owner.boolSetting("Akarin Background Blur", true, active);
        blurStrength = owner.doubleSetting("Akarin Blur Strength", 15.0, 1.0, 30.0, 1.0,
                () -> active.check() && backgroundBlur.getValue());
        backgroundAlpha = owner.intSetting("Akarin Background Alpha", 80, 0, 255, 1, active);
        radius = owner.doubleSetting("Akarin Radius", (double) CARD_RADIUS, 0.0, 14.0, 0.5, active);
        displayTime = owner.intSetting("Akarin Display Time", 800, 500, 10000, 100, active);
    }

    public int getDisplayTime() {
        return displayTime.getValue();
    }

    public float height(float scale) {
        return CARD_HEIGHT * scale;
    }

    public float totalHeight(int count, float scale) {
        if (count <= 0) return height(scale);
        return (CARD_HEIGHT * count + ENTRY_GAP * (count - 1)) * scale;
    }

    public float width(TextRenderer metrics, List<Notification> entries, float scale, float fontScale) {
        float maxWidth = MIN_WIDTH * scale;
        float titleScale = titleScale(scale, fontScale);
        float statusScale = statusScale(scale, fontScale);
        for (Notification notification : entries) {
            maxWidth = Math.max(maxWidth, TEXT_X * scale
                    + Math.max(metrics.getWidth("Module", titleScale),
                    metrics.getWidth(statusText(notification), statusScale))
                    + RIGHT_PADDING * scale);
        }
        return maxWidth;
    }

    public boolean isVisible(Notification notification) {
        return animationProgress(notification) > 0.001f;
    }

    public void render(UiTree.Scope scope, TextRenderer metrics, List<Notification> entries,
                       float boundsX, float boundsY, float boundsWidth, float scale, float fontScale,
                       HudModule.HorizontalAnchor horizontalAnchor, HudModule.VerticalAnchor verticalAnchor) {
        float cardHeight = height(scale);
        float gap = ENTRY_GAP * scale;
        float totalHeight = totalHeight(entries.size(), scale);
        boolean stackUp = verticalAnchor == HudModule.VerticalAnchor.Bottom;
        float targetY = stackUp ? boundsY + totalHeight - cardHeight : boundsY;
        float cardRadius = radius.getValue().floatValue() * scale;

        for (Notification notification : entries) {
            float progress = animationProgress(notification);
            if (progress <= 0.001f) continue;

            float cardWidth = width(metrics, List.of(notification), scale, fontScale);
            float baseX = switch (horizontalAnchor) {
                case Right -> boundsX + boundsWidth - cardWidth;
                case Center -> boundsX + (boundsWidth - cardWidth) * 0.5f;
                case Left -> boundsX;
            };
            renderToast(scope, metrics, notification, baseX, targetY, cardWidth, cardHeight,
                    cardRadius, scale, fontScale, horizontalAnchor, progress);
            targetY += stackUp ? -(cardHeight + gap) : cardHeight + gap;
        }
    }

    private void renderToast(UiTree.Scope scope, TextRenderer metrics, Notification notification,
                             float x, float y, float width, float height, float cardRadius,
                             float scale, float fontScale, HudModule.HorizontalAnchor horizontalAnchor,
                             float progress) {
        float eased = Easing.EASE_OUT_CUBIC.getFunction().apply(progress);
        float direction = horizontalAnchor == HudModule.HorizontalAnchor.Left ? -1.0f : 1.0f;
        float actualX = x + direction * (1.0f - eased) * (width + SLIDE_MARGIN * scale);
        int alpha = Math.clamp(Math.round(255.0f * eased), 0, 255);

        if (backgroundBlur.getValue()) {
            BlurShader.INSTANCE.render(actualX, y, width, height, cardRadius, blurStrength.getValue().floatValue() * scale);
        }

        scope.roundRect(actualX, y, width, height, cardRadius,
                new Color(8, 12, 22, Math.round(backgroundAlpha.getValue() * eased)));

        Color topColor = ModuleList.INSTANCE.getThemeColor(0, 0L);
        Color bottomColor = ModuleList.INSTANCE.getThemeColor(1, 1L);
        float titleScale = titleScale(scale, fontScale);
        float titleHeight = metrics.getHeight(titleScale);
        float stripeY = y + 5.0f * scale;
        float stripeHeight = Math.max(4.0f * scale, titleHeight * 0.68f);
        Color stripeGlow = withAlpha(topColor, Math.round(alpha * 0.13f));
        scope.roundRect(actualX - 0.2f * scale, stripeY - 1.5f * scale,
                (STRIPE_WIDTH + 1.5f) * scale, stripeHeight + 3.0f * scale, 2.0f * scale, stripeGlow);
        scope.roundRectVerticalGradient(actualX, stripeY, STRIPE_WIDTH * scale, stripeHeight,
                STRIPE_WIDTH * 0.5f * scale, withAlpha(topColor, alpha), withAlpha(bottomColor, alpha));

        float textX = actualX + TEXT_X * scale;
        scope.text("Module", textX, y + 4.5f * scale, titleScale, new Color(244, 246, 250, alpha));
        float statusScale = statusScale(scale, fontScale);
        scope.scissor(actualX, y, width, height, textScope -> textScope.text(
                statusText(notification), textX, y + 15.5f * scale, statusScale,
                new Color(244, 246, 250, Math.round(alpha * (160.0f / 255.0f)))));
    }

    private float animationProgress(Notification notification) {
        float enter = notification.shouldSkipIntroAnim()
                ? 1.0f
                : Math.clamp(notification.getElapsedTime() / (float) TRANSITION_DURATION, 0.0f, 1.0f);
        long exitTime = notification.getExitTime();
        if (exitTime < 0L) return enter;
        return enter * (1.0f - Math.clamp(exitTime / (float) TRANSITION_DURATION, 0.0f, 1.0f));
    }

    private String statusText(Notification notification) {
        String title = notification.getTitle() == null ? "" : notification.getTitle();
        String subTitle = notification.getSubTitle() == null ? "" : notification.getSubTitle();
        if (subTitle.isEmpty()) return title;
        if (title.isEmpty()) return subTitle;
        return subTitle + " " + title;
    }

    private float titleScale(float scale, float fontScale) {
        return Math.max(0.25f, fontScale * 0.95f * scale);
    }

    private float statusScale(float scale, float fontScale) {
        return Math.max(0.25f, fontScale * scale);
    }

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    private Color withAlpha(Color color, float alpha) {
        return withAlpha(color, Math.round(color.getAlpha() * Math.clamp(alpha, 0.0f, 1.0f)));
    }
}
