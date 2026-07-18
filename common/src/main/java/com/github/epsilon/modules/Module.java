package com.github.epsilon.modules;

import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.SettingHost;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class Module implements SettingHost {

    private final String name;

    private String addonId;

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

    public final List<Setting<?>> settings = new ArrayList<>();
    public final List<SettingGroup> settingGroups = new ArrayList<>();

    protected final Minecraft mc;

    public TranslateComponent translateComponent;

    public Module(String name, Category category) {
        this.name = name;
        this.category = category;
        mc = Minecraft.getInstance();
    }

    public void initI18n(TranslateComponent moduleComponent) {
        this.translateComponent = moduleComponent;
        for (SettingGroup group : settingGroups) {
            group.initTranslateComponent(moduleComponent.createChild(group.getName().toLowerCase()));
        }
        for (Setting<?> setting : settings) {
            setting.initTranslateComponent(moduleComponent.createChild(setting.getName().toLowerCase()));
        }
    }

    public void setAddonId(String addonId) {
        this.addonId = addonId;
    }

    public String getAddonId() {
        return addonId;
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
            if (enabled) {
                EventBus.INSTANCE.subscribe(this);
                if (!nullCheck()) {
                    Managers.NOTIFICATION.moduleState(this.getTranslatedName(), getNotificationHash(), true);
                }
                onEnable();
            } else {
                EventBus.INSTANCE.unsubscribe(this);
                if (!nullCheck()) {
                    Managers.NOTIFICATION.moduleState(this.getTranslatedName(), getNotificationHash(), false);
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
        String owner = addonId != null ? addonId : "epsilon";
        return (owner + ":" + name).hashCode();
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
