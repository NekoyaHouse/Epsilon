package com.github.epsilon.gui.panel

import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.managers.ModuleManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.ClientSetting

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

    private var selectedCategoryStore: Category by mutableStateOf(Category.COMBAT)
    private var selectedModuleStore: Module? by mutableStateOf(null)
    private var searchQueryStore by mutableStateOf("")
    private var activePopupStore: ActivePopup by mutableStateOf(ActivePopup.NONE)
    private var listeningKeyBindModuleStore: Module? by mutableStateOf(null)
    private var sidebarExpandedStore by mutableStateOf(false)
    private var moduleScrollStore by mutableStateOf(0.0f)
    private var detailScrollStore by mutableStateOf(0.0f)
    private var maxModuleScrollStore by mutableStateOf(0.0f)
    private var maxDetailScrollStore by mutableStateOf(0.0f)

    private var clientSettingModeStore by mutableStateOf(false)
    private var clientSettingTabStore: ClientSettingTab by mutableStateOf(ClientSettingTab.GENERAL)
    private var clientSettingScrollStore by mutableStateOf(0.0f)
    private var maxClientSettingScrollStore by mutableStateOf(0.0f)
    private var friendScrollStore by mutableStateOf(0.0f)
    private var maxFriendScrollStore by mutableStateOf(0.0f)
    private var configScrollStore by mutableStateOf(0.0f)
    private var maxConfigScrollStore by mutableStateOf(0.0f)

    init {
        ensureValidSelection()
    }

    fun getSelectedCategory(): Category {
        return selectedCategoryStore
    }

    fun setSelectedCategory(category: Category) {
        selectedCategoryStore = category
        moduleScrollStore = 0.0f
        ensureValidSelection()
    }

    fun getSelectedModule(): Module? {
        return selectedModuleStore
    }

    fun setSelectedModule(module: Module?) {
        selectedModuleStore = module
    }

    fun getSearchQuery(): String {
        return searchQueryStore
    }

    fun setSearchQuery(searchQuery: String?) {
        this.searchQueryStore = searchQuery ?: ""
        moduleScrollStore = 0.0f
        ensureValidSelection()
    }

    fun getActivePopup(): ActivePopup {
        return activePopupStore
    }

    fun setActivePopup(activePopup: ActivePopup?) {
        this.activePopupStore = activePopup ?: ActivePopup.NONE
    }

    fun getListeningKeyBindModule(): Module? {
        return listeningKeyBindModuleStore
    }

    fun setListeningKeyBindModule(listeningKeyBindModule: Module?) {
        this.listeningKeyBindModuleStore = listeningKeyBindModule
    }

    fun isSidebarExpanded(): Boolean {
        return sidebarExpandedStore
    }

    fun setSidebarExpanded(sidebarExpanded: Boolean) {
        this.sidebarExpandedStore = sidebarExpanded
    }

    fun toggleSidebarExpanded() {
        sidebarExpandedStore = !sidebarExpandedStore
    }

    fun getVisibleModules(): List<Module> {
        val visible = filteredModules()
        return visible
    }

    fun getModuleScroll(): Float {
        return moduleScrollStore
    }

    fun scrollModules(amount: Double) {
        moduleScrollStore = clampScroll(moduleScrollStore + amount.toFloat(), maxModuleScrollStore)
    }

    fun getDetailScroll(): Float {
        return detailScrollStore
    }

    fun scrollDetail(amount: Double) {
        detailScrollStore = clampScroll(detailScrollStore + amount.toFloat(), maxDetailScrollStore)
    }

    fun getMaxModuleScroll(): Float {
        return maxModuleScrollStore
    }

    fun setModuleScroll(scroll: Float) {
        this.moduleScrollStore = clampScroll(scroll, maxModuleScrollStore)
    }

    fun setMaxModuleScroll(maxModuleScroll: Float) {
        this.maxModuleScrollStore = maxOf(0.0f, maxModuleScroll)
        moduleScrollStore = clampScroll(moduleScrollStore, this.maxModuleScrollStore)
    }

    fun getMaxDetailScroll(): Float {
        return maxDetailScrollStore
    }

    fun setDetailScroll(scroll: Float) {
        this.detailScrollStore = clampScroll(scroll, maxDetailScrollStore)
    }

    fun setMaxDetailScroll(maxDetailScroll: Float) {
        this.maxDetailScrollStore = maxOf(0.0f, maxDetailScroll)
        detailScrollStore = clampScroll(detailScrollStore, this.maxDetailScrollStore)
    }

    private fun filteredModules(): List<Module> {
        val loweredSearch = searchQueryStore.lowercase()
        return ArrayList(
            ModuleManager.getModules().stream()
                .filter { module -> module.category == selectedCategoryStore }
                .filter { module -> loweredSearch.isBlank() || matchesSearch(module, loweredSearch) }
                .sorted(getComparator())
                .toList()
        )
    }

    fun ensureValidSelection() {
        val modules = filteredModules()
        if (modules.isNotEmpty() && (selectedModuleStore == null || !modules.contains(selectedModuleStore))) {
            selectedModuleStore = modules.first()
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
        return module.name
    }

    private fun matchesSearch(module: Module, loweredSearch: String): Boolean {
        return module.name.lowercase().contains(loweredSearch) ||
                module.translatedName.lowercase().contains(loweredSearch) ||
                module.category!!.name.lowercase().contains(loweredSearch)
    }

    fun isClientSettingMode(): Boolean {
        return clientSettingModeStore
    }

    fun setClientSettingMode(clientSettingMode: Boolean) {
        if (this.clientSettingModeStore != clientSettingMode) {
            this.clientSettingModeStore = clientSettingMode
            if (clientSettingMode) {
                listeningKeyBindModuleStore = null
            } else {
                clientSettingScrollStore = 0.0f
                friendScrollStore = 0.0f
                configScrollStore = 0.0f
                clientSettingTabStore = ClientSettingTab.GENERAL
            }
        }
    }


    fun getClientSettingScroll(): Float {
        return clientSettingScrollStore
    }

    fun scrollClientSetting(amount: Double) {
        clientSettingScrollStore = clampScroll(clientSettingScrollStore + amount.toFloat(), maxClientSettingScrollStore)
    }

    fun getMaxClientSettingScroll(): Float {
        return maxClientSettingScrollStore
    }

    fun setClientSettingScroll(scroll: Float) {
        this.clientSettingScrollStore = clampScroll(scroll, maxClientSettingScrollStore)
    }

    fun setMaxClientSettingScroll(maxClientSettingScroll: Float) {
        this.maxClientSettingScrollStore = maxOf(0.0f, maxClientSettingScroll)
        clientSettingScrollStore = clampScroll(clientSettingScrollStore, this.maxClientSettingScrollStore)
    }

    fun getClientSettingTab(): ClientSettingTab {
        return clientSettingTabStore
    }

    fun setClientSettingTab(tab: ClientSettingTab) {
        if (this.clientSettingTabStore != tab) {
            this.clientSettingTabStore = tab
        }
    }

    fun getFriendScroll(): Float {
        return friendScrollStore
    }

    fun scrollFriend(amount: Double) {
        friendScrollStore = clampScroll(friendScrollStore + amount.toFloat(), maxFriendScrollStore)
    }

    fun getMaxFriendScroll(): Float {
        return maxFriendScrollStore
    }

    fun setFriendScroll(scroll: Float) {
        this.friendScrollStore = clampScroll(scroll, maxFriendScrollStore)
    }

    fun setMaxFriendScroll(maxFriendScroll: Float) {
        this.maxFriendScrollStore = maxOf(0.0f, maxFriendScroll)
        friendScrollStore = clampScroll(friendScrollStore, this.maxFriendScrollStore)
    }

    fun getConfigScroll(): Float {
        return configScrollStore
    }

    fun scrollConfig(amount: Double) {
        configScrollStore = clampScroll(configScrollStore + amount.toFloat(), maxConfigScrollStore)
    }

    fun getMaxConfigScroll(): Float {
        return maxConfigScrollStore
    }

    fun setConfigScroll(scroll: Float) {
        this.configScrollStore = clampScroll(scroll, maxConfigScrollStore)
    }

    fun setMaxConfigScroll(maxConfigScroll: Float) {
        this.maxConfigScrollStore = maxOf(0.0f, maxConfigScroll)
        configScrollStore = clampScroll(configScrollStore, this.maxConfigScrollStore)
    }

    private fun clampScroll(scroll: Float, maxScroll: Float): Float {
        return (scroll).coerceIn(0.0f, maxScroll)
    }

}
