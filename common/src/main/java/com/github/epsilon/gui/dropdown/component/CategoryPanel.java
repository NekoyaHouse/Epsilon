package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.control.UiScrollBar;
import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.ClientSetting;

import java.util.*;

public class CategoryPanel extends AbstractDropdownPanel {

    private final Category category;
    private final List<ModuleButton> moduleButtons = new ArrayList<>();
    private List<ModuleButton> sortedModuleButtons = List.of();
    private List<ModuleButton> visibleModuleButtons = List.of();
    private final Map<ModuleButton, String> searchTextCache = new HashMap<>();
    private String searchQuery = "";
    private ClientSetting.ModuleSort cachedSortMode;
    private long cachedSortSignature = Long.MIN_VALUE;
    private long cachedFilterSignature = Long.MIN_VALUE;
    private long cachedSearchTextRevision = Long.MIN_VALUE;
    private int cachedContentHeightFrameId = Integer.MIN_VALUE;
    private float cachedContentHeight;

    public CategoryPanel(Category category, int panelIndex) {
        super("category:" + category, category::getName, category.icon, panelIndex);
        this.category = category;
        List<Module> modules = ModuleHolder.INSTANCE.getModules().stream()
                .filter(m -> m.getCategory() == category)
                .toList();
        initModuleButtons(modules);
    }

    public CategoryPanel(String id, String title, String icon, int panelIndex, List<? extends Module> modules) {
        super(id, title, icon, panelIndex);
        this.category = null;
        initModuleButtons(modules);
    }

    private void initModuleButtons(List<? extends Module> modules) {
        for (Module module : modules) {
            if (module == null) continue;
            moduleButtons.add(new ModuleButton(module));
        }
        refreshSortedModuleButtons();
    }

    @Override
    protected void drawPanelContent(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float visibleHeight) {
        List<ModuleButton> buttons = visibleButtons();
        float expand = openAnim.getValue();
        int frameId = getRenderFrameId();
        float buttonWidth = maxScroll > 0.0f ? width - UiScrollBar.HOVER_WIDTH + 1f : width;
        var stack = scope.stack(new UiRect(
                x,
                y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll,
                buttonWidth,
                computeContentHeight()
        ));
        for (ModuleButton button : buttons) {
            float btnH = button.getHeightForFrame(frameId);
            stack.item(btnH, (bounds, itemScope) -> {
                float visibleTop = y + DropdownTheme.PANEL_HEADER_HEIGHT;
                float visibleBottom = visibleTop + visibleHeight * expand;
                if (bounds.bottom() > visibleTop && bounds.y() < visibleBottom) {
                    button.drawInScope(itemScope, textMetrics, mouseX, mouseY, bounds);
                }
            });
        }
    }

