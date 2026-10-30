package com.github.epsilon.gui.troll;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.settings.impl.ColorSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;

public class TrollColorPicker extends TrollWindow {

    private static final TranslateComponent[] CHANNELS = {
            EpsilonTranslations.TrollGui.RED, EpsilonTranslations.TrollGui.GREEN,
            EpsilonTranslations.TrollGui.BLUE, EpsilonTranslations.TrollGui.ALPHA
    };

    private static final float CONTENT_PADDING = 9.0f;
    private final ColorSetting setting;
    private final Color original;
    private final UiRect[] sliders = new UiRect[4];
    private final UiRect[] buttons = new UiRect[3];
    private UiRect field;
    private UiRect hueBar;
    private UiRect previousColor;
    private UiRect currentColor;
    private float hue;
    private float saturation;
    private float brightness;
    private int alpha;
    private int draggingPart = -1;
    private boolean closed;

    public TrollColorPicker(ColorSetting setting, float x, float y) {
        this.setting = setting;
        original = setting.getValue();
        setRgb(original);
        alpha = setting.isAllowAlpha() ? original.getAlpha() : 255;
        position(x, y);
    }

    @Override
    public void layout(TextRenderer metrics, float screenWidth, float screenHeight) {
        headerHeight = TrollTheme.headerHeight(metrics);
        float step = TrollTheme.rowHeight(metrics) + 3.0f;
        int channels = channelCount();
        float contentTop = headerHeight + CONTENT_PADDING;
        size(Math.min(441.0f, screenWidth),
                Math.min(contentTop + (channels + 3) * step - 3.0f + CONTENT_PADDING, screenHeight),
                screenWidth, screenHeight);
        step = Math.clamp((bounds.height() - contentTop - CONTENT_PADDING + 3.0f) / (channels + 3), 1.0f, step);
        float fieldSize = Math.clamp(bounds.width() * 0.48f - 27.0f, 18.0f, bounds.height() - contentTop - CONTENT_PADDING);
        field = new UiRect(bounds.x() + 6.0f, bounds.y() + contentTop, fieldSize, fieldSize);
        hueBar = new UiRect(field.right() + 9.0f, field.y(), 12.0f, fieldSize);
        float sliderX = hueBar.right() + 12.0f;
        float sliderWidth = Math.max(1.0f, bounds.right() - 6.0f - sliderX);
        for (int i = 0; i < channels; i++)
            sliders[i] = new UiRect(sliderX, field.y() + i * step, sliderWidth, step - 3.0f);
        float buttonWidth = Math.min(87.0f, sliderWidth * 0.48f);
        for (int i = 0; i < buttons.length; i++)
            buttons[i] = new UiRect(bounds.right() - 6.0f - buttonWidth,
                    field.y() + (channels + i) * step, buttonWidth, step - 3.0f);
        float previewWidth = Math.max(1.0f, (sliderWidth - buttonWidth - 9.0f) / 2.0f);
        previousColor = new UiRect(sliderX, buttons[0].y(), previewWidth, step * 3.0f - 3.0f);
        currentColor = previousColor.translate(previewWidth + 3.0f, 0.0f);
    }

