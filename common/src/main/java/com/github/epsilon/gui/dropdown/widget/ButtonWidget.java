package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.settings.impl.ButtonSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

public class ButtonWidget extends SettingWidget<ButtonSetting> {

    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);

    public ButtonWidget(ButtonSetting setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + 2.0f;
    }

    @Override
    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        float btnX = DropdownTheme.SETTING_PADDING_X;
        float btnY = 1.0f;
        float btnW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float btnH = DropdownTheme.BUTTON_HEIGHT;

        boolean hovered = isHovered(mouseX, mouseY, absoluteX(btnX), absoluteY(btnY), btnW, btnH);
        hoverAnim.run(hovered ? 1.0f : 0.0f);

        scope.roundRect(btnX, btnY, btnW, btnH, DropdownTheme.BUTTON_RADIUS, DropdownTheme.buttonSurface(hoverAnim.getValue()));

        String label = setting.getDisplayName();
        float textW = textMetrics.textWidth(label, DropdownTheme.SETTING_TEXT_SCALE);
        float textY = btnY + (btnH - textMetrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f;
        scope.text(label, btnX + (btnW - textW) * 0.5f, textY, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.buttonText());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        float btnX = absoluteX(DropdownTheme.SETTING_PADDING_X);
        float btnY = absoluteY(1.0f);
        float btnW = width - DropdownTheme.SETTING_PADDING_X * 2.0f;
        float btnH = DropdownTheme.BUTTON_HEIGHT;

        if (isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
            Runnable action = setting.getValue();
            if (action != null) {
                action.run();
            }
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.BUTTON_ACTION);
            return true;
        }
        return false;
    }

}
