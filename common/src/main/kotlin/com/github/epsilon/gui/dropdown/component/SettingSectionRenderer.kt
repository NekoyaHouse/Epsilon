package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.dropdown.widget.*
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingLayoutPlanner
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import java.util.HashMap

/**
 * Dropdown 侧 Setting section 树的共用渲染器。
 *
 * 负责把 [SettingLayoutPlanner.Section] 树包装成控件树，并统一处理嵌套分组的
 * 高度、卡片背景、缩进、展开动画、命中与输入分发；`SettingsContent` 与
 * `ModuleButton` 只提供控件工厂和坐标原点。
 *
 * 绘制坐标使用调用方 scope 的局部坐标，命中测试使用「局部坐标 + hitOffset」得到的绝对坐标，
 * 因此控件缓存的绝对位置可以直接参与命中。
 */
class SettingSectionRenderer private constructor(nodes: List<Node>) {

    private val nodes: List<Node> = nodes.toList()
    private val hoverAnimations: MutableMap<String, Animation> = HashMap()
    private val expandAnimations: MutableMap<String, Animation> = HashMap()

    fun isEmpty(): Boolean {
        return nodes.isEmpty()
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
        scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int,
        x: Float, y: Float, width: Float, hitOffsetX: Float, hitOffsetY: Float,
    ) {
        val sectionX = x + DropdownTheme.SETTING_INDENT
        val sectionWidth = sectionWidth(width)
        var cursor = y
        for (node in nodes) {
            drawNode(scope, textMetrics, mouseX, mouseY, node, 0, sectionX, cursor, sectionWidth, hitOffsetX, hitOffsetY)
            cursor += nodeHeight(node)
        }
    }

    fun mouseClicked(
        mouseX: Double, mouseY: Double, button: Int,
        x: Float, y: Float, width: Float, hitOffsetX: Float, hitOffsetY: Float,
    ): Boolean {
        val sectionX = x + DropdownTheme.SETTING_INDENT + hitOffsetX
        val sectionWidth = sectionWidth(width)
        var cursor = y + hitOffsetY
        for (node in nodes) {
            if (clickNode(node, 0, sectionX, cursor, sectionWidth, mouseX, mouseY, button)) {
                return true
            }
            cursor += nodeHeight(node)
        }
        return false
    }

    fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return anyWidget { widget -> widget.mouseReleased(mouseX, mouseY, button) }
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return anyWidget { widget -> widget.keyPressed(keyCode, scanCode, modifiers) }
    }

    fun charTyped(typedText: String): Boolean {
        return anyWidget { widget -> widget.charTyped(typedText) }
    }

    fun hasActiveInput(): Boolean {
        return hasListeningKeybind() || hasFocusedInput()
    }

    fun hasListeningKeybind(): Boolean {
        return anyWidget({ widget -> widget is KeybindWidget && widget.isListening() }, false)
    }

    fun hasFocusedInput(): Boolean {
        return anyWidget({ widget -> isFocusedInput(widget) }, false)
    }

    fun blurAllInputs() {
        for (node in nodes) {
            forEachWidget(node) { widget -> blurInput(widget) }
        }
    }

    // --- 高度 ---

    private fun nodeHeight(node: Node): Float {
        if (!node.hasHeader()) {
            return elementsHeight(node)
        }
        val contentHeight = DropdownTheme.GROUP_INSET + elementsHeight(node)
        return DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP + contentHeight * expandProgress(node)
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

    private fun drawNode(
        scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int,
        node: Node, depth: Int, x: Float, y: Float, width: Float,
        hitOffsetX: Float, hitOffsetY: Float,
    ) {
        if (!node.hasHeader()) {
            drawElements(scope, textMetrics, mouseX, mouseY, node, depth, x, y, width, hitOffsetX, hitOffsetY)
            return
        }

        val cardBounds = card(node, depth, x, width)
        val progress = expandProgress(node)
        val contentHeight = DropdownTheme.GROUP_INSET + elementsHeight(node)
        val cardHeight = DropdownTheme.GROUP_HEADER_HEIGHT + (DropdownTheme.SETTING_GAP + contentHeight) * progress
        val radius = DropdownTheme.groupCardRadius(progress)

        scope.roundRect(cardBounds.x, y, cardBounds.width, cardHeight, radius, DropdownTheme.groupCardBackground(depth))
        scope.outline(cardBounds.x, y, cardBounds.width, cardHeight, radius, 0.8f, DropdownTheme.groupCardOutline(depth))

        val hoverAnimation = hoverAnimations.getOrPut(node.key()) { createAnimation(DropdownTheme.ANIM_HOVER, 0.0f) }
        val hovered = isHovered(
            mouseX.toDouble(), mouseY.toDouble(), cardBounds.x + hitOffsetX, y + hitOffsetY,
            cardBounds.width, DropdownTheme.GROUP_HEADER_HEIGHT
        )
        hoverAnimation.run(if (hovered) 1.0f else 0.0f)
        val hoverProgress = hoverAnimation.value
        if (hoverProgress > 0.01f) {
            scope.roundRect(
                cardBounds.x, y, cardBounds.width, DropdownTheme.GROUP_HEADER_HEIGHT, radius,
                DropdownTheme.groupHeaderHover(hoverProgress)
            )
        }

        val label = trimToWidth(
            node.title(), DropdownTheme.GROUP_HEADER_TEXT_SCALE,
            cardBounds.width - 74.0f, textMetrics
        )
        val labelY = y + (DropdownTheme.GROUP_HEADER_HEIGHT - textMetrics.textHeight(DropdownTheme.GROUP_HEADER_TEXT_SCALE)) * 0.5f
        scope.text(
            label, cardBounds.x + DropdownTheme.SETTING_PADDING_X, labelY,
            DropdownTheme.GROUP_HEADER_TEXT_SCALE, DropdownTheme.groupText()
        )

        val countLabel = node.totalSettingCount().toString()
        val countWidth = textMetrics.textWidth(countLabel, DropdownTheme.GROUP_COUNT_TEXT_SCALE) + DropdownTheme.GROUP_COUNT_CHIP_PADDING * 2.0f
        val countX = cardBounds.x + cardBounds.width - DropdownTheme.SETTING_PADDING_X - countWidth - 12.0f
        val chipHeight = DropdownTheme.GROUP_COUNT_CHIP_HEIGHT
        val countY = y + (DropdownTheme.GROUP_HEADER_HEIGHT - chipHeight) * 0.5f
        scope.roundRect(countX, countY, countWidth, chipHeight, chipHeight / 2.0f, DropdownTheme.groupCountChip())
        val countTextY = countY + (chipHeight - textMetrics.textHeight(DropdownTheme.GROUP_COUNT_TEXT_SCALE)) * 0.5f
        scope.text(
            countLabel, countX + DropdownTheme.GROUP_COUNT_CHIP_PADDING, countTextY,
            DropdownTheme.GROUP_COUNT_TEXT_SCALE, DropdownTheme.groupCountText()
        )

        val chevronSize = 2.5f
        scope.triangle(
            cardBounds.x + cardBounds.width - DropdownTheme.SETTING_PADDING_X - chevronSize,
            y + DropdownTheme.GROUP_HEADER_HEIGHT * 0.5f, chevronSize, progress,
            DropdownTheme.groupChevron(hoverProgress)
        )

        if (progress > 0.001f) {
            val contentY = y + DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP
            scope.scissorIf(
                progress < 1.0f, cardBounds.x, contentY, cardBounds.width,
                contentHeight * progress + DropdownTheme.SETTING_GAP
            ) { clippedScope ->
                val innerX = cardBounds.x + DropdownTheme.GROUP_INSET
                val innerWidth = maxOf(1.0f, cardBounds.width - DropdownTheme.GROUP_INSET * 2.0f)
                drawElements(
                    clippedScope, textMetrics, mouseX, mouseY, node, depth + 1, innerX,
                    contentY + DropdownTheme.GROUP_INSET, innerWidth, hitOffsetX, hitOffsetY
                )
            }
        }
    }

    private fun drawElements(
        scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int,
        node: Node, depth: Int, x: Float, y: Float, width: Float,
        hitOffsetX: Float, hitOffsetY: Float,
    ) {
        var cursor = y
        for (element in node.elements) {
            when (element) {
                is WidgetElement -> {
                    val widget = element.widget
                    if (!widget.isVisible()) {
                        continue
                    }
                    // 控件位置必须使用绝对坐标：UiTree.pushAbsolute 不会叠加当前 bound。
                    val bounds = UiRect(x + hitOffsetX, cursor + hitOffsetY, width, widget.getHeight())
                    scope.pushAbsolute(bounds) { itemScope ->
                        widget.drawInScope(itemScope, textMetrics, mouseX, mouseY, bounds)
                    }
                    cursor += widget.getHeight() + DropdownTheme.SETTING_GAP
                }
                is ChildElement -> {
                    val child = element.node
                    drawNode(scope, textMetrics, mouseX, mouseY, child, depth, x, cursor, width, hitOffsetX, hitOffsetY)
                    cursor += nodeHeight(child)
                }
            }
        }
    }

    // --- 命中 ---

    private fun clickNode(
        node: Node, depth: Int, x: Float, y: Float, width: Float,
        mouseX: Double, mouseY: Double, button: Int,
    ): Boolean {
        if (!node.hasHeader()) {
            return clickElements(node, depth, x, y, width, mouseX, mouseY, button)
        }

        val cardBounds = card(node, depth, x, width)
        if (isHovered(mouseX, mouseY, cardBounds.x, y, cardBounds.width, DropdownTheme.GROUP_HEADER_HEIGHT)) {
            toggleCollapsed(node)
            return true
        }
        if (expandProgress(node) < 0.999f) {
            return false
        }

        val contentY = y + DropdownTheme.GROUP_HEADER_HEIGHT + DropdownTheme.SETTING_GAP
        val innerX = cardBounds.x + DropdownTheme.GROUP_INSET
        val innerWidth = maxOf(1.0f, cardBounds.width - DropdownTheme.GROUP_INSET * 2.0f)
        return clickElements(node, depth + 1, innerX, contentY + DropdownTheme.GROUP_INSET, innerWidth, mouseX, mouseY, button)
    }

    private fun clickElements(
        node: Node, depth: Int, x: Float, y: Float, width: Float,
        mouseX: Double, mouseY: Double, button: Int,
    ): Boolean {
        var cursor = y
        for (element in node.elements) {
            when (element) {
                is WidgetElement -> {
                    val widget = element.widget
                    if (!widget.isVisible()) {
                        continue
                    }
                    if (widget.mouseClicked(mouseX, mouseY, button)) {
                        return true
                    }
                    cursor += widget.getHeight() + DropdownTheme.SETTING_GAP
                }
                is ChildElement -> {
                    val child = element.node
                    if (clickNode(child, depth, x, cursor, width, mouseX, mouseY, button)) {
                        return true
                    }
                    cursor += nodeHeight(child)
                }
            }
        }
        return false
    }

    private fun toggleCollapsed(node: Node) {
        node.toggleCollapsed()
        if (node.isCollapsed()) {
            forEachWidget(node) { widget -> blurInput(widget) }
        }
        SoundManager.playInUi(if (node.isCollapsed()) SoundKey.SETTINGS_CLOSE else SoundKey.SETTINGS_OPEN)
        DropdownScreen.react(
            if (node.isCollapsed()) ReisaDropdownCompanion.Action.PANEL_CLOSE else ReisaDropdownCompanion.Action.PANEL_OPEN
        )
    }

    // --- 控件分发 ---

    private fun anyWidget(action: (SettingWidget<*>) -> Boolean): Boolean {
        return anyWidget(action, true)
    }

    /**
     * @param skipCollapsed 是否跳过折叠分组内的控件；焦点检查需要覆盖所有分组
     */
    private fun anyWidget(action: (SettingWidget<*>) -> Boolean, skipCollapsed: Boolean): Boolean {
        for (node in nodes) {
            if (visitWidget(node, action, skipCollapsed)) {
                return true
            }
        }
        return false
    }

    private fun visitWidget(node: Node, action: (SettingWidget<*>) -> Boolean, skipCollapsed: Boolean): Boolean {
        if (skipCollapsed && node.hasHeader() && node.isCollapsed()) {
            return false
        }
        for (element in node.elements) {
            when (element) {
                is WidgetElement -> {
                    val widget = element.widget
                    if (widget.isVisible() && action(widget)) {
                        return true
                    }
                }
                is ChildElement -> {
                    if (visitWidget(element.node, action, skipCollapsed)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun forEachWidget(node: Node, action: (SettingWidget<*>) -> Unit) {
        for (element in node.elements) {
            when (element) {
                is WidgetElement -> action(element.widget)
                is ChildElement -> forEachWidget(element.node, action)
            }
        }
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
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
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

        private fun buildNode(section: SettingLayoutPlanner.Section, widgetFactory: (Setting<*>) -> SettingWidget<*>?): Node? {
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

        private fun isFocusedInput(widget: SettingWidget<*>): Boolean {
            if (widget is StringWidget && widget.isFocused()) return true
            if (widget is IntSliderWidget && widget.isFocused()) return true
            if (widget is DoubleSliderWidget && widget.isFocused()) return true
            return widget is ColorWidget && widget.hasFocusedInput()
        }

        private fun blurInput(widget: SettingWidget<*>) {
            if (widget is StringWidget && widget.isFocused()) {
                widget.blurInput()
            } else if (widget is IntSliderWidget && widget.isFocused()) {
                widget.blurInput()
            } else if (widget is DoubleSliderWidget && widget.isFocused()) {
                widget.blurInput()
            } else if (widget is ColorWidget && widget.hasFocusedInput()) {
                widget.blurAllInputs()
            }
        }
    }

}
