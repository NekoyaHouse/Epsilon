package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.dropdown.widget.SettingWidget
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.screen.PlatformNoticeScreen
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingLayoutPlanner
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

/**
 * Dropdown 侧 Setting section 树的共用渲染器。
 *
 * 负责把 [SettingLayoutPlanner.Section] 树包装成控件树，并统一处理嵌套分组的
 * 高度、卡片背景、缩进、展开动画与作用域内输入订阅；`SettingsContent` 与
 * `ModuleButton` 只提供控件工厂和坐标原点。
 *
 * 绘制坐标使用调用方 scope 的局部坐标，命中测试使用「局部坐标 + scope.origin」得到的绝对坐标，
 * 因此控件缓存的绝对位置可以直接参与命中。
 */
class SettingSectionRenderer private constructor(nodes: List<Node>) {

    private val nodes: List<Node> = nodes.toList()
    private val hoverAnimations: MutableMap<String, Animation> = HashMap()
    private val expandAnimations: MutableMap<String, Animation> = HashMap()

    fun isEmpty(): Boolean {
        return nodes.none { it.hasVisibleSetting() }
    }

    /**
     * 返回所有 section 的高度之和（不含宿主自行添加的起始间距）。
     */
    fun height(): Float {
        var height = 0.0f
        for (node in nodes) {
            height += nodeHeight(node)
        }
        return height
    }

    fun draw(
        scope: UiScope, mouseX: Int, mouseY: Int,
        x: Float, y: Float, width: Float,
    ) {
        val sectionX = x + DropdownTheme.SETTING_INDENT
        val sectionWidth = sectionWidth(width)
        var cursor = y
        with(scope) {
            val labels = ArrayList<HeaderLabels>()
            for (node in nodes) {
                val height = nodeHeight(node)
                if (height == 0.0f) {
                    continue
                }
                drawNode(mouseX, mouseY, node, 0, sectionX, cursor, sectionWidth, labels)
                cursor += height
            }
            flushLabels(labels)
        }
    }


    // --- 高度 ---

    private fun nodeHeight(node: Node): Float {
        if (!node.hasVisibleSetting()) {
            return 0.0f
        }
        if (!node.hasHeader()) {
            return elementsHeight(node)
        }
        val progress = expandProgress(node)
        if (progress == 0.0f) return DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP
        val contentHeight = DropdownTheme.GROUP_INSET * 2.0f + elementsHeight(node)
        return DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP + contentHeight * progress
    }

    private fun elementsHeight(node: Node): Float {
        var height = 0.0f
        for (element in node.elements) {
            when (element) {
                is WidgetElement -> {
                    val widget = element.widget
                    if (widget.isVisible()) {
                        height += widget.getHeight() + DropdownTheme.SETTING_GAP
                    }
                }

                is ChildElement -> height += nodeHeight(element.node)
            }
        }
        return height
    }

    // --- 绘制 ---

