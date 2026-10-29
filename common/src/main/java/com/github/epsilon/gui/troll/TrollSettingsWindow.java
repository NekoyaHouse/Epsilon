package com.github.epsilon.gui.troll;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.SettingLayoutPlanner;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class TrollSettingsWindow extends TrollWindow {

    private static final float GROUP_INSET = 6.0f;

    private final Module module;
    private final Map<Setting<?>, TrollSettingRow> rows = new IdentityHashMap<>();
    private final TrollSettingRow bindRow;
    private final TrollSettingRow hiddenRow;
    private final List<Entry> entries = new ArrayList<>();

    public TrollSettingsWindow(Module module, float x, float y) {
        this.module = module;
        bindRow = new TrollSettingRow(module, TrollSettingRow.ModuleAction.BIND);
        hiddenRow = new TrollSettingRow(module, TrollSettingRow.ModuleAction.HIDDEN);
        position(x, y);
    }

    @Override
    public void layout(TextRenderer metrics, float screenWidth, float screenHeight) {
        List<TrollSettingRow> previous = entries.stream().filter(entry -> entry.row != null).map(Entry::row).toList();
        entries.clear();
        if (module.getCategory() != null) {
            entries.add(new Entry(bindRow, null, 0));
            entries.add(new Entry(hiddenRow, null, 0));
        }
        for (SettingLayoutPlanner.Section section : SettingLayoutPlanner.plan(module.getName(), module.getSettings()))
            append(section, 0);
        for (TrollSettingRow row : previous) {
            if (entries.stream().noneMatch(entry -> entry.row == row)) row.resetInput();
        }
        float width = Math.max(180.0f, metrics.getWidth(module.getTranslatedName(), TrollTheme.TEXT_SCALE) + 18.0f);
        for (Entry entry : entries) {
            width = Math.max(width, (entry.row != null ? entry.row.minimumWidth(metrics)
                    : metrics.getWidth(entry.section.title(), TrollTheme.TEXT_SCALE) + 30.0f) + entry.depth * 15.0f + TrollTheme.xMargin() * 2.0f);
        }
        headerHeight = TrollTheme.headerHeight(metrics);
        float rowHeight = TrollTheme.rowHeight(metrics);
        float rowGap = TrollTheme.yMargin();
        float contentHeight = rowGap;
        for (Entry entry : entries) {
            contentHeight += rowHeight + rowGap + (entry.leadingGroupInsets + entry.closingGroups.size()) * GROUP_INSET;
        }
        size(Math.min(Math.min(width, 390.0f), screenWidth), Math.min(Math.min(headerHeight + contentHeight, 300.0f), screenHeight), screenWidth, screenHeight);
        scrollPosition = scroll.update(Math.max(0.0f, contentHeight - viewport.height()));
        float cursor = viewport.y() + rowGap - scrollPosition;
        for (Entry entry : entries) {
            cursor += entry.leadingGroupInsets * GROUP_INSET;
            float inset = Math.min(entry.depth * 7.5f, viewport.width() * 0.25f);
            entry.bounds = new UiRect(viewport.x() + inset, cursor, viewport.width() - inset * 2.0f, rowHeight);
            entry.groupBounds = entry.bounds;
            if (entry.row != null) entry.row.layout(entry.bounds);
            cursor += rowHeight;
            for (Entry group : entry.closingGroups) {
                cursor += GROUP_INSET;
                group.groupBounds = new UiRect(group.bounds.x(), group.bounds.y(), group.bounds.width(), cursor - group.bounds.y());
            }
            cursor += rowGap;
        }
    }

    private void append(SettingLayoutPlanner.Section section, int depth) {
        Entry header = null;
        if (section.hasHeader()) {
            header = new Entry(null, section, depth);
            entries.add(header);
            if (section.isCollapsed()) return;
            depth++;
        }
        int firstChild = entries.size();
        for (SettingLayoutPlanner.Section.Element element : section.elements()) {
            switch (element) {
                case SettingLayoutPlanner.Section.SettingElement leaf -> {
                    if (leaf.setting().isAvailable()) {
                        TrollSettingRow row = rows.computeIfAbsent(leaf.setting(), setting -> {
                            TrollSettingRow created = new TrollSettingRow(setting);
                            created.startOpenAnimation();
                            return created;
                        });
                        entries.add(new Entry(row, null, depth));
                    }
                }
                case SettingLayoutPlanner.Section.GroupElement group -> append(group.section(), depth);
            }
        }
        if (header != null && entries.size() > firstChild) {
            entries.get(firstChild).leadingGroupInsets++;
            entries.getLast().closingGroups.add(header);
        }
    }

    @Override
    public void draw(UiTree.Scope scope, TextRenderer metrics, int mouseX, int mouseY) {
        TrollTheme.window(scope, bounds, headerHeight, module.getTranslatedName(), metrics);
        scope.scissor(viewport, content -> {
            for (Entry entry : entries) {
                if (entry.row != null) {
                    entry.row.draw(content, metrics, mouseX, mouseY);
                } else {
                    content.rect(1, entry.bounds.x(), entry.bounds.y(), entry.bounds.width(), entry.bounds.height(), new Color(255, 255, 255, entry.bounds.contains(mouseX, mouseY) ? 32 : 12));
                    String label = (entry.section.isCollapsed() ? "+ " : "- ") + entry.section.title();
                    content.text(3, TrollTheme.trim(label, metrics, TrollTheme.TEXT_SCALE, entry.bounds.width() - 18.0f), entry.bounds.x() + 9.0f, entry.bounds.y() + 2.25f, TrollTheme.TEXT_SCALE, TrollTheme.text());
                }
            }
            for (Entry entry : entries) {
                if (entry.section == null) continue;
                UiRect card = entry.groupBounds.inset(0.6f);
                Color outline = TrollTheme.alpha(TrollTheme.primary(), 255);
                content.rectOutline(5, card.x(), card.y(), card.width(), card.height(), 0.5f, outline);
            }
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event) {
        if (captureMouseBind(event)) return true;
        for (TrollSettingRow row : allRows()) row.resetInput();
        if (super.mouseClicked(event)) return true;
        if (!viewport.contains(event.x(), event.y())) return false;
        for (Entry entry : List.copyOf(entries)) {
            if (!entry.bounds.contains(event.x(), event.y())) continue;
            if (entry.row != null) return entry.row.mouseClicked(event);
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT)
                entry.section.toggleCollapsed();
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event) {
        if (super.mouseDragged(event)) return true;
        for (TrollSettingRow row : allRows()) if (row.mouseDragged(event)) return true;
        return false;
    }

    @Override
    public void mouseReleased(MouseButtonEvent event) {
        super.mouseReleased(event);
        for (TrollSettingRow row : allRows()) row.mouseReleased(event);
    }

    /**
     * 二级面板展开时重置所有 Setting 行的填充动画。
     */
    public void startSettingAnimations() {
        for (TrollSettingRow row : allRows()) row.startOpenAnimation();
    }

    public boolean keyPressed(KeyEvent event) {
        for (TrollSettingRow row : allRows()) if (row.keyPressed(event)) return true;
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        for (TrollSettingRow row : allRows()) if (row.charTyped(event)) return true;
        return false;
    }

    public boolean captureMouseBind(MouseButtonEvent event) {
        for (TrollSettingRow row : allRows()) if (row.captureMouseBind(event)) return true;
        return false;
    }

    public void listenForBind() {
        bindRow.listenForBind();
    }

    @Override
    public void resetInput() {
        super.resetInput();
        for (TrollSettingRow row : allRows()) row.resetInput();
    }

    private List<TrollSettingRow> allRows() {
        List<TrollSettingRow> result = new ArrayList<>(rows.values());
        result.add(bindRow);
        result.add(hiddenRow);
        return result;
    }

    private static class Entry {
        private final TrollSettingRow row;
        private final SettingLayoutPlanner.Section section;
        private final int depth;
        private final List<Entry> closingGroups = new ArrayList<>();
        private int leadingGroupInsets;
        private UiRect bounds;
        private UiRect groupBounds;

        private Entry(TrollSettingRow row, SettingLayoutPlanner.Section section, int depth) {
            this.row = row;
            this.section = section;
            this.depth = depth;
        }

        private TrollSettingRow row() {
            return row;
        }
    }

}