    @Override
    protected float computeContentHeight() {
        int frameId = getRenderFrameId();
        if (cachedContentHeightFrameId == frameId) {
            return cachedContentHeight;
        }

        List<ModuleButton> buttons = visibleButtons();
        float total = 0.0f;
        for (ModuleButton button : buttons) {
            total += button.getHeightForFrame(frameId);
        }
        cachedContentHeightFrameId = frameId;
        cachedContentHeight = total;
        return total;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        for (ModuleButton mb : visibleButtons()) {
            if (mb.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        for (ModuleButton mb : visibleButtons()) {
            if (mb.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (ModuleButton mb : visibleButtons()) {
            if (mb.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        for (ModuleButton mb : visibleButtons()) {
            if (mb.charTyped(typedText)) {
                return true;
            }
        }
        return false;
    }

    public Category getCategory() {
        return category;
    }

    public void setSearchQuery(String searchQuery) {
        String normalized = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        if (this.searchQuery.equals(normalized)) {
            return;
        }
        this.searchQuery = normalized;
        cachedFilterSignature = Long.MIN_VALUE;
        setScrollImmediate(0.0f);
    }

    @Override
    public boolean hasActiveInput() {
        for (ModuleButton mb : visibleButtons()) {
            if (mb.hasListeningKeybind() || mb.hasFocusedInput()) return true;
        }
        return false;
    }

    private void refreshSortedModuleButtons() {
        ClientSetting.ModuleSort sortMode = ClientSetting.INSTANCE.moduleSort.getValue();
        long sortSignature = buildSortSignature(sortMode);
        if (sortMode == cachedSortMode && sortSignature == cachedSortSignature) {
            return;
        }
        sortedModuleButtons = moduleButtons.stream()
                .sorted(getComparator(sortMode))
                .toList();
        cachedSortMode = sortMode;
        cachedSortSignature = sortSignature;
        cachedFilterSignature = Long.MIN_VALUE;
    }

    private List<ModuleButton> visibleButtons() {
        refreshSortedModuleButtons();
        long filterSignature = cachedSortSignature * 31L + searchQuery.hashCode();
        if (filterSignature == cachedFilterSignature) {
            return visibleModuleButtons;
        }
        visibleModuleButtons = sortedModuleButtons.stream()
                .filter(this::matchesSearch)
                .toList();
        cachedFilterSignature = filterSignature;
        return visibleModuleButtons;
    }

    private long buildSortSignature(ClientSetting.ModuleSort sortMode) {
        long signature = 17L;
        signature = signature * 31L + sortMode.ordinal();
        signature = signature * 31L + TranslateHolder.INSTANCE.getRevision();
        signature = signature * 31L + moduleButtons.size();
        for (ModuleButton button : moduleButtons) {
            Module module = button.getModule();
            signature = signature * 31L + Objects.hashCode(module.getName());
            signature = signature * 31L + Objects.hashCode(module.getAddonId());
            signature = signature * 31L + (module.isEnabled() ? 1 : 0);
        }
        return signature;
    }

    private Comparator<ModuleButton> getComparator(ClientSetting.ModuleSort sortMode) {
        Comparator<ModuleButton> nameComparator = Comparator.comparing(button -> normalizedName(button.getModule()), String.CASE_INSENSITIVE_ORDER);
        return switch (sortMode) {
            case EnabledFirst ->
                    Comparator.comparing((ModuleButton button) -> button.getModule().isEnabled()).reversed().thenComparing(nameComparator);
            case Addon ->
                    Comparator.comparing((ModuleButton button) -> normalizedAddon(button.getModule()), String.CASE_INSENSITIVE_ORDER).thenComparing(nameComparator);
            case Name -> nameComparator;
        };
    }

    private String normalizedName(Module module) {
        String translated = module.getTranslatedName();
        if (translated != null && !translated.isBlank()) return translated;
        String name = module.getName();
        return name == null ? "" : name;
    }

    private String normalizedAddon(Module module) {
        String addonId = module.getAddonId();
        return addonId == null || addonId.isBlank() ? "unknown" : addonId.toLowerCase(Locale.ROOT);
    }

    private boolean matchesSearch(ModuleButton button) {
        if (searchQuery.isBlank()) return true;
        refreshSearchTextCache();
        String searchText = searchTextCache.get(button);
        return searchText != null && searchText.contains(searchQuery);
    }

    private void refreshSearchTextCache() {
        long revision = TranslateHolder.INSTANCE.getRevision();
        if (cachedSearchTextRevision == revision && !searchTextCache.isEmpty()) {
            return;
        }
        searchTextCache.clear();
        for (ModuleButton button : moduleButtons) {
            Module module = button.getModule();
            String translated = module.getTranslatedName() == null ? "" : module.getTranslatedName();
            String name = module.getName() == null ? "" : module.getName();
            String categoryName = module.getCategory() == null ? "" : module.getCategory().getName();
            String addon = module.getAddonId() == null ? "" : module.getAddonId();
            searchTextCache.put(button, (translated + '\n' + name + '\n' + categoryName + '\n' + addon).toLowerCase(Locale.ROOT));
        }
        cachedSearchTextRevision = revision;
    }

}
