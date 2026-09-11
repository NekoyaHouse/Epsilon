package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.awt.*;

public class KeybindWidget extends SettingWidget<KeybindSetting> {

    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private boolean listening;
    private float buttonX;
    private float buttonY;
    private float buttonW = DropdownTheme.KEYBIND_WIDTH;
    private float buttonH = DropdownTheme.KEYBIND_HEIGHT;

    public KeybindWidget(KeybindSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT;
    }

    @Override
    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        float lineHeight = textMetrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE);
        float labelTextY = (getHeight() - lineHeight) * 0.5f;
        scope.text(setting.getDisplayName(), DropdownTheme.SETTING_PADDING_X, labelTextY, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        String keyText = listening ? "..." : KeybindUtils.format(setting.getValue());
        float textW = textMetrics.textWidth(keyText, DropdownTheme.SETTING_TEXT_SCALE);
        buttonW = Math.max(DropdownTheme.KEYBIND_WIDTH, textW + 8.0f);
        buttonH = DropdownTheme.KEYBIND_HEIGHT;
        float localButtonX = width - DropdownTheme.SETTING_PADDING_X - buttonW;
        float localButtonY = labelTextY + (lineHeight - buttonH) * 0.5f;
        buttonX = absoluteX(localButtonX);
        buttonY = absoluteY(localButtonY);

        boolean hovered = isHovered(mouseX, mouseY, buttonX - 2.0f, buttonY - 2.0f, buttonW + 4.0f, buttonH + 4.0f);
        hoverAnim.run(hovered || listening ? 1.0f : 0.0f);

        Color outline = listening
                ? MD3Theme.withAlpha(MD3Theme.PRIMARY, 200)
                : MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.OUTLINE, 96), MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, 136), hoverAnim.getValue() * 0.55f);

        scope.roundRect(localButtonX, localButtonY, buttonW, buttonH, DropdownTheme.KEYBIND_RADIUS, DropdownTheme.keybindSurface(listening));
        scope.outline(localButtonX, localButtonY, buttonW, buttonH, DropdownTheme.KEYBIND_RADIUS, 0.7f, outline);
        scope.text(keyText, localButtonX + (buttonW - textW) * 0.5f, labelTextY, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.keybindText(listening));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY, buttonX, buttonY, buttonW, buttonH)) {
            listening = !listening;
            DropdownScreen.INSTANCE.react(listening
                    ? ReisaDropdownCompanion.Action.KEY_BIND
                    : ReisaDropdownCompanion.Action.CANCEL);
            return true;
        }

        if (listening && button != 0) {
            setting.setValue(KeybindUtils.encodeMouseButton(button));
            listening = false;
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!listening) return false;

        if (keyCode == 256) {
            setting.setValue(KeybindUtils.NONE);
        } else if (keyCode == 259) {
            setting.setValue(KeybindUtils.NONE);
        } else {
            setting.setValue(keyCode);
        }
        listening = false;
        DropdownScreen.INSTANCE.react(keyCode == 256
                ? ReisaDropdownCompanion.Action.CANCEL
                : ReisaDropdownCompanion.Action.CONFIRM);
        return true;
    }

    public boolean isListening() {
        return listening;
    }

}
