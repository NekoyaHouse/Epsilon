package com.github.epsilon.settings;

import com.github.epsilon.assets.i18n.TranslateComponent;

/**
 * Setting 的显式分组模型。
 * <p>
 * 分组只描述语义和折叠状态，具体位置仍交给 UiTree / Dropdown stack 统一计算。
 */
public class SettingGroup {

    private final String name;
    private TranslateComponent translateComponent;
    private boolean collapsed = true;

    public SettingGroup(String name) {
        this.name = name;
    }

    public void initTranslateComponent(TranslateComponent component) {
        this.translateComponent = component;
    }

    public TranslateComponent getTranslateComponent() {
        return translateComponent;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return translateComponent != null ? translateComponent.getTranslatedName() : name;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public void toggleCollapsed() {
        collapsed = !collapsed;
    }

}
