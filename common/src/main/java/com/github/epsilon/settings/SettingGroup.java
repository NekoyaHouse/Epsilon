package com.github.epsilon.settings;

import com.github.epsilon.assets.i18n.TranslateComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Setting 的显式分组模型。
 * <p>
 * 分组只描述语义和折叠状态，具体位置仍交给 UiTree / Dropdown stack 统一计算。
 * 分组可以嵌套：{@link #child(String)} 创建或复用子分组，子分组只属于创建它的父分组。
 */
public class SettingGroup {

    private final String name;
    private final List<SettingGroup> children = new ArrayList<>();
    private SettingGroup parent;
    private TranslateComponent translateComponent;
    private boolean collapsed = true;

    public SettingGroup(String name) {
        this.name = name;
    }

    /**
     * 创建或复用同名子分组（忽略大小写）。
     *
     * @param childName 子分组名称
     * @return 该父分组下的子分组
     */
    public SettingGroup child(String childName) {
        for (SettingGroup child : children) {
            if (child.name.equalsIgnoreCase(childName)) {
                return child;
            }
        }
        SettingGroup child = new SettingGroup(childName);
        child.parent = this;
        children.add(child);
        return child;
    }

    /**
     * 返回按声明顺序排列的子分组。
     */
    public List<SettingGroup> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * 返回父分组；顶层分组的父分组为 {@code null}。
     */
    public SettingGroup getParent() {
        return parent;
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
