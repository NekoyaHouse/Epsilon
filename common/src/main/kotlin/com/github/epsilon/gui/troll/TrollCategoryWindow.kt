package com.github.epsilon.gui.troll

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.managers.ModuleManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.ClientSetting
import net.minecraft.client.input.MouseButtonEvent
import java.util.Comparator
import java.util.IdentityHashMap

class TrollCategoryWindow(private val category: Category?) : TrollWindow() {

    private val rows: MutableMap<Module, TrollSettingRow> = IdentityHashMap()
    private var visibleModules: List<Module> = emptyList()

    private fun title(): String {
        return if (category == null) EpsilonTranslations.TrollGui.CLIENT.translatedName else category.getName()
    }

    override fun layout(metrics: TextRenderer, screenWidth: Float, screenHeight: Float) {
        var order: Comparator<Module> =
            Comparator.comparing<Module, String>({ it.translatedName }, String.CASE_INSENSITIVE_ORDER)
        if (ClientSetting.moduleSort.`is`(ClientSetting.ModuleSort.EnabledFirst)) {
            order = Comparator.comparing<Module, Boolean> { it.isEnabled }.reversed().thenComparing(order)
        }
        visibleModules = ModuleManager.INSTANCE.modules.stream()
            .filter { module -> module.category == category }
            .sorted(order)
            .toList()
        var width = Math.max(120.0f, metrics.getWidth(title(), TrollTheme.TEXT_SCALE) + 30.0f)
        for (module in visibleModules) {
            val row = rows.getOrPut(module) {
                TrollSettingRow(module, TrollSettingRow.ModuleAction.TOGGLE)
            }
            width = Math.max(width, row.minimumWidth(metrics) + TrollTheme.xMargin() * 2.0f)
        }
        headerHeight = TrollTheme.headerHeight(metrics)
        size(Math.min(Math.min(width, 300.0f), screenWidth), Math.min(600.0f, screenHeight), screenWidth, screenHeight)
        val step = TrollTheme.rowHeight(metrics) + TrollTheme.yMargin()
        scrollPosition = scroll.update(Math.max(0.0f, visibleModules.size * step + TrollTheme.yMargin() - viewport.height))
        var y = viewport.y + TrollTheme.yMargin() - scrollPosition
        for (module in visibleModules) {
            rows.getValue(module).layout(UiRect(viewport.x, y, viewport.width, TrollTheme.rowHeight(metrics)))
            y += step
        }
    }

    override fun allowHeaderOnly(): Boolean {
        return true
    }

    override fun draw(scope: UiTree.Scope, metrics: TextRenderer, mouseX: Int, mouseY: Int) {
        TrollTheme.window(scope, bounds, headerHeight, title(), metrics)
        scope.scissor(viewport) { content ->
            for (module in visibleModules) {
                rows.getValue(module).draw(content, metrics, mouseX, mouseY)
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent): Boolean {
        if (super.mouseClicked(event)) return true
        if (!viewport.contains(event.x(), event.y())) return false
        for (module in visibleModules) {
            if (rows.getValue(module).mouseClicked(event)) return true
        }
        return true
    }

}
