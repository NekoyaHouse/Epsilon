package com.github.epsilon.gui.dropdown.widget;

import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.awt.*;

/**
 * 集合类型 Setting 的下拉菜单基类。
 * <p>
 * 统一了所有 *ListSetting / *SetSetting 类型 Widget 的渲染与交互逻辑，
 * 子类只需提供翻译关键字和弹窗打开动作。
 */
public abstract class AbstractSetSettingWidget<S extends Setting<?>> extends SettingWidget<S> {

    private static final float FIELD_HEIGHT = 14.0f;
    private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);

    protected AbstractSetSettingWidget(S setting) {
        super(setting);
    }

    /**
     * 集合当前元素数量
     */
    protected abstract int elementCount();

    /**
     * 按钮标签翻译组件（如 "3 items" 中的 "items"）
     */
    protected abstract String labelText();

    /**
     * 点击按钮时执行的弹窗打开动作
     */
    protected abstract void openPopup();

    @Override
    public float getHeight() {
        return DropdownTheme.SETTING_HEIGHT - 1.0f + FIELD_HEIGHT;
    }

    @Override
    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY) {
        boolean hovered = isFieldHovered(mouseX, mouseY);
        hoverAnim.run(hovered ? 1.0f : 0.0f);

        scope.text(setting.getDisplayName(), DropdownTheme.SETTING_PADDING_X, 1.0f,
                DropdownTheme.SETTING_TEXT_SCALE, DropdownTheme.settingLabel());

        // 渲染使用本地坐标
        float fieldX = DropdownTheme.SETTING_PADDING_X;
        float fieldY = DropdownTheme.SETTING_HEIGHT - 1.0f;
        float fieldW = getFieldWidth();
        float hover = hoverAnim.getValue();
        Color background = MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, hover * 0.6f);
        Color outline = MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.OUTLINE, 90), MD3Theme.PRIMARY, hover);
        String label = elementCount() + labelText();
        float labelScale = 0.50f;
        float iconScale = 0.54f;
        float labelY = centeredTextY(textMetrics, fieldY, FIELD_HEIGHT, labelScale);
        float iconY = centeredTextY(textMetrics, fieldY, FIELD_HEIGHT, iconScale);

        scope.roundRect(fieldX, fieldY, fieldW, FIELD_HEIGHT, DropdownTheme.INPUT_RADIUS, background);
        scope.outline(fieldX, fieldY, fieldW, FIELD_HEIGHT, DropdownTheme.INPUT_RADIUS, 0.7f, outline);
        scope.text(label, fieldX + 6.0f, labelY, labelScale, MD3Theme.ON_SECONDARY_CONTAINER);
        scope.text("+", fieldX + fieldW - 12.0f, iconY, iconScale, MD3Theme.ON_SECONDARY_CONTAINER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isFieldHovered(mouseX, mouseY)) return false;
        openPopup();
        Managers.SOUND.playInUi(SoundKey.SETTINGS_OPEN);
        DropdownScreen.INSTANCE.react(ReisaDropdownCompanion.Action.PANEL_OPEN);
        return true;
    }

    /**
     * hit-test 使用绝对坐标
     */
    protected boolean isFieldHovered(double mouseX, double mouseY) {
        return isHovered(mouseX, mouseY, getFieldX(), getFieldY(), getFieldWidth(), FIELD_HEIGHT);
    }

    /**
     * hit-test uses absolute coordinates.
     */
    protected float getFieldX() {
        return absoluteX(DropdownTheme.SETTING_PADDING_X);
    }

    protected float getFieldY() {
        return absoluteY(DropdownTheme.SETTING_HEIGHT - 1.0f);
    }

    protected float getFieldWidth() {
        return width - DropdownTheme.SETTING_PADDING_X * 2.0f;
    }

    private float centeredTextY(UiTextMetrics textMetrics, float boxY, float boxHeight, float scale) {
        return boxY + (boxHeight - textMetrics.textHeight(scale)) * 0.5f;
    }
}
