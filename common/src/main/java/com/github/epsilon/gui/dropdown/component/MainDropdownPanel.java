package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class MainDropdownPanel extends AbstractDropdownPanel {

    private static final float ICON_SIZE = 30.0f;
    private static final float ICON_GAP = 7.0f;
    private static final float ICON_SCALE = 0.88f;
    private static final float CONTENT_PADDING = 7.0f;
    private static final int ICON_COLUMNS = 4;

    private final List<Entry> entries = new ArrayList<>();
    private final SettingsContent settingsContent;

    public MainDropdownPanel(int panelIndex, Consumer<String> togglePanel, BooleanSupplier anySubPanelVisible, PanelVisibleResolver panelVisibleResolver) {
        super("main", Constants.NAME, "", panelIndex);
        this.width = 160.0f;
        this.settingsContent = new SettingsContent("client-settings:dropdown-main", ClientSetting.INSTANCE.getSettings());
        setVisible(true);
        setOpened(true);
        add(Category.COMBAT::getName, Category.COMBAT.icon, "category:combat", togglePanel, panelVisibleResolver);
        add(Category.PLAYER::getName, Category.PLAYER.icon, "category:player", togglePanel, panelVisibleResolver);
        add(Category.MOVEMENT::getName, Category.MOVEMENT.icon, "category:movement", togglePanel, panelVisibleResolver);
        add(Category.RENDER::getName, Category.RENDER.icon, "category:render", togglePanel, panelVisibleResolver);
        add(EpsilonTranslations.Gui.TAB_FRIEND::getTranslatedName, IconChars.PEOPLE, "friend", togglePanel, panelVisibleResolver);
        add(EpsilonTranslations.Gui.TAB_CONFIG::getTranslatedName, IconChars.SETTINGS, "config", togglePanel, panelVisibleResolver);
        add(EpsilonTranslations.Gui.TAB_ADDON::getTranslatedName, IconChars.ADD, "addon", togglePanel, panelVisibleResolver);
        entries.add(new Entry(EpsilonTranslations.Gui.DROPDOWN_COLLAPSE_ALL::getTranslatedName, IconChars.CLOSE, "__collapse_all__", togglePanel, anySubPanelVisible));
    }

    private void add(LabelSupplier labelSupplier, String icon, String panelId, Consumer<String> togglePanel, PanelVisibleResolver panelVisibleResolver) {
        entries.add(new Entry(labelSupplier, icon, panelId, togglePanel, () -> panelVisibleResolver.getAsBoolean(panelId)));
    }

    @Override
    public void drawBackground(UiTree.Scope scope, UiTextMetrics textMetrics) {
        super.drawBackground(scope, textMetrics);

        float versionScale = 0.48f;
        float versionX = x + textMetrics.textWidth(getTitle(), DropdownTheme.HEADER_TEXT_SCALE) + 12.0f;
        float versionMaxWidth = x + width - 17.0f - versionX;
        if (versionMaxWidth <= 2.0f) return;

        String version = trimToWidth(Constants.VERSION, versionScale, versionMaxWidth, textMetrics);
        if (!version.isEmpty()) {
            float nameY = y + (DropdownTheme.PANEL_HEADER_HEIGHT - textMetrics.textHeight(DropdownTheme.HEADER_TEXT_SCALE)) * 0.5f;
            float versionY = nameY + textMetrics.textHeight(DropdownTheme.HEADER_TEXT_SCALE) - textMetrics.textHeight(versionScale);
            scope.text(version, versionX, versionY, versionScale, MD3Theme.TEXT_MUTED);
        }
    }

    @Override
    protected float computeContentHeight() {
        int rows = getIconRows();
        return CONTENT_PADDING + rows * ICON_SIZE + Math.max(0, rows - 1) * ICON_GAP + 8.0f + CONTENT_PADDING + settingsContent.computeContentHeight(getRenderFrameId());
    }

    @Override
    protected void drawPanelContent(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float visibleHeight) {
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll + CONTENT_PADDING;
        scope.rect(x + CONTENT_PADDING, y + DropdownTheme.PANEL_HEADER_HEIGHT, width - CONTENT_PADDING * 2.0f, 0.7f, MD3Theme.withAlpha(MD3Theme.OUTLINE, 55));

        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            float iconX = getIconX(index);
            int row = index / ICON_COLUMNS;
            float iconY = currentY + row * (ICON_SIZE + ICON_GAP);
            boolean hovered = isHovered(mouseX, mouseY, iconX, iconY, ICON_SIZE, ICON_SIZE);
            boolean active = entry.isActive();
            entry.hoverAnim.run(hovered ? 1.0f : 0.0f);
            float hover = entry.hoverAnim.getValue();
            scope.roundRect(iconX, iconY, ICON_SIZE, ICON_SIZE, DropdownTheme.BUTTON_RADIUS, MD3Theme.lerp(active ? MD3Theme.PRIMARY_CONTAINER : MD3Theme.SURFACE_CONTAINER_HIGH, MD3Theme.PRIMARY_CONTAINER, hover * 0.5f));
            float iconScale = ICON_SCALE;
            float iconW = textMetrics.textWidth(entry.icon, iconScale, StaticFontLoader.ICONS);
            float iconH = textMetrics.textHeight(iconScale, StaticFontLoader.ICONS);
            scope.text(entry.icon, iconX + (ICON_SIZE - iconW) * 0.5f, iconY + (ICON_SIZE - iconH) * 0.5f - 1.0f, iconScale, active ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY, StaticFontLoader.ICONS);
            if (hovered) {
                String label = entry.labelSupplier.get();
                float labelScale = 0.42f;
                float labelW = textMetrics.textWidth(label, labelScale);
                float labelX = Math.max(x + 2.0f, Math.min(iconX + (ICON_SIZE - labelW) * 0.5f, x + width - labelW - 2.0f));
                scope.text(label, labelX, iconY + ICON_SIZE + 1.0f, labelScale, MD3Theme.TEXT_MUTED);
            }
        }
        int rows = getIconRows();
        currentY += rows * ICON_SIZE + Math.max(0, rows - 1) * ICON_GAP + 4.0f + CONTENT_PADDING;
        scope.rect(x + CONTENT_PADDING, currentY - 3.0f, width - CONTENT_PADDING * 2.0f, 0.7f, MD3Theme.withAlpha(MD3Theme.OUTLINE, 55));
        settingsContent.draw(scope, textMetrics, mouseX, mouseY, x, currentY, width, getRenderFrameId());
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll + CONTENT_PADDING;
        if (button == 0) {
            for (int index = 0; index < entries.size(); index++) {
                Entry entry = entries.get(index);
                float iconX = getIconX(index);
                float iconY = currentY + (index / ICON_COLUMNS) * (ICON_SIZE + ICON_GAP);
                if (isHovered(mouseX, mouseY, iconX, iconY, ICON_SIZE, ICON_SIZE)) {
                    entry.action.accept(entry.panelId);
                    return true;
                }
            }
        }
        int rows = getIconRows();
        float settingsY = currentY + rows * ICON_SIZE + Math.max(0, rows - 1) * ICON_GAP + 8.0f + CONTENT_PADDING;
        return settingsContent.mouseClicked(mouseX, mouseY, button, x, settingsY, width);
    }

    @Override
    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        float settingsY = getSettingsY();
        return settingsContent.mouseReleased(mouseX, mouseY, button, x, settingsY, width);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return settingsContent.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(String typedText) {
        return settingsContent.charTyped(typedText);
    }

    @Override
    public boolean hasActiveInput() {
        return settingsContent.hasActiveInput();
    }

    private float getSettingsY() {
        int rows = getIconRows();
        return y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll + CONTENT_PADDING + rows * ICON_SIZE + Math.max(0, rows - 1) * ICON_GAP + 8.0f + CONTENT_PADDING;
    }

    private int getIconRows() {
        return (int) Math.ceil(entries.size() / (float) ICON_COLUMNS);
    }

    private float getIconX(int index) {
        int rowStart = (index / ICON_COLUMNS) * ICON_COLUMNS;
        int rowCount = Math.min(ICON_COLUMNS, entries.size() - rowStart);
        float rowWidth = rowCount * ICON_SIZE + Math.max(0, rowCount - 1) * ICON_GAP;
        float rowX = x + (width - rowWidth) * 0.5f;
        return rowX + (index - rowStart) * (ICON_SIZE + ICON_GAP);
    }

    @FunctionalInterface
    public interface PanelVisibleResolver {
        boolean getAsBoolean(String panelId);
    }

    private final class Entry {
        private final LabelSupplier labelSupplier;
        private final String icon;
        private final String panelId;
        private final Consumer<String> action;
        private final BooleanSupplier activeSupplier;
        private final Animation hoverAnim = new Animation(Easing.EASE_OUT_CUBIC, DropdownTheme.ANIM_HOVER);

        private Entry(LabelSupplier labelSupplier, String icon, String panelId, Consumer<String> action, BooleanSupplier activeSupplier) {
            this.labelSupplier = labelSupplier;
            this.icon = icon;
            this.panelId = panelId;
            this.action = action;
            this.activeSupplier = activeSupplier;
        }

        private boolean isActive() {
            return activeSupplier != null && activeSupplier.getAsBoolean();
        }
    }

    @FunctionalInterface
    private interface LabelSupplier {
        String get();
    }

}
