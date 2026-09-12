package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.*;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.*;

import java.util.List;

public class SettingsContent {

    private final SettingSectionRenderer renderer;
    private int cachedContentHeightFrameId = Integer.MAX_VALUE;
    private float cachedContentHeight;

    public SettingsContent(String ownerKey, List<Setting<?>> settings) {
        this.renderer = SettingSectionRenderer.create(ownerKey, settings, SettingsContent::createWidget);
    }

    public static SettingWidget<?> createWidget(Setting<?> setting) {
        if (setting instanceof BoolSetting s) return new BoolWidget(s);
        if (setting instanceof IntSetting s) return new IntSliderWidget(s);
        if (setting instanceof DoubleSetting s) return new DoubleSliderWidget(s);
        if (setting instanceof EnumSetting<?> s) return new EnumWidget(s);
        if (setting instanceof ColorSetting s) return new ColorWidget(s);
        if (setting instanceof RegistryListSetting<?> s) return new RegistryListSettingWidget(s);
        if (setting instanceof KeybindSetting s) return new KeybindWidget(s);
        if (setting instanceof StringSetting s) return new StringWidget(s);
        if (setting instanceof ButtonSetting s) return new ButtonWidget(s);
        if (setting instanceof StringListSetting s) return new StringListSettingWidget(s);
        return null;
    }

    public float computeContentHeight() {
        if (renderer.isEmpty()) {
            return DropdownTheme.MODULE_HEIGHT;
        }
        return DropdownTheme.SETTING_GAP + renderer.height();
    }

    public float computeContentHeight(int frameId) {
        if (frameId == Integer.MIN_VALUE) {
            return computeContentHeight();
        }
        if (cachedContentHeightFrameId != frameId) {
            cachedContentHeight = computeContentHeight();
            cachedContentHeightFrameId = frameId;
        }
        return cachedContentHeight;
    }

    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float panelX, float contentY, float panelWidth) {
        draw(scope, textMetrics, mouseX, mouseY, panelX, contentY, panelWidth, Integer.MIN_VALUE);
    }

    public void draw(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float panelX, float contentY, float panelWidth, int frameId) {
        if (renderer.isEmpty()) {
            String label = EpsilonTranslations.Gui.NO_SETTINGS.getTranslatedName();
            float labelScale = 0.58f;
            float textW = textMetrics.textWidth(label, labelScale);
            scope.text(label, panelX + (panelWidth - textW) * 0.5f, contentY + 8.0f, labelScale, MD3Theme.TEXT_MUTED);
            return;
        }

        if (frameId != Integer.MIN_VALUE) {
            computeContentHeight(frameId);
        }
        renderer.draw(scope, textMetrics, mouseX, mouseY, panelX, contentY + DropdownTheme.SETTING_GAP,
                panelWidth, 0.0f, 0.0f);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, float panelX, float contentY, float panelWidth) {
        if (renderer.mouseClicked(mouseX, mouseY, button, panelX, contentY + DropdownTheme.SETTING_GAP,
                panelWidth, 0.0f, 0.0f)) {
            return true;
        }
        renderer.blurAllInputs();
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button, float panelX, float contentY, float panelWidth) {
        return renderer.mouseReleased(mouseX, mouseY, button);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return renderer.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(String typedText) {
        return renderer.charTyped(typedText);
    }

    public boolean hasActiveInput() {
        return renderer.hasActiveInput();
    }

}
