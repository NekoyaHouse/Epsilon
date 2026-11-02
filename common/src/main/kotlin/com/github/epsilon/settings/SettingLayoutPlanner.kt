package com.github.epsilon.settings

import java.util.*

/**
 * 将显式 SettingGroup 转换为 GUI 可消费的 section 树。
 *
 * 这里不再根据名称或控件类型自动推断分组，模块需要通过 settingGroup(...).group(...)
 * 手动表达结构；布局层只负责聚合、排序和暴露折叠状态。分组可以嵌套，父分组内
 * 「直接挂载的 Setting」与「首次出现的子分组」按声明顺序交错排列。
 */
object SettingLayoutPlanner {

    fun plan(settings: List<Setting<*>>?): List<Section> = plan(inferOwnerKey(settings), settings)

    fun plan(ownerKey: String?, settings: List<Setting<*>>?): List<Section> {
        if (settings.isNullOrEmpty()) {
            return emptyList()
        }

        val sanitized = settings.filter { it != null }
        if (sanitized.isEmpty()) {
            return emptyList()
        }

        val resolvedOwnerKey = if (ownerKey.isNullOrBlank()) inferOwnerKey(sanitized) else ownerKey
        val root = Node(null, null, false, resolvedOwnerKey)
        val groupNodes = IdentityHashMap<SettingGroup, Node>()
        val visited: MutableSet<Setting<*>> = Collections.newSetFromMap(IdentityHashMap())
        var inlineIndex = 0

        for (setting in sanitized) {
            val group = setting.groupRef
            if (group == null) {
                var inlineRun = root.lastInlineRun()
                if (inlineRun == null) {
                    inlineRun = Node(null, root, true, "$resolvedOwnerKey:inline:${inlineIndex++}")
                    root.items.add(Item.Child(inlineRun))
                }
                inlineRun.addSetting(setting, visited)
                continue
            }

            var container = root
            for (ancestor in groupChain(group)) {
                var node = groupNodes[ancestor]
                if (node == null) {
                    val nodeKey = if (container.isRoot()) {
                        "$resolvedOwnerKey:group:${normalizeKey(ancestor.name)}"
                    } else {
                        "${container.key()}/${normalizeKey(ancestor.name)}"
                    }
                    node = Node(ancestor, container, false, nodeKey)
                    groupNodes[ancestor] = node
                    container.items.add(Item.Child(node))
                }
                container = node
            }
            container.addSetting(setting, visited)
        }

        val sections = ArrayList<Section>(root.items.size)
        for (item in root.items) {
            if (item is Item.Child) {
                val section = toSection(item.node)
                if (section != null) {
                    sections.add(section)
                }
            }
        }
        return sections
    }

    fun signature(settings: List<Setting<*>>?): Long = signature(inferOwnerKey(settings), settings)

    fun signature(ownerKey: String?, settings: List<Setting<*>>?): Long {
        var signature = 23L
        for (section in plan(ownerKey, settings)) {
            signature = mixSignature(signature, section)
        }
        return signature
    }

    private fun mixSignature(initial: Long, section: Section): Long {
        var signature = initial
        signature = signature * 31L + section.key().hashCode()
        signature = signature * 31L + section.title().hashCode()
        signature = signature * 31L + (if (section.hasHeader()) 1 else 0)
        signature = signature * 31L + (if (section.isCollapsed()) 1 else 0)
        for (element in section.elements()) {
            when (element) {
                is Section.SettingElement -> {
                    signature = signature * 31L + element.setting().name.hashCode()
                    signature = signature * 31L + if (element.setting().isSupported()) 1L else 0L
                }

                is Section.GroupElement -> signature = mixSignature(signature, element.section())
            }
        }
        return signature
    }

    private fun toSection(node: Node): Section? {
        val elements = ArrayList<Section.Element>(node.items.size)
        for (item in node.items) {
            when (item) {
                is Item.SettingLeaf -> elements.add(Section.SettingElement(item.setting))
                is Item.Child -> {
                    val child = toSection(item.node)
                    if (child != null) {
                        elements.add(Section.GroupElement(child))
                    }
                }
            }
        }
        if (elements.isEmpty()) {
            return null
        }
        return if (node.group() == null) {
            Section.inline(node.key(), elements)
        } else {
            Section.group(node.key(), node.group()!!, elements)
        }
    }

