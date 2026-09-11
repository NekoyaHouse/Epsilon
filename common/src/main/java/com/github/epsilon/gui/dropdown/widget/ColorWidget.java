package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class ColorWidget extends SettingWidget<ColorSetting> {

    private static final float CHANNEL_ROW_HEIGHT = 15.0f;
    private static final float CHANNEL_LABEL_WIDTH = 8.0f;
    private static final float CHANNEL_BOX_WIDTH = 24.0f;
    private static final float CHANNEL_BOX_HEIGHT = 12.0f;
    private static final float CHANNEL_TRACK_HEIGHT = 4.0f;
    private static final float CHANNEL_GAP = 2.0f;
    private static final float CHANNEL_TEXT_SCALE = 0.44f;
    private static final float PICKER_HEIGHT = 48.0f;
    private static final float HUE_HEIGHT = 6.0f;
    private static final float PICKER_TO_HUE_GAP = 3.0f;
    private static final float HUE_TO_CHANNEL_GAP = 6.0f;
    private static final float BOTTOM_PADDING = 2.0f;

    private final Animation openAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_EXPAND);

    private static final Channel[] RGB_CHANNELS = {Channel.RED, Channel.GREEN, Channel.BLUE};
    private static final Channel[] RGBA_CHANNELS = {Channel.RED, Channel.GREEN, Channel.BLUE, Channel.ALPHA};

    private final DropdownTextField redField = new DropdownTextField(3, value -> value.matches("[0-9]"));
    private final DropdownTextField greenField = new DropdownTextField(3, value -> value.matches("[0-9]"));
    private final DropdownTextField blueField = new DropdownTextField(3, value -> value.matches("[0-9]"));
    private final DropdownTextField alphaField = new DropdownTextField(3, value -> value.matches("[0-9]"));

    private boolean opened;
    private boolean pickingSB;
    private boolean pickingHue;
    private Channel pickingChannel;
    private Color pendingValue;

    public ColorWidget(ColorSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        openAnim.run(opened ? 1.0f : 0.0f);
        int channelCount = getChannels().length;
        float expandedHeight = PICKER_HEIGHT + PICKER_TO_HUE_GAP + HUE_HEIGHT + HUE_TO_CHANNEL_GAP
                + channelCount * CHANNEL_ROW_HEIGHT
                + Math.max(0, channelCount - 1) * CHANNEL_GAP
                + BOTTOM_PADDING;
        return DropdownTheme.SETTING_HEIGHT + expandedHeight * openAnim.getValue();
    }

    @Override
    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        syncAlphaAvailability();
        openAnim.run(opened ? 1.0f : 0.0f);
        float t = openAnim.getValue();

        scope.text(setting.getDisplayName(), DropdownTheme.SETTING_PADDING_X,
                (DropdownTheme.SETTING_HEIGHT - textMetrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f,
                DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        float previewX = width - DropdownTheme.SETTING_PADDING_X - DropdownTheme.COLOR_PREVIEW_SIZE;
        float previewY = (DropdownTheme.SETTING_HEIGHT - DropdownTheme.COLOR_PREVIEW_SIZE) * 0.5f;
        scope.roundRect(previewX, previewY, DropdownTheme.COLOR_PREVIEW_SIZE, DropdownTheme.COLOR_PREVIEW_SIZE, 2.0f, getVisibleColor());

        if (t < 0.01f) return;

        Color color = getVisibleColor();
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

        float padX = DropdownTheme.SETTING_PADDING_X;
        float gradX = padX;
        float gradY = DropdownTheme.SETTING_HEIGHT + 2.0f;
        float gradW = width - padX * 2.0f;
        float gradH = PICKER_HEIGHT * t;

        Color hueColor = Color.getHSBColor(hsb[0], 1.0f, 1.0f);
        drawSaturationBrightnessPalette(scope, gradX, gradY, gradW, gradH, hueColor);

        float hueY = gradY + gradH + PICKER_TO_HUE_GAP;
        float hueH = HUE_HEIGHT * t;
        for (int i = 0; i < (int) gradW; i++) {
            Color c = Color.getHSBColor(i / gradW, 1.0f, 1.0f);
            scope.rect(gradX + i, hueY, 1.0f, hueH, c);
        }
        drawSliderPicker(scope, gradX + gradW * hsb[0], hueY, hueH);

        if (pickingSB) {
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.COLOR_PICK);
            float newSat = Mth.clamp((mouseX - absoluteX(gradX)) / gradW, 0.0f, 1.0f);
            float newBri = 1.0f - Mth.clamp((mouseY - absoluteY(gradY)) / (PICKER_HEIGHT * t), 0.0f, 1.0f);
            Color newColor = Color.getHSBColor(hsb[0], newSat, newBri);
            newColor = new Color(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), color.getAlpha());
            previewColor(newColor);
        }

        if (pickingHue) {
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.COLOR_PICK);
            float newHue = Mth.clamp((mouseX - absoluteX(gradX)) / gradW, 0.0f, 1.0f);
            Color newColor = Color.getHSBColor(newHue, hsb[1], hsb[2]);
            newColor = new Color(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), color.getAlpha());
            previewColor(newColor);
        }

        if (pickingChannel != null) {
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.COLOR_PICK);
            updateChannelFromMouse(pickingChannel, mouseX);
        }

        color = getVisibleColor();
        hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);

        float pickerCx = gradX + gradW * hsb[1];
        float pickerCy = gradY + gradH * (1.0f - hsb[2]);
        scope.rect(pickerCx - 3.0f, pickerCy - 3.0f, 6.0f, 6.0f, new Color(0, 0, 0, 135));
        scope.rect(pickerCx - 2.0f, pickerCy - 2.0f, 4.0f, 4.0f, Color.WHITE);

        drawChannelRows(scope, textMetrics, mouseX, mouseY, hueY + hueH + HUE_TO_CHANNEL_GAP, t);
    }

    private void drawSaturationBrightnessPalette(UiTree.Scope scope, float localX, float localY, float width, float height, Color hueColor) {
        scope.rect(localX - 1.0f, localY - 1.0f, width + 2.0f, height + 2.0f, new Color(255, 255, 255, 45));
        scope.rectGradient(localX, localY, width, height, Color.WHITE, Color.WHITE, hueColor, hueColor);
        scope.rectGradient(localX, localY, width, height, new Color(0, 0, 0, 0), Color.BLACK, Color.BLACK, new Color(0, 0, 0, 0));
    }

    private void drawSliderPicker(UiTree.Scope scope, float centerX, float localY, float height) {
        float pickerW = 3.0f;
        scope.rect(centerX - pickerW * 0.5f - 1.0f, localY - 2.0f, pickerW + 2.0f, height + 4.0f, new Color(0, 0, 0, 145));
        scope.rect(centerX - pickerW * 0.5f, localY - 1.0f, pickerW, height + 2.0f, Color.WHITE);
    }

    private void drawChannelRows(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float startY, float alphaProgress) {
        syncFieldsFromColor();
        Channel[] channels = getChannels();
        for (int i = 0; i < channels.length; i++) {
            Channel channel = channels[i];
            float rowY = startY + i * (CHANNEL_ROW_HEIGHT + CHANNEL_GAP) * alphaProgress;
            float rowAlpha = alphaProgress;
            drawChannelRow(scope, textMetrics, mouseX, mouseY, channel, rowY, rowAlpha);
        }
    }

    private void drawChannelRow(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, Channel channel, float rowY, float alphaProgress) {
        int value = getChannelValue(channel);
        float textY = rowY + (CHANNEL_ROW_HEIGHT - textMetrics.textHeight(CHANNEL_TEXT_SCALE)) * 0.5f;
        Color textColor = MD3Theme.withAlpha(DropdownTheme.settingLabel(), (int) (DropdownTheme.settingLabel().getAlpha() * alphaProgress));
        scope.text(channel.label, DropdownTheme.SETTING_PADDING_X, textY, CHANNEL_TEXT_SCALE, textColor);

        float boxX = getLocalFieldX();
        float boxY = rowY + (CHANNEL_ROW_HEIGHT - CHANNEL_BOX_HEIGHT) * 0.5f;
        float trackX = getLocalTrackX();
        float trackY = rowY + (CHANNEL_ROW_HEIGHT - CHANNEL_TRACK_HEIGHT) * 0.5f;
        float trackW = getLocalTrackWidth();
        drawChannelTrack(scope, channel, trackX, trackY, trackW, CHANNEL_TRACK_HEIGHT, alphaProgress);

        float knobX = trackX + trackW * (value / 255.0f);
        float knobR = 2.75f;
        scope.roundRect(knobX - knobR, trackY + CHANNEL_TRACK_HEIGHT * 0.5f - knobR, knobR * 2.0f, knobR * 2.0f, knobR, DropdownTheme.sliderKnob());

        DropdownTextField field = getField(channel);
        field.drawCentered(scope, textMetrics, boxX, boxY, CHANNEL_BOX_WIDTH, CHANNEL_BOX_HEIGHT, mouseX, mouseY, Integer.toString(value), CHANNEL_TEXT_SCALE);
    }

    private void drawChannelTrack(UiTree.Scope scope, Channel channel, float trackX, float trackY, float trackW, float trackH, float alphaProgress) {
        Color current = getVisibleColor();
        Color start = switch (channel) {
            case RED -> new Color(0, current.getGreen(), current.getBlue());
            case GREEN -> new Color(current.getRed(), 0, current.getBlue());
            case BLUE -> new Color(current.getRed(), current.getGreen(), 0);
            case ALPHA -> new Color(current.getRed(), current.getGreen(), current.getBlue(), 0);
        };
        Color end = switch (channel) {
            case RED -> new Color(255, current.getGreen(), current.getBlue());
            case GREEN -> new Color(current.getRed(), 255, current.getBlue());
            case BLUE -> new Color(current.getRed(), current.getGreen(), 255);
            case ALPHA -> new Color(current.getRed(), current.getGreen(), current.getBlue(), 255);
        };
        scope.roundRectHorizontalGradient(trackX, trackY, trackW, trackH, trackH * 0.5f, MD3Theme.withAlpha(start, (int) (start.getAlpha() * alphaProgress)), MD3Theme.withAlpha(end, (int) (end.getAlpha() * alphaProgress)));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float previewX = absoluteX(width - DropdownTheme.SETTING_PADDING_X - DropdownTheme.COLOR_PREVIEW_SIZE);
        float previewY = absoluteY((DropdownTheme.SETTING_HEIGHT - DropdownTheme.COLOR_PREVIEW_SIZE) * 0.5f);
        if ((button == 0 || button == 1) && isHovered(mouseX, mouseY, previewX - 2, previewY - 2, DropdownTheme.COLOR_PREVIEW_SIZE + 4, DropdownTheme.COLOR_PREVIEW_SIZE + 4)) {
            if (hasFocusedInput()) {
                commitFocusedInput();
                blurFields();
            }
            opened = !opened;
            DropdownScreen.INSTANCE.react(opened
                    ? ReisaDropdownCompanion.Action.COLOR_PICK
                    : ReisaDropdownCompanion.Action.PANEL_CLOSE);
            return true;
        }

        if (button != 0) return false;

        if (!opened || openAnim.getValue() < 0.5f) return false;

        float padX = DropdownTheme.SETTING_PADDING_X;
        float gradX = absoluteX(padX);
        float gradY = absoluteY(DropdownTheme.SETTING_HEIGHT + 2.0f);
        float gradW = width - padX * 2.0f;
        float gradH = PICKER_HEIGHT * openAnim.getValue();
        float hueY = gradY + gradH + PICKER_TO_HUE_GAP;
        float hueH = HUE_HEIGHT * openAnim.getValue();

        for (Channel channel : getChannels()) {
            float fieldY = getChannelY(channel) + (CHANNEL_ROW_HEIGHT - CHANNEL_BOX_HEIGHT) * 0.5f;
            if (isHovered(mouseX, mouseY, getFieldX(), fieldY, CHANNEL_BOX_WIDTH, CHANNEL_BOX_HEIGHT)) {
                blurFields();
                DropdownTextField field = getField(channel);
                field.setText(Integer.toString(getChannelValue(channel)));
                field.focusIfContainsCentered(mouseX, mouseY, getFieldX(), fieldY, CHANNEL_BOX_WIDTH, CHANNEL_BOX_HEIGHT);
                DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.TYPING);
                return true;
            }
        }
        if (hasFocusedInput()) {
            commitFocusedInput();
            blurFields();
        }

        if (isHovered(mouseX, mouseY, gradX, gradY, gradW, gradH)) {
            pickingSB = true;
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.COLOR_PICK);
            return true;
        }
        if (isHovered(mouseX, mouseY, gradX, hueY, gradW, hueH)) {
            pickingHue = true;
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.COLOR_PICK);
            return true;
        }
        for (Channel channel : getChannels()) {
            float trackX = getTrackX();
            float trackY = getChannelY(channel) + (CHANNEL_ROW_HEIGHT - CHANNEL_TRACK_HEIGHT) * 0.5f;
            float trackW = getTrackWidth();
            if (isHovered(mouseX, mouseY, trackX, trackY - 3.0f, trackW, CHANNEL_TRACK_HEIGHT + 6.0f)) {
                pickingChannel = channel;
                updateChannelFromMouse(channel, mouseX);
                DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.COLOR_PICK);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (pickingSB || pickingHue || pickingChannel != null)) {
            commitPendingColor();
            pickingSB = false;
            pickingHue = false;
            pickingChannel = null;
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        DropdownTextField focused = getFocusedField();
        if (focused == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitFocusedInput();
            focused.blur();
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            clearPendingColor();
            syncFieldsFromColor(true);
            focused.blur();
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CANCEL);
            return true;
        }
        if (focused.keyPressed(keyCode)) {
            syncFocusedInput();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        DropdownTextField focused = getFocusedField();
        if (focused == null) return false;
        if (focused.charTyped(typedText)) {
            syncFocusedInput();
            return true;
        }
        return false;
    }

    public boolean hasFocusedInput() {
        return getFocusedField() != null;
    }

    public void blurAllInputs() {
        DropdownTextField focused = getFocusedField();
        if (focused != null) {
            syncFocusedInput();
            focused.blur();
        }
    }

    private float getChannelY(Channel channel) {
        float expanded = openAnim.getValue();
        float baseY = absoluteY(DropdownTheme.SETTING_HEIGHT + 2.0f
                + PICKER_HEIGHT * expanded
                + PICKER_TO_HUE_GAP
                + HUE_HEIGHT * expanded
                + HUE_TO_CHANNEL_GAP);
        return baseY + getChannelIndex(channel) * (CHANNEL_ROW_HEIGHT + CHANNEL_GAP) * expanded;
    }

    private float getFieldX() {
        return absoluteX(width - DropdownTheme.SETTING_PADDING_X - CHANNEL_BOX_WIDTH);
    }

    private float getLocalFieldX() {
        return width - DropdownTheme.SETTING_PADDING_X - CHANNEL_BOX_WIDTH;
    }

    private float getTrackX() {
        return absoluteX(DropdownTheme.SETTING_PADDING_X + CHANNEL_LABEL_WIDTH + 4.0f);
    }

    private float getLocalTrackX() {
        return DropdownTheme.SETTING_PADDING_X + CHANNEL_LABEL_WIDTH + 4.0f;
    }

    private float getTrackWidth() {
        return getFieldX() - getTrackX() - 5.0f;
    }

    private float getLocalTrackWidth() {
        return getLocalFieldX() - getLocalTrackX() - 5.0f;
    }

    private void updateChannelFromMouse(Channel channel, double mouseX) {
        float trackX = getTrackX();
        float trackW = getTrackWidth();
        int value = Mth.clamp(Math.round(Mth.clamp((float) (mouseX - trackX) / trackW, 0.0f, 1.0f) * 255.0f), 0, 255);
        setChannelValue(channel, value, true);
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

    private void setChannelValue(Channel channel, int value, boolean defer) {
        Color current = getVisibleColor();
        int clamped = Mth.clamp(value, 0, 255);
        Color updated = switch (channel) {
            case RED -> new Color(clamped, current.getGreen(), current.getBlue(), current.getAlpha());
            case GREEN -> new Color(current.getRed(), clamped, current.getBlue(), current.getAlpha());
            case BLUE -> new Color(current.getRed(), current.getGreen(), clamped, current.getAlpha());
            case ALPHA -> new Color(current.getRed(), current.getGreen(), current.getBlue(), clamped);
        };
        if (defer) {
            previewColor(updated);
        } else {
            applyColorNow(updated);
        }
        syncFieldsFromColor();
    }

    private DropdownTextField getField(Channel channel) {
        return switch (channel) {
            case RED -> redField;
            case GREEN -> greenField;
            case BLUE -> blueField;
            case ALPHA -> alphaField;
        };
    }

    private DropdownTextField getFocusedField() {
        for (Channel channel : getChannels()) {
            DropdownTextField field = getField(channel);
            if (field.isFocused()) return field;
        }
        return null;
    }

    private Channel getFocusedChannel() {
        for (Channel channel : getChannels()) {
            if (getField(channel).isFocused()) return channel;
        }
        return null;
    }

    private void syncFocusedInput() {
        Channel channel = getFocusedChannel();
        if (channel == null) return;
        String value = getField(channel).getText();
        if (value.isEmpty()) return;
        setChannelValue(channel, parseChannel(value), true);
    }

    private void commitFocusedInput() {
        Channel channel = getFocusedChannel();
        if (channel == null) return;
        DropdownTextField field = getField(channel);
        int value = field.getText().isEmpty() ? getChannelValue(channel) : parseChannel(field.getText());
        setChannelValue(channel, value, false);
        field.setText(Integer.toString(getChannelValue(channel)));
        field.setCursorToEnd();
    }

    private int parseChannel(String value) {
        try {
            return Mth.clamp(Integer.parseInt(value), 0, 255);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void syncFieldsFromColor() {
        syncFieldsFromColor(false);
    }

    private void syncFieldsFromColor(boolean force) {
        for (Channel channel : getChannels()) {
            DropdownTextField field = getField(channel);
            if (!force && field.isFocused()) continue;
            String value = Integer.toString(getChannelValue(channel));
            if (!value.equals(field.getText())) {
                field.setText(value);
                field.setCursorToEnd();
            }
        }
    }

    private void blurFields() {
        for (Channel channel : RGBA_CHANNELS) {
            getField(channel).blur();
        }
    }

    private Channel[] getChannels() {
        return setting.isAllowAlpha() ? RGBA_CHANNELS : RGB_CHANNELS;
    }

    private int getChannelIndex(Channel channel) {
        Channel[] channels = getChannels();
        for (int i = 0; i < channels.length; i++) {
            if (channels[i] == channel) return i;
        }
        return 0;
    }

    private void syncAlphaAvailability() {
        if (setting.isAllowAlpha()) return;
        if (pickingChannel == Channel.ALPHA) {
            pickingChannel = null;
        }
        alphaField.blur();
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

    private void clearPendingColor() {
        pendingValue = null;
    }

    private enum Channel {

        RED("R"),
        GREEN("G"),
        BLUE("B"),
        ALPHA("A");

        private final String label;

        Channel(String label) {
            this.label = label;
        }

    }

}
