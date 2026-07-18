package com.github.epsilon.elements.impl.notification;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.hudeditor.HudEditorScreen;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.render.animation.Easing;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Notifications extends HudModule {

    public static final Notifications INSTANCE = new Notifications();

    private Notifications() {
        super("Notifications", 3.2f, 3.2f, DEFAULT_BOX_WIDTH, DEFAULT_BOX_HEIGHT);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.05);
    private final DoubleSetting fontScale = doubleSetting("Font Scale", 0.80, 0.5, 2.0, 0.05);
    private final DoubleSetting subtitleYOffset = doubleSetting("Subtitle Y Offset", 0.4, -10.0, 20.0, 0.1);
    private final IntSetting boxWidth = intSetting("Width", DEFAULT_BOX_WIDTH, 80, 300, 1);
    private final IntSetting boxHeight = intSetting("Height", DEFAULT_BOX_HEIGHT, 24, 80, 1);
    private final IntSetting backgroundAlpha = intSetting("Background Alpha", 145, 0, 255, 1);
    public final IntSetting displayTime = intSetting("Display Time", 2000, 500, 5000, 100);

    private static final int DEFAULT_BOX_WIDTH = 120;
    private static final int DEFAULT_BOX_HEIGHT = 30;
    private static final float ACCENT_BAR_WIDTH = 2.4f;
    private static final float TEXT_PADDING = 6.0f;
    private static final float ENTRY_GAP = 3.0f;
    private static final float LINE_GAP = 1.8f;
    private static final float SUBTITLE_SCALE = 0.92f;

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    public void render(DeltaTracker deltaTracker) {
        Managers.NOTIFICATION.update();
        Notification previewNotification = createPreviewNotification();
        if (Managers.NOTIFICATION.isEmpty() && previewNotification == null) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        UiTree.Scope scope = renderScope();

        float s = scale.getValue().floatValue();
        float textScale = fontScale.getValue().floatValue() * s;
        float anchorWidth = boxWidth.getValue() * s;
        float boxHeight = this.boxHeight.getValue() * s;
        float spacing = boxHeight + ENTRY_GAP * s;
        int bgAlpha = backgroundAlpha.getValue();

        List<RenderEntry> entries = new ArrayList<>();
        float totalHeight = 0f;

        for (Notification notification : Managers.NOTIFICATION.getNotifications()) {
            RenderFrame frame = getRenderFrame(notification, spacing);
            if (frame.stage == RenderStage.HIDDEN) continue;

            totalHeight += frame.occupiedHeight;
            entries.add(new RenderEntry(notification, anchorWidth, frame));
        }

        if (entries.isEmpty() && previewNotification != null) {
            totalHeight = spacing;
            entries.add(new RenderEntry(previewNotification, anchorWidth, new RenderFrame(RenderStage.SHOW, 1.0f, spacing)));
        }

        if (entries.isEmpty()) return;

        float resolvedHeight = Math.max(boxHeight, totalHeight);
        float currentY = getBaseY(resolvedHeight);

        for (RenderEntry entry : entries) {
            float renderX = getRenderX(anchorWidth, entry.boxWidth);
            renderNotification(scope, textRenderer, entry.notification, entry.frame, renderX, currentY, anchorWidth, entry.boxWidth, boxHeight, s, textScale, bgAlpha);
            currentY += entry.frame.occupiedHeight;
        }

        setBounds(anchorWidth, boxHeight);
    }

    private float getSubTitleScale(float scale) {
        return scale * SUBTITLE_SCALE;
    }

    private float getRenderX(float anchorWidth, float boxWidth) {
        return getHorizontalAnchor() == HorizontalAnchor.Right ? this.x + anchorWidth - boxWidth
                : getHorizontalAnchor() == HorizontalAnchor.Center ? this.x + (anchorWidth - boxWidth) / 2.0f
                  : this.x;
    }

    private float getBaseY(float totalHeight) {
        return getVerticalAnchor() == VerticalAnchor.Bottom ? this.y + this.height - totalHeight : this.y;
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
        if (exitTime < 0L) {
            return new RenderFrame(RenderStage.SHOW, 1.0f, occupiedHeight);
        }

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
            case ENTER_BAR, EXIT_BAR -> {
                renderStage1(scope, notification, x, y, anchorWidth, boxWidth, boxHeight, frame.progress);
            }
            case ENTER_CONTENT, EXIT_CONTENT, SHOW -> {
                renderStage2(scope, metrics, notification, x, y, boxWidth, boxHeight, scale, textScale, bgAlpha, frame.progress);
            }
            case HIDDEN -> {
            }
        }
    }

    private void renderStage1(UiTree.Scope scope, Notification notification, float x, float y, float anchorWidth, float boxWidth, float boxHeight, float progress) {
        float width = isLeftDocked() ? boxWidth * progress : boxWidth - anchorWidth * (1.0f - progress);
        float renderX = isLeftDocked() ? x : x + boxWidth - width;
        scope.rect(renderX, y, width, boxHeight, notification.getMode().getColor());
    }

    private void renderStage2(UiTree.Scope scope, TextRenderer metrics, Notification notification, float x, float y, float boxWidth, float boxHeight, float scale, float textScale, int bgAlpha, float progress) {
        scope.rect(x, y, boxWidth, boxHeight, new Color(0, 0, 0, bgAlpha));
        boolean requiresScissor = textExceedsBox(metrics, notification, boxWidth, boxHeight, scale, textScale);
        scope.scissorIf(requiresScissor, x, y, boxWidth, boxHeight,
                textScope -> renderText(textScope, metrics, notification, x, y, boxWidth, boxHeight,
                        scale, textScale, Math.round(255.0f * progress)));
        float accentWidth = ACCENT_BAR_WIDTH * scale + (boxWidth - ACCENT_BAR_WIDTH * scale) * (1.0f - progress);
        float accentX = isLeftDocked() ? x + boxWidth - accentWidth : x;
        scope.rect(accentX, y, accentWidth, boxHeight, notification.getMode().getColor());
    }

    private void renderText(UiTree.Scope scope, TextRenderer metrics, Notification n, float x, float y, float boxWidth, float boxHeight, float scale, float desiredTextScale, int alpha) {
        boolean hasSubTitle = !n.getSubTitle().isEmpty();
        float textScale = getFittedTextScale(metrics, n, boxWidth, scale, desiredTextScale);
        float subTitleScale = getSubTitleScale(textScale);
        float lineGap = getLineGap(textScale, scale);
        float titleHeight = metrics.getHeight(textScale);
        float subTitleHeight = hasSubTitle ? metrics.getHeight(subTitleScale) : 0.0f;
        float contentHeight = titleHeight + subTitleHeight + (hasSubTitle ? lineGap : 0.0f);
        float textX = x + (isLeftDocked() ? TEXT_PADDING * scale : (ACCENT_BAR_WIDTH + TEXT_PADDING) * scale);
        float titleY = y + (boxHeight - contentHeight) / 2.0f;

        scope.text(n.getTitle(), textX, titleY, textScale, new Color(255, 255, 255, alpha));
        if (hasSubTitle) {
            float subTitleY = titleY + titleHeight + lineGap;
            scope.text(n.getSubTitle(), textX, subTitleY, subTitleScale, n.getMode().getColor(Math.round(alpha * 0.86f)));
        }
    }

    private float getLineGap(float textScale, float scale) {
        return LINE_GAP * textScale + subtitleYOffset.getValue().floatValue() * scale;
    }

    private float getFittedTextScale(TextRenderer metrics, Notification notification, float boxWidth, float scale, float desiredTextScale) {
        float maxWidth = Math.max(metrics.getWidth(notification.getTitle(), desiredTextScale),
                metrics.getWidth(notification.getSubTitle(), getSubTitleScale(desiredTextScale)));
        float availableWidth = Math.max(1.0f, boxWidth - (TEXT_PADDING * 2.0f + ACCENT_BAR_WIDTH) * scale);
        float widthFit = maxWidth > availableWidth ? availableWidth / maxWidth : 1.0f;

        return Math.max(0.35f, desiredTextScale * widthFit);
    }

    private boolean textExceedsBox(TextRenderer metrics, Notification notification, float boxWidth,
                                   float boxHeight, float scale, float desiredTextScale) {
        float textScale = getFittedTextScale(metrics, notification, boxWidth, scale, desiredTextScale);
        float subTitleScale = getSubTitleScale(textScale);
        float maxWidth = Math.max(metrics.getWidth(notification.getTitle(), textScale),
                metrics.getWidth(notification.getSubTitle(), subTitleScale));
        float availableWidth = Math.max(1.0f,
                boxWidth - (TEXT_PADDING * 2.0f + ACCENT_BAR_WIDTH) * scale);

        float titleHeight = metrics.getHeight(textScale);
        if (notification.getSubTitle().isEmpty()) {
            return maxWidth > availableWidth || titleHeight > boxHeight;
        }

        float subTitleHeight = metrics.getHeight(subTitleScale);
        float lineGap = getLineGap(textScale, scale);
        float contentHeight = titleHeight + lineGap + subTitleHeight;
        float titleY = (boxHeight - contentHeight) * 0.5f;
        float subTitleY = titleY + titleHeight + lineGap;
        float contentTop = Math.min(titleY, subTitleY);
        float contentBottom = Math.max(titleY + titleHeight, subTitleY + subTitleHeight);
        return maxWidth > availableWidth || contentTop < 0.0f || contentBottom > boxHeight;
    }

    private boolean isLeftDocked() {
        return getHorizontalAnchor() == HorizontalAnchor.Left;
    }

    private Notification createPreviewNotification() {
        if (mc.screen instanceof HudEditorScreen) {
            return new Notification("Preview", "Notification", NotificationMode.Success, false);
        }
        return null;
    }

    private enum RenderStage {
        ENTER_BAR,
        ENTER_CONTENT,
        SHOW,
        EXIT_CONTENT,
        EXIT_BAR,
        HIDDEN
    }

    private record RenderFrame(RenderStage stage, float progress, float occupiedHeight) {
    }

    private record RenderEntry(Notification notification, float boxWidth, RenderFrame frame) {
    }

}
