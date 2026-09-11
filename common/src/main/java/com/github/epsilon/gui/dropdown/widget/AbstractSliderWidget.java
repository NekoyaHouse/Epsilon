package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.settings.Setting;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

public abstract class AbstractSliderWidget<S extends Setting<T>, T extends Number> extends SettingWidget<S> {

    protected static final float VALUE_TEXT_SCALE = 0.46f;
    protected static final float VALUE_TEXT_Y_OFFSET = 3.0f;
    protected static final float EDITOR_Y_OFFSET = 5.0f;

    protected final DropdownTextField inputField;
    protected boolean dragging;
    protected T pendingValue;
    private int sessionId = -1;

    public AbstractSliderWidget(S setting, int maxInputLength, Predicate<String> inputFilter) {
        super(setting);
        this.inputField = new DropdownTextField(maxInputLength, inputFilter);
    }

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT + 15.0f;
    }

    @Override
    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        syncSessionState();

        float ratio = getRatio();
        float sliderRatio = Mth.clamp(ratio, 0.0f, 1.0f);

        scope.text(setting.getDisplayName(), DropdownTheme.SETTING_PADDING_X, 1.0f, DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        float trackX = getLocalTrackX();
        float trackY = getLocalTrackY();
        float trackW = getTrackWidth();
        float trackH = DropdownTheme.SLIDER_HEIGHT;

        boolean editing = inputField.isFocused();
        if (editing) {
            inputField.draw(scope, textMetrics, getLocalEditorX(), getLocalEditorY(), getEditorWidth(), getEditorHeight(), mouseX, mouseY, formatPlainValue(), DropdownTheme.SETTING_TEXT_SCALE);
        } else {
            scope.roundRect(trackX, trackY, trackW, trackH, DropdownTheme.SLIDER_RADIUS, DropdownTheme.sliderTrack());

            float activeW = trackW * sliderRatio;
            if (activeW > 0.5f) {
                scope.roundRect(trackX, trackY, activeW, trackH, DropdownTheme.SLIDER_RADIUS, DropdownTheme.sliderActive());
            }

            float knobX = trackX + trackW * sliderRatio;
            float knobY = trackY + trackH * 0.5f;
            float kr = DropdownTheme.SLIDER_KNOB_RADIUS;
            scope.roundRect(knobX - kr, knobY - kr, kr * 2.0f, kr * 2.0f, kr, DropdownTheme.sliderKnob());

            if (dragging && mouseX >= 0) {
                DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.SLIDER_ADJUST);
                float rawRatio = Mth.clamp((float) (mouseX - getTrackX()) / trackW, 0.0f, 1.0f);
                updateValueFromRatio(rawRatio);
            }
        }

        if (!editing) {
            drawValueLabels(scope, textMetrics, trackX, trackY, trackW);
        }
    }

    protected abstract float getRatio();

    protected abstract void updateValueFromRatio(float rawRatio);

    protected abstract String formatValue(T value);

    protected abstract String formatPlainValue();

    protected abstract void commitInput();

    protected abstract void syncInputValue();

    protected abstract void applyValue(T value);

    protected T getVisibleValue() {
        return pendingValue != null ? pendingValue : setting.getValue();
    }

    protected void previewValue(T value) {
        if (setting.isApplyWhenRelease()) {
            pendingValue = value;
        } else {
            applyValue(value);
        }
    }

    protected void applyValueNow(T value) {
        pendingValue = null;
        applyValue(value);
    }

    protected void commitPendingValue() {
        if (pendingValue == null) {
            return;
        }
        T value = pendingValue;
        applyValueNow(value);
    }

    protected void clearPendingValue() {
        pendingValue = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncSessionState();
        if (button == 1) {
            if (isEditorHitboxHovered(mouseX, mouseY)) {
                inputField.setText(formatPlainValue());
                inputField.focusIfContains(mouseX, mouseY, getEditorX(), getEditorY(), getEditorWidth(), getEditorHeight());
                dragging = false;
                DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.TYPING);
                return true;
            }
        }
        if (button == 0) {
            if (inputField.isFocused()) {
                if (isEditorBoundsHovered(mouseX, mouseY)) {
                    inputField.focusIfContains(mouseX, mouseY, getEditorX(), getEditorY(), getEditorWidth(), getEditorHeight());
                    DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.TYPING);
                    return true;
                }
                commitInput();
                inputField.blur();
            }
            if (isEditorHitboxHovered(mouseX, mouseY)) {
                dragging = true;
                DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.SLIDER_ADJUST);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncSessionState();
        if (button == 0 && dragging) {
            commitPendingValue();
            dragging = false;
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }
        if (inputField.isFocused()) {
            if (isEditorBoundsHovered(mouseX, mouseY)) {
                return true;
            }
            commitInput();
            inputField.blur();
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        syncSessionState();
        if (!inputField.isFocused()) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitInput();
            inputField.blur();
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            clearPendingValue();
            inputField.setText(formatPlainValue());
            inputField.blur();
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CANCEL);
            return true;
        }
        if (inputField.keyPressed(keyCode)) {
            syncInputValue();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        syncSessionState();
        if (inputField.charTyped(typedText)) {
            syncInputValue();
            return true;
        }
        return false;
    }

    public boolean isFocused() {
        return inputField.isFocused();
    }

    public void blurInput() {
        commitInput();
        inputField.blur();
    }

    private void syncSessionState() {
        int currentSessionId = DropdownScreen.INSTANCE.getSessionId();
        if (sessionId == currentSessionId) {
            return;
        }
        sessionId = currentSessionId;
        dragging = false;
        clearPendingValue();
        inputField.blur();
    }

    protected void drawValueLabels(UiTree.Scope scope, UiTextMetrics textMetrics, float trackX, float trackY, float trackW) {
        String minValue = formatValue(getMin());
        String currentValue = formatValue(getVisibleValue());
        String maxValue = formatValue(getMax());
        float textY = trackY + DropdownTheme.SLIDER_HEIGHT + VALUE_TEXT_Y_OFFSET;

        scope.text(minValue, trackX, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabelMuted());

        float currentWidth = textMetrics.textWidth(currentValue, VALUE_TEXT_SCALE);
        scope.text(currentValue, trackX + (trackW - currentWidth) * 0.5f, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabel());

        float maxWidth = textMetrics.textWidth(maxValue, VALUE_TEXT_SCALE);
        scope.text(maxValue, trackX + trackW - maxWidth, textY, VALUE_TEXT_SCALE, DropdownTheme.settingLabelMuted());
    }

    protected abstract T getMin();

    protected abstract T getMax();

    protected float getTrackX() {
        return absoluteX(DropdownTheme.SETTING_PADDING_X);
    }

    protected float getTrackY() {
        return absoluteY(DropdownTheme.SETTING_HEIGHT);
    }

    protected float getLocalTrackX() {
        return DropdownTheme.SETTING_PADDING_X;
    }

    protected float getLocalTrackY() {
        return DropdownTheme.SETTING_HEIGHT;
    }

    protected float getTrackWidth() {
        return width - DropdownTheme.SETTING_PADDING_X * 2.0f;
    }

    protected float getEditorX() {
        return getTrackX();
    }

    protected float getEditorY() {
        return getTrackY() - EDITOR_Y_OFFSET;
    }

    protected float getLocalEditorX() {
        return getLocalTrackX();
    }

    protected float getLocalEditorY() {
        return getLocalTrackY() - EDITOR_Y_OFFSET;
    }

    protected float getEditorWidth() {
        return getTrackWidth();
    }

    protected float getEditorHeight() {
        return DropdownTheme.INPUT_HEIGHT;
    }

    protected boolean isEditorBoundsHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, getEditorX(), getEditorY(), getEditorWidth(), getEditorHeight());
    }

    protected boolean isEditorHitboxHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, getTrackX(), getEditorY(), getTrackWidth(), DropdownTheme.INPUT_HEIGHT);
    }

}
