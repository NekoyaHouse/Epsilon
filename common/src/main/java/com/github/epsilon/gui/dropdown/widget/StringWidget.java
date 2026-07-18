package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.settings.impl.StringSetting;

import java.util.Objects;

public class StringWidget extends SettingWidget<StringSetting> {

    private final DropdownTextField inputField = new DropdownTextField(100);

    public StringWidget(StringSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + DropdownTheme.INPUT_HEIGHT + 2.0f;
    }

    @Override
    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        scope.text(setting.getDisplayName(), DropdownTheme.SETTING_PADDING_X, 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        float fieldX = DropdownTheme.SETTING_PADDING_X;
        float fieldY = DropdownTheme.SETTING_HEIGHT;
        float fieldW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float fieldH = DropdownTheme.INPUT_HEIGHT;

        if (!inputField.isFocused() && !inputField.getText().equals(setting.getValue())) {
            inputField.setText(setting.getValue());
        }
        inputField.draw(scope, textMetrics, fieldX, fieldY, fieldW, fieldH, mouseX, mouseY, "...", DropdownTheme.SETTING_TEXT_SCALE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float fieldX = absoluteX(DropdownTheme.SETTING_PADDING_X);
        float fieldY = absoluteY(DropdownTheme.SETTING_HEIGHT);
        float fieldW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float fieldH = DropdownTheme.INPUT_HEIGHT;

        if (button == 0 && isHovered(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH)) {
            if (!inputField.isFocused()) {
                inputField.setText(setting.getValue());
            }
            inputField.focusIfContains(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH);
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.TYPING);
            return true;
        }
        if (inputField.isFocused()) {
            commitSetting();
            inputField.blur();
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!inputField.isFocused()) return false;

        if (keyCode == 257 || keyCode == 335) {
            commitSetting();
            inputField.blur();
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }
        if (keyCode == 256) {
            inputField.setText(setting.getValue());
            inputField.blur();
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CANCEL);
            return true;
        }
        if (inputField.keyPressed(keyCode)) {
            previewSetting();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        if (inputField.charTyped(typedText)) {
            previewSetting();
            return true;
        }
        return false;
    }

    public boolean isFocused() {
        return inputField.isFocused();
    }

    public void blurInput() {
        commitSetting();
        inputField.blur();
    }

    private void previewSetting() {
        if (!setting.isApplyWhenRelease()) {
            applyText(inputField.getText());
        }
    }

    private void commitSetting() {
        applyText(inputField.getText());
    }

    private void applyText(String value) {
        if (!Objects.equals(setting.getValue(), value)) {
            setting.setValue(value);
        }
    }

}
