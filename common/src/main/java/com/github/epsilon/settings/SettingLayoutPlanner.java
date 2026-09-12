package com.github.epsilon.settings;

import java.util.*;

/**
 * 将显式 SettingGroup 转换为 GUI 可消费的 section 树。
 * <p>
 * 这里不再根据名称或控件类型自动推断分组，模块需要通过 settingGroup(...).group(...)
 * 手动表达结构；布局层只负责聚合、排序和暴露折叠状态。分组可以嵌套，父分组内
 * 「直接挂载的 Setting」与「首次出现的子分组」按声明顺序交错排列。
 */
public class SettingLayoutPlanner {

    private SettingLayoutPlanner() {
    }

    public static List<Section> plan(List<Setting<?>> settings) {
        return plan(inferOwnerKey(settings), settings);
    }

    public static List<Section> plan(String ownerKey, List<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }

        List<Setting<?>> sanitized = settings.stream().filter(Objects::nonNull).toList();
        if (sanitized.isEmpty()) {
            return List.of();
        }

        String resolvedOwnerKey = ownerKey == null || ownerKey.isBlank() ? inferOwnerKey(sanitized) : ownerKey;
        Node root = new Node(null, null, false, resolvedOwnerKey);
        Map<SettingGroup, Node> groupNodes = new IdentityHashMap<>();
        Set<Setting<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int inlineIndex = 0;

        for (Setting<?> setting : sanitized) {
            SettingGroup group = setting.getGroup();
            if (group == null) {
                Node inlineRun = root.lastInlineRun();
                if (inlineRun == null) {
                    inlineRun = new Node(null, root, true, resolvedOwnerKey + ":inline:" + inlineIndex++);
                    root.items.add(new Item.Child(inlineRun));
                }
                inlineRun.addSetting(setting, visited);
                continue;
            }

            Node container = root;
            for (SettingGroup ancestor : groupChain(group)) {
                Node node = groupNodes.get(ancestor);
                if (node == null) {
                    String nodeKey = container.isRoot()
                            ? resolvedOwnerKey + ":group:" + normalizeKey(ancestor.getName())
                            : container.key() + "/" + normalizeKey(ancestor.getName());
                    node = new Node(ancestor, container, false, nodeKey);
                    groupNodes.put(ancestor, node);
                    container.items.add(new Item.Child(node));
                }
                container = node;
            }
            container.addSetting(setting, visited);
        }

