package com.github.epsilon.settings;

import java.util.*;

/**
 * 将显式 SettingGroup 转换为 GUI 可消费的 section。
 * <p>
 * 这里不再根据名称或控件类型自动推断分组，模块和 Addon 需要通过 settingGroup(...).group(...)
 * 手动表达结构；布局层只负责聚合、排序和暴露折叠状态。
 */
public final class SettingLayoutPlanner {

    private SettingLayoutPlanner() {
    }

    public static List<Section> plan(List<Setting<?>> settings) {
        return plan(inferOwnerKey(settings), settings);
    }

    public static List<Section> plan(String ownerKey, List<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            return List.of();
        }

        List<Setting<?>> sanitized = settings.stream().filter(setting -> setting != null).toList();
        if (sanitized.isEmpty()) {
            return List.of();
        }

        List<MutableSection> mutableSections = new ArrayList<>();
        Map<String, MutableSection> groupedSections = new LinkedHashMap<>();
        int inlineIndex = 0;

        for (Setting<?> setting : sanitized) {
            SettingGroup group = setting.getGroup();
            if (group == null) {
                MutableSection section = lastInlineSection(mutableSections);
                if (section == null) {
                    section = MutableSection.inline("inline:" + inlineIndex++);
                    mutableSections.add(section);
                }
                section.settings().add(setting);
                continue;
            }

            String groupKey = normalizeKey(group.getName());
            MutableSection section = groupedSections.get(groupKey);
            if (section == null) {
                section = MutableSection.group(groupKey, group);
                groupedSections.put(groupKey, section);
                mutableSections.add(section);
            }
            section.settings().add(setting);
        }

        List<Section> sections = new ArrayList<>();
        for (MutableSection mutableSection : mutableSections) {
            List<Setting<?>> sectionSettings = List.copyOf(mutableSection.settings());
            if (sectionSettings.isEmpty()) {
                continue;
            }

            if (mutableSection.group() == null) {
                sections.add(Section.inline(ownerKey + ":" + mutableSection.key(), sectionSettings));
            } else {
                sections.add(new Section(
                        ownerKey + ":" + mutableSection.key(),
                        mutableSection.group().getDisplayName(),
                        sectionSettings,
                        true,
                        mutableSection.group()
                ));
            }
        }
        return sections;
    }

    public static long signature(List<Setting<?>> settings) {
        return signature(inferOwnerKey(settings), settings);
    }

    public static long signature(String ownerKey, List<Setting<?>> settings) {
        long signature = 23L;
        for (Section section : plan(ownerKey, settings)) {
            signature = signature * 31L + section.key().hashCode();
            signature = signature * 31L + section.title().hashCode();
            signature = signature * 31L + (section.hasHeader() ? 1 : 0);
            signature = signature * 31L + (section.isCollapsed() ? 1 : 0);
            for (Setting<?> setting : section.settings()) {
                signature = signature * 31L + setting.getName().hashCode();
            }
        }
        return signature;
    }

    private static MutableSection lastInlineSection(List<MutableSection> sections) {
        if (sections.isEmpty()) {
            return null;
        }
        MutableSection last = sections.getLast();
        return last.group() == null ? last : null;
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

    private record MutableSection(String key, SettingGroup group, List<Setting<?>> settings) {
        private static MutableSection inline(String key) {
            return new MutableSection(key, null, new ArrayList<>());
        }

        private static MutableSection group(String key, SettingGroup group) {
            return new MutableSection(key, group, new ArrayList<>());
        }
    }

    public record Section(String key, String title, List<Setting<?>> settings, boolean hasHeader, SettingGroup group) {

        private static Section inline(String key, List<Setting<?>> settings) {
            return new Section(key, "", List.copyOf(settings), false, null);
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
    }

}
