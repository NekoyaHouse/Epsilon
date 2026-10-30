package com.github.epsilon.gui.panel.adapter

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.panel.component.setting.ColorSettingRow
import com.github.epsilon.gui.panel.component.setting.DoubleSettingRow
import com.github.epsilon.gui.panel.component.setting.EnumSettingRow
import com.github.epsilon.gui.panel.component.setting.IntSettingRow
import com.github.epsilon.gui.panel.component.setting.RegistryListSettingRow
import com.github.epsilon.gui.panel.component.setting.StringListSettingRow
import com.github.epsilon.gui.panel.popup.ColorPickerPopup
import com.github.epsilon.gui.panel.popup.EnumSelectPopup
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.popup.RegistryListSelectPopup
import com.github.epsilon.gui.panel.popup.StringListSelectPopup
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingLayoutPlanner
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent
import java.awt.Color
import java.util.ArrayList
import java.util.HashMap

class SettingListController(private val popupHost: PanelPopupHost) : AutoCloseable {

    private val measureTextRenderer: TextRenderer = TextRenderer.create()
    private val rowCache = HashMap<Setting<*>, SettingRow<*>>()
    private val sectionHoverAnimations = HashMap<String, Animation>()
    private val sectionExpandAnimations = HashMap<String, Animation>()
    private val settingEntries = ArrayList<SettingEntry>()
    private val sectionEntries = ArrayList<SectionEntry>()

    private var draggingSliderEntry: SettingEntry? = null
    private var activeEnumRow: EnumSettingRow? = null

    fun getPopupHost(): PanelPopupHost {
        return popupHost
    }

    fun isPopupHovered(mouseX: Int, mouseY: Int): Boolean {
        val popup = popupHost.activePopup ?: return false
        return popup.getBounds().contains(mouseX.toDouble(), mouseY.toDouble())
    }

    fun prepareLayout(settings: List<Setting<*>>) {
        prepareLayout(null, settings)
    }

    fun prepareLayout(ownerKey: String?, settings: List<Setting<*>>) {
        closeRowsNotIn(settings)
        val visibleSections = ArrayList<String>()
        collectSectionKeys(buildSections(ownerKey, settings), visibleSections)
        sectionHoverAnimations.keys.removeIf { key -> !visibleSections.contains(key) }
        sectionExpandAnimations.keys.removeIf { key -> !visibleSections.contains(key) }
        settingEntries.clear()
        sectionEntries.clear()
    }

    fun getContentHeight(settings: List<Setting<*>>): Float {
        return getContentHeight(null, settings)
    }

    fun getContentHeight(ownerKey: String?, settings: List<Setting<*>>): Float {
        if (settings.isEmpty()) {
            return 0.0f
        }

        var height = 0.0f
        for (section in buildSections(ownerKey, settings)) {
            if (section.hasHeader()) {
                height += getSectionHeight(section) + MD3Theme.ROW_GAP
            } else {
                for (setting in section.settings()) {
                    val row = rowFor(setting)
                    if (row != null) {
                        height += row.getHeight() + MD3Theme.ROW_GAP
                    }
                }
            }
        }
        return height
    }

    fun layoutRows(
        settings: List<Setting<*>>, viewport: UiRect, scroll: Float, rowWidth: Float,
        scope: UiTree.Scope, textRenderer: TextRenderer, mouseX: Int, mouseY: Int,
        callback: RowRenderCallback,
    ) {
        layoutRows(null, settings, viewport, scroll, rowWidth, scope, textRenderer, mouseX, mouseY, callback)
    }

    fun layoutRows(
        ownerKey: String?, settings: List<Setting<*>>, viewport: UiRect, scroll: Float, rowWidth: Float,
        scope: UiTree.Scope, textRenderer: TextRenderer, mouseX: Int, mouseY: Int,
        callback: RowRenderCallback,
    ) {
        prepareLayout(ownerKey, settings)

        val activeRow = activeEnumRow
        if (activeRow != null && popupHost.activePopup == null) {
            activeRow.setDropdownOpen(false)
            activeEnumRow = null
        }

        appendRows(ownerKey, settings, viewport, scroll, rowWidth, scope, textRenderer, mouseX, mouseY, callback)
    }

