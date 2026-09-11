package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;

import java.awt.*;

public class ColorPickerPopup implements PanelPopupHost.Popup {

    private enum Channel {
        RED("R", new Color(239, 83, 80)),
        GREEN("G", new Color(102, 187, 106)),
        BLUE("B", new Color(66, 165, 245)),
        ALPHA("A", MD3Theme.PRIMARY);

        private final String shortLabel;
        private final Color accent;

        Channel(String shortLabel, Color accent) {
            this.shortLabel = shortLabel;
            this.accent = accent;
        }
    }

    private final UiRect bounds;
    private final UiRect anchorBounds;
    private final ColorSetting setting;
    private final TextRenderer textRenderer = TextRenderer.create();
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private final Animation[] indicatorAnimations = new Animation[]{
            new Animation(Easing.EASE_OUT_CUBIC, 120L),
            new Animation(Easing.EASE_OUT_CUBIC, 120L),
            new Animation(Easing.EASE_OUT_CUBIC, 120L),
            new Animation(Easing.EASE_OUT_CUBIC, 120L)
    };
    private Channel draggingChannel;
    private Channel focusedChannel;
    private String inputBuffer;
    private int cursorIndex;
    private Color pendingValue;

    public ColorPickerPopup(UiRect bounds, UiRect anchorBounds, ColorSetting setting) {
        this.bounds = bounds;
        this.anchorBounds = anchorBounds;
        this.setting = setting;
        this.openAnimation.setStartValue(0.0f);
        for (Animation animation : indicatorAnimations) {
            animation.setStartValue(0.0f);
        }
    }

    @Override
    public UiRect getBounds() {
        return bounds;
    }