    private fun groupChain(group: SettingGroup): List<SettingGroup> {
        val chain = LinkedList<SettingGroup>()
        var current: SettingGroup? = group
        while (current != null) {
            chain.addFirst(current)
            current = current.parent
        }
        return chain
    }

    private fun inferOwnerKey(settings: List<Setting<*>>?): String {
        if (settings.isNullOrEmpty()) {
            return "settings:empty"
        }
        val first = settings.first()
        val last = settings.last()
        return "settings:${System.identityHashCode(first)}:${System.identityHashCode(last)}:${settings.size}"
    }

    private fun normalizeKey(value: String?): String {
        if (value.isNullOrBlank()) {
            return "group"
        }
        var normalized = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")
        normalized = normalized.replace(Regex("^_+|_+$"), "")
        return if (normalized.isBlank()) "group" else normalized
    }

    /**
     * 规划期的容器节点：顶层、inline 段和分组都复用同一结构，只有分组对应真实 Section。
     */
    private class Node(
        private val group: SettingGroup?,
        private val parent: Node?,
        val inline: Boolean,
        private val nodeKey: String,
    ) {

        val items: MutableList<Item> = ArrayList()

        fun group(): SettingGroup? = group

        fun key(): String = nodeKey

        fun isRoot(): Boolean = parent == null

        fun lastInlineRun(): Node? {
            if (items.isEmpty()) {
                return null
            }
            val last = items.last()
            return if (last is Item.Child && last.node.inline) last.node else null
        }

        fun addSetting(setting: Setting<*>, visited: MutableSet<Setting<*>>) {
            if (!visited.add(setting)) {
                return
            }
            items.add(Item.SettingLeaf(setting))
        }
    }

    private sealed interface Item {

        data class SettingLeaf(val setting: Setting<*>) : Item

        data class Child(val node: Node) : Item

    }

    /**
     * GUI 可消费的 section。
     *
     * [elements] 保留声明顺序，直接 Setting 与子分组在其中交错；
     * [settings] 与 [children] 只是按类型过滤后的视图。
     */
    class Section private constructor(
        private val sectionKey: String,
        private val sectionTitle: String,
        private val header: Boolean,
        private val sectionGroup: SettingGroup?,
        private val sectionElements: List<Element>,
    ) {

        private val directSettings: List<Setting<*>>

        private val childSections: List<Section>

        private val settingCount: Int

        init {
            val direct = ArrayList<Setting<*>>()
            val children = ArrayList<Section>()
            var count = 0
            for (element in sectionElements) {
                when (element) {
                    is SettingElement -> {
                        direct.add(element.setting)
                        count++
                    }

                    is GroupElement -> {
                        children.add(element.section)
                        count += element.section.totalSettingCount()
                    }
                }
            }
            directSettings = direct
            childSections = children
            settingCount = count
        }

        fun key(): String = sectionKey

        fun hasHeader(): Boolean = header

        fun group(): SettingGroup? = sectionGroup

        fun elements(): List<Element> = sectionElements

        fun settings(): List<Setting<*>> = directSettings

        fun children(): List<Section> = childSections

        /**
         * 该分组自身与所有子孙分组包含的 Setting 总数。
         */
        fun totalSettingCount(): Int = settingCount

        fun title(): String {
            val owner = sectionGroup
            if (header && owner != null) {
                return owner.getDisplayName()
            }
            return sectionTitle
        }

        fun isCollapsed(): Boolean = header && sectionGroup?.isCollapsed == true

        fun toggleCollapsed() {
            if (header && sectionGroup != null) {
                sectionGroup.toggleCollapsed()
            }
        }

        /**
         * section 内容元素：直接 Setting 或嵌套子分组。
         */
        sealed interface Element

        class SettingElement(val setting: Setting<*>) : Element {

            fun setting(): Setting<*> = setting
        }

        class GroupElement(val section: Section) : Element {

            fun section(): Section = section
        }

        companion object {

            fun inline(key: String, elements: List<Element>): Section =
                Section(key, "", false, null, elements)

            fun group(key: String, group: SettingGroup, elements: List<Element>): Section =
                Section(key, group.getDisplayName(), true, group, elements)

        }

    }

}