        List<Section> sections = new ArrayList(root.items.size());
        for (Item item : root.items) {
            if (item instanceof Item.Child childItem) {
                Section section = toSection(childItem.node());
                if (section != null) {
                    sections.add(section);
                }
            }
        }
        return List.copyOf(sections);
    }

    public static long signature(List<Setting<?>> settings) {
        return signature(inferOwnerKey(settings), settings);
    }

    public static long signature(String ownerKey, List<Setting<?>> settings) {
        long signature = 23L;
        for (Section section : plan(ownerKey, settings)) {
            signature = mixSignature(signature, section);
        }
        return signature;
    }

    private static long mixSignature(long signature, Section section) {
        signature = signature * 31L + section.key().hashCode();
        signature = signature * 31L + section.title().hashCode();
        signature = signature * 31L + (section.hasHeader() ? 1 : 0);
        signature = signature * 31L + (section.isCollapsed() ? 1 : 0);
        for (Section.Element element : section.elements()) {
            switch (element) {
                case Section.SettingElement settingElement ->
                        signature = signature * 31L + settingElement.setting().getName().hashCode();
                case Section.GroupElement groupElement ->
                        signature = mixSignature(signature, groupElement.section());
            }
        }
        return signature;
    }

    private static Section toSection(Node node) {
        List<Section.Element> elements = new ArrayList<>(node.items.size());
        for (Item item : node.items) {
            switch (item) {
                case Item.SettingLeaf settingLeaf ->
                        elements.add(new Section.SettingElement(settingLeaf.setting()));
                case Item.Child childItem -> {
                    Section child = toSection(childItem.node());
                    if (child != null) {
                        elements.add(new Section.GroupElement(child));
                    }
                }
            }
        }
        if (elements.isEmpty()) {
            return null;
        }
        return node.group() == null
                ? Section.inline(node.key(), elements)
                : Section.group(node.key(), node.group(), elements);
    }

    private static List<SettingGroup> groupChain(SettingGroup group) {
        LinkedList<SettingGroup> chain = new LinkedList<>();
        for (SettingGroup current = group; current != null; current = current.getParent()) {
            chain.addFirst(current);
        }
        return chain;
    }

    private static String inferOwnerKey(List<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            return "settings:empty";
        }
        Setting<?> first = settings.getFirst();
        Setting<?> last = settings.getLast();
        return "settings:" + System.identityHashCode(first) + ":" + System.identityHashCode(last) + ":" + settings.size();
    }

    private static String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "group";
        }
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "group" : normalized;
    }

    /**
     * 规划期的容器节点：顶层、inline 段和分组都复用同一结构，只有分组对应真实 Section。
     */
    private static final class Node {

        private final SettingGroup group;
        private final Node parent;
        private final boolean inline;
        private final String key;
        private final List<Item> items = new ArrayList<>();

        private Node(SettingGroup group, Node parent, boolean inline, String key) {
            this.group = group;
            this.parent = parent;
            this.inline = inline;
            this.key = key;
        }

        private SettingGroup group() {
            return group;
        }

        private String key() {
            return key;
        }

        private boolean isRoot() {
            return parent == null;
        }

        private Node lastInlineRun() {
            if (items.isEmpty()) {
                return null;
            }
            return items.getLast() instanceof Item.Child childItem && childItem.node().inline
                    ? childItem.node()
                    : null;
        }

        private void addSetting(Setting<?> setting, Set<Setting<?>> visited) {
            if (!visited.add(setting)) {
                return;
            }
            items.add(new Item.SettingLeaf(setting));
        }
    }

    private sealed interface Item permits Item.SettingLeaf, Item.Child {

        record SettingLeaf(Setting<?> setting) implements Item {
        }

        record Child(Node node) implements Item {
        }
    }

    /**
     * GUI 可消费的 section。
     * <p>
     * {@link #elements()} 保留声明顺序，直接 Setting 与子分组在其中交错；
     * {@link #settings()} 与 {@link #children()} 只是按类型过滤后的视图。
     */
    public static final class Section {

        private final String key;
        private final String title;
        private final boolean hasHeader;
        private final SettingGroup group;
        private final List<Element> elements;
        private final List<Setting<?>> settings;
        private final List<Section> children;
        private final int totalSettingCount;

        private Section(String key, String title, boolean hasHeader, SettingGroup group, List<Element> elements) {
            this.key = key;
            this.title = title;
            this.hasHeader = hasHeader;
            this.group = group;
            this.elements = List.copyOf(elements);

            List<Setting<?>> directSettings = new ArrayList<>();
            List<Section> childSections = new ArrayList<>();
            int count = 0;
            for (Element element : this.elements) {
                switch (element) {
                    case SettingElement settingElement -> {
                        directSettings.add(settingElement.setting());
                        count++;
                    }
                    case GroupElement groupElement -> {
                        childSections.add(groupElement.section());
                        count += groupElement.section().totalSettingCount();
                    }
                }
            }
            this.settings = List.copyOf(directSettings);
            this.children = List.copyOf(childSections);
            this.totalSettingCount = count;
        }

        private static Section inline(String key, List<Element> elements) {
            return new Section(key, "", false, null, elements);
        }

        private static Section group(String key, SettingGroup group, List<Element> elements) {
            return new Section(key, group.getDisplayName(), true, group, elements);
        }

        public String key() {
            return key;
        }

        public boolean hasHeader() {
            return hasHeader;
        }

        public SettingGroup group() {
            return group;
        }

        public List<Element> elements() {
            return elements;
        }

        public List<Setting<?>> settings() {
            return settings;
        }

        public List<Section> children() {
            return children;
        }

        /**
         * 该分组自身与所有子孙分组包含的 Setting 总数。
         */
        public int totalSettingCount() {
            return totalSettingCount;
        }

        public String title() {
            if (hasHeader && group != null) {
                return group.getDisplayName();
            }
            return title;
        }

        public boolean isCollapsed() {
            return hasHeader && group != null && group.isCollapsed();
        }

        public void toggleCollapsed() {
            if (hasHeader && group != null) {
                group.toggleCollapsed();
            }
        }

        /**
         * section 内容元素：直接 Setting 或嵌套子分组。
         */
        public sealed interface Element permits SettingElement, GroupElement {
        }

        public record SettingElement(Setting<?> setting) implements Element {
        }

        public record GroupElement(Section section) implements Element {
        }
    }

}