    fun appendRows(
        settings: List<Setting<*>>, viewport: UiRect, scroll: Float, rowWidth: Float,
        scope: UiTree.Scope, textRenderer: TextRenderer, mouseX: Int, mouseY: Int,
        callback: RowRenderCallback,
    ) {
        appendRows(null, settings, viewport, scroll, rowWidth, scope, textRenderer, mouseX, mouseY, callback)
    }

    fun appendRows(
        ownerKey: String?, settings: List<Setting<*>>, viewport: UiRect, scroll: Float, rowWidth: Float,
        scope: UiTree.Scope, textRenderer: TextRenderer, mouseX: Int, mouseY: Int,
        callback: RowRenderCallback,
    ) {
        var rowY = viewport.y - scroll
        for (section in buildSections(ownerKey, settings)) {
            if (section.hasHeader()) {
                rowY += appendGroupSection(
                    scope, textRenderer, section, viewport.x, rowY, rowWidth, 0,
                    mouseX, mouseY, callback
                ) + MD3Theme.ROW_GAP
                continue
            }

            for (setting in section.settings()) {
                val row = rowFor(setting) ?: continue
                val rowBounds = UiRect(viewport.x, rowY, rowWidth, row.getHeight())
                settingEntries.add(SettingEntry(row, rowBounds))
                callback.render(setting, row, rowBounds)
                rowY += row.getHeight() + MD3Theme.ROW_GAP
            }
        }
    }

    /**
     * 递归渲染分组卡片：卡片覆盖组头与子内容，嵌套分组在父卡片内容区内继续缩进。
     *
     * @return 该分组卡片的高度
     */
    private fun appendGroupSection(
        scope: UiTree.Scope, textRenderer: TextRenderer, section: SettingLayoutPlanner.Section,
        x: Float, y: Float, width: Float, depth: Int, mouseX: Int, mouseY: Int,
        callback: RowRenderCallback,
    ): Float {
        val nest = groupNestInset(depth, width)
        val cardX = x + nest
        val cardWidth = Math.max(1.0f, width - nest * 2.0f)
        val sectionBounds = UiRect(cardX, y, cardWidth, getSectionHeight(section))
        val headerBounds = UiRect(cardX, y, cardWidth, GROUP_HEADER_HEIGHT)
        sectionEntries.add(SectionEntry(section, headerBounds))
        buildSectionCard(scope, textRenderer, section, sectionBounds, headerBounds, depth, mouseX, mouseY)

        if (section.isCollapsed()) {
            return sectionBounds.height
        }

        val innerX = cardX + GROUP_ROW_INSET
        val innerWidth = Math.max(1.0f, cardWidth - GROUP_ROW_INSET * 2.0f)
        var cursor = y + GROUP_HEADER_HEIGHT + GROUP_ROW_INSET
        for (element in section.elements()) {
            when (element) {
                is SettingLayoutPlanner.Section.SettingElement -> {
                    val setting = element.setting()
                    val row = rowFor(setting) ?: continue
                    val rowBounds = UiRect(innerX, cursor, innerWidth, row.getHeight())
                    settingEntries.add(SettingEntry(row, rowBounds))
                    callback.render(setting, row, rowBounds)
                    cursor += row.getHeight() + MD3Theme.ROW_GAP
                }

                is SettingLayoutPlanner.Section.GroupElement -> {
                    cursor += appendGroupSection(
                        scope, textRenderer, element.section(), innerX, cursor, innerWidth, depth + 1,
                        mouseX, mouseY, callback
                    ) + MD3Theme.ROW_GAP
                }
            }
        }
        return sectionBounds.height
    }

    fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean, popupBounds: UiRect): Boolean {
        return mouseClicked(event, isDoubleClick, popupBounds, null)
    }

    fun mouseClicked(
        event: MouseButtonEvent, isDoubleClick: Boolean, popupBounds: UiRect, interceptor: RowClickInterceptor?,
    ): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }

        clearFocus()
        for (entry in sectionEntries) {
            if (entry.bounds.contains(event.x(), event.y())) {
                entry.section.toggleCollapsed()
                draggingSliderEntry = null
                SoundManager.INSTANCE.playInUi(
                    if (entry.section.isCollapsed()) SoundKey.SETTINGS_CLOSE else SoundKey.SETTINGS_OPEN
                )
                return true
            }
        }

        for (entry in settingEntries) {
            if (interceptor != null && interceptor.handle(entry.row, entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = null
                return true
            }
            val intRow = entry.row as? IntSettingRow
            if (intRow != null && intRow.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = if (intRow.isDragging()) entry else null
                return true
            }
            val doubleRow = entry.row as? DoubleSettingRow
            if (doubleRow != null && doubleRow.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = if (doubleRow.isDragging()) entry else null
                return true
            }
            val enumRow = entry.row as? EnumSettingRow
            if (enumRow != null && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createEnumPopup(enumRow, entry.bounds, popupBounds))
                enumRow.setDropdownOpen(true)
                val previous = activeEnumRow
                if (previous != null && previous !== enumRow) {
                    previous.setDropdownOpen(false)
                }
                activeEnumRow = enumRow
                draggingSliderEntry = null
                return true
            }
            val colorRow = entry.row as? ColorSettingRow
            if (colorRow != null && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createColorPopup(colorRow, entry.bounds, popupBounds))
                draggingSliderEntry = null
                return true
            }
            val stringListRow = entry.row as? StringListSettingRow
            if (stringListRow != null && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                val popup = createStringListSettingPopup(stringListRow, popupBounds)
                if (popup != null) popupHost.open(popup)
                draggingSliderEntry = null
                return true
            }
            val registryRow = entry.row as? RegistryListSettingRow
            if (registryRow != null && entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                popupHost.open(createRegistryListSettingPopup(registryRow, popupBounds))
                draggingSliderEntry = null
                return true
            }
            if (entry.row.mouseClicked(entry.bounds, event, isDoubleClick)) {
                draggingSliderEntry = null
                return true
            }
        }

        draggingSliderEntry = null
        return false
    }

    fun mouseReleased(event: MouseButtonEvent): Boolean {
        val dragging = draggingSliderEntry ?: return false

        dragging.row.mouseReleased(dragging.bounds, event)
        draggingSliderEntry = null
        return true
    }

    fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        val dragging = draggingSliderEntry
        if (dragging == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        val intRow = dragging.row as? IntSettingRow
        if (intRow != null) {
            intRow.updateFromMouse(dragging.bounds, event.x())
            return true
        }
        val doubleRow = dragging.row as? DoubleSettingRow
        if (doubleRow != null) {
            doubleRow.updateFromMouse(dragging.bounds, event.x())
            return true
        }
        return false
    }

    fun keyPressed(event: KeyEvent): Boolean {
        for (entry in settingEntries) {
            if (entry.row.keyPressed(event)) {
                return true
            }
        }
        return false
    }

    fun charTyped(event: CharacterEvent): Boolean {
        for (entry in settingEntries) {
            if (entry.row.charTyped(event)) {
                return true
            }
        }
        return false
    }

    fun preeditUpdated(event: PreeditEvent): Boolean {
        for (entry in settingEntries) {
            if (entry.row.preeditUpdated(event)) {
                return true
            }
        }
        return false
    }

    fun clearFocus() {
        draggingSliderEntry = null
        for (row in rowCache.values) {
            row.setFocused(false)
        }
    }

    fun clearAll() {
        clearFocus()
        for (row in rowCache.values) {
            row.close()
        }
        settingEntries.clear()
        rowCache.clear()
        sectionHoverAnimations.clear()
        sectionExpandAnimations.clear()
        val activeRow = activeEnumRow
        if (activeRow != null) {
            activeRow.setDropdownOpen(false)
            activeEnumRow = null
        }
    }

    fun resetTransientState() {
        clearFocus()
        val activeRow = activeEnumRow
        if (activeRow != null) {
            activeRow.setDropdownOpen(false)
            activeEnumRow = null
        }
    }

    override fun close() {
        clearAll()
        measureTextRenderer.close()
    }

    fun hasActiveAnimations(): Boolean {
        return sectionHoverAnimations.values.any { animation -> !animation.isFinished } ||
                sectionExpandAnimations.values.any { animation -> !animation.isFinished }
    }

    private fun buildSectionCard(
        scope: UiTree.Scope, textRenderer: TextRenderer, section: SettingLayoutPlanner.Section,
        groupBounds: UiRect, headerBounds: UiRect, depth: Int, mouseX: Int, mouseY: Int,
    ) {
        val hoverAnimation = sectionHoverAnimations.getOrPut(section.key()) { createAnimation(120L, 0.0f) }
        val expandAnimation = sectionExpandAnimations.getOrPut(section.key()) {
            createAnimation(180L, if (section.isCollapsed()) 0.0f else 1.0f)
        }
        val hoverProgress = scope.animate(hoverAnimation, headerBounds.contains(mouseX.toDouble(), mouseY.toDouble()))
        val expandProgress = scope.animate(expandAnimation, !section.isCollapsed())

        scope.pushAbsolute(groupBounds) { group ->
            val localHeader = headerBounds.relativeTo(groupBounds)
            group.roundRect(
                0.0f, 0.0f, groupBounds.width, groupBounds.height, MD3Theme.CARD_RADIUS.toFloat(), groupOutline(depth)
            )
            group.roundRect(
                GROUP_OUTLINE_INSET,
                GROUP_OUTLINE_INSET,
                groupBounds.width - GROUP_OUTLINE_INSET * 2.0f,
                groupBounds.height - GROUP_OUTLINE_INSET * 2.0f,
                Math.max(1.0f, MD3Theme.CARD_RADIUS - GROUP_OUTLINE_INSET),
                MD3Theme.lerp(groupSurface(depth), MD3Theme.SURFACE_CONTAINER, expandProgress)
            )
            if (hoverProgress > 0.01f) {
                group.roundRect(
                    localHeader.x, localHeader.y, localHeader.width, localHeader.height,
                    MD3Theme.CARD_RADIUS.toFloat(),
                    MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, if (MD3Theme.isLightTheme()) 10 else 14)
                )
            }

            val labelScale = 0.66f
            val label = trimToWidth(section.title(), labelScale, headerBounds.width - 74.0f, textRenderer)
            val labelY = localHeader.y + (GROUP_HEADER_HEIGHT - textRenderer.getHeight(labelScale)) / 2.0f
            group.text(
                label, localHeader.x + MD3Theme.ROW_CONTENT_INSET + 2.0f, labelY, labelScale, MD3Theme.TEXT_PRIMARY
            )

            val countLabel = section.totalSettingCount().toString()
            val countScale = 0.46f
            val countWidth = textRenderer.getWidth(countLabel, countScale) + 10.0f
            val countX = localHeader.x + localHeader.width - MD3Theme.ROW_TRAILING_INSET - 20.0f - countWidth
            val countY = localHeader.y + (GROUP_HEADER_HEIGHT - GROUP_COUNT_CHIP_HEIGHT) / 2.0f
            group.roundRect(
                countX, countY, countWidth, GROUP_COUNT_CHIP_HEIGHT, GROUP_COUNT_CHIP_HEIGHT / 2.0f,
                MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 210)
            )
            group.text(
                countLabel,
                countX + (countWidth - textRenderer.getWidth(countLabel, countScale)) / 2.0f,
                countY + (GROUP_COUNT_CHIP_HEIGHT - textRenderer.getHeight(countScale)) / 2.0f,
                countScale,
                MD3Theme.ON_SECONDARY_CONTAINER
            )

            val chevronSize = 3.0f
            val chevronCenterX = localHeader.x + localHeader.width - MD3Theme.ROW_TRAILING_INSET - chevronSize - 3.0f
            val chevronCenterY = localHeader.y + GROUP_HEADER_HEIGHT / 2.0f
            group.triangle(
                chevronCenterX, chevronCenterY, chevronSize, expandProgress,
                MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.PRIMARY, hoverProgress)
            )
        }
    }

    private fun buildSections(ownerKey: String?, settings: List<Setting<*>>): List<SettingLayoutPlanner.Section> {
        if (settings.isEmpty()) {
            return emptyList()
        }
        return if (ownerKey == null || ownerKey.isBlank()) {
            SettingLayoutPlanner.plan(settings)
        } else {
            SettingLayoutPlanner.plan(ownerKey, settings)
        }
    }

    private fun getSectionHeight(section: SettingLayoutPlanner.Section): Float {
        if (!section.hasHeader()) {
            return 0.0f
        }
        if (section.isCollapsed()) {
            return GROUP_HEADER_HEIGHT
        }

        var height = GROUP_HEADER_HEIGHT + GROUP_ROW_INSET * 2.0f
        for (element in section.elements()) {
            when (element) {
                is SettingLayoutPlanner.Section.SettingElement -> {
                    val row = rowFor(element.setting())
                    if (row != null) {
                        height += row.getHeight() + MD3Theme.ROW_GAP
                    }
                }

                is SettingLayoutPlanner.Section.GroupElement -> {
                    height += getSectionHeight(element.section()) + MD3Theme.ROW_GAP
                }
            }
        }
        return height
    }

    /**
     * 收集 section 树中所有分组 key，用于清理失效的悬浮/展开动画。
     */
    private fun collectSectionKeys(sections: List<SettingLayoutPlanner.Section>, output: MutableList<String>) {
        for (section in sections) {
            if (!section.hasHeader()) {
                continue
            }
            output.add(section.key())
            collectSectionKeys(section.children(), output)
        }
    }

    /**
     * 嵌套层级缩进；达到上限后不再增加，并在宽度不足时收敛，避免内容宽度塌缩。
     */
    private fun groupNestInset(depth: Int, availableWidth: Float): Float {
        val limit = Math.max(0.0f, (availableWidth - GROUP_MIN_WIDTH) * 0.5f)
        val clampedDepth = Math.min(Math.max(depth, 0), GROUP_DEPTH_LIMIT)
        return Math.min(GROUP_NEST_INSET * clampedDepth, limit)
    }

    /**
     * 分组卡片表面色，层级越深越浅，用于说明 Setting 属于该分组。
     */
    private fun groupSurface(depth: Int): Color {
        if (depth <= 0) {
            return MD3Theme.SURFACE_CONTAINER_LOW
        }
        val clampedDepth = Math.min(depth, GROUP_DEPTH_LIMIT)
        val ratio = clampedDepth / GROUP_DEPTH_LIMIT.toFloat() * 0.65f
        return MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_LOW, MD3Theme.SURFACE_CONTAINER_HIGH, ratio)
    }

    private fun groupOutline(depth: Int): Color {
        if (depth <= 0) {
            return MD3Theme.OUTLINE_SOFT
        }
        val clampedDepth = Math.min(depth, GROUP_DEPTH_LIMIT)
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, Math.min(160, 96 + 12 * clampedDepth))
    }

    private fun createAnimation(duration: Long, startValue: Float): Animation {
        val animation = Animation(Easing.EASE_OUT_CUBIC, duration)
        animation.startValue = startValue
        return animation
    }

    /**
     * 等价于 Java 的 `rowCache.computeIfAbsent(setting, SettingViewFactory::create)`：
     * 工厂返回 null 时不写缓存。
     */
    private fun rowFor(setting: Setting<*>?): SettingRow<*>? {
        if (setting == null) {
            return null
        }
        val cached = rowCache[setting]
        if (cached != null) {
            return cached
        }
        val created = SettingViewFactory.create(setting)
        if (created != null) {
            rowCache[setting] = created
        }
        return created
    }

    private fun closeRowsNotIn(settings: List<Setting<*>>?) {
        val iterator = rowCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val remove = settings == null || !settings.contains(entry.key)
            if (remove) {
                entry.value.close()
                iterator.remove()
            }
        }
    }

    private fun trimToWidth(value: String?, scale: Float, width: Float, textRenderer: TextRenderer): String {
        if (value == null || value.isEmpty() || width <= 0.0f) {
            return ""
        }
        if (textRenderer.getWidth(value, scale) <= width) {
            return value
        }
        val ellipsis = "..."
        val ellipsisWidth = textRenderer.getWidth(ellipsis, scale)
        if (ellipsisWidth >= width) {
            return ellipsis
        }
        for (length in value.length - 1 downTo 0) {
            val candidate = value.substring(0, length) + ellipsis
            if (textRenderer.getWidth(candidate, scale) <= width) {
                return candidate
            }
        }
        return ellipsis
    }

    private fun createEnumPopup(enumRow: EnumSettingRow, rowBounds: UiRect, popupBounds: UiRect): EnumSelectPopup {
        val chipBounds = enumRow.getChipBounds(measureTextRenderer, rowBounds)
        val optionCount = enumRow.getSetting().getModes().size
        val visibleCount = Math.min(optionCount, EnumSelectPopup.MAX_VISIBLE_ITEMS)
        val popupHeight = visibleCount * 24.0f + 12.0f
        val popupWidth = Math.max(108.0f, chipBounds.width + 24.0f)
        val popupX = Math.max(popupBounds.x + MD3Theme.PANEL_VIEWPORT_INSET, chipBounds.right() - popupWidth)
        var popupY = chipBounds.bottom() + 4.0f
        val maxBottom = popupBounds.bottom() - MD3Theme.PANEL_VIEWPORT_INSET
        if (popupY + popupHeight > maxBottom) {
            popupY = chipBounds.y - popupHeight - 4.0f
        }
        return EnumSelectPopup(UiRect(popupX, popupY, popupWidth, popupHeight), enumRow.getSetting())
    }

    private fun createColorPopup(colorRow: ColorSettingRow, rowBounds: UiRect, popupBounds: UiRect): ColorPickerPopup {
        val swatchBounds = colorRow.getSwatchBounds(rowBounds)
        val channelCount = if (colorRow.getSetting().isAllowAlpha()) 4 else 3
        val popupWidth = 156.0f
        val popupHeight = 58.0f + channelCount * 24.0f
        val popupX = Math.max(popupBounds.x + MD3Theme.PANEL_VIEWPORT_INSET, swatchBounds.right() - popupWidth)
        var popupY = swatchBounds.bottom() + 4.0f
        val maxBottom = popupBounds.bottom() - MD3Theme.PANEL_VIEWPORT_INSET
        if (popupY + popupHeight > maxBottom) {
            popupY = swatchBounds.y - popupHeight - 4.0f
        }
        return ColorPickerPopup(UiRect(popupX, popupY, popupWidth, popupHeight), swatchBounds, colorRow.getSetting())
    }

    private fun createStringListSettingPopup(row: StringListSettingRow, popupBounds: UiRect): PanelPopupHost.Popup {
        val bounds = popupHost.getCenteredBounds(
            Math.min(300.0f, popupBounds.width - 24.0f), Math.min(220.0f, popupBounds.height - 24.0f)
        )
        val setting = row.getSetting()
        return StringListSelectPopup(bounds, setting, { value -> setting.add(value) }, { value -> setting.remove(value) })
    }

    private fun createRegistryListSettingPopup(row: RegistryListSettingRow, popupBounds: UiRect): PanelPopupHost.Popup {
        val bounds = popupHost.getCenteredBounds(
            Math.min(360.0f, popupBounds.width - 24.0f), Math.min(246.0f, popupBounds.height - 24.0f)
        )
        return RegistryListSelectPopup.create(bounds, row.getSetting())
    }

    fun interface RowRenderCallback {
        fun render(setting: Setting<*>, row: SettingRow<*>, rowBounds: UiRect)
    }

    fun interface RowClickInterceptor {
        fun handle(row: SettingRow<*>, rowBounds: UiRect, event: MouseButtonEvent, isDoubleClick: Boolean): Boolean
    }

    private class SettingEntry(val row: SettingRow<*>, val bounds: UiRect)

    private class SectionEntry(val section: SettingLayoutPlanner.Section, val bounds: UiRect)

    private companion object {

        private const val GROUP_HEADER_HEIGHT = 30.0f
        private const val GROUP_ROW_INSET = 4.0f
        private const val GROUP_NEST_INSET = 8.0f
        private const val GROUP_MIN_WIDTH = 72.0f
        private const val GROUP_DEPTH_LIMIT = 3
        private const val GROUP_OUTLINE_INSET = 1.0f
        private const val GROUP_COUNT_CHIP_HEIGHT = 14.0f

    }

}
