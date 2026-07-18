package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.util.Mth;

public class BoolWidget extends SettingWidget<BoolSetting> {

    private static final float SWITCH_WIDTH = 22.0f;
    private static final float SWITCH_HEIGHT = 12.0f;
    private static final float SWITCH_RADIUS = 6.0f;

    private static final float KNOB_SIZE_OFF = 6.0f;
    private static final float KNOB_SIZE_ON = 9.0f;
    private static final float KNOB_INSET_OFF = 3.5f;
    private static final float KNOB_INSET_ON = 2.0f;
    private static final float STATE_LAYER_SIZE = 16.0f;
    private static final float KNOB_STRETCH = 3.5f;

    private final Animation toggleAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_TOGGLE);
    private final Animation knobBounceAnim = new Animation(Easing.EASE_OUT_ELASTIC, 450L);
    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);

    public BoolWidget(BoolSetting setting) {
        super(setting);
        float initial = setting.getValue() ? 1.0f : 0.0f;
        toggleAnim.setStartValue(initial);
        knobBounceAnim.setStartValue(initial);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT;
    }

    @Override
    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        float target = setting.getValue() ? 1.0f : 0.0f;
        toggleAnim.run(target);
        knobBounceAnim.run(target);
        float t = toggleAnim.getValue();
        float bounce = knobBounceAnim.getValue();

        float sw = SWITCH_WIDTH;
        float sh = SWITCH_HEIGHT;
        float sx = width - DropdownTheme.SETTING_PADDING_X - sw;
        float sy = (getHeight() - sh) * 0.5f;

        boolean hovered = isHovered(mouseX, mouseY, absoluteX(sx - 2), absoluteY(sy - 2), sw + 4, sh + 4);
        hoverAnim.run(hovered ? 1.0f : 0.0f);
        float hoverProgress = hoverAnim.getValue();

        scope.text(setting.getDisplayName(), DropdownTheme.SETTING_PADDING_X,
                (getHeight() - textMetrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) * 0.5f,
                DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        scope.roundRect(sx, sy, sw, sh, SWITCH_RADIUS, MD3Theme.switchTrack(t));

        float outlineW = MD3Theme.switchTrackOutlineWidth(t);
        if (outlineW > 0.01f) {
            scope.outline(sx, sy, sw, sh, SWITCH_RADIUS, outlineW, MD3Theme.switchTrackOutline(t, hoverProgress));
        }

        float knobSize = Mth.lerp(Mth.clamp(t, 0.0f, 1.0f), KNOB_SIZE_OFF, KNOB_SIZE_ON);
        float stretchFactor = 4.0f * t * (1.0f - t);
        float knobW = knobSize + KNOB_STRETCH * stretchFactor;
        float inset = Mth.lerp(t, KNOB_INSET_OFF, KNOB_INSET_ON);
        float knobMinX = sx + inset + knobW * 0.5f;
        float knobMaxX = sx + sw - inset - knobW * 0.5f;
        float knobCx = Mth.lerp(bounce, knobMinX, knobMaxX);
        float knobCy = sy + sh * 0.5f;

        if (hoverProgress > 0.02f) {
            float haloX = knobCx - STATE_LAYER_SIZE * 0.5f;
            float haloY = knobCy - STATE_LAYER_SIZE * 0.5f;
            scope.roundRect(haloX, haloY, STATE_LAYER_SIZE, STATE_LAYER_SIZE, STATE_LAYER_SIZE * 0.5f, MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, 18));
        }

        float knobRadius = knobSize * 0.5f;
        scope.roundRect(knobCx - knobW * 0.5f, knobCy - knobSize * 0.5f, knobW, knobSize, knobRadius, MD3Theme.switchKnob(t));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            float sw = SWITCH_WIDTH;
            float sh = SWITCH_HEIGHT;
            float sx = absoluteX(width - DropdownTheme.SETTING_PADDING_X - sw);
            float sy = absoluteY((getHeight() - sh) * 0.5f);
            if (isHovered(mouseX, mouseY, sx - 2, sy - 2, sw + 4, sh + 4)) {
                boolean newValue = !setting.getValue();
                setting.setValue(newValue);
                Managers.SOUND.playInUi(newValue ? SoundKey.SETTINGS_OPEN : SoundKey.SETTINGS_CLOSE);
                DropdownScreen.INSTANCE.react(newValue
                        ? ReisaDropdownCompanion.Action.TOGGLE_ON
                        : ReisaDropdownCompanion.Action.TOGGLE_OFF);
                return true;
            }
        }
        return false;
    }

}
