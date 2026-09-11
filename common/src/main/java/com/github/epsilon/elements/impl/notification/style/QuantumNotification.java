package com.github.epsilon.elements.impl.notification.style;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.elements.impl.notification.Notification;
import com.github.epsilon.elements.impl.notification.NotificationMode;
import com.github.epsilon.elements.impl.notification.Notifications;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.animation.Easing;

import java.awt.*;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class QuantumNotification {

    private static final float TOAST_WIDTH = 160.0f;
    private static final float TOAST_HEIGHT = 30.0f;
    private static final float BORDER_RADIUS = 9.0f;
    private static final float ICON_SIZE = 18.0f;
    private static final float PADDING_H = 8.0f;
    private static final float CONTENT_GAP = 6.0f;
    private static final float TOAST_GAP = 5.0f;
    private static final float SLIDE_MARGIN = 12.0f;
    private static final long TRANSITION_DURATION = 200L;

    private final Map<Notification, SpringState> states = new IdentityHashMap<>();
    private final ColorSetting backgroundColor;
    private final BoolSetting backgroundBlur;
    private final DoubleSetting blurStrength;
    private final BoolSetting drawShadow;
    private final DoubleSetting shadowBlur;
    private final ColorSetting shadowColor;
    private final IntSetting displayTime;

    public QuantumNotification(Notifications owner) {
        Setting.Dependency active = () -> owner.isStyle(Notifications.Style.Quantum);
        backgroundColor = owner.colorSetting("Background Color", new Color(0, 0, 0, 50), active);
        backgroundBlur = owner.boolSetting("Background Blur", true, active);
        blurStrength = owner.doubleSetting("Blur Strength", 5.0, 1.0, 20.0, 1.0, () -> active.check() && backgroundBlur.getValue());
        drawShadow = owner.boolSetting("Drop Shadow", true, active);
        shadowBlur = owner.doubleSetting("Shadow Blur", 10.0, 2.0, 32.0, 1.0, () -> active.check() && drawShadow.getValue());
        shadowColor = owner.colorSetting("Shadow Color", new Color(255, 255, 255, 110), () -> active.check() && drawShadow.getValue());
        displayTime = owner.intSetting("Quantum Display Time", 5000, 500, 10000, 100, active);
    }

    public int getDisplayTime() {
        return displayTime.getValue();
    }

    public float width(float scale) {
        return TOAST_WIDTH * scale;
    }

    public float height(float scale) {
        return TOAST_HEIGHT * scale;
    }

    public float totalHeight(int count, float scale) {
        if (count <= 0) return height(scale);
        return (TOAST_HEIGHT * count + TOAST_GAP * (count - 1)) * scale;
    }

    public void clear() {
        states.clear();
    }

    public boolean isVisible(Notification notification) {
        return animationProgress(notification) > 0.001f;
    }

    public void render(UiTree.Scope scope, TextRenderer metrics, List<Notification> entries, float boundsX, float boundsY, float scale, float fontScale, HudModule.HorizontalAnchor horizontalAnchor, HudModule.VerticalAnchor verticalAnchor) {
        states.keySet().removeIf(existing -> entries.stream().noneMatch(entry -> entry == existing));

        float toastWidth = width(scale);
        float toastHeight = height(scale);
        float gap = TOAST_GAP * scale;
        float totalHeight = totalHeight(entries.size(), scale);
        boolean stackUp = verticalAnchor == HudModule.VerticalAnchor.Bottom;
        float targetY = stackUp ? boundsY + totalHeight - toastHeight : boundsY;

        for (Notification notification : entries) {
            float progress = animationProgress(notification);
            if (progress <= 0.001f) continue;

            SpringState state = states.computeIfAbsent(notification, ignored -> new SpringState());
            float renderY = state.update(targetY);
            renderToast(scope, metrics, notification, boundsX, renderY, toastWidth, toastHeight, scale, fontScale, horizontalAnchor, progress);
            targetY += stackUp ? -(toastHeight + gap) : toastHeight + gap;
        }
    }

    private void renderToast(UiTree.Scope scope, TextRenderer metrics, Notification notification, float x, float y, float width, float height, float scale, float fontScale, HudModule.HorizontalAnchor horizontalAnchor, float progress) {
        float eased = Easing.SMOOTH_STEP.getFunction().apply(progress);
        float direction = horizontalAnchor == HudModule.HorizontalAnchor.Left ? -1.0f : 1.0f;
        float actualX = x + direction * (1.0f - eased) * (width + SLIDE_MARGIN * scale);
        int alpha = Math.clamp(Math.round(255.0f * eased), 0, 255);

        if (backgroundBlur.getValue()) {
            BlurShader.INSTANCE.render(actualX, y, width, height, BORDER_RADIUS * scale, blurStrength.getValue().floatValue());
        }
        if (drawShadow.getValue()) {
            scope.shadow(actualX, y, width, height, BORDER_RADIUS * scale, shadowBlur.getValue().floatValue() * scale, shadowColor.getValue());
        }
        scope.roundRect(actualX, y, width, height, BORDER_RADIUS * scale, withAlpha(backgroundColor.getValue(), Math.round(backgroundColor.getValue().getAlpha() * eased)));

        float iconX = actualX + PADDING_H * scale;
        float iconY = y + (height - ICON_SIZE * scale) * 0.5f;
        drawIcon(scope, metrics, notification.getMode(), iconX, iconY, scale, alpha);

        float contentX = iconX + ICON_SIZE * scale + CONTENT_GAP * scale;
        float contentRight = actualX + width - PADDING_H * scale;
        scope.scissor(actualX, y, width, height, textScope -> {
            drawContent(textScope, metrics, notification, contentX, contentRight, y, height, scale, fontScale, alpha);
        });
    }

    private void drawContent(UiTree.Scope scope, TextRenderer metrics, Notification notification, float x, float right, float y, float height, float scale, float fontScale, int alpha) {
        float titleScale = fitTextScale(metrics, notification.getTitle(), fontScale * scale, right - x);
        float messageScale = fitTextScale(metrics, notification.getSubTitle(), titleScale * 0.86f, right - x);
        boolean hasMessage = !notification.getSubTitle().isEmpty();
        float titleHeight = metrics.getHeight(titleScale);
        float messageHeight = hasMessage ? metrics.getHeight(messageScale) : 0.0f;
        float lineGap = hasMessage ? 1.5f * scale : 0.0f;
        float contentHeight = titleHeight + lineGap + messageHeight;
        float titleY = y + (height - contentHeight) * 0.5f;

        scope.text(notification.getTitle(), x, titleY, titleScale, new Color(255, 255, 255, alpha));
        if (hasMessage) {
            scope.text(notification.getSubTitle(), x, titleY + titleHeight + lineGap, messageScale, new Color(255, 255, 255, Math.round(alpha * 0.7f)));
        }
    }

    private void drawIcon(UiTree.Scope scope, TextRenderer metrics, NotificationMode mode, float x, float y, float scale, int alpha) {
        float iconScale = ICON_SIZE * scale / 48.0f;
        float centerX = x + ICON_SIZE * scale * 0.5f;
        float centerY = y + ICON_SIZE * scale * 0.5f;
        float time = System.nanoTime() / 1_000_000_000.0f;
        Color color = quantumColor(mode, alpha);

        switch (mode) {
            case Success -> drawSuccess(scope, centerX, centerY, iconScale, time, color, alpha);
            case Info -> drawInfo(scope, centerX, centerY, iconScale, time, color, alpha);
            case Warning -> drawWarning(scope, metrics, centerX, centerY, iconScale, time, color, alpha);
            case Error -> drawError(scope, centerX, centerY, iconScale, time, color, alpha);
        }
    }

    private void drawSuccess(UiTree.Scope scope, float cx, float cy, float s, float time, Color color, int alpha) {
        drawDashedCircle(scope, cx, cy, 22.0f * s, 2.0f * s, 16.0f, 12.0f, 0.0f, withAlpha(color, Math.round(alpha * 0.2f)));

        float phase = positiveModulo(time, 2.5f) / 2.5f;
        if (phase < 0.4f) {
            float local = phase < 0.15f ? phase / 0.15f : (phase - 0.15f) / 0.25f;
            float ringScale = phase < 0.15f ? 0.8f + 0.3f * local : 1.1f + 0.2f * local;
            float ringAlpha = phase < 0.15f ? 0.4f * local : 0.4f * (1.0f - local);
            scope.arc(cx, cy, 18.0f * s * ringScale, Math.max(0.5f * s, 2.0f * s), 0.0f, 360.0f, true, withAlpha(color, Math.round(alpha * ringAlpha)));
        }

        float checkProgress = Math.min(1.0f, positiveModulo(time, 3.0f) / 0.7f);
        float x1 = cx - 10.0f * s;
        float x2 = cx - 3.0f * s;
        float y2 = cy + 7.0f * s;
        float x3 = cx + 10.0f * s;
        float y3 = cy - 7.0f * s;
        drawProgressLine(scope, x1, cy, x2, y2, 1.4f * s, checkProgress * 1.85f, color);
        drawProgressLine(scope, x2, y2, x3, y3, 1.4f * s, (checkProgress - 0.46f) * 1.85f, color);
    }

    private void drawInfo(UiTree.Scope scope, float cx, float cy, float s, float time, Color color, int alpha) {
        scope.arc(cx, cy, 20.0f * s, 0.5f * s, 0.0f, 360.0f, false, withAlpha(color, Math.round(alpha * 0.2f)));
        scope.arc(cx, cy, 12.0f * s, 0.5f * s, 0.0f, 360.0f, false, withAlpha(color, Math.round(alpha * 0.1f)));

        float angle = positiveModulo(time, 4.0f) / 4.0f * 360.0f;
        scope.gradientArc(cx, cy, 15.5f * s, 9.0f * s, angle - 90.0f, 78.0f, false, angle, withAlpha(color, 0), withAlpha(color, Math.round(alpha * 0.12f)), withAlpha(color, Math.round(alpha * 0.38f)));

        float[][] points = {{14.0f, -6.0f, 0.2f}, {-14.0f, 6.0f, 0.8f}, {4.0f, 16.0f, 1.4f}};
        for (float[] point : points) {
            float pointPhase = positiveModulo(time - point[2], 2.0f) / 2.0f;
            float pulse = pointPhase < 0.5f ? pointPhase * 2.0f : (1.0f - pointPhase) * 2.0f;
            dot(scope, cx + point[0] * s, cy + point[1] * s, (0.75f + pulse * 0.45f) * s, withAlpha(color, Math.round(alpha * pulse)));
        }
        dot(scope, cx, cy, 1.5f * s, color);
    }

    private void drawWarning(UiTree.Scope scope, TextRenderer metrics, float cx, float cy, float s, float time, Color color, int alpha) {
        float renderScale = s * 48.0f / ICON_SIZE;
        scope.arc(cx, cy, 10.2f * renderScale, 0.45f * renderScale, 0.0f, 360.0f, false, withAlpha(color, Math.round(alpha * 0.16f)));
        scope.arc(cx, cy, 8.0f * renderScale, 0.35f * renderScale, 0.0f, 360.0f, false, withAlpha(color, Math.round(alpha * 0.09f)));

        float sweep = 58.0f + 10.0f * (0.5f + 0.5f * (float) Math.sin(time * 1.4f));
        float angle = positiveModulo(time * 38.0f, 360.0f);
        scope.gradientArc(cx, cy, 11.0f * renderScale, 1.15f * renderScale, angle, sweep, true, angle - 90.0f, withAlpha(color, 0), withAlpha(color, Math.round(alpha * 0.12f)), withAlpha(color, Math.round(alpha * 0.32f)));

        float pulse = positiveModulo(time, 2.8f) / 2.8f;
        float pulseWave = pulse < 0.5f ? pulse * 2.0f : (1.0f - pulse) * 2.0f;
        scope.arc(cx, cy, (11.4f + pulseWave * 1.1f) * renderScale, 0.3f * renderScale, 0.0f, 360.0f, false, withAlpha(color, Math.round(alpha * (0.04f + pulseWave * 0.05f))));

        float iconWidth = metrics.getWidth(IconChars.WARNING_AMBER, renderScale, StaticFontLoader.ICONS);
        float iconHeight = metrics.getHeight(renderScale, StaticFontLoader.ICONS);
        scope.text(IconChars.WARNING_AMBER, cx - iconWidth * 0.5f, cy - iconHeight * 0.5f, renderScale, color, StaticFontLoader.ICONS);
    }

    private void drawError(UiTree.Scope scope, float cx, float cy, float s, float time, Color color, int alpha) {
        float phase = positiveModulo(time, 2.0f) / 2.0f;
        if (phase < 0.6f) {
            float local = phase < 0.3f ? phase / 0.3f : (phase - 0.3f) / 0.3f;
            float ringScale = phase < 0.3f ? 1.4f - 0.4f * local : 1.0f - 0.3f * local;
            float ringAlpha = phase < 0.3f ? 0.4f * local : 0.4f * (1.0f - local);
            drawDashedCircle(scope, cx, cy, 20.0f * s * ringScale, 1.0f * s, 22.0f, 18.0f, 0.0f, withAlpha(color, Math.round(alpha * ringAlpha)));
        }

        float crossTime = positiveModulo(time, 2.0f);
        float first = Math.clamp((crossTime - 0.1f) / 0.4f, 0.0f, 1.0f);
        float second = Math.clamp((crossTime - 0.2f) / 0.4f, 0.0f, 1.0f);
        drawProgressLine(scope, cx - 8.0f * s, cy - 8.0f * s, cx + 8.0f * s, cy + 8.0f * s, 1.4f * s, first, color);
        drawProgressLine(scope, cx + 8.0f * s, cy - 8.0f * s, cx - 8.0f * s, cy + 8.0f * s, 1.4f * s, second, color);
    }

    private void drawDashedCircle(UiTree.Scope scope, float cx, float cy, float radius, float stroke, float dashDegrees, float gapDegrees, float rotation, Color color) {
        float step = dashDegrees + gapDegrees;
        for (float angle = 0.0f; angle < 360.0f; angle += step) {
            scope.arc(cx, cy, radius, stroke, rotation + angle, Math.min(dashDegrees, 360.0f - angle), true, color);
        }
    }

    private void drawProgressLine(UiTree.Scope scope, float x1, float y1, float x2, float y2, float radius, float progress, Color color) {
        progress = Math.clamp(progress, 0.0f, 1.0f);
        if (progress <= 0.0f) return;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(2, (int) Math.ceil(length / Math.max(radius, 0.35f)));
        int visibleSteps = Math.max(1, Math.round(steps * progress));
        for (int i = 0; i < visibleSteps; i++) {
            float t = i / (float) (steps - 1);
            dot(scope, x1 + dx * t, y1 + dy * t, radius, color);
        }
    }

    private void dot(UiTree.Scope scope, float centerX, float centerY, float radius, Color color) {
        scope.roundRect(centerX - radius, centerY - radius, radius * 2.0f, radius * 2.0f, radius, color);
    }

    private float animationProgress(Notification notification) {
        float enter = notification.shouldSkipIntroAnim()
                ? 1.0f
                : Math.clamp(notification.getElapsedTime() / (float) TRANSITION_DURATION, 0.0f, 1.0f);
        long exitTime = notification.getExitTime();
        if (exitTime < 0L) return enter;
        return 1.0f - Math.clamp(exitTime / (float) TRANSITION_DURATION, 0.0f, 1.0f);
    }

    private static float fitTextScale(TextRenderer metrics, String text, float desiredScale, float availableWidth) {
        if (text.isEmpty()) return desiredScale;
        float width = metrics.getWidth(text, desiredScale);
        if (width <= availableWidth) return desiredScale;
        return Math.max(0.35f, desiredScale * availableWidth / width);
    }

    private static Color quantumColor(NotificationMode mode, int alpha) {
        return switch (mode) {
            case Success -> new Color(0x00, 0xFF, 0xAA, alpha);
            case Info -> new Color(0x00, 0xAA, 0xFF, alpha);
            case Warning -> new Color(0xFF, 0xCC, 0x00, alpha);
            case Error -> new Color(0xFF, 0x33, 0x66, alpha);
        };
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    private static float positiveModulo(float value, float divisor) {
        float result = value % divisor;
        return result < 0.0f ? result + divisor : result;
    }

    private static final class SpringState {
        private float currentY = Float.NaN;
        private float velocityY;
        private long lastTime = System.nanoTime();

        float update(float targetY) {
            long now = System.nanoTime();
            if (!Float.isFinite(currentY)) {
                currentY = targetY;
                lastTime = now;
                return currentY;
            }

            float delta = Math.min((now - lastTime) / 1_000_000_000.0f, 0.1f);
            lastTime = now;
            float displacement = currentY - targetY;
            float force = -200.0f * displacement - 25.0f * velocityY;
            velocityY += force * delta;
            currentY += velocityY * delta;
            if (Math.abs(displacement) < 0.5f && Math.abs(velocityY) < 5.0f) {
                currentY = targetY;
                velocityY = 0.0f;
            }
            return currentY;
        }
    }

}