    @Override
    public void draw(UiTree.Scope scope, TextRenderer metrics, int mouseX, int mouseY) {
        TrollTheme.window(scope, bounds, headerHeight, EpsilonTranslations.TrollGui.COLOR_PICKER.getTranslatedName(), metrics);
        Color hueColor = Color.getHSBColor(hue, 1.0f, 1.0f);
        scope.rectGradient(1, field.x(), field.y(), field.width(), field.height(), Color.WHITE, Color.WHITE, hueColor, hueColor);
        scope.rectGradient(1, field.x(), field.y(), field.width(), field.height(), new Color(0, 0, 0, 0), Color.BLACK, Color.BLACK, new Color(0, 0, 0, 0));
        for (int i = 0; i < 6; i++) {
            Color top = Color.getHSBColor(i / 6.0f, 1.0f, 1.0f);
            Color bottom = Color.getHSBColor((i + 1) / 6.0f, 1.0f, 1.0f);
            scope.rectGradient(1, hueBar.x(), hueBar.y() + hueBar.height() * i / 6.0f, hueBar.width(), hueBar.height() / 6.0f + 0.3f, top, bottom, bottom, top);
        }
        scope.rectOutline(2, field.x() + saturation * field.width() - 3.0f, field.y() + (1.0f - brightness) * field.height() - 3.0f,
                6.0f, 6.0f, 0.8f, Color.WHITE);
        scope.rect(2, hueBar.x() - 3.0f, hueBar.y() + hue * hueBar.height() - 1.5f, hueBar.width() + 6.0f, 3.0f, Color.WHITE);
        Color current = color();
        int[] values = {current.getRed(), current.getGreen(), current.getBlue(), alpha};
        Color[] fills = {new Color(values[0], 0, 0), new Color(0, values[1], 0), new Color(0, 0, values[2]), Color.WHITE};
        for (int i = 0; i < channelCount(); i++) {
            UiRect slider = sliders[i];
            scope.rect(1, slider.x(), slider.y(), slider.width(), slider.height(), new Color(0, 0, 0, 40));
            scope.rect(1, slider.x(), slider.y(), slider.width() * values[i] / 255.0f, slider.height(), TrollTheme.alpha(fills[i], 200));
            scope.text(3, TrollTheme.trim(CHANNELS[i].getTranslatedName(), metrics, TrollTheme.TEXT_SCALE, slider.width() - 33.0f),
                    slider.x() + 3.0f, slider.y() + 2.25f, TrollTheme.TEXT_SCALE, TrollTheme.text());
            String value = Integer.toString(values[i]);
            scope.text(3, value, slider.right() - metrics.getWidth(value, TrollTheme.VALUE_SCALE) - 3.0f,
                    slider.bottom() - metrics.getHeight(TrollTheme.VALUE_SCALE) - 3.0f, TrollTheme.VALUE_SCALE, TrollTheme.text());
        }
        drawPreview(scope, previousColor, original);
        drawPreview(scope, currentColor, current);
        TranslateComponent[] labels = {EpsilonTranslations.TrollGui.OKAY, EpsilonTranslations.TrollGui.CANCEL, EpsilonTranslations.TrollGui.APPLY};
        for (int i = 0; i < buttons.length; i++) {
            UiRect button = buttons[i];
            scope.rect(1, button.x(), button.y(), button.width(), button.height(),
                    TrollTheme.alpha(TrollTheme.primary(), button.contains(mouseX, mouseY) ? 180 : 70));
            String label = TrollTheme.trim(labels[i].getTranslatedName(), metrics, TrollTheme.TEXT_SCALE, button.width() - 6.0f);
            scope.text(3, label, button.centerX() - metrics.getWidth(label, TrollTheme.TEXT_SCALE) / 2.0f,
                    button.y() + 2.25f, TrollTheme.TEXT_SCALE, TrollTheme.text());
        }
    }

    private void drawPreview(UiTree.Scope scope, UiRect bounds, Color color) {
        scope.rect(1, bounds.x(), bounds.y(), bounds.width(), bounds.height(), Color.WHITE);
        scope.rect(1, bounds.x(), bounds.centerY(), bounds.width(), bounds.height() / 2.0f, Color.BLACK);
        scope.rect(2, bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event) {
        if (super.mouseClicked(event)) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return true;
        if (field.contains(event.x(), event.y())) draggingPart = 0;
        else if (hueBar.contains(event.x(), event.y())) draggingPart = 1;
        else {
            for (int i = 0; i < channelCount(); i++)
                if (sliders[i].contains(event.x(), event.y())) draggingPart = i + 2;
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i].contains(event.x(), event.y())) {
                    if (i == 1) cancel();
                    else {
                        setting.setValue(color());
                        if (i == 0) closed = true;
                    }
                    return true;
                }
            }
        }
        update(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event) {
        if (super.mouseDragged(event)) return true;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) update(event.x(), event.y());
        return true;
    }

    @Override
    public void mouseReleased(MouseButtonEvent event) {
        super.mouseReleased(event);
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) draggingPart = -1;
    }

    public void confirm() {
        setting.setValue(color());
        closed = true;
    }

    public void cancel() {
        setting.setValue(original);
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    private void update(double x, double y) {
        if (draggingPart == 0) {
            saturation = (float) Math.clamp((x - field.x()) / field.width(), 0.0, 1.0);
            brightness = (float) Math.clamp(1.0 - (y - field.y()) / field.height(), 0.0, 1.0);
        } else if (draggingPart == 1) {
            hue = (float) Math.clamp((y - hueBar.y()) / hueBar.height(), 0.0, 1.0);
        } else if (draggingPart >= 2) {
            int channel = draggingPart - 2;
            UiRect slider = sliders[channel];
            int value = (int) Math.round(Math.clamp((x - slider.x()) / slider.width(), 0.0, 1.0) * 255.0);
            Color current = color();
            switch (channel) {
                case 0 -> setRgb(new Color(value, current.getGreen(), current.getBlue()));
                case 1 -> setRgb(new Color(current.getRed(), value, current.getBlue()));
                case 2 -> setRgb(new Color(current.getRed(), current.getGreen(), value));
                case 3 -> alpha = value;
            }
        }
    }

    private void setRgb(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    private Color color() {
        return TrollTheme.alpha(Color.getHSBColor(hue, saturation, brightness), alpha);
    }

    private int channelCount() {
        return setting.isAllowAlpha() ? 4 : 3;
    }

}
