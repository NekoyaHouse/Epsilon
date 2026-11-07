package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.kernel.state.*
import com.github.epsilon.gui.lib.LuminUIItem
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.control.UiScrollBar
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
    private var queryStore: String by mutableStateOf<String>("")

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
        val expand = openAnim.value
        val frameId = getRenderFrameId()
        val buttonWidth = if (maxScroll > 0.0f) width - UiScrollBar.HOVER_WIDTH + 1f else width
        val visibleBottom = visibleHeight * expand
        var itemY = -scroll
        for (button in buttons) {
            val buttonHeight = button.getHeightForFrame(frameId)
            val visible = itemY + buttonHeight > 0.0f && itemY < visibleBottom
            if (visible) {
                // 逐项缓存：只有模块状态、悬停或动画变化的行才重新捕获节点。
                val itemBounds = UiRect(0.0f, itemY, buttonWidth, buttonHeight)
                LuminUIItem(button) {
                    // 与迁移前一致：按钮宽度取 buttonWidth（滚动条出现时收窄），而不是面板宽度。
                    button.drawInScope(this, mouseX, mouseY, itemBounds)
                }
            }
            itemY += buttonHeight
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

    fun setSearchQuery(queryStore: String?) {
        val normalized = queryStore?.trim()?.lowercase(Locale.ROOT) ?: ""
        if (this.queryStore == normalized) {
            return
        }
        this.queryStore = normalized
        setScrollImmediate(0.0f)
    }

    override fun hasActiveInput(): Boolean {
        for (mb in visibleButtons()) {
            if (mb.hasListeningKeybind() || mb.hasFocusedInput()) return true
        }
        return false
    }

    private val visibleState = derivedStateOf {
        moduleButtons.sortedWith(getComparator(ClientSetting.moduleSort.value)).filter { button ->
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
