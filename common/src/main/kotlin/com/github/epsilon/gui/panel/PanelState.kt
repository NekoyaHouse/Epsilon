package com.github.epsilon.gui.panel

import com.github.epsilon.managers.ModuleManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.settings.impl.KeybindSetting
import java.util.ArrayList
import java.util.Comparator

class PanelState {

    enum class ActivePopup {
        NONE,
        ENUM_SELECT,
        KEY_BIND,
        COLOR_PICKER
    }

    enum class ClientSettingTab {
        GENERAL,
        FRIEND,
        CONFIG
    }

    private var selectedCategory: Category = Category.COMBAT
    private var selectedModule: Module? = null
    private var searchQuery = ""
    private var activePopup: ActivePopup = ActivePopup.NONE
    private var listeningKeyBindModule: Module? = null
    private var sidebarExpanded = false
    private var moduleScroll = 0.0f
    private var detailScroll = 0.0f
    private var maxModuleScroll = 0.0f
    private var maxDetailScroll = 0.0f

    private var clientSettingMode = false
    private var clientSettingTab: ClientSettingTab = ClientSettingTab.GENERAL
    private var listeningKeybindSetting: KeybindSetting? = null
    private var clientSettingScroll = 0.0f
    private var maxClientSettingScroll = 0.0f
    private var friendScroll = 0.0f
    private var maxFriendScroll = 0.0f
    private var configScroll = 0.0f
    private var maxConfigScroll = 0.0f

    init {
        ensureValidSelection()
    }

    fun getSelectedCategory(): Category {
        return selectedCategory
    }

    fun setSelectedCategory(category: Category) {
        selectedCategory = category
        moduleScroll = 0.0f
        ensureValidSelection()
    }

    fun getSelectedModule(): Module? {
        ensureValidSelection()
        return selectedModule
    }

    fun setSelectedModule(module: Module?) {
        selectedModule = module
    }

    fun getSearchQuery(): String {
        return searchQuery
    }

    fun setSearchQuery(searchQuery: String?) {
        this.searchQuery = searchQuery ?: ""
        moduleScroll = 0.0f
        ensureValidSelection()
    }

    fun getActivePopup(): ActivePopup {
        return activePopup
    }

    fun setActivePopup(activePopup: ActivePopup?) {
        this.activePopup = activePopup ?: ActivePopup.NONE
    }

    fun getListeningKeyBindModule(): Module? {
        return listeningKeyBindModule
    }

    fun setListeningKeyBindModule(listeningKeyBindModule: Module?) {
        this.listeningKeyBindModule = listeningKeyBindModule
    }

    fun isSidebarExpanded(): Boolean {
        return sidebarExpanded
    }

    fun setSidebarExpanded(sidebarExpanded: Boolean) {
        this.sidebarExpanded = sidebarExpanded
    }

    fun toggleSidebarExpanded() {
        sidebarExpanded = !sidebarExpanded
    }

    fun getVisibleModules(): List<Module> {
        val visible = filteredModules()
        if (visible.isNotEmpty() && (selectedModule == null || !visible.contains(selectedModule))) {
            selectedModule = visible.first()
        }
        return visible
    }

    fun getModuleScroll(): Float {
        return moduleScroll
    }

    fun scrollModules(amount: Double) {
        moduleScroll = clampScroll(moduleScroll + amount.toFloat(), maxModuleScroll)
    }

    fun getDetailScroll(): Float {
        return detailScroll
    }

    fun scrollDetail(amount: Double) {
        detailScroll = clampScroll(detailScroll + amount.toFloat(), maxDetailScroll)
    }

    fun getMaxModuleScroll(): Float {
        return maxModuleScroll
    }

    fun setModuleScroll(scroll: Float) {
        this.moduleScroll = clampScroll(scroll, maxModuleScroll)
    }

    fun setMaxModuleScroll(maxModuleScroll: Float) {
        this.maxModuleScroll = Math.max(0.0f, maxModuleScroll)
        moduleScroll = clampScroll(moduleScroll, this.maxModuleScroll)
    }

    fun getMaxDetailScroll(): Float {
        return maxDetailScroll
    }

    fun setDetailScroll(scroll: Float) {
        this.detailScroll = clampScroll(scroll, maxDetailScroll)
    }

    fun setMaxDetailScroll(maxDetailScroll: Float) {
        this.maxDetailScroll = Math.max(0.0f, maxDetailScroll)
        detailScroll = clampScroll(detailScroll, this.maxDetailScroll)
    }