    private fun UiScope.drawNode(
        mouseX: Int, mouseY: Int,
        node: Node, depth: Int, x: Float, y: Float, width: Float,
        labels: MutableList<HeaderLabels>,
    ) {
        if (!node.hasHeader()) {
            flushLabels(labels)
            drawElements(mouseX, mouseY, node, depth, x, y, width)
            return
        }

        val cardBounds = card(node, depth, x, width)
        val progress = expandProgress(node)
        // 只跨越互不重叠的折叠组头合批；展开卡片与内容保留独立的绘制边界。
        if (progress > 0.0f) flushLabels(labels)
        val contentHeight = if (progress == 0.0f) 0.0f else DropdownTheme.GROUP_INSET * 2.0f + elementsHeight(node)
        Interaction(key = node, bounds = UiRect(cardBounds.x, y, cardBounds.width, DropdownTheme.GROUP_HEADER_HEIGHT)) {
            onClick { toggleCollapsed(node); true }
        }
        val cardHeight = DropdownTheme.GROUP_HEADER_HEIGHT + (DropdownTheme.SETTING_GAP + contentHeight) * progress
        val radius = DropdownTheme.groupCardRadius(progress)

        RoundRect(cardBounds.x, y, cardBounds.width, cardHeight, radius, DropdownTheme.groupCardBackground(depth))
        Outline(
            cardBounds.x,
            y,
            cardBounds.width,
            cardHeight,
            radius,
            0.8f,
            DropdownTheme.groupCardOutline(depth)
        )

        val hoverAnimation = hoverAnimations.getOrPut(node.key()) { createAnimation(DropdownTheme.ANIM_HOVER, 0.0f) }
        val hovered = isHovered(
            mouseX.toDouble(), mouseY.toDouble(), origin.x + cardBounds.x, origin.y + y,
            cardBounds.width, DropdownTheme.GROUP_HEADER_HEIGHT
        )
        hoverAnimation.run(if (hovered) 1.0f else 0.0f)
        val hoverProgress = hoverAnimation.value
        if (hoverProgress > 0.01f) {
            RoundRect(
                cardBounds.x, y, cardBounds.width, DropdownTheme.GROUP_HEADER_HEIGHT, radius,
                DropdownTheme.groupHeaderHover(hoverProgress)
            )
        }

        val label = trimToWidth(
            node.title(), DropdownTheme.GROUP_HEADER_TEXT_SCALE,
            cardBounds.width - 74.0f, metrics
        )
        val labelY =
            y + (DropdownTheme.GROUP_HEADER_HEIGHT - metrics.textHeight(DropdownTheme.GROUP_HEADER_TEXT_SCALE)) * 0.5f

        val countLabel = node.totalSettingCount().toString()
        val countWidth = metrics.textWidth(
            countLabel,
            DropdownTheme.GROUP_COUNT_TEXT_SCALE
        ) + DropdownTheme.GROUP_COUNT_CHIP_PADDING * 2.0f
        val countX = cardBounds.x + cardBounds.width - DropdownTheme.SETTING_PADDING_X - countWidth - 12.0f
        val chipHeight = DropdownTheme.GROUP_COUNT_CHIP_HEIGHT
        val countY = y + (DropdownTheme.GROUP_HEADER_HEIGHT - chipHeight) * 0.5f
        RoundRect(countX, countY, countWidth, chipHeight, chipHeight / 2.0f, DropdownTheme.groupCountChip())
        val countTextY = countY + (chipHeight - metrics.textHeight(DropdownTheme.GROUP_COUNT_TEXT_SCALE)) * 0.5f

        val chevronSize = 2.5f
        Triangle(
            cardBounds.x + cardBounds.width - DropdownTheme.SETTING_PADDING_X - chevronSize,
            y + DropdownTheme.GROUP_HEADER_HEIGHT * 0.5f, chevronSize, progress,
            DropdownTheme.groupChevron(hoverProgress)
        )
        labels.add(
            HeaderLabels(
                label, cardBounds.x + DropdownTheme.SETTING_PADDING_X, labelY,
                countLabel, countX + DropdownTheme.GROUP_COUNT_CHIP_PADDING, countTextY
            )
        )
        if (progress > 0.0f) flushLabels(labels)

        if (progress > 0.001f) {
            val contentY = y + DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP
            ClipIf(
                progress < 1.0f, cardBounds.x, contentY, cardBounds.width,
                contentHeight * progress + DropdownTheme.SETTING_GAP
            ) {
                val innerX = cardBounds.x + DropdownTheme.GROUP_INSET
                val innerWidth = maxOf(1.0f, cardBounds.width - DropdownTheme.GROUP_INSET * 2.0f)
                drawElements(
                    mouseX, mouseY, node, depth + 1, innerX,
                    contentY + DropdownTheme.GROUP_INSET, innerWidth
                )
            }
        }
    }

