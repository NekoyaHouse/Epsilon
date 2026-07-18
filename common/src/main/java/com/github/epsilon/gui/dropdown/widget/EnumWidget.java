package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class EnumWidget extends SettingWidget<EnumSetting<?>> {

    private static final float FIELD_HEIGHT = 14.0f;
    private static final float FIELD_RADIUS = 5.0f;
    private static final float FIELD_TEXT_SCALE = 0.50f;
    private static final float FIELD_TEXT_PADDING_X = 6.0f;
    private static final float FIELD_ARROW_SIZE = 3.0f;
    private static final float LIST_GAP_Y = 3.0f;
    private static final float LIST_PADDING_Y = 2.0f;
    private static final float OPTION_HEIGHT = 12.0f;
    private static final float OPTION_GAP = 1.0f;
    private static final float OPTION_TEXT_SCALE = 0.48f;

    private final Animation expandAnim = new Animation(Easing.DECELERATE, DropdownTheme.ANIM_EXPAND);
    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);
    private boolean expanded;

    public EnumWidget(EnumSetting<?> setting) {
        super(setting);
    }

    @Override
    public float getHeight() {
        expandAnim.run(shouldExpand() ? 1.0f : 0.0f);
        return getCollapsedHeight() + getExpandedTotalHeight() * expandAnim.getValue();
    }

    @Override
    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        float expand = updateExpandProgress();
        float hover = updateHoverProgress(isFieldHovered(mouseX, mouseY));
        float fieldX = getLocalFieldX();
        float fieldY = getLocalFieldY();
        float fieldW = getFieldWidth();

        scope.text(
                setting.getDisplayName(),
                DropdownTheme.SETTING_PADDING_X,
                1.0f,
                DropdownTheme.SETTING_TEXT_SCALE,
                DropdownTheme.settingLabel()
        );

        drawCurrentValueField(scope, textMetrics, fieldX, fieldY, fieldW, hover, expand);

        if (expand > 0.001f && getHiddenModeCount() > 0) {
            drawExpandedOptions(scope, textMetrics, mouseX, mouseY, fieldX, fieldW, expand);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isFieldHovered(mouseX, mouseY)) {
            return handleFieldClick(button);
        }

        if (expanded) {
            return handleExpandedClick(mouseX, mouseY);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (expanded && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            expanded = false;
            Managers.SOUND.playInUi(SoundKey.SETTINGS_CLOSE);
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CANCEL);
            return true;
        }
        return false;
    }

    private void drawCurrentValueField(UiTree.Scope scope, UiTextMetrics textMetrics, float fieldX, float fieldY, float fieldW, float hover, float expand) {
        Color background = MD3Theme.filledFieldSurface(expanded, hover);
        Color outline = MD3Theme.filledFieldIndicator(expanded, hover);
        float textY = fieldY + (FIELD_HEIGHT - textMetrics.textHeight(FIELD_TEXT_SCALE)) * 0.5f;
        float arrowCenterX = fieldX + fieldW - 10.0f;
        float arrowCenterY = fieldY + FIELD_HEIGHT * 0.5f;

        scope.roundRect(fieldX, fieldY, fieldW, FIELD_HEIGHT, FIELD_RADIUS, background);
        scope.outline(fieldX, fieldY, fieldW, FIELD_HEIGHT, FIELD_RADIUS, 0.7f, outline);
        scope.text(
                setting.getTranslatedValue(),
                fieldX + FIELD_TEXT_PADDING_X,
                textY,
                FIELD_TEXT_SCALE,
                MD3Theme.filledFieldContent(expanded)
        );
        scope.triangle(arrowCenterX, arrowCenterY, FIELD_ARROW_SIZE, expand, DropdownTheme.expandArrow(expand));
    }

    private void drawExpandedOptions(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float fieldX, float fieldW, float expand) {
        float listX = fieldX;
        float listY = getLocalListY();
        float listH = getListHeight();
        float clipH = listH * expand;
        float visibleBottom = listY + clipH;

        drawExpandedBackground(scope, listX, listY, fieldW, clipH);

        Enum<?>[] hiddenModes = getHiddenModes();
        for (int optionIndex = 0; optionIndex < hiddenModes.length; optionIndex++) {
            drawOption(scope, textMetrics, mouseX, mouseY, listX, fieldW, visibleBottom, optionIndex, hiddenModes[optionIndex]);
        }
    }

    private Enum<?> getHoveredOption(double mouseX, double mouseY) {
        Enum<?>[] hiddenModes = getHiddenModes();
        for (int optionIndex = 0; optionIndex < hiddenModes.length; optionIndex++) {
            if (isOptionHovered(mouseX, mouseY, optionIndex)) {
                return hiddenModes[optionIndex];
            }
        }
        return null;
    }

    private boolean handleFieldClick(int button) {
        if ((button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
                && getHiddenModeCount() > 0) {
            expanded = !expanded;
            Managers.SOUND.playInUi(expanded ? SoundKey.SETTINGS_OPEN : SoundKey.SETTINGS_CLOSE);
            DropdownScreen.INSTANCE.react(expanded
                    ? ReisaDropdownCompanion.Action.PANEL_OPEN
                    : ReisaDropdownCompanion.Action.PANEL_CLOSE);
            return true;
        }
        return expanded;
    }

    private boolean handleExpandedClick(double mouseX, double mouseY) {
        Enum<?> mode = getHoveredOption(mouseX, mouseY);
        if (mode != null) {
            setting.setMode(mode.name());
            expanded = false;
            Managers.SOUND.playInUi(SoundKey.SETTINGS_CLOSE);
            DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.CONFIRM);
            return true;
        }
        expanded = false;
        Managers.SOUND.playInUi(SoundKey.SETTINGS_CLOSE);
        return false;
    }

    private float updateExpandProgress() {
        expandAnim.run(shouldExpand() ? 1.0f : 0.0f);
        return expandAnim.getValue();
    }

    private float updateHoverProgress(boolean fieldHovered) {
        hoverAnim.run(fieldHovered || expanded ? 1.0f : 0.0f);
        return hoverAnim.getValue();
    }

    private void drawExpandedBackground(UiTree.Scope scope, float listX, float listY, float fieldW, float clipH) {
        scope.roundRect(listX, listY, fieldW, clipH, FIELD_RADIUS, DropdownTheme.settingSurface());
        scope.outline(listX, listY, fieldW, clipH, FIELD_RADIUS, 0.7f, MD3Theme.withAlpha(MD3Theme.OUTLINE, 96));
    }

    private void drawOption(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float listX, float fieldW, float visibleBottom, int optionIndex, Enum<?> mode) {
        float optionY = getLocalOptionY(optionIndex);
        if (optionY >= visibleBottom) {
            return;
        }

        float visibleHeight = Math.min(OPTION_HEIGHT, visibleBottom - optionY);
        if (visibleHeight <= 0.0f) {
            return;
        }

        boolean hovered = isOptionHovered(mouseX, mouseY, optionIndex);
        if (hovered) {
            scope.roundRect(
                    listX + 1.5f,
                    optionY,
                    fieldW - 3.0f,
                    visibleHeight,
                    FIELD_RADIUS - 1.0f,
                    MD3Theme.rowSurface(1.0f)
            );
        }

        float lineHeight = textMetrics.textHeight(OPTION_TEXT_SCALE);
        float textY = optionY + (OPTION_HEIGHT - lineHeight) * 0.5f;
        if (textY + lineHeight > visibleBottom) {
            return;
        }

        float alpha = Mth.clamp((visibleBottom - optionY) / OPTION_HEIGHT, 0.0f, 1.0f);
        Color textColor = hovered ? MD3Theme.TEXT_PRIMARY : DropdownTheme.settingLabelMuted();
        textColor = MD3Theme.withAlpha(textColor, Mth.clamp((int) (textColor.getAlpha() * alpha), 0, 255));
        scope.text(
                setting.getTranslatedValueUnchecked(mode),
                listX + FIELD_TEXT_PADDING_X,
                textY,
                OPTION_TEXT_SCALE,
                textColor
        );
    }

    private boolean isFieldHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, getFieldX(), getFieldY(), getFieldWidth(), FIELD_HEIGHT);
    }

    private boolean isOptionHovered(double mouseX, double mouseY, int optionIndex) {
        return isHovered(mouseX, mouseY, getFieldX(), getOptionY(optionIndex), getFieldWidth(), OPTION_HEIGHT);
    }

    private float getCollapsedHeight() {
        return DropdownTheme.SETTING_HEIGHT - 1.0f + FIELD_HEIGHT;
    }

    private float getExpandedTotalHeight() {
        int hiddenCount = getHiddenModeCount();
        if (hiddenCount <= 0) return 0.0f;
        return LIST_GAP_Y + getListHeight();
    }

    private float getListHeight() {
        int hiddenCount = getHiddenModeCount();
        if (hiddenCount <= 0) return 0.0f;
        return LIST_PADDING_Y * 2.0f + hiddenCount * OPTION_HEIGHT + Math.max(0, hiddenCount - 1) * OPTION_GAP;
    }

    private int getHiddenModeCount() {
        return Math.max(0, setting.getModes().length - 1);
    }

    private Enum<?>[] getHiddenModes() {
        Enum<?> selected = setting.getValue();
        Enum<?>[] modes = setting.getModes();
        Enum<?>[] hiddenModes = new Enum<?>[getHiddenModeCount()];
        int hiddenIndex = 0;
        for (Enum<?> mode : modes) {
            if (mode != selected) {
                hiddenModes[hiddenIndex++] = mode;
            }
        }
        return hiddenModes;
    }

    private boolean shouldExpand() {
        return expanded && getHiddenModeCount() > 0;
    }

    private float getFieldX() {
        return absoluteX(DropdownTheme.SETTING_PADDING_X);
    }

    private float getFieldY() {
        return absoluteY(DropdownTheme.SETTING_HEIGHT - 1.0f);
    }

    private float getLocalFieldX() {
        return DropdownTheme.SETTING_PADDING_X;
    }

    private float getLocalFieldY() {
        return DropdownTheme.SETTING_HEIGHT - 1.0f;
    }

    private float getFieldWidth() {
        return width - DropdownTheme.SETTING_PADDING_X * 2.0f;
    }

    private float getListY() {
        return getFieldY() + FIELD_HEIGHT + LIST_GAP_Y;
    }

    private float getLocalListY() {
        return getLocalFieldY() + FIELD_HEIGHT + LIST_GAP_Y;
    }

    private float getOptionY(int optionIndex) {
        return getListY() + LIST_PADDING_Y + optionIndex * (OPTION_HEIGHT + OPTION_GAP);
    }

    private float getLocalOptionY(int optionIndex) {
        return getLocalListY() + LIST_PADDING_Y + optionIndex * (OPTION_HEIGHT + OPTION_GAP);
    }

}
