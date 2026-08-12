package com.github.epsilon.elements.impl.notification.style;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.elements.impl.notification.Notification;
import com.github.epsilon.elements.impl.notification.Notifications;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.animation.Easing;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NVIDIANotification {

    private enum RenderStage {
        ENTER_BAR,
        ENTER_CONTENT,
        SHOW,
        EXIT_CONTENT,
        EXIT_BAR,
        HIDDEN
    }

    private static final float ACCENT_BAR_WIDTH = 2.4f;
    private static final float TEXT_PADDING = 6.0f;
    private static final float ENTRY_GAP = 3.0f;
    private static final float LINE_GAP = 1.8f;
    private static final float SUBTITLE_SCALE = 0.92f;

    private final Notifications owner;
    private final DoubleSetting subtitleYOffset;
    private final IntSetting boxWidth;
    private final IntSetting boxHeight;
    private final IntSetting backgroundAlpha;
    private final IntSetting displayTime;

    public NVIDIANotification(Notifications owner) {
        this.owner = owner;
        Setting.Dependency active = () -> owner.isStyle(Notifications.Style.NVIDIA);
        subtitleYOffset = owner.doubleSetting("Subtitle Y Offset", 0.4, -10.0, 20.0, 0.1, active);
        boxWidth = owner.intSetting("Width", 120, 80, 300, 1, active);
        boxHeight = owner.intSetting("Height", 30, 24, 80, 1, active);
        backgroundAlpha = owner.intSetting("Background Alpha", 145, 0, 255, 1, active);
        displayTime = owner.intSetting("Display Time", 2000, 500, 5000, 100, active);
    }

    public int getDisplayTime() {
        return displayTime.getValue();
    }

    public void render(UiTree.Scope scope, TextRenderer metrics, Notification previewNotification) {
        float scale = owner.getScale();
        float textScale = owner.getFontScale() * scale;
        float anchorWidth = boxWidth.getValue() * scale;
        float toastHeight = boxHeight.getValue() * scale;
        float spacing = toastHeight + ENTRY_GAP * scale;
        int bgAlpha = backgroundAlpha.getValue();

        List<RenderEntry> entries = new ArrayList<>();
        float totalHeight = 0.0f;
        for (Notification notification : NotificationManager.INSTANCE.getNotifications()) {
            RenderFrame frame = getRenderFrame(notification, spacing);
            if (frame.stage == RenderStage.HIDDEN) continue;
            totalHeight += frame.occupiedHeight;
            entries.add(new RenderEntry(notification, frame));
        }

        if (entries.isEmpty() && previewNotification != null) {
            totalHeight = spacing;
            entries.add(new RenderEntry(previewNotification, new RenderFrame(RenderStage.SHOW, 1.0f, spacing)));
        }
        if (entries.isEmpty()) return;

        float resolvedHeight = Math.max(toastHeight, totalHeight);
        float currentY = getBaseY(resolvedHeight);
        for (RenderEntry entry : entries) {
            float renderX = getRenderX(anchorWidth, anchorWidth);
            renderNotification(scope, metrics, entry.notification, entry.frame, renderX, currentY, anchorWidth, anchorWidth, toastHeight, scale, textScale, bgAlpha);
            currentY += entry.frame.occupiedHeight;
        }
        owner.setNotificationBounds(anchorWidth, toastHeight);
    }

    private float getRenderX(float anchorWidth, float boxWidth) {
        return owner.getHorizontalAnchor() == HudModule.HorizontalAnchor.Right ? owner.x + anchorWidth - boxWidth
                : owner.getHorizontalAnchor() == HudModule.HorizontalAnchor.Center ? owner.x + (anchorWidth - boxWidth) / 2.0f
                : owner.x;
    }

    private float getBaseY(float totalHeight) {
        return owner.getVerticalAnchor() == HudModule.VerticalAnchor.Bottom
                ? owner.y + owner.height - totalHeight : owner.y;
    }

    private RenderFrame getRenderFrame(Notification notification, float occupiedHeight) {
        long elapsedTime = notification.getElapsedTime();
        if (!notification.shouldSkipIntroAnim()) {
            if (elapsedTime <= 300L) {
                float progress = Easing.EASE_OUT_CUBIC.getFunction().apply(elapsedTime / 300.0f);
                return new RenderFrame(RenderStage.ENTER_BAR, progress, occupiedHeight * progress);
            }
            if (elapsedTime <= 500L) {
                float progress = Easing.EASE_OUT_CUBIC.getFunction().apply((elapsedTime - 300L) / 200.0f);
                return new RenderFrame(RenderStage.ENTER_CONTENT, progress, occupiedHeight);
            }
        }

        long exitTime = notification.getExitTime();
        if (exitTime < 0L) return new RenderFrame(RenderStage.SHOW, 1.0f, occupiedHeight);
        if (exitTime <= 200L) {
            float progress = 1.0f - Easing.EASE_OUT_CUBIC.getFunction().apply(exitTime / 200.0f);
            return new RenderFrame(RenderStage.EXIT_CONTENT, progress, occupiedHeight);
        }
        if (exitTime <= 500L) {
            float progress = 1.0f - Easing.EASE_OUT_CUBIC.getFunction().apply((exitTime - 200L) / 300.0f);
            return new RenderFrame(RenderStage.EXIT_BAR, progress, occupiedHeight * progress);
        }
        return new RenderFrame(RenderStage.HIDDEN, 0.0f, 0.0f);
    }

    private void renderNotification(UiTree.Scope scope, TextRenderer metrics, Notification notification, RenderFrame frame, float x, float y, float anchorWidth, float boxWidth, float boxHeight, float scale, float textScale, int bgAlpha) {
        switch (frame.stage) {
            case ENTER_BAR, EXIT_BAR ->
                    renderStage1(scope, notification, x, y, anchorWidth, boxWidth, boxHeight, frame.progress);
            case ENTER_CONTENT, EXIT_CONTENT, SHOW ->
                    renderStage2(scope, metrics, notification, x, y, boxWidth, boxHeight, scale, textScale, bgAlpha, frame.progress);
            case HIDDEN -> {
            }
        }
    }

    private void renderStage1(UiTree.Scope scope, Notification notification, float x, float y, float anchorWidth, float boxWidth, float boxHeight, float progress) {
        boolean left = owner.getHorizontalAnchor() == HudModule.HorizontalAnchor.Left;
        float width = left ? boxWidth * progress : boxWidth - anchorWidth * (1.0f - progress);
        float renderX = left ? x : x + boxWidth - width;
        scope.rect(renderX, y, width, boxHeight, notification.getMode().getColor());
    }

    private void renderStage2(UiTree.Scope scope, TextRenderer metrics, Notification notification, float x, float y, float boxWidth, float boxHeight, float scale, float textScale, int bgAlpha, float progress) {
        scope.rect(x, y, boxWidth, boxHeight, new Color(0, 0, 0, bgAlpha));
        boolean clip = textExceedsBox(metrics, notification, boxWidth, boxHeight, scale, textScale);
        scope.scissorIf(clip, x, y, boxWidth, boxHeight, textScope -> {
            renderText(textScope, metrics, notification, x, y, boxWidth, boxHeight, scale, textScale, Math.round(255.0f * progress));
        });
        float accentWidth = ACCENT_BAR_WIDTH * scale + (boxWidth - ACCENT_BAR_WIDTH * scale) * (1.0f - progress);
        float accentX = owner.getHorizontalAnchor() == HudModule.HorizontalAnchor.Left ? x + boxWidth - accentWidth : x;
        scope.rect(accentX, y, accentWidth, boxHeight, notification.getMode().getColor());
    }

    private void renderText(UiTree.Scope scope, TextRenderer metrics, Notification notification, float x, float y, float boxWidth, float boxHeight, float scale, float desiredTextScale, int alpha) {
        boolean hasSubtitle = !notification.getSubTitle().isEmpty();
        float fittedScale = getFittedTextScale(metrics, notification, boxWidth, scale, desiredTextScale);
        float subtitleScale = fittedScale * SUBTITLE_SCALE;
        float lineGap = LINE_GAP * fittedScale + subtitleYOffset.getValue().floatValue() * scale;
        float titleHeight = metrics.getHeight(fittedScale);
        float subtitleHeight = hasSubtitle ? metrics.getHeight(subtitleScale) : 0.0f;
        float contentHeight = titleHeight + subtitleHeight + (hasSubtitle ? lineGap : 0.0f);
        float textX = x + (owner.getHorizontalAnchor() == HudModule.HorizontalAnchor.Left ? TEXT_PADDING * scale : (ACCENT_BAR_WIDTH + TEXT_PADDING) * scale);
        float titleY = y + (boxHeight - contentHeight) / 2.0f;

        scope.text(notification.getTitle(), textX, titleY, fittedScale, new Color(255, 255, 255, alpha));
        if (hasSubtitle) {
            scope.text(notification.getSubTitle(), textX, titleY + titleHeight + lineGap, subtitleScale, notification.getMode().getColor(Math.round(alpha * 0.86f)));
        }
    }

    private float getFittedTextScale(TextRenderer metrics, Notification notification, float boxWidth, float scale, float desiredTextScale) {
        float maxWidth = Math.max(metrics.getWidth(notification.getTitle(), desiredTextScale), metrics.getWidth(notification.getSubTitle(), desiredTextScale * SUBTITLE_SCALE));
        float availableWidth = Math.max(1.0f, boxWidth - (TEXT_PADDING * 2.0f + ACCENT_BAR_WIDTH) * scale);
        return Math.max(0.35f, desiredTextScale * (maxWidth > availableWidth ? availableWidth / maxWidth : 1.0f));
    }

    private boolean textExceedsBox(TextRenderer metrics, Notification notification, float boxWidth, float boxHeight, float scale, float desiredTextScale) {
        float fittedScale = getFittedTextScale(metrics, notification, boxWidth, scale, desiredTextScale);
        float subtitleScale = fittedScale * SUBTITLE_SCALE;
        float maxWidth = Math.max(metrics.getWidth(notification.getTitle(), fittedScale), metrics.getWidth(notification.getSubTitle(), subtitleScale));
        float availableWidth = Math.max(1.0f, boxWidth - (TEXT_PADDING * 2.0f + ACCENT_BAR_WIDTH) * scale);
        float titleHeight = metrics.getHeight(fittedScale);
        if (notification.getSubTitle().isEmpty()) return maxWidth > availableWidth || titleHeight > boxHeight;
        float subtitleHeight = metrics.getHeight(subtitleScale);
        float lineGap = LINE_GAP * fittedScale + subtitleYOffset.getValue().floatValue() * scale;
        float contentHeight = titleHeight + lineGap + subtitleHeight;
        return maxWidth > availableWidth || (boxHeight - contentHeight) * 0.5f < 0.0f || (boxHeight - contentHeight) * 0.5f + contentHeight > boxHeight;
    }

    private record RenderFrame(RenderStage stage, float progress, float occupiedHeight) {
    }

    private record RenderEntry(Notification notification, RenderFrame frame) {
    }

}