    private fun UiScope.drawElements(
        mouseX: Int, mouseY: Int,
        node: Node, depth: Int, x: Float, y: Float, width: Float,
    ) {
        var cursor = y
        val labels = ArrayList<HeaderLabels>()
        for (element in node.elements) {
            when (element) {
                is WidgetElement -> {
                    val widget = element.widget
                    if (!widget.isVisible()) {
                        continue
                    }
                    flushLabels(labels)
                    val widgetHeight = widget.getHeight()
                    val widgetBounds = UiRect(x, cursor, width, widgetHeight)
                    if (widget.setting !is BoolSetting && !widget.setting.isViaSupported()) {
                        LuminUIItem(widget) {
                            Positioned(widgetBounds.x, widgetBounds.y, widgetBounds.width, widgetBounds.height) {
                                Interaction(key = widget to "unsupported") {
                                    onClick { PlatformNoticeScreen.show(widget.setting); true }
                                }
                                Text(
                                    widget.setting.displayName + " · " + widget.setting.viaRequirement!!.displayName(),
                                    DropdownTheme.SETTING_PADDING_X,
                                    3.0f,
                                    DropdownTheme.SETTING_TEXT_SCALE,
                                    MD3Theme.TEXT_MUTED
                                )
                            }
                        }
                    } else {
                        // 逐项缓存：设置值、悬停状态或控件动画变化时才重新绘制该控件。
                        LuminUIItem(widget) {
                            widget.drawInScope(this, mouseX, mouseY, widgetBounds)
                        }
                    }
                    cursor += widgetHeight + DropdownTheme.SETTING_GAP
                }

                is ChildElement -> {
                    val child = element.node
                    val childHeight = nodeHeight(child)
                    if (childHeight == 0.0f) {
                        continue
                    }
                    drawNode(mouseX, mouseY, child, depth, x, cursor, width, labels)
                    cursor += childHeight
                }
            }
        }
        // 队列属于当前裁剪作用域，不能把文字带出展开动画的 ClipIf。
        flushLabels(labels)
    }

    private fun UiScope.flushLabels(labels: MutableList<HeaderLabels>) {
        for (header in labels) {
            Text(
                header.title, header.titleX, header.titleY,
                DropdownTheme.GROUP_HEADER_TEXT_SCALE, DropdownTheme.groupText()
            )
            Text(
                header.count, header.countX, header.countY,
                DropdownTheme.GROUP_COUNT_TEXT_SCALE, DropdownTheme.groupCountText()
            )
        }
        labels.clear()
    }

    // --- 命中 ---


    private fun toggleCollapsed(node: Node) {
        node.toggleCollapsed()
        SoundManager.playInUi(if (node.isCollapsed()) SoundKey.SETTINGS_CLOSE else SoundKey.SETTINGS_OPEN)
        DropdownScreen.react(
            if (node.isCollapsed()) ReisaDropdownCompanion.Action.PANEL_CLOSE else ReisaDropdownCompanion.Action.PANEL_OPEN
        )
    }


    // --- 布局工具 ---

    private fun expandProgress(node: Node): Float {
        val animation = expandAnimations.getOrPut(node.key()) {
            createAnimation(DropdownTheme.ANIM_GROUP, if (node.isCollapsed()) 0.0f else 1.0f)
        }
        animation.run(if (node.isCollapsed()) 0.0f else 1.0f)
        return animation.value
    }

    private fun sectionWidth(width: Float): Float {
        return maxOf(1.0f, width - DropdownTheme.SETTING_INDENT * 2.0f)
    }

    private fun card(node: Node, depth: Int, x: Float, width: Float): Card {
        if (!node.hasHeader()) {
            return Card(x, width)
        }
        val nest = DropdownTheme.groupNestInset(depth, width)
        return Card(x + nest, maxOf(1.0f, width - nest * 2.0f))
    }

