package com.github.epsilon.gui.troll

import com.github.epsilon.gui.lib.LuminUIItem
import com.github.epsilon.gui.lib.asUiScope
import com.github.epsilon.kernel.state.*
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.managers.ModuleManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.ClientSetting
import net.minecraft.client.input.MouseButtonEvent
import java.util.*

class TrollCategoryWindow(private val category: Category?) : TrollWindow() {

    private val rows: MutableMap<Module, TrollSettingRow> = IdentityHashMap()
    private var visibleModules: List<Module> by mutableStateOf<List<Module>>(emptyList())
    private var filterStore: String by mutableStateOf<String>("")

    private fun title(): String {
        return if (category == null) EpsilonTranslations.TrollGui.CLIENT.getTranslatedName() else category.name
    }

    fun setSearchFilter(filter: String) {
        if (filterStore == filter) return
        filterStore = filter
        scroll.reset()
    }

    override fun layout(metrics: TextRenderer, screenWidth: Float, screenHeight: Float) {
        var order: Comparator<Module> =
            Comparator.comparing<Module, String>({ it.translatedName }, String.CASE_INSENSITIVE_ORDER)
        if (ClientSetting.moduleSort.`is`(ClientSetting.ModuleSort.EnabledFirst)) {
            order = Comparator.comparing<Module, Boolean> { it.isEnabled }.reversed().thenComparing(order)
        }
        val allModules = ModuleManager.getModules().stream()
            .filter { module -> module.category == category }
            .sorted(order)
            .toList()
        visibleModules = if (filterStore.isEmpty()) {
            allModules
        } else {
            allModules.filter { module -> matchesSearch(module) }
        }
        var width = maxOf(120.0f, metrics.getWidth(title(), TrollTheme.TEXT_SCALE) + 30.0f)
        for (module in allModules) {
            val row = rows.getOrPut(module) {
                val created = TrollSettingRow(module, TrollSettingRow.ModuleAction.TOGGLE)
                created.startOpenAnimation()
                created
            }
            width = maxOf(width, row.minimumWidth(metrics) + TrollTheme.xMargin() * 2.0f)
        }
        headerHeight = TrollTheme.headerHeight(metrics)
        size(minOf(minOf(width, 300.0f), screenWidth), minOf(600.0f, screenHeight), screenWidth, screenHeight)
        val step = TrollTheme.rowHeight(metrics) + TrollTheme.yMargin()
        scrollPosition = scroll.update(maxOf(0.0f, visibleModules.size * step + TrollTheme.yMargin() - viewport.height))
        var y = viewport.y + TrollTheme.yMargin() - scrollPosition
        for (module in visibleModules) {
            rows.getValue(module).layout(UiRect(viewport.x, y, viewport.width, TrollTheme.rowHeight(metrics)))
            y += step
        }
    }

    override fun onOpenAnimationStarted() {
        for (row in rows.values) row.startOpenAnimation()
    }

    override fun allowHeaderOnly(): Boolean {
        return true
    }

    override fun draw(scope: UiTree.Scope, metrics: TextRenderer, mouseX: Int, mouseY: Int) {
        TrollTheme.window(scope, bounds, headerHeight, title(), metrics)
        scope.scissor(viewport) { content ->
            for (module in visibleModules) {
                content.asUiScope(metrics).LuminUIItem(rows.getValue(module)) { rows.getValue(module).draw(tree, metrics, mouseX, mouseY) }
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent): Boolean {
        return super.mouseClicked(event)
    }

    private fun matchesSearch(module: Module): Boolean {
        return normalizeSearchText(module.name).contains(filterStore, ignoreCase = true) ||
                normalizeSearchText(module.translatedName).contains(filterStore, ignoreCase = true)
    }

    private fun normalizeSearchText(value: String): String {
        return value.filterNot { it.isWhitespace() }
    }

}