    private fun filteredModules(): List<Module> {
        val loweredSearch = searchQuery.lowercase()
        return ArrayList(
            ModuleManager.INSTANCE.getModules().stream()
                .filter { module -> module.category == selectedCategory }
                .filter { module -> loweredSearch.isBlank() || matchesSearch(module, loweredSearch) }
                .sorted(getComparator())
                .toList()
        )
    }

    private fun ensureValidSelection() {
        val modules = filteredModules()
        if (modules.isNotEmpty() && (selectedModule == null || !modules.contains(selectedModule))) {
            selectedModule = modules.first()
        }
    }

    private fun getComparator(): Comparator<Module> {
        val nameComparator: Comparator<Module> =
            Comparator.comparing({ module: Module -> normalizedName(module) }, String.CASE_INSENSITIVE_ORDER)
        return when (ClientSetting.moduleSort.value) {
            ClientSetting.ModuleSort.EnabledFirst ->
                compareByDescending<Module> { it.isEnabled }.thenComparing(nameComparator)

            ClientSetting.ModuleSort.Name -> nameComparator
        }
    }

    private fun normalizedName(module: Module): String {
        val translated = module.translatedName
        if (translated != null && !translated.isBlank()) return translated
        return module.name ?: ""
    }

    private fun matchesSearch(module: Module, loweredSearch: String): Boolean {
        return module.name.lowercase().contains(loweredSearch) ||
                module.translatedName.lowercase().contains(loweredSearch) ||
                module.category!!.getName().lowercase().contains(loweredSearch)
    }

    fun isClientSettingMode(): Boolean {
        return clientSettingMode
    }

    fun setClientSettingMode(clientSettingMode: Boolean) {
        if (this.clientSettingMode != clientSettingMode) {
            this.clientSettingMode = clientSettingMode
            if (clientSettingMode) {
                listeningKeyBindModule = null
            } else {
                listeningKeybindSetting = null
                clientSettingScroll = 0.0f
                friendScroll = 0.0f
                configScroll = 0.0f
                clientSettingTab = ClientSettingTab.GENERAL
            }
        }
    }

    fun getListeningKeybindSetting(): KeybindSetting? {
        return listeningKeybindSetting
    }

    fun setListeningKeybindSetting(listeningKeybindSetting: KeybindSetting?) {
        this.listeningKeybindSetting = listeningKeybindSetting
    }

    fun getClientSettingScroll(): Float {
        return clientSettingScroll
    }

    fun scrollClientSetting(amount: Double) {
        clientSettingScroll = clampScroll(clientSettingScroll + amount.toFloat(), maxClientSettingScroll)
    }

    fun getMaxClientSettingScroll(): Float {
        return maxClientSettingScroll
    }

    fun setClientSettingScroll(scroll: Float) {
        this.clientSettingScroll = clampScroll(scroll, maxClientSettingScroll)
    }

    fun setMaxClientSettingScroll(maxClientSettingScroll: Float) {
        this.maxClientSettingScroll = Math.max(0.0f, maxClientSettingScroll)
        clientSettingScroll = clampScroll(clientSettingScroll, this.maxClientSettingScroll)
    }

    fun getClientSettingTab(): ClientSettingTab {
        return clientSettingTab
    }

    fun setClientSettingTab(tab: ClientSettingTab) {
        if (this.clientSettingTab != tab) {
            this.clientSettingTab = tab
        }
    }

    fun getFriendScroll(): Float {
        return friendScroll
    }

    fun scrollFriend(amount: Double) {
        friendScroll = clampScroll(friendScroll + amount.toFloat(), maxFriendScroll)
    }

    fun getMaxFriendScroll(): Float {
        return maxFriendScroll
    }

    fun setFriendScroll(scroll: Float) {
        this.friendScroll = clampScroll(scroll, maxFriendScroll)
    }

    fun setMaxFriendScroll(maxFriendScroll: Float) {
        this.maxFriendScroll = Math.max(0.0f, maxFriendScroll)
        friendScroll = clampScroll(friendScroll, this.maxFriendScroll)
    }

    fun getConfigScroll(): Float {
        return configScroll
    }

    fun scrollConfig(amount: Double) {
        configScroll = clampScroll(configScroll + amount.toFloat(), maxConfigScroll)
    }

    fun getMaxConfigScroll(): Float {
        return maxConfigScroll
    }

    fun setConfigScroll(scroll: Float) {
        this.configScroll = clampScroll(scroll, maxConfigScroll)
    }

    fun setMaxConfigScroll(maxConfigScroll: Float) {
        this.maxConfigScroll = Math.max(0.0f, maxConfigScroll)
        configScroll = clampScroll(configScroll, this.maxConfigScroll)
    }

    private fun clampScroll(scroll: Float, maxScroll: Float): Float {
        return Math.clamp(scroll, 0.0f, maxScroll)
    }

}