    private fun isHovered(mouseX: Double, mouseY: Double, x: Float, y: Float, width: Float, height: Float): Boolean {
        return UiRect(x, y, width, height).contains(mouseX, mouseY)
    }

    private fun trimToWidth(value: String?, scale: Float, maxWidth: Float, textMetrics: UiTextMetrics): String {
        if (value == null || value.isEmpty() || maxWidth <= 0.0f) return ""
        if (textMetrics.textWidth(value, scale) <= maxWidth) return value
        val ellipsis = "..."
        val ellipsisWidth = textMetrics.textWidth(ellipsis, scale)
        if (ellipsisWidth >= maxWidth) return ellipsis
        for (length in value.length - 1 downTo 0) {
            val candidate = value.substring(0, length) + ellipsis
            if (textMetrics.textWidth(candidate, scale) <= maxWidth) return candidate
        }
        return ellipsis
    }

    private data class Card(val x: Float, val width: Float)

    private data class HeaderLabels(
        val title: String, val titleX: Float, val titleY: Float,
        val count: String, val countX: Float, val countY: Float,
    )

    private sealed interface Element

    private data class WidgetElement(val widget: SettingWidget<*>) : Element

    private data class ChildElement(val node: Node) : Element

    private class Node(model: SettingLayoutPlanner.Section, elements: List<Element>) {

        private val model: SettingLayoutPlanner.Section = model
        val elements: List<Element> = elements.toList()

        fun key(): String {
            return model.key()
        }

        fun title(): String {
            return model.title()
        }

        fun hasHeader(): Boolean {
            return model.hasHeader()
        }

        fun isCollapsed(): Boolean {
            return model.isCollapsed()
        }

        fun toggleCollapsed() {
            model.toggleCollapsed()
        }

        fun totalSettingCount(): Int {
            return model.totalSettingCount()
        }

        fun hasVisibleSetting(): Boolean {
            return elements.any { element ->
                when (element) {
                    is WidgetElement -> element.widget.isVisible()
                    is ChildElement -> element.node.hasVisibleSetting()
                }
            }
        }
    }

    companion object {

        /**
         * 按 owner key 构建 section 树。
         *
         * @param ownerKey      section key 前缀，用于区分同一帧内的不同宿主
         * @param settings      宿主 Setting 列表
         * @param widgetFactory Setting 到 Dropdown 控件的工厂，返回 `null` 表示该 Setting 不参与渲染
         */
        fun create(
            ownerKey: String,
            settings: List<Setting<*>>,
            widgetFactory: (Setting<*>) -> SettingWidget<*>?,
        ): SettingSectionRenderer {
            val nodes = ArrayList<Node>()
            for (section in SettingLayoutPlanner.plan(ownerKey, settings)) {
                val node = buildNode(section, widgetFactory)
                if (node != null) {
                    nodes.add(node)
                }
            }
            return SettingSectionRenderer(nodes)
        }

        private fun buildNode(
            section: SettingLayoutPlanner.Section,
            widgetFactory: (Setting<*>) -> SettingWidget<*>?
        ): Node? {
            val elements = ArrayList<Element>()
            for (element in section.elements()) {
                when (element) {
                    is SettingLayoutPlanner.Section.SettingElement -> {
                        val widget = widgetFactory(element.setting())
                        if (widget == null) {
                            continue
                        }
                        elements.add(WidgetElement(widget))
                    }

                    is SettingLayoutPlanner.Section.GroupElement -> {
                        val child = buildNode(element.section(), widgetFactory)
                        if (child == null) {
                            continue
                        }
                        elements.add(ChildElement(child))
                    }

                    else -> {
                    }
                }
            }
            if (elements.isEmpty()) {
                return null
            }
            return Node(section, elements)
        }

        private fun createAnimation(duration: Long, startValue: Float): Animation {
            val animation = Animation(Easing.EASE_OUT_CUBIC, duration)
            animation.startValue = startValue
            return animation
        }


    }

}
