package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.graphics.schedulers.render2d.Render2DTransform
import com.github.epsilon.gui.lib.LuminUIItem
import com.github.epsilon.gui.lib.Transform
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.control.UiScrollBar
import com.github.epsilon.kernel.state.derivedStateOf
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.managers.ModuleManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.ClientSetting
import java.util.*

class CategoryPanel : AbstractDropdownPanel {

    val category: Category?
    private var cachedContentHeightFrameId = Int.MIN_VALUE
    private var cachedContentHeight = 0.0f
    private val moduleButtons: MutableList<ModuleButton> = ArrayList()
    private var queryStore: String by mutableStateOf("")

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
    }

    override fun UiScope.drawPanelContent(mouseX: Int, mouseY: Int, visibleHeight: Float) {
        val buttons = visibleButtons()
        val frameId = getRenderFrameId()
        val buttonWidth = if (maxScroll > 0.0f) width - UiScrollBar.HOVER_WIDTH + 1f else width
        Transform({ Render2DTransform.translation(0f, -scroll) }) {
            var itemY = 0f
            for (button in buttons) {
                val buttonHeight = button.getHeightForFrame(frameId)
                val itemBounds = UiRect(0f, itemY, buttonWidth, buttonHeight)
                LuminUIItem(button) { button.drawInScope(this, mouseX, mouseY, itemBounds) }
                itemY += buttonHeight
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


    fun setSearchQuery(queryStore: String?) {
        val normalized = queryStore?.trim()?.lowercase(Locale.ROOT) ?: ""
        if (this.queryStore == normalized) {
            return
        }
        this.queryStore = normalized
        setScrollImmediate(0.0f)
    }


    private val visibleState = derivedStateOf {
        moduleButtons.sortedWith(getComparator(ClientSetting.moduleSort)).filter { button ->
            val module = button.module
            queryStore.isBlank() || (module.translatedName + "\n" + module.name + "\n" + module.category?.name)
                .lowercase(Locale.ROOT).contains(queryStore)
        }
    }

    private fun visibleButtons(): List<ModuleButton> = visibleState.value

    private fun getComparator(sortMode: ClientSetting.ModuleSort): Comparator<ModuleButton> {
        val nameComparator: Comparator<ModuleButton> =
            Comparator.comparing<ModuleButton, String>(
                { button -> normalizedName(button.module) },
                String.CASE_INSENSITIVE_ORDER
            )
        return when (sortMode) {
            ClientSetting.ModuleSort.EnabledFirst ->
                Comparator.comparing<ModuleButton, Boolean> { button -> button.module.isEnabled }.reversed()
                    .thenComparing(nameComparator)

            ClientSetting.ModuleSort.Name -> nameComparator
        }
    }

    private fun normalizedName(module: Module): String {
        val translated = module.translatedName
        if (translated.isNotBlank()) return translated
        val name = module.name
        return name
    }

}
