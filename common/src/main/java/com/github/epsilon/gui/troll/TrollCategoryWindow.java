package com.github.epsilon.gui.troll;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class TrollCategoryWindow extends TrollWindow {

    private final Category category;
    private final Map<Module, TrollSettingRow> rows = new IdentityHashMap<>();
    private List<Module> visibleModules = List.of();

    public TrollCategoryWindow(Category category) {
        this.category = category;
    }

    private String title() {
        return category == null ? EpsilonTranslations.TrollGui.CLIENT.getTranslatedName() : category.getName();
    }

    @Override
    public void layout(TextRenderer metrics, float screenWidth, float screenHeight) {
        Comparator<Module> order = Comparator.comparing(Module::getTranslatedName, String.CASE_INSENSITIVE_ORDER);
        if (ClientSetting.INSTANCE.moduleSort.is(ClientSetting.ModuleSort.EnabledFirst)) {
            order = Comparator.comparing(Module::isEnabled).reversed().thenComparing(order);
        }
        visibleModules = ModuleManager.INSTANCE.getModules().stream().filter(module -> module.getCategory() == category).sorted(order).toList();
        float width = Math.max(120.0f, metrics.getWidth(title(), TrollTheme.TEXT_SCALE) + 30.0f);
        for (Module module : visibleModules) {
            TrollSettingRow row = rows.computeIfAbsent(module, value -> new TrollSettingRow(value, TrollSettingRow.ModuleAction.TOGGLE));
            width = Math.max(width, row.minimumWidth(metrics) + TrollTheme.xMargin() * 2.0f);
        }
        headerHeight = TrollTheme.headerHeight(metrics);
        size(Math.min(Math.min(width, 300.0f), screenWidth), Math.min(600.0f, screenHeight), screenWidth, screenHeight);
        float step = TrollTheme.rowHeight(metrics) + TrollTheme.yMargin();
        scrollPosition = scroll.update(Math.max(0.0f, visibleModules.size() * step + TrollTheme.yMargin() - viewport.height()));
        float y = viewport.y() + TrollTheme.yMargin() - scrollPosition;
        for (Module module : visibleModules) {
            rows.get(module).layout(new UiRect(viewport.x(), y, viewport.width(), TrollTheme.rowHeight(metrics)));
            y += step;
        }
    }

    @Override
    protected boolean allowHeaderOnly() {
        return true;
    }

    @Override
    public void draw(UiTree.Scope scope, TextRenderer metrics, int mouseX, int mouseY) {
        TrollTheme.window(scope, bounds, headerHeight, title(), metrics);
        scope.scissor(viewport, content -> {
            for (Module module : visibleModules) rows.get(module).draw(content, metrics, mouseX, mouseY);
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event) {
        if (super.mouseClicked(event)) return true;
        if (!viewport.contains(event.x(), event.y())) return false;
        for (Module module : visibleModules) {
            if (rows.get(module).mouseClicked(event)) return true;
        }
        return true;
    }

}
