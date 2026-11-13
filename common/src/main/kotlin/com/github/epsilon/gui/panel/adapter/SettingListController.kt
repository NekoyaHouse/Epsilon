package com.github.epsilon.gui.panel.adapter

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.state.afterLuminCommit
import com.github.epsilon.gui.panel.component.SettingRow
import com.github.epsilon.gui.panel.component.setting.*
import com.github.epsilon.gui.panel.popup.*
import com.github.epsilon.gui.screen.PlatformNoticeScreen
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingLayoutPlanner
import com.github.epsilon.settings.impl.BoolSetting
import java.awt.Color
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

class SettingListController(private val popupHost: PanelPopupHost) : AutoCloseable {

    private val measureTextRenderer: TextRenderer = TextRenderer.create()
    private val rowCache = HashMap<Setting<*>, SettingRow<*>>()
    private val sectionHoverAnimations = HashMap<String, Animation>()
    private val sectionExpandAnimations = HashMap<String, Animation>()

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
        scope: UiScope, mouseX: Int, mouseY: Int,
        callback: RowRenderCallback,
    ) {
        layoutRows(null, settings, viewport, scroll, rowWidth, scope, mouseX, mouseY, callback)
    }

    fun layoutRows(
        ownerKey: String?, settings: List<Setting<*>>, viewport: UiRect, scroll: Float, rowWidth: Float,
        scope: UiScope, mouseX: Int, mouseY: Int,
        callback: RowRenderCallback,
    ) {
        prepareLayout(ownerKey, settings)

        val activeRow = activeEnumRow
        if (activeRow != null && popupHost.activePopup == null) {
            afterLuminCommit { activeRow.setDropdownOpen(false) }
            activeEnumRow = null
        }

        appendRows(ownerKey, settings, viewport, scroll, rowWidth, scope, mouseX, mouseY, callback)
    }

    fun appendRows(
        settings: List<Setting<*>>, viewport: UiRect, scroll: Float, rowWidth: Float,
        scope: UiScope, mouseX: Int, mouseY: Int,
        callback: RowRenderCallback,
    ) {
        appendRows(null, settings, viewport, scroll, rowWidth, scope, mouseX, mouseY, callback)
    }

    fun appendRows(
        ownerKey: String?, settings: List<Setting<*>>, viewport: UiRect, scroll: Float, rowWidth: Float,
        scope: UiScope, mouseX: Int, mouseY: Int,
        callback: RowRenderCallback,
    ) {
        var rowY = viewport.y - scroll
        val labels = ArrayList<HeaderLabels>()
        for (section in buildSections(ownerKey, settings)) {
            if (section.hasHeader()) {
                rowY += scope.appendGroupSection(
                    section, viewport.x, rowY, rowWidth, 0, mouseX, mouseY, callback, labels
                ) + MD3Theme.ROW_GAP
                continue
            }

            for (setting in section.settings()) {
                val row = rowFor(setting) ?: continue
                scope.flushLabels(labels)
                val rowBounds = UiRect(viewport.x, rowY, rowWidth, row.getHeight())
                scope.bindRowInput(row, rowBounds, viewport)
                if (setting !is BoolSetting && !setting.isViaSupported()) {
                    scope.drawDisabledSettingRow(setting, rowBounds)
                } else callback.render(setting, row, rowBounds)
                rowY += row.getHeight() + MD3Theme.ROW_GAP
            }
        }
        scope.flushLabels(labels)
    }

    /**
     * 在行作用域中绘制“当前平台不可用”的提示文案。
     */
    private fun UiScope.drawDisabledSettingRow(setting: Setting<*>, absoluteBounds: UiRect) {
        val local = absoluteBounds.relativeTo(origin)
        Positioned(local.x, local.y, local.width, local.height) {
            Text(
                setting.displayName + " · " + setting.viaRequirement!!.displayName(),
                MD3Theme.ROW_CONTENT_INSET, 5.0f, 0.68f, MD3Theme.TEXT_MUTED
            )
        }
    }

    /**
     * 递归渲染分组卡片：卡片覆盖组头与子内容，嵌套分组在父卡片内容区内继续缩进。
     *
     * @return 该分组卡片的高度
     */
    private fun UiScope.appendGroupSection(
        section: SettingLayoutPlanner.Section,
        x: Float, y: Float, width: Float, depth: Int, mouseX: Int, mouseY: Int,
        callback: RowRenderCallback,
        labels: MutableList<HeaderLabels>,
    ): Float {
        val nest = groupNestInset(depth, width)
        val cardX = x + nest
        val cardWidth = maxOf(1.0f, width - nest * 2.0f)
        val sectionBounds = UiRect(cardX, y, cardWidth, getSectionHeight(section))
        val headerBounds = UiRect(cardX, y, cardWidth, GROUP_HEADER_HEIGHT)
        // 同层折叠组头互不重叠；遇到展开卡片先提交前面的文字，保留父子遮挡顺序。
        if (!section.isCollapsed()) flushLabels(labels)
        labels.add(buildSectionCard(section, sectionBounds, headerBounds, depth, mouseX, mouseY))

        if (section.isCollapsed()) {
            return sectionBounds.height
        }
        flushLabels(labels)

        val innerX = cardX + GROUP_ROW_INSET
        val innerWidth = maxOf(1.0f, cardWidth - GROUP_ROW_INSET * 2.0f)
        var cursor = y + GROUP_HEADER_HEIGHT + GROUP_ROW_INSET
        val childLabels = ArrayList<HeaderLabels>()
        for (element in section.elements()) {
            when (element) {
                is SettingLayoutPlanner.Section.SettingElement -> {
                    val setting = element.setting()
                    val row = rowFor(setting) ?: continue
                    flushLabels(childLabels)
                    val rowBounds = UiRect(innerX, cursor, innerWidth, row.getHeight())
                    bindRowInput(row, rowBounds, sectionBounds)
                    if (setting !is BoolSetting && !setting.isViaSupported()) {
                        drawDisabledSettingRow(setting, rowBounds)
                    } else callback.render(setting, row, rowBounds)
                    cursor += row.getHeight() + MD3Theme.ROW_GAP
                }

                is SettingLayoutPlanner.Section.GroupElement -> {
                    cursor += appendGroupSection(
                        element.section(), innerX, cursor, innerWidth, depth + 1,
                        mouseX, mouseY, callback, childLabels
                    ) + MD3Theme.ROW_GAP
                }
            }
        }
        flushLabels(childLabels)
        return sectionBounds.height
    }

    private fun UiScope.bindRowInput(row: SettingRow<*>, bounds: UiRect, popupBounds: UiRect) {
        InputRegion(
            key = row, bounds = bounds.relativeTo(origin),
            onPreviewPress = { event, _ -> row is KeybindSettingRow && row.bindMouse(event) },
            onPress = { event, double ->
                if (!row.getSetting().isViaSupported()) {
                    PlatformNoticeScreen.show(row.getSetting())
                    true
                } else if (!row.mouseClicked(bounds, event, double)) false
                else {
                    when (row) {
                        is EnumSettingRow -> {
                            popupHost.open(createEnumPopup(row, bounds, popupBounds))
                            row.setDropdownOpen(true)
                            activeEnumRow = row
                        }

                        is ColorSettingRow -> popupHost.open(createColorPopup(row, bounds, popupBounds))
                        is StringListSettingRow -> popupHost.open(createStringListSettingPopup(row, popupBounds))
                        is RegistryListSettingRow -> popupHost.open(createRegistryListSettingPopup(row, popupBounds))
                    }
                    true
                }
            },
            onDrag = { event, _, _ ->
                when (row) {
                    is IntSettingRow -> if (row.isDragging()) row.updateFromMouse(bounds, event.x())
                    is DoubleSettingRow -> if (row.isDragging()) row.updateFromMouse(bounds, event.x())
                }
                true
            }, onRelease = { row.mouseReleased(bounds, it) }, onCancel = row::cancelInput,
            onKey = row::keyPressed, onText = row::charTyped,
            onPreedit = { it != null && row.preeditUpdated(it) }, onBlur = { row.setFocused(false) })
    }


    fun clearFocus() {
        for (row in rowCache.values) {
            row.setFocused(false)
        }
    }

    fun clearAll() {
        clearFocus()
        for (row in rowCache.values) {
            row.close()
        }
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

    private fun UiScope.buildSectionCard(
        section: SettingLayoutPlanner.Section,
        groupBounds: UiRect, headerBounds: UiRect, depth: Int, mouseX: Int, mouseY: Int,
    ): HeaderLabels {
        Interaction(key = section.key() to "header", bounds = headerBounds.relativeTo(origin)) {
            onClick {
                section.toggleCollapsed()
                SoundManager.playInUi(if (section.isCollapsed()) SoundKey.SETTINGS_CLOSE else SoundKey.SETTINGS_OPEN)
                true
            }
        }
        val hoverAnimation = sectionHoverAnimations.getOrPut(section.key()) { createAnimation(120L, 0.0f) }
        val expandAnimation = sectionExpandAnimations.getOrPut(section.key()) {
            createAnimation(180L, if (section.isCollapsed()) 0.0f else 1.0f)
        }
        val hoverProgress = animate(hoverAnimation, headerBounds.contains(mouseX.toDouble(), mouseY.toDouble()))
        val expandProgress = animate(expandAnimation, !section.isCollapsed())

        val localGroup = groupBounds.relativeTo(origin)
        var labels: HeaderLabels? = null
        Positioned(localGroup.x, localGroup.y, localGroup.width, localGroup.height) {
            val localHeader = headerBounds.relativeTo(groupBounds)
            RoundRect(
                0.0f, 0.0f, groupBounds.width, groupBounds.height, MD3Theme.CARD_RADIUS.toFloat(), groupOutline(depth)
            )
            RoundRect(
                GROUP_OUTLINE_INSET,
                GROUP_OUTLINE_INSET,
                groupBounds.width - GROUP_OUTLINE_INSET * 2.0f,
                groupBounds.height - GROUP_OUTLINE_INSET * 2.0f,
                maxOf(1.0f, MD3Theme.CARD_RADIUS - GROUP_OUTLINE_INSET),
                MD3Theme.lerp(groupSurface(depth), MD3Theme.SURFACE_CONTAINER, expandProgress)
            )
            if (hoverProgress > 0.01f) {
                RoundRect(
                    localHeader.x, localHeader.y, localHeader.width, localHeader.height,
                    MD3Theme.CARD_RADIUS.toFloat(),
                    MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hoverProgress, if (MD3Theme.isLightTheme()) 10 else 14)
                )
            }

            val labelScale = 0.66f
            val label = trimToWidth(section.title(), labelScale, headerBounds.width - 74.0f, metrics)
            val labelY = localHeader.y + (GROUP_HEADER_HEIGHT - metrics.textHeight(labelScale)) / 2.0f

            val countLabel = section.totalSettingCount().toString()
            val countScale = 0.46f
            val countWidth = metrics.textWidth(countLabel, countScale) + 10.0f
            val countX = localHeader.x + localHeader.width - MD3Theme.ROW_TRAILING_INSET - 20.0f - countWidth
            val countY = localHeader.y + (GROUP_HEADER_HEIGHT - GROUP_COUNT_CHIP_HEIGHT) / 2.0f
            RoundRect(
                countX, countY, countWidth, GROUP_COUNT_CHIP_HEIGHT, GROUP_COUNT_CHIP_HEIGHT / 2.0f,
                MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 210)
            )

            val chevronSize = 3.0f
            val chevronCenterX = localHeader.x + localHeader.width - MD3Theme.ROW_TRAILING_INSET - chevronSize - 3.0f
            val chevronCenterY = localHeader.y + GROUP_HEADER_HEIGHT / 2.0f
            Triangle(
                chevronCenterX, chevronCenterY, chevronSize, expandProgress,
                MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.PRIMARY, hoverProgress)
            )
            labels = HeaderLabels(
                localGroup, label, localHeader.x + MD3Theme.ROW_CONTENT_INSET + 2.0f, labelY, labelScale, countLabel,
                countX + (countWidth - metrics.textWidth(countLabel, countScale)) / 2.0f,
                countY + (GROUP_COUNT_CHIP_HEIGHT - metrics.textHeight(countScale)) / 2.0f,
                countScale
            )
        }
        return checkNotNull(labels)
    }

    private fun UiScope.flushLabels(labels: MutableList<HeaderLabels>) {
        for (header in labels) {
            val bounds = header.bounds
            Positioned(bounds.x, bounds.y, bounds.width, bounds.height) {
                Text(header.title, header.titleX, header.titleY, header.titleScale, MD3Theme.TEXT_PRIMARY)
                Text(header.count, header.countX, header.countY, header.countScale, MD3Theme.ON_SECONDARY_CONTAINER)
            }
        }
        labels.clear()
    }

    private data class HeaderLabels(
        val bounds: UiRect, val title: String, val titleX: Float, val titleY: Float, val titleScale: Float,
        val count: String, val countX: Float, val countY: Float, val countScale: Float,
    )

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
        val limit = maxOf(0.0f, (availableWidth - GROUP_MIN_WIDTH) * 0.5f)
        val clampedDepth = minOf(maxOf(depth, 0), GROUP_DEPTH_LIMIT)
        return minOf(GROUP_NEST_INSET * clampedDepth, limit)
    }

    /**
     * 分组卡片表面色，层级越深越浅，用于说明 Setting 属于该分组。
     */
    private fun groupSurface(depth: Int): Color {
        if (depth <= 0) {
            return MD3Theme.SURFACE_CONTAINER_LOW
        }
        val clampedDepth = minOf(depth, GROUP_DEPTH_LIMIT)
        val ratio = clampedDepth / GROUP_DEPTH_LIMIT.toFloat() * 0.65f
        return MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER_LOW, MD3Theme.SURFACE_CONTAINER_HIGH, ratio)
    }

    private fun groupOutline(depth: Int): Color {
        if (depth <= 0) {
            return MD3Theme.OUTLINE_SOFT
        }
        val clampedDepth = minOf(depth, GROUP_DEPTH_LIMIT)
        return MD3Theme.withAlpha(MD3Theme.OUTLINE, minOf(160, 96 + 12 * clampedDepth))
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

    private fun trimToWidth(value: String?, scale: Float, width: Float, metrics: UiTextMetrics): String {
        if (value == null || value.isEmpty() || width <= 0.0f) {
            return ""
        }
        if (metrics.textWidth(value, scale) <= width) {
            return value
        }
        val ellipsis = "..."
        val ellipsisWidth = metrics.textWidth(ellipsis, scale)
        if (ellipsisWidth >= width) {
            return ellipsis
        }
        for (length in value.length - 1 downTo 0) {
            val candidate = value.substring(0, length) + ellipsis
            if (metrics.textWidth(candidate, scale) <= width) {
                return candidate
            }
        }
        return ellipsis
    }

    private fun createEnumPopup(enumRow: EnumSettingRow, rowBounds: UiRect, popupBounds: UiRect): EnumSelectPopup {
        val chipBounds = enumRow.getChipBounds(measureTextRenderer.asUiTextMetrics(), rowBounds)
        val optionCount = enumRow.getSetting().modes.size
        val visibleCount = minOf(optionCount, EnumSelectPopup.MAX_VISIBLE_ITEMS)
        val popupHeight = visibleCount * 24.0f + 12.0f
        val popupWidth = maxOf(108.0f, chipBounds.width + 24.0f)
        val popupX = maxOf(popupBounds.x + MD3Theme.PANEL_VIEWPORT_INSET, chipBounds.right() - popupWidth)
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
        val popupX = maxOf(popupBounds.x + MD3Theme.PANEL_VIEWPORT_INSET, swatchBounds.right() - popupWidth)
        var popupY = swatchBounds.bottom() + 4.0f
        val maxBottom = popupBounds.bottom() - MD3Theme.PANEL_VIEWPORT_INSET
        if (popupY + popupHeight > maxBottom) {
            popupY = swatchBounds.y - popupHeight - 4.0f
        }
        return ColorPickerPopup(UiRect(popupX, popupY, popupWidth, popupHeight), swatchBounds, colorRow.getSetting())
    }

    private fun createStringListSettingPopup(row: StringListSettingRow, popupBounds: UiRect): PanelPopupHost.Popup {
        val bounds = popupHost.getCenteredBounds(
            minOf(300.0f, popupBounds.width - 24.0f), minOf(220.0f, popupBounds.height - 24.0f)
        )
        val setting = row.getSetting()
        return StringListSelectPopup(
            bounds,
            setting,
            { value -> setting.add(value) },
            { value -> setting.remove(value) })
    }

    private fun createRegistryListSettingPopup(row: RegistryListSettingRow, popupBounds: UiRect): PanelPopupHost.Popup {
        val bounds = popupHost.getCenteredBounds(
            minOf(360.0f, popupBounds.width - 24.0f), minOf(246.0f, popupBounds.height - 24.0f)
        )
        return RegistryListSelectPopup.create(bounds, row.getSetting())
    }

    fun interface RowRenderCallback {
        fun render(setting: Setting<*>, row: SettingRow<*>, rowBounds: UiRect)
    }

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
