package com.github.epsilon.gui.panel;

import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.settings.impl.KeybindSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PanelState {

    public enum ActivePopup {
        NONE,
        ENUM_SELECT,
        KEY_BIND,
        COLOR_PICKER
    }

    public enum ClientSettingTab {
        GENERAL,
        FRIEND,
        CONFIG,
        ADDON
    }

    private Category selectedCategory = Category.COMBAT;
    private Module selectedModule;
    private String searchQuery = "";
    private ActivePopup activePopup = ActivePopup.NONE;
    private Module listeningKeyBindModule;
    private boolean sidebarExpanded;
    private float moduleScroll;
    private float detailScroll;
    private float maxModuleScroll;
    private float maxDetailScroll;

    private boolean clientSettingMode;
    private ClientSettingTab clientSettingTab = ClientSettingTab.GENERAL;
    private KeybindSetting listeningKeybindSetting;
    private float clientSettingScroll;
    private float maxClientSettingScroll;
    private float friendScroll;
    private float maxFriendScroll;
    private float configScroll;
    private float maxConfigScroll;
    private String selectedAddonId = "";
    private float addonListScroll;
    private float maxAddonListScroll;
    private float addonDetailScroll;
    private float maxAddonDetailScroll;

    public PanelState() {
        ensureValidSelection();
    }

    public Category getSelectedCategory() {
        return selectedCategory;
    }

    public void setSelectedCategory(Category category) {
        selectedCategory = category;
        moduleScroll = 0.0f;
        ensureValidSelection();
    }

    public Module getSelectedModule() {
        ensureValidSelection();
        return selectedModule;
    }

    public void setSelectedModule(Module module) {
        selectedModule = module;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null ? "" : searchQuery;
        moduleScroll = 0.0f;
        ensureValidSelection();
    }

    public ActivePopup getActivePopup() {
        return activePopup;
    }

    public void setActivePopup(ActivePopup activePopup) {
        this.activePopup = activePopup == null ? ActivePopup.NONE : activePopup;
    }

    public Module getListeningKeyBindModule() {
        return listeningKeyBindModule;
    }

    public void setListeningKeyBindModule(Module listeningKeyBindModule) {
        this.listeningKeyBindModule = listeningKeyBindModule;
    }

    public boolean isSidebarExpanded() {
        return sidebarExpanded;
    }

    public void setSidebarExpanded(boolean sidebarExpanded) {
        this.sidebarExpanded = sidebarExpanded;
    }

    public void toggleSidebarExpanded() {
        sidebarExpanded = !sidebarExpanded;
    }

    public List<Module> getVisibleModules() {
        String loweredSearch = searchQuery.toLowerCase();
        List<Module> modules = new ArrayList<>(ModuleHolder.INSTANCE.getModules().stream()
                .filter(module -> module.getCategory() == selectedCategory)
                .filter(module -> loweredSearch.isBlank() || matchesSearch(module, loweredSearch))
                .sorted(getComparator())
                .toList());

        if (!modules.isEmpty() && (selectedModule == null || !modules.contains(selectedModule))) {
            selectedModule = modules.getFirst();
        }

        return modules;
    }

    public float getModuleScroll() {
        return moduleScroll;
    }

    public void scrollModules(double amount) {
        moduleScroll = clampScroll(moduleScroll + (float) amount, maxModuleScroll);
    }

    public float getDetailScroll() {
        return detailScroll;
    }

    public void scrollDetail(double amount) {
        detailScroll = clampScroll(detailScroll + (float) amount, maxDetailScroll);
    }

    public float getMaxModuleScroll() {
        return maxModuleScroll;
    }

    public void setModuleScroll(float scroll) {
        this.moduleScroll = clampScroll(scroll, maxModuleScroll);
    }

    public void setMaxModuleScroll(float maxModuleScroll) {
        this.maxModuleScroll = Math.max(0.0f, maxModuleScroll);
        moduleScroll = clampScroll(moduleScroll, this.maxModuleScroll);
    }

    public float getMaxDetailScroll() {
        return maxDetailScroll;
    }

    public void setDetailScroll(float scroll) {
        this.detailScroll = clampScroll(scroll, maxDetailScroll);
    }

    public void setMaxDetailScroll(float maxDetailScroll) {
        this.maxDetailScroll = Math.max(0.0f, maxDetailScroll);
        detailScroll = clampScroll(detailScroll, this.maxDetailScroll);
    }

    private void ensureValidSelection() {
        String loweredSearch = searchQuery.toLowerCase();
        List<Module> modules = ModuleHolder.INSTANCE.getModules().stream()
                .filter(module -> module.getCategory() == selectedCategory)
                .filter(module -> loweredSearch.isBlank() || matchesSearch(module, loweredSearch))
                .sorted(getComparator())
                .toList();
        if (!modules.isEmpty() && (selectedModule == null || !modules.contains(selectedModule))) {
            selectedModule = modules.getFirst();
        }
    }

    private Comparator<Module> getComparator() {
        Comparator<Module> nameComparator = Comparator.comparing(this::normalizedName, String.CASE_INSENSITIVE_ORDER);
        return switch (ClientSetting.INSTANCE.moduleSort.getValue()) {
            case EnabledFirst -> Comparator.comparing(Module::isEnabled).reversed()
                    .thenComparing(nameComparator);
            case Addon -> Comparator.comparing(this::normalizedAddon, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(nameComparator);
            case Name -> nameComparator;
        };
    }

    private String normalizedName(Module module) {
        String translated = module.getTranslatedName();
        if (translated != null && !translated.isBlank()) return translated;
        String name = module.getName();
        return name == null ? "" : name;
    }

    private String normalizedAddon(Module module) {
        String addonId = module.getAddonId();
        return addonId == null || addonId.isBlank() ? "unknown" : addonId.toLowerCase(Locale.ROOT);
    }

    private boolean matchesSearch(Module module, String loweredSearch) {
        return module.getName().toLowerCase().contains(loweredSearch)
                || module.getTranslatedName().toLowerCase().contains(loweredSearch)
                || module.getCategory().getName().toLowerCase().contains(loweredSearch)
                || normalizedAddon(module).contains(loweredSearch);
    }

    public boolean isClientSettingMode() {
        return clientSettingMode;
    }

    public void setClientSettingMode(boolean clientSettingMode) {
        if (this.clientSettingMode != clientSettingMode) {
            this.clientSettingMode = clientSettingMode;
            if (clientSettingMode) {
                listeningKeyBindModule = null;
            } else {
                listeningKeybindSetting = null;
                clientSettingScroll = 0.0f;
                friendScroll = 0.0f;
                configScroll = 0.0f;
                addonListScroll = 0.0f;
                addonDetailScroll = 0.0f;
                selectedAddonId = "";
                clientSettingTab = ClientSettingTab.GENERAL;
            }
        }
    }

    public KeybindSetting getListeningKeybindSetting() {
        return listeningKeybindSetting;
    }

    public void setListeningKeybindSetting(KeybindSetting listeningKeybindSetting) {
        this.listeningKeybindSetting = listeningKeybindSetting;
    }

    public float getClientSettingScroll() {
        return clientSettingScroll;
    }

    public void scrollClientSetting(double amount) {
        clientSettingScroll = clampScroll(clientSettingScroll + (float) amount, maxClientSettingScroll);
    }

    public float getMaxClientSettingScroll() {
        return maxClientSettingScroll;
    }

    public void setClientSettingScroll(float scroll) {
        this.clientSettingScroll = clampScroll(scroll, maxClientSettingScroll);
    }

    public void setMaxClientSettingScroll(float maxClientSettingScroll) {
        this.maxClientSettingScroll = Math.max(0.0f, maxClientSettingScroll);
        clientSettingScroll = clampScroll(clientSettingScroll, this.maxClientSettingScroll);
    }

    public ClientSettingTab getClientSettingTab() {
        return clientSettingTab;
    }

    public void setClientSettingTab(ClientSettingTab tab) {
        if (this.clientSettingTab != tab) {
            this.clientSettingTab = tab;
        }
    }

    public float getFriendScroll() {
        return friendScroll;
    }

    public void scrollFriend(double amount) {
        friendScroll = clampScroll(friendScroll + (float) amount, maxFriendScroll);
    }

    public float getMaxFriendScroll() {
        return maxFriendScroll;
    }

    public void setFriendScroll(float scroll) {
        this.friendScroll = clampScroll(scroll, maxFriendScroll);
    }

    public void setMaxFriendScroll(float maxFriendScroll) {
        this.maxFriendScroll = Math.max(0.0f, maxFriendScroll);
        friendScroll = clampScroll(friendScroll, this.maxFriendScroll);
    }

    public float getConfigScroll() {
        return configScroll;
    }

    public void scrollConfig(double amount) {
        configScroll = clampScroll(configScroll + (float) amount, maxConfigScroll);
    }

    public float getMaxConfigScroll() {
        return maxConfigScroll;
    }

    public void setConfigScroll(float scroll) {
        this.configScroll = clampScroll(scroll, maxConfigScroll);
    }

    public void setMaxConfigScroll(float maxConfigScroll) {
        this.maxConfigScroll = Math.max(0.0f, maxConfigScroll);
        configScroll = clampScroll(configScroll, this.maxConfigScroll);
    }

    public String getSelectedAddonId() {
        return selectedAddonId;
    }

    public void setSelectedAddonId(String selectedAddonId) {
        this.selectedAddonId = selectedAddonId == null ? "" : selectedAddonId;
    }

    public float getAddonListScroll() {
        return addonListScroll;
    }

    public void scrollAddonList(double amount) {
        addonListScroll = clampScroll(addonListScroll + (float) amount, maxAddonListScroll);
    }

    public float getMaxAddonListScroll() {
        return maxAddonListScroll;
    }

    public void setAddonListScroll(float scroll) {
        this.addonListScroll = clampScroll(scroll, maxAddonListScroll);
    }

    public void setMaxAddonListScroll(float maxAddonListScroll) {
        this.maxAddonListScroll = Math.max(0.0f, maxAddonListScroll);
        addonListScroll = clampScroll(addonListScroll, this.maxAddonListScroll);
    }

    public float getAddonDetailScroll() {
        return addonDetailScroll;
    }

    public void scrollAddonDetail(double amount) {
        addonDetailScroll = clampScroll(addonDetailScroll + (float) amount, maxAddonDetailScroll);
    }

    public float getMaxAddonDetailScroll() {
        return maxAddonDetailScroll;
    }

    public void setAddonDetailScroll(float scroll) {
        this.addonDetailScroll = clampScroll(scroll, maxAddonDetailScroll);
    }

    public void setMaxAddonDetailScroll(float maxAddonDetailScroll) {
        this.maxAddonDetailScroll = Math.max(0.0f, maxAddonDetailScroll);
        addonDetailScroll = clampScroll(addonDetailScroll, this.maxAddonDetailScroll);
    }

    private float clampScroll(float scroll, float maxScroll) {
        return Math.clamp(scroll, 0, maxScroll);
    }

}
