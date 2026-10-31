package com.github.epsilon.gui.troll

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingLayoutPlanner
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import java.util.*

class TrollSettingsWindow(
    private val module: Module,
    x: Float,
    y: Float,
) : TrollWindow() {

    private val rows: MutableMap<Setting<*>, TrollSettingRow> = IdentityHashMap()
    private val bindRow: TrollSettingRow = TrollSettingRow(module, TrollSettingRow.ModuleAction.BIND)
    private val hiddenRow: TrollSettingRow = TrollSettingRow(module, TrollSettingRow.ModuleAction.HIDDEN)
    private val entries: MutableList<Entry> = ArrayList()

    init {
        position(x, y)
    }

    override fun layout(metrics: TextRenderer, screenWidth: Float, screenHeight: Float) {
        val previous: List<TrollSettingRow?> = entries.stream()
            .filter { it.row != null }
            .map { it.row }
            .toList()
        entries.clear()
        if (module.category != null) {
            entries.add(Entry(bindRow, null, 0))
            entries.add(Entry(hiddenRow, null, 0))
        }
        for (section in SettingLayoutPlanner.plan(module.name, module.settings)) {
            append(section, 0)
        }
        for (row in previous) {
            if (entries.none { it.row === row }) row!!.resetInput()
        }
        var width = maxOf(180.0f, metrics.getWidth(module.translatedName, TrollTheme.TEXT_SCALE) + 18.0f)
        for (entry in entries) {
            val entryWidth = if (entry.row != null) {
                entry.row.minimumWidth(metrics)
            } else {
                metrics.getWidth(entry.section!!.title(), TrollTheme.TEXT_SCALE) + 30.0f
            }
            width = maxOf(width, entryWidth + entry.depth * 15.0f + TrollTheme.xMargin() * 2.0f)
        }
        headerHeight = TrollTheme.headerHeight(metrics)
        val rowHeight = TrollTheme.rowHeight(metrics)
        val rowGap = TrollTheme.yMargin()
        var contentHeight = rowGap
        for (entry in entries) {
            contentHeight += rowHeight + rowGap + (entry.leadingGroupInsets + entry.closingGroups.size) * GROUP_INSET
        }
        size(
            minOf(minOf(width, 390.0f), screenWidth),
            minOf(minOf(headerHeight + contentHeight, 300.0f), screenHeight),
            screenWidth, screenHeight
        )
        scrollPosition = scroll.update(maxOf(0.0f, contentHeight - viewport.height))
        var cursor = viewport.y + rowGap - scrollPosition
        for (entry in entries) {
            cursor += entry.leadingGroupInsets * GROUP_INSET
            val inset = minOf(entry.depth * 7.5f, viewport.width * 0.25f)
            entry.bounds = UiRect(viewport.x + inset, cursor, viewport.width - inset * 2.0f, rowHeight)
            entry.groupBounds = entry.bounds
            entry.row?.layout(entry.bounds)
            cursor += rowHeight
            for (group in entry.closingGroups) {
                cursor += GROUP_INSET
                group.groupBounds = UiRect(
                    group.bounds.x, group.bounds.y, group.bounds.width,
                    cursor - group.bounds.y
                )
            }
            cursor += rowGap
        }
    }

    private fun append(section: SettingLayoutPlanner.Section, depth: Int) {
        var header: Entry? = null
        var currentDepth = depth
        if (section.hasHeader()) {
            header = Entry(null, section, currentDepth)
            entries.add(header)
            if (section.isCollapsed()) return
            currentDepth++
        }
        val firstChild = entries.size
        for (element in section.elements()) {
            when (element) {
                is SettingLayoutPlanner.Section.SettingElement -> {
                    if (element.setting().isVisible) {
                        val row = rows.getOrPut(element.setting()) {
                            val created = TrollSettingRow(element.setting())
                            created.startOpenAnimation()
                            created
                        }
                        entries.add(Entry(row, null, currentDepth))
                    }
                }

                is SettingLayoutPlanner.Section.GroupElement -> append(element.section(), currentDepth)
                else -> {
                }
            }
        }
        if (header != null && entries.size > firstChild) {
            entries[firstChild].leadingGroupInsets++
            entries.last().closingGroups.add(header)
        }
    }

    override fun draw(scope: UiTree.Scope, metrics: TextRenderer, mouseX: Int, mouseY: Int) {
        TrollTheme.window(scope, bounds, headerHeight, module.translatedName, metrics)
        scope.scissor(viewport) { content ->
            for (entry in entries) {
                if (entry.row != null) {
                    entry.row.draw(content, metrics, mouseX, mouseY)
                } else {
                    val hovered = entry.bounds.contains(mouseX.toDouble(), mouseY.toDouble())
                    content.rect(
                        1, entry.bounds.x, entry.bounds.y, entry.bounds.width, entry.bounds.height,
                        Color(255, 255, 255, if (hovered) 32 else 12)
                    )
                    val label = (if (entry.section!!.isCollapsed()) "+ " else "- ") + entry.section.title()
                    content.textWithShadow(
                        3, TrollTheme.trim(label, metrics, TrollTheme.TEXT_SCALE, entry.bounds.width - 18.0f),
                        entry.bounds.x + 9.0f, entry.bounds.y + 2.25f, TrollTheme.TEXT_SCALE, TrollTheme.text()
                    )
                }
            }
            for (entry in entries) {
                val section = entry.section ?: continue
                val card = entry.groupBounds.inset(0.6f)
                val outline = TrollTheme.alpha(TrollTheme.primary(), 255)
                content.rectOutline(5, card.x, card.y, card.width, card.height, 0.5f, outline)
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent): Boolean {
        if (captureMouseBind(event)) return true
        for (row in allRows()) row.resetInput()
        if (super.mouseClicked(event)) return true
        if (!viewport.contains(event.x(), event.y())) return false
        for (entry in entries.toList()) {
            if (!entry.bounds.contains(event.x(), event.y())) continue
            entry.row?.let { return it.mouseClicked(event) }
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                entry.section!!.toggleCollapsed()
            }
            return true
        }
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent): Boolean {
        if (super.mouseDragged(event)) return true
        for (row in allRows()) {
            if (row.mouseDragged(event)) return true
        }
        return false
    }

    override fun mouseReleased(event: MouseButtonEvent) {
        super.mouseReleased(event)
        for (row in allRows()) row.mouseReleased(event)
    }

    fun startSettingAnimations() {
        for (row in allRows()) row.startOpenAnimation()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        for (row in allRows()) {
            if (row.keyPressed(event)) return true
        }
        return false
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        for (row in allRows()) {
            if (row.charTyped(event)) return true
        }
        return false
    }

    fun captureMouseBind(event: MouseButtonEvent): Boolean {
        for (row in allRows()) {
            if (row.captureMouseBind(event)) return true
        }
        return false
    }

    fun listenForBind() {
        bindRow.listenForBind()
    }

    override fun resetInput() {
        super.resetInput()
        for (row in allRows()) row.resetInput()
    }

    private fun allRows(): List<TrollSettingRow> {
        val result = ArrayList<TrollSettingRow>(rows.values)
        result.add(bindRow)
        result.add(hiddenRow)
        return result
    }

    private class Entry(
        val row: TrollSettingRow?,
        val section: SettingLayoutPlanner.Section?,
        val depth: Int,
    ) {
        val closingGroups: MutableList<Entry> = ArrayList()
        var leadingGroupInsets: Int = 0
        var bounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
        var groupBounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    }

    companion object {
        private const val GROUP_INSET = 6.0f
    }

}
