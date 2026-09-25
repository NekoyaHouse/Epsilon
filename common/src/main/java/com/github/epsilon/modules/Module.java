package com.github.epsilon.modules;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.SettingHost;
import com.github.epsilon.modules.orchestration.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class Module implements SettingHost {

    private final String name;

    private final Category category;

    private int keyBind = -1;

    public enum BindMode {
        Toggle,
        Hold
    }

    private BindMode bindMode = BindMode.Toggle;

    private boolean hidden = true;

    private boolean enabled;

    private boolean defaultHidden = true;

    private boolean defaultEnabled = false;

    private final ModuleId moduleId;
    private final ModuleDeclaration declaration;
    private ModuleDispatchMode dispatchMode = ModuleDispatchMode.LEGACY;

    public final List<Setting<?>> settings = new ArrayList<>();
    public final List<SettingGroup> settingGroups = new ArrayList<>();

    protected final Minecraft mc;

    public TranslateComponent translateComponent;

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
        this.moduleId = ModuleId.of(name.toLowerCase().replace(' ', '_'));
        this.declaration = new ModuleDeclaration(moduleId);
        mc = Minecraft.getInstance();
    }

    protected final NodeBuilder<com.github.epsilon.events.impl.PlayerTickEvent.Pre> node(NodeKey key) {
        return node(com.github.epsilon.events.impl.PlayerTickEvent.Pre.class, key);
    }

    protected final <E> NodeBuilder<E> node(Class<E> eventType, NodeKey key) {
        return declaration.node(eventType, key);
    }

    @SuppressWarnings("unchecked")
    public final <E> NodeBuilder<E> nodeUnchecked(Class<?> eventType, NodeKey key) {
        return (NodeBuilder<E>) declaration.node(eventType, key);
    }

    public final void setDispatchModeForAdapter() { this.dispatchMode = ModuleDispatchMode.LEGACY_ADAPTER; }

    protected final void part(ModulePart part) { declaration.part(part); }
    public final ModuleId moduleId() { return moduleId; }
    public final ModuleDeclaration declaration() { return declaration; }
    public final ModuleDispatchMode dispatchMode() { return dispatchMode; }
    protected final void setDispatchMode(ModuleDispatchMode mode) { this.dispatchMode = mode; }

    public void initI18n(TranslateComponent moduleComponent) {
        this.translateComponent = moduleComponent;
        for (SettingGroup group : settingGroups) {
            initGroupI18n(moduleComponent, group);
        }
        for (Setting<?> setting : settings) {
            setting.initTranslateComponent(moduleComponent.createChild(setting.getName().toLowerCase()));
        }
    }

    /**
     * 递归初始化分组翻译组件；子分组 key 逐级拼接在父分组 key 之下。
     */
    private static void initGroupI18n(TranslateComponent parentComponent, SettingGroup group) {
        TranslateComponent component = parentComponent.createChild(group.getName().toLowerCase());
        group.initTranslateComponent(component);
        for (SettingGroup child : group.getChildren()) {
            initGroupI18n(component, child);
        }
    }

    protected boolean nullCheck() {
        return mc.player == null || mc.level == null;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            ModuleOrchestrator.INSTANCE.setEnabled(moduleId, enabled);
            if (enabled) {
                if (dispatchMode == ModuleDispatchMode.LEGACY) EventBus.INSTANCE.subscribe(this);
                if (!nullCheck()) {
                    NotificationManager.INSTANCE.moduleState(this.getTranslatedName(), getNotificationHash(), true);
                }
                onEnable();
            } else {
                if (dispatchMode == ModuleDispatchMode.LEGACY) EventBus.INSTANCE.unsubscribe(this);
                if (!nullCheck()) {
                    NotificationManager.INSTANCE.moduleState(this.getTranslatedName(), getNotificationHash(), false);
                }
                onDisable();
            }
        }
    }

    protected void setDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
        setEnabled(defaultEnabled);
    }

    protected void setDefaultHidden(boolean defaultHidden) {
        this.defaultHidden = defaultHidden;
        this.hidden = defaultHidden;
    }

    private int getNotificationHash() {
        return ("epsilon:" + name).hashCode();
    }

    public void reset() {
        setEnabled(false);
        keyBind = -1;
        bindMode = BindMode.Toggle;
        hidden = defaultHidden;
        resetCustomState();
        for (Setting<?> setting : settings) {
            setting.reset();
        }
        if (defaultEnabled) {
            setEnabled(true);
        }
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public List<SettingGroup> getSettingGroups() {
        return settingGroups;
    }

    @Override
    public List<Setting<?>> mutableSettings() {
        return settings;
    }

    @Override
    public List<SettingGroup> mutableSettingGroups() {
        return settingGroups;
    }


    public Category getCategory() {
        return category;
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public BindMode getBindMode() {
        return bindMode;
    }

    public void setBindMode(BindMode bindMode) {
        this.bindMode = bindMode;
    }

    public String getName() {
        return name;
    }

    public String getTranslatedName() {
        return translateComponent != null ? translateComponent.getTranslatedName() : name;
    }

    public String getInfo() {
        return null;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    protected void resetCustomState() {
    }

    public JsonObject saveCustomState() {
        return null;
    }

    public void loadCustomState(JsonObject state) {
    }

}