    @Override
    public void extractGui(GuiGraphicsExtractor GuiGraphicsExtractor, UiRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        UiTree tree = UiTree.build(scope -> {
            float progress = scope.animate(openAnimation, 1.0f);
            float popupY = bounds.y() - (1.0f - progress) * 6.0f;
            UiRect popupBounds = new UiRect(bounds.x(), popupY, bounds.width(), bounds.height());
            scope.pushAbsolute(popupBounds, popup -> {
                popup.popupCard(popupBounds.atOrigin(),
                        MD3Theme.CARD_RADIUS,
                        MD3Theme.POPUP_SHADOW_BLUR,
                        MD3Theme.withAlpha(MD3Theme.SHADOW, (int) (MD3Theme.POPUP_SHADOW_ALPHA * progress)),
                        MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255));
                popup.pushAbsolute(anchorBounds, anchor ->
                        anchor.roundRect(0.0f, 0.0f, anchorBounds.width(), anchorBounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 255)));

                UiRect previewBounds = getPreviewBounds(popupY);
                UiRect swatchBounds = getPreviewSwatchBounds(popupY);
                UiRect localPreviewBounds = previewBounds.relativeTo(popupBounds);
                UiRect localSwatchBounds = swatchBounds.relativeTo(popupBounds);
                Color previewSurface = MD3Theme.isLightTheme() ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER;
                Color swatchSurface = MD3Theme.isLightTheme() ? MD3Theme.SURFACE_CONTAINER : MD3Theme.SURFACE_CONTAINER_HIGHEST;
                popup.roundRect(localPreviewBounds.x(), localPreviewBounds.y(), localPreviewBounds.width(), localPreviewBounds.height(), 10.0f, MD3Theme.withAlpha(previewSurface, 255));
                popup.roundRect(localSwatchBounds.x(), localSwatchBounds.y(), localSwatchBounds.width(), localSwatchBounds.height(), 8.0f, MD3Theme.withAlpha(swatchSurface, 255));
                if (setting.isAllowAlpha()) {
                    buildCheckerboard(popup, localSwatchBounds, 255);
                }
                popup.roundRect(localSwatchBounds.x(), localSwatchBounds.y(), localSwatchBounds.width(), localSwatchBounds.height(), 8.0f, getVisibleColor());
                popup.roundRect(localSwatchBounds.x(), localSwatchBounds.y(), localSwatchBounds.width(), localSwatchBounds.height(), 8.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE_SOFT, (int) (72 * progress)));
                popup.text(formatHex(getVisibleColor()), localPreviewBounds.x() + 40.0f, localPreviewBounds.y() + 8.0f, 0.64f, MD3Theme.TEXT_PRIMARY);
                popup.text(setting.isAllowAlpha() ? "RGBA" : "RGB", localPreviewBounds.x() + 40.0f, localPreviewBounds.y() + 19.0f, 0.52f, MD3Theme.TEXT_SECONDARY);

                Channel[] channels = getChannels();
                for (int i = 0; i < channels.length; i++) {
                    Channel channel = channels[i];
                    UiRect rowBounds = getChannelRowBounds(popupY, i);
                    UiRect localRowBounds = rowBounds.relativeTo(popupBounds);
                    UiRect trackBounds = getTrackBounds(rowBounds);
                    UiRect localTrackBounds = trackBounds.relativeTo(popupBounds);
                    UiRect valueBounds = getValueBounds(rowBounds);
                    UiRect localValueBounds = valueBounds.relativeTo(popupBounds);
                    boolean hovered = rowBounds.contains(mouseX, mouseY);
                    float channelProgress = getChannelValue(channel) / 255.0f;
                    float handleX = localTrackBounds.x() + localTrackBounds.width() * channelProgress - 2.0f;
                    float indicatorProgress = scope.animate(indicatorAnimations[i], hovered || draggingChannel == channel);
                    Color rowSurface = hovered
                            ? (MD3Theme.isLightTheme() ? MD3Theme.SURFACE_CONTAINER_HIGHEST : MD3Theme.SURFACE_CONTAINER_HIGH)
                            : MD3Theme.SURFACE_CONTAINER;

                    popup.text(channel.shortLabel, localRowBounds.x() + 8.0f, localRowBounds.y() + 6.5f, 0.58f,
                            MD3Theme.isLightTheme() ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_SECONDARY);
                    popup.roundRect(localRowBounds.x(), localRowBounds.y(), localRowBounds.width(), localRowBounds.height(), 8.0f, MD3Theme.withAlpha(rowSurface, 255));
                    popup.slider(localTrackBounds, channelProgress, 2.5f,
                            MD3Theme.withAlpha(MD3Theme.isLightTheme() ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER_HIGHEST, 255),
                            0.0f, 3.0f, MD3Theme.withAlpha(channel.accent, 255),
                            4.0f, 12.0f, 2.0f, MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 255));

                    String valueText = focusedChannel == channel ? getDisplayBuffer() : Integer.toString(getChannelValue(channel));
                    buildValueBox(popup, localValueBounds, valueText, focusedChannel == channel, 255);
                    if (indicatorProgress > 0.01f) {
                        buildValueIndicator(popup, handleX + 2.0f, localRowBounds.y() - 4.0f, Integer.toString(getChannelValue(channel)), indicatorProgress);
                    }
                }
            });
        });
        renderBatch.render(tree);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0 || !bounds.contains(event.x(), event.y())) {
            return false;
        }
        Channel[] channels = getChannels();
        for (int i = 0; i < channels.length; i++) {
            UiRect rowBounds = getChannelRowBounds(bounds.y(), i);
            UiRect trackBounds = getTrackBounds(rowBounds);
            UiRect valueBounds = getValueBounds(rowBounds);
            if (valueBounds.contains(event.x(), event.y())) {
                draggingChannel = null;
                focusedChannel = channels[i];
                inputBuffer = Integer.toString(getChannelValue(focusedChannel));
                cursorIndex = getCursorIndex(event.x(), valueBounds, inputBuffer);
                return true;
            }
            UiRect interactiveBounds = new UiRect(trackBounds.x(), trackBounds.y() - 5.0f, trackBounds.width(), trackBounds.height() + 10.0f);
            if (interactiveBounds.contains(event.x(), event.y())) {
                draggingChannel = channels[i];
                focusedChannel = null;
                inputBuffer = null;
                updateChannelFromMouse(draggingChannel, event.x(), trackBounds);
                return true;
            }
        }
        focusedChannel = null;
        inputBuffer = null;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && draggingChannel != null) {
            commitPendingColor();
        }
        draggingChannel = null;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (draggingChannel == null || event.button() != 0) {
            return false;
        }
        int index = getChannelIndex(draggingChannel);
        UiRect trackBounds = getTrackBounds(getChannelRowBounds(bounds.y(), index));
        updateChannelFromMouse(draggingChannel, event.x(), trackBounds);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (focusedChannel == null) {
            return false;
        }
        return switch (event.key()) {
            case 257, 335 -> {
                commitInput();
                focusedChannel = null;
                inputBuffer = null;
                yield true;
            }
            case 256 -> {
                focusedChannel = null;
                inputBuffer = null;
                yield true;
            }
            case 259 -> {
                if (inputBuffer != null && cursorIndex > 0) {
                    inputBuffer = inputBuffer.substring(0, cursorIndex - 1) + inputBuffer.substring(cursorIndex);
                    cursorIndex--;
                }
                yield true;
            }
            case 261 -> {
                if (inputBuffer != null && cursorIndex < inputBuffer.length()) {
                    inputBuffer = inputBuffer.substring(0, cursorIndex) + inputBuffer.substring(cursorIndex + 1);
                }
                yield true;
            }
            case 263 -> {
                cursorIndex = Math.max(0, cursorIndex - 1);
                yield true;
            }
            case 262 -> {
                cursorIndex = Math.min(getDisplayBuffer().length(), cursorIndex + 1);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focusedChannel == null) {
            return false;
        }
        String typed = event.codepointAsString();
        if (!typed.matches("[0-9]")) {
            return false;
        }
        String current = getDisplayBuffer();
        if (current.length() >= 3) {
            return true;
        }
        inputBuffer = current.substring(0, cursorIndex) + typed + current.substring(cursorIndex);
        cursorIndex++;
        return true;
    }

    private UiRect getPreviewBounds(float popupY) {
        return new UiRect(bounds.x() + 8.0f, popupY + 8.0f, bounds.width() - 16.0f, 34.0f);
    }

    private UiRect getPreviewSwatchBounds(float popupY) {
        return new UiRect(bounds.x() + 14.0f, popupY + 13.0f, 24.0f, 24.0f);
    }

    private UiRect getChannelRowBounds(float popupY, int index) {
        float rowY = popupY + 50.0f + index * 24.0f;
        return new UiRect(bounds.x() + 8.0f, rowY, bounds.width() - 16.0f, 20.0f);
    }

    private UiRect getTrackBounds(UiRect rowBounds) {
        return new UiRect(rowBounds.x() + 18.0f, rowBounds.y() + 8.0f, rowBounds.width() - 48.0f, 4.0f);
    }

    private UiRect getValueBounds(UiRect rowBounds) {
        return new UiRect(rowBounds.right() - 24.0f, rowBounds.y() + 2.0f, 18.0f, 16.0f);
    }

    private Channel[] getChannels() {
        return setting.isAllowAlpha()
                ? new Channel[]{Channel.RED, Channel.GREEN, Channel.BLUE, Channel.ALPHA}
                : new Channel[]{Channel.RED, Channel.GREEN, Channel.BLUE};
    }

    private int getChannelIndex(Channel channel) {
        Channel[] channels = getChannels();
        for (int i = 0; i < channels.length; i++) {
            if (channels[i] == channel) {
                return i;
            }
        }
        return 0;
    }

    private int getChannelValue(Channel channel) {
        Color color = getVisibleColor();
        return switch (channel) {
            case RED -> color.getRed();
            case GREEN -> color.getGreen();
            case BLUE -> color.getBlue();
            case ALPHA -> color.getAlpha();
        };
    }

    private void updateChannelFromMouse(Channel channel, double mouseX, UiRect trackBounds) {
        double progress = (mouseX - trackBounds.x()) / trackBounds.width();
        progress = Mth.clamp(progress, 0.0, 1.0);
        int value = Mth.clamp((int) Math.round(progress * 255.0), 0, 255);
        Color current = getVisibleColor();
        Color updated = switch (channel) {
            case RED -> new Color(value, current.getGreen(), current.getBlue(), current.getAlpha());
            case GREEN -> new Color(current.getRed(), value, current.getBlue(), current.getAlpha());
            case BLUE -> new Color(current.getRed(), current.getGreen(), value, current.getAlpha());
            case ALPHA -> new Color(current.getRed(), current.getGreen(), current.getBlue(), value);
        };
        previewColor(updated);
    }

    private String formatHex(Color color) {
        if (setting.isAllowAlpha()) {
            return String.format("#%02X%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        }
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void buildValueIndicator(UiTree.Scope scope, float anchorX, float rowTop, String valueText, float progress) {
        float textScale = 0.54f;
        float bubbleWidth = textRenderer.getWidth(valueText, textScale) + 12.0f;
        float bubbleHeight = 16.0f;
        float bubbleX = anchorX - bubbleWidth / 2.0f;
        float bubbleY = rowTop - 14.0f - (1.0f - progress) * 4.0f;
        int bubbleAlpha = (int) (255 * progress);
        scope.roundRect(bubbleX, bubbleY, bubbleWidth, bubbleHeight, 8.0f, MD3Theme.withAlpha(MD3Theme.INVERSE_SURFACE, bubbleAlpha));
        float textY = bubbleY + (bubbleHeight - textRenderer.getHeight(textScale)) / 2.0f;
        scope.text(valueText, bubbleX + (bubbleWidth - textRenderer.getWidth(valueText, textScale)) / 2.0f, textY, textScale, MD3Theme.withAlpha(MD3Theme.INVERSE_ON_SURFACE, bubbleAlpha));
    }

    private void buildValueBox(UiTree.Scope scope, UiRect bounds, String valueText, boolean focused, int alpha) {
        Color boxBase = MD3Theme.isLightTheme() ? MD3Theme.SURFACE_CONTAINER : MD3Theme.SURFACE_CONTAINER_HIGH;
        Color boxHover = MD3Theme.SURFACE_CONTAINER_HIGHEST;
        Color boxColor = focused ? MD3Theme.INVERSE_SURFACE : MD3Theme.withAlpha(MD3Theme.lerp(boxBase, boxHover, 0.85f), alpha);
        Color textColor = focused ? MD3Theme.INVERSE_ON_SURFACE : MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, alpha);
        scope.pushRelative(bounds, box -> {
            box.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), 6.0f, boxColor);
            float textScale = 0.52f;
            float textWidth = textRenderer.getWidth(valueText, textScale);
            float textHeight = textRenderer.getHeight(textScale);
            float textX = (bounds.width() - textWidth) / 2.0f;
            float textY = (bounds.height() - textHeight) / 2.0f;
            box.text(valueText, textX, textY, textScale, textColor);
            if (focused) {
                float caretX = textX + textRenderer.getWidth(valueText.substring(0, Math.min(cursorIndex, valueText.length())), textScale);
                box.rect(caretX, 3.0f, 1.0f, bounds.height() - 6.0f, MD3Theme.INVERSE_ON_SURFACE);
            }
        });
    }

    private void buildCheckerboard(UiTree.Scope scope, UiRect bounds, int alpha) {
        float inset = 2.0f;
        float cell = 4.0f;
        Color dark = MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, alpha);
        Color light = MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGH, alpha);
        for (float y = bounds.y() + inset; y < bounds.bottom() - inset; y += cell) {
            for (float x = bounds.x() + inset; x < bounds.right() - inset; x += cell) {
                int ix = (int) Math.floor((x - bounds.x()) / cell);
                int iy = (int) Math.floor((y - bounds.y()) / cell);
                Color color = ((ix + iy) & 1) == 0 ? light : dark;
                float width = Math.min(cell, bounds.right() - inset - x);
                float height = Math.min(cell, bounds.bottom() - inset - y);
                scope.roundRect(x, y, width, height, 0.0f, color);
            }
        }
    }

    private void commitInput() {
        if (focusedChannel == null || inputBuffer == null || inputBuffer.isBlank()) {
            return;
        }
        int value;
        try {
            value = Mth.clamp(Integer.parseInt(inputBuffer), 0, 255);
        } catch (NumberFormatException ignored) {
            value = getChannelValue(focusedChannel);
        }
        Color current = getVisibleColor();
        Color updated = switch (focusedChannel) {
            case RED -> new Color(value, current.getGreen(), current.getBlue(), current.getAlpha());
            case GREEN -> new Color(current.getRed(), value, current.getBlue(), current.getAlpha());
            case BLUE -> new Color(current.getRed(), current.getGreen(), value, current.getAlpha());
            case ALPHA -> new Color(current.getRed(), current.getGreen(), current.getBlue(), value);
        };
        applyColorNow(updated);
        inputBuffer = Integer.toString(value);
        cursorIndex = inputBuffer.length();
    }

    private String getDisplayBuffer() {
        return inputBuffer == null ? Integer.toString(getChannelValue(focusedChannel)) : inputBuffer;
    }

    private int getCursorIndex(double mouseX, UiRect fieldBounds, String text) {
        float scale = 0.52f;
        float textWidth = textRenderer.getWidth(text, scale);
        float textStart = fieldBounds.x() + (fieldBounds.width() - textWidth) / 2.0f;
        for (int i = 0; i <= text.length(); i++) {
            float width = textRenderer.getWidth(text.substring(0, i), scale);
            if (mouseX <= textStart + width) {
                return i;
            }
        }
        return text.length();
    }

    private Color getVisibleColor() {
        return pendingValue != null ? pendingValue : setting.getValue();
    }

    private void previewColor(Color color) {
        if (setting.isApplyWhenRelease()) {
            pendingValue = color;
        } else {
            setting.setValue(color);
        }
    }

    private void applyColorNow(Color color) {
        pendingValue = null;
        setting.setValue(color);
    }

    private void commitPendingColor() {
        if (pendingValue == null) {
            return;
        }
        Color color = pendingValue;
        applyColorNow(color);
    }

    @Override
    public void close() {
        textRenderer.close();
    }

}
