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
        float width = Math.max(120.0f, metrics.getWidth(module.getTranslatedName(), TrollTheme.TEXT_SCALE) + 12.0f);
        for (Entry entry : entries) {
            width = Math.max(width, (entry.row != null ? entry.row.minimumWidth(metrics)
                    : metrics.getWidth(entry.section.title(), TrollTheme.TEXT_SCALE) + 16.0f) + entry.depth * 5.0f + TrollTheme.xMargin() * 2.0f);
        }
        headerHeight = TrollTheme.headerHeight(metrics);
        float step = TrollTheme.rowHeight(metrics) + TrollTheme.yMargin();
        float contentHeight = entries.size() * step + TrollTheme.yMargin();
        size(Math.min(Math.min(width, 260.0f), screenWidth), Math.min(Math.min(headerHeight + contentHeight, 200.0f), screenHeight), screenWidth, screenHeight);
        scrollPosition = scroll.update(Math.max(0.0f, contentHeight - viewport.height()));
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float inset = Math.min(entry.depth * 5.0f, viewport.width() * 0.25f);
            entry.bounds = new UiRect(viewport.x() + inset, viewport.y() + TrollTheme.yMargin() + i * step - scrollPosition, viewport.width() - inset, TrollTheme.rowHeight(metrics));
            if (entry.row != null) entry.row.layout(entry.bounds);
        }
    }

    private void append(SettingLayoutPlanner.Section section, int depth) {
        if (section.hasHeader()) {
            entries.add(new Entry(null, section, depth));
            if (section.isCollapsed()) return;
            depth++;
        }
        for (SettingLayoutPlanner.Section.Element element : section.elements()) {
            switch (element) {
                case SettingLayoutPlanner.Section.SettingElement leaf -> {
                    if (leaf.setting().isAvailable())
                        entries.add(new Entry(rows.computeIfAbsent(leaf.setting(), TrollSettingRow::new), null, depth));
                }
                case SettingLayoutPlanner.Section.GroupElement group -> append(group.section(), depth);
            }
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
                    content.rect(1, entry.bounds.x(), entry.bounds.y(), entry.bounds.width(), entry.bounds.height(),
                            new Color(255, 255, 255, entry.bounds.contains(mouseX, mouseY) ? 32 : 12));
                    String label = (entry.section.isCollapsed() ? "+ " : "- ") + entry.section.title();
                    content.text(3, TrollTheme.trim(label, metrics, TrollTheme.TEXT_SCALE, entry.bounds.width() - 4.0f),
                            entry.bounds.x() + 2.0f, entry.bounds.y() + 1.5f, TrollTheme.TEXT_SCALE, TrollTheme.text());
                }
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
        private UiRect bounds;

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
