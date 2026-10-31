package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.control.UiScrollBar
import com.github.epsilon.managers.ModuleManager
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.ClientSetting
import java.util.Comparator
import java.util.HashMap
import java.util.Locale

class CategoryPanel : AbstractDropdownPanel {

    val category: Category?
    private val moduleButtons: MutableList<ModuleButton> = ArrayList()
    private var sortedModuleButtons: List<ModuleButton> = emptyList()
    private var visibleModuleButtons: List<ModuleButton> = emptyList()
    private val searchTextCache: MutableMap<ModuleButton, String> = HashMap()
    private var searchQuery: String = ""
    private var cachedSortMode: ClientSetting.ModuleSort? = null
    private var cachedSortSignature: Long = Long.MIN_VALUE
    private var cachedFilterSignature: Long = Long.MIN_VALUE
    private var cachedSearchTextRevision: Long = Long.MIN_VALUE
    private var cachedContentHeightFrameId: Int = Int.MIN_VALUE
    private var cachedContentHeight: Float = 0.0f

    constructor(category: Category, panelIndex: Int) :
            super("category:$category", TitleSupplier { category.name }, category.icon, panelIndex) {
        this.category = category
        val modules = ModuleManager.getModules().stream()
            .filter { m -> m.category == category }
            .toList()
        initModuleButtons(modules)
    }

    constructor(id: String, title: String, icon: String?, panelIndex: Int, modules: List<Module>) :
            super(id, title, icon, panelIndex) {
        this.category = null
        initModuleButtons(modules)
    }

    private fun initModuleButtons(modules: List<Module>) {
        for (module in modules) {
            moduleButtons.add(ModuleButton(module))
        }
        refreshSortedModuleButtons()
    }

    override fun drawPanelContent(
        scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int, visibleHeight: Float,
    ) {
        val buttons = visibleButtons()
        val expand = openAnim.value
        val frameId = getRenderFrameId()
        val buttonWidth = if (maxScroll > 0.0f) width - UiScrollBar.HOVER_WIDTH + 1f else width
        val stack = scope.stack(
            UiRect(
                x,
                y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll,
                buttonWidth,
                computeContentHeight()
            )
        )
        for (button in buttons) {
            val btnH = button.getHeightForFrame(frameId)
            stack.item(btnH) { bounds, itemScope ->
                val visibleTop = y + DropdownTheme.PANEL_HEADER_HEIGHT
                val visibleBottom = visibleTop + visibleHeight * expand
                if (bounds.bottom() > visibleTop && bounds.y < visibleBottom) {
                    button.drawInScope(itemScope, textMetrics, mouseX, mouseY, bounds)
                }
            }
        }
    }

    override fun computeContentHeight(): Float {
        val frameId = getRenderFrameId()
        if (cachedContentHeightFrameId == frameId) {
            return cachedContentHeight
        }

        val buttons = visibleButtons()
        var total = 0.0f
        for (button in buttons) {
            total += button.getHeightForFrame(frameId)
        }
        cachedContentHeightFrameId = frameId
        cachedContentHeight = total
        return total
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseClickedContent(mouseX: Double, mouseY: Double, button: Int): Boolean {
        for (mb in visibleButtons()) {
            if (mb.mouseClicked(mouseX, mouseY, button)) {
                return true
            }
        }
        return false
    }

    override fun mouseReleasedContent(mouseX: Double, mouseY: Double, button: Int): Boolean {
        for (mb in visibleButtons()) {
            if (mb.mouseReleased(mouseX, mouseY, button)) {
                return true
            }
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        for (mb in visibleButtons()) {
            if (mb.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
        }
        return false
    }

    override fun charTyped(typedText: String): Boolean {
        for (mb in visibleButtons()) {
            if (mb.charTyped(typedText)) {
                return true
            }
        }
        return false
    }

    fun setSearchQuery(searchQuery: String?) {
        val normalized = searchQuery?.trim()?.lowercase(Locale.ROOT) ?: ""
        if (this.searchQuery == normalized) {
            return
        }
        this.searchQuery = normalized
        cachedFilterSignature = Long.MIN_VALUE
        setScrollImmediate(0.0f)
    }

    override fun hasActiveInput(): Boolean {
        for (mb in visibleButtons()) {
            if (mb.hasListeningKeybind() || mb.hasFocusedInput()) return true
        }
        return false
    }

    private fun refreshSortedModuleButtons() {
        val sortMode: ClientSetting.ModuleSort = ClientSetting.moduleSort.value
        val sortSignature = buildSortSignature(sortMode)
        if (sortMode == cachedSortMode && sortSignature == cachedSortSignature) {
            return
        }
        sortedModuleButtons = moduleButtons.stream()
            .sorted(getComparator(sortMode))
            .toList()
        cachedSortMode = sortMode
        cachedSortSignature = sortSignature
        cachedFilterSignature = Long.MIN_VALUE
    }

    private fun visibleButtons(): List<ModuleButton> {
        refreshSortedModuleButtons()
        val filterSignature = cachedSortSignature * 31L + searchQuery.hashCode()
        if (filterSignature == cachedFilterSignature) {
            return visibleModuleButtons
        }
        visibleModuleButtons = sortedModuleButtons.stream()
            .filter { button -> matchesSearch(button) }
            .toList()
        cachedFilterSignature = filterSignature
        return visibleModuleButtons
    }

    private fun buildSortSignature(sortMode: ClientSetting.ModuleSort): Long {
        var signature = 17L
        signature = signature * 31L + sortMode.ordinal
        signature = signature * 31L + TranslationManager.getRevision()
        signature = signature * 31L + moduleButtons.size
        for (button in moduleButtons) {
            val module = button.module
            signature = signature * 31L + module.name.hashCode()
            signature = signature * 31L + if (module.isEnabled) 1 else 0
        }
        return signature
    }

    private fun getComparator(sortMode: ClientSetting.ModuleSort): Comparator<ModuleButton> {
        val nameComparator: Comparator<ModuleButton> =
            Comparator.comparing<ModuleButton, String>({ button -> normalizedName(button.module) }, String.CASE_INSENSITIVE_ORDER)
        return when (sortMode) {
            ClientSetting.ModuleSort.EnabledFirst ->
                Comparator.comparing<ModuleButton, Boolean> { button -> button.module.isEnabled }.reversed().thenComparing(nameComparator)
            ClientSetting.ModuleSort.Name -> nameComparator
        }
    }

    private fun normalizedName(module: Module): String {
        val translated = module.translatedName
        if (translated.isNotBlank()) return translated
        val name = module.name
        return name
    }

    private fun matchesSearch(button: ModuleButton): Boolean {
        if (searchQuery.isBlank()) return true
        refreshSearchTextCache()
        val searchText = searchTextCache[button]
        return searchText != null && searchText.contains(searchQuery)
    }

    private fun refreshSearchTextCache() {
        val revision = TranslationManager.getRevision()
        if (cachedSearchTextRevision == revision && searchTextCache.isNotEmpty()) {
            return
        }
        searchTextCache.clear()
        for (button in moduleButtons) {
            val module = button.module
            val translated = module.translatedName ?: ""
            val name = module.name
            val categoryName = module.category?.name ?: ""
            searchTextCache[button] = (translated + '\n' + name + '\n' + categoryName).lowercase(Locale.ROOT)
        }
        cachedSearchTextRevision = revision
    }

}
