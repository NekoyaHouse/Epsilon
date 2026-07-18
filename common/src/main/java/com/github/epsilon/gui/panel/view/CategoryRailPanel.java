package com.github.epsilon.gui.panel.view;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.panel.PanelState;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.holders.ModuleHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.modules.Category;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;

public class CategoryRailPanel {

    private static final float CATEGORY_ITEM_HEIGHT = 34.0f;
    private static final float CATEGORY_ITEM_SPACING = 38.0f;
    private static final float CATEGORY_START_Y = 40.0f;
    private static final float RAIL_ICON_CENTER_X_OFFSET = 2.0f;
    private static final String SETTINGS_ICON = IconChars.SETTINGS;

    protected final PanelState state;
    private final TextRenderer textRenderer;
    private final Animation expandAnimation = new Animation(Easing.EASE_OUT_CUBIC, 240L);
    private final Animation contentAnimation = new Animation(Easing.EASE_OUT_CUBIC, 180L);
    private final Animation menuHoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private final Animation headerTitleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 220L);
    private final Animation headerSubtitleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 260L);
    private final Animation headerDividerAnimation = new Animation(Easing.EASE_OUT_CUBIC, 220L);
    private final Animation selectionYAnimation = new Animation(Easing.EASE_OUT_CUBIC, 180L);
    private final Animation selectionHeightAnimation = new Animation(Easing.EASE_OUT_CUBIC, 180L);
    private final Animation hoverYAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private final Animation hoverAlphaAnimation = new Animation(Easing.EASE_OUT_CUBIC, 100L);
    private final Animation settingsHoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private UiRect bounds;

    public CategoryRailPanel(PanelState state, TextRenderer textRenderer) {
        this.state = state;
        this.textRenderer = textRenderer;
        this.expandAnimation.setStartValue(MD3Theme.RAIL_COLLAPSED_WIDTH);
        this.contentAnimation.setStartValue(0.0f);
        this.menuHoverAnimation.setStartValue(0.0f);
        this.headerTitleAnimation.setStartValue(0.0f);
        this.headerSubtitleAnimation.setStartValue(0.0f);
        this.headerDividerAnimation.setStartValue(0.0f);
        this.selectionYAnimation.setStartValue(0.0f);
        this.selectionHeightAnimation.setStartValue(32.0f);
        this.hoverYAnimation.setStartValue(0.0f);
        this.hoverAlphaAnimation.setStartValue(0.0f);
        this.settingsHoverAnimation.setStartValue(0.0f);
    }

    public float getAnimatedWidth() {
        expandAnimation.run(state.isSidebarExpanded() ? MD3Theme.RAIL_EXPANDED_WIDTH : MD3Theme.RAIL_COLLAPSED_WIDTH);
        return expandAnimation.getValue();
    }

    public void render(GuiGraphicsExtractor GuiGraphicsExtractor, UiRenderBatch renderBatch, UiRect bounds, int mouseX, int mouseY, float partialTick) {
        this.bounds = bounds;
        float targetWidth = state.isSidebarExpanded() ? MD3Theme.RAIL_EXPANDED_WIDTH : MD3Theme.RAIL_COLLAPSED_WIDTH;
        float targetContent = state.isSidebarExpanded() ? 1.0f : 0.0f;
        boolean requiresScissor = Math.abs(bounds.width() - targetWidth) > 0.001f
                || Math.abs(contentAnimation.getValue() - targetContent) > 0.001f;
        UiTree tree = UiTree.build(root -> root.scissorIf(requiresScissor, bounds, scope -> {
            float contentProgress = scope.animate(contentAnimation, state.isSidebarExpanded());
            float titleProgress = scope.animate(headerTitleAnimation, contentProgress);
            float subtitleProgress = scope.animate(headerSubtitleAnimation, contentProgress > 0.08f);
            float dividerProgress = scope.animate(headerDividerAnimation, contentProgress > 0.12f);
            float titleScale = 0.78f;
            float subtitleScale = 0.52f;
            float itemIconScale = 1.02f;
            float itemLabelScale = 0.62f;
            float itemCountScale = 0.58f;

            UiRect menuButton = getMenuButtonBounds();
            float menuHover = scope.animate(menuHoverAnimation, mouseOver(menuButton, mouseX, mouseY));
            scope.pushAbsolute(menuButton, menu -> {
                menu.roundRect(0.0f, 0.0f, menuButton.width(), menuButton.height(), 12.0f,
                        MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, 0), MD3Theme.SURFACE_CONTAINER_HIGH, menuHover));
                buildMenuGlyph(menu, menuButton);
            });

            float categoryStartY = getCategoryStartY(bounds);
            if (titleProgress > 0.02f) {
                float titleY = 7.0f;
                float titleHeight = textRenderer.getHeight(titleScale);
                float pad = 3.0f;
                float subtitleY = titleY + titleHeight + pad;
                float titleOffset = (1.0f - titleProgress) * 8.0f;
                float subtitleOffset = (1.0f - subtitleProgress) * 10.0f;
                scope.pushAbsolute(bounds, rail -> {
                    rail.text(Constants.NAME, 38.0f + titleOffset, titleY, titleScale, MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, (int) (255 * titleProgress)));
                    if (subtitleProgress > 0.02f) {
                        rail.text(Constants.VERSION, 38.0f + subtitleOffset, subtitleY, subtitleScale, MD3Theme.withAlpha(MD3Theme.TEXT_SECONDARY, (int) (210 * subtitleProgress)));
                    }
                    if (dividerProgress > 0.02f) {
                        float dividerY = subtitleY + textRenderer.getHeight(subtitleScale) + 4.0f;
                        float dividerBaseX = 7.0f;
                        float dividerTargetWidth = bounds.width() - 14.0f;
                        float dividerWidth = dividerTargetWidth * dividerProgress;
                        float dividerX = dividerBaseX + (1.0f - dividerProgress) * 6.0f;
                        rail.rect(dividerX, dividerY, dividerWidth, 1.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE_SOFT, (int) (120 * dividerProgress)));
                        rail.rect(dividerX, dividerY, Math.min(18.0f, dividerWidth), 1.0f, MD3Theme.withAlpha(MD3Theme.TEXT_SECONDARY, (int) (52 * dividerProgress)));
                    }
                });
            }

            float selectedItemY = categoryStartY;
            if (state.isClientSettingMode()) {
                selectedItemY = getSettingsButtonY();
            } else {
                float lookupY = categoryStartY;
                for (Category category : Category.values()) {
                    if (state.getSelectedCategory() == category) {
                        selectedItemY = lookupY;
                        break;
                    }
                    lookupY += CATEGORY_ITEM_SPACING;
                }
            }

            float hoveredY = -1.0f;
            float scanY = categoryStartY;
            for (Category category : Category.values()) {
                UiRect scanRect = new UiRect(bounds.x() + 5.0f, scanY, bounds.width() - 10.0f, CATEGORY_ITEM_HEIGHT);
                if (scanRect.contains(mouseX, mouseY)) {
                    hoveredY = scanY;
                    break;
                }
                scanY += CATEGORY_ITEM_SPACING;
            }
            if (hoveredY < 0) {
                UiRect settingsScanRect = new UiRect(bounds.x() + 5.0f, getSettingsButtonY(), bounds.width() - 10.0f, CATEGORY_ITEM_HEIGHT);
                if (settingsScanRect.contains(mouseX, mouseY)) {
                    hoveredY = getSettingsButtonY();
                }
            }

            float hoverAlpha = scope.animate(hoverAlphaAnimation, hoveredY >= 0);
            if (hoveredY >= 0) {
                scope.animate(hoverYAnimation, hoveredY);
            }
            if (hoverAlpha > 0.01f) {
                scope.pushAbsolute(bounds.x() + 5.0f, hoverYAnimation.getValue(), hover ->
                        hover.roundRect(0.0f, 0.0f, bounds.width() - 10.0f, CATEGORY_ITEM_HEIGHT, MD3Theme.CARD_RADIUS,
                                MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGH, (int) (200 * hoverAlpha))));
            }

            float animatedSelectionY = scope.animate(selectionYAnimation, selectedItemY);
            float animatedSelectionHeight = scope.animate(selectionHeightAnimation, CATEGORY_ITEM_HEIGHT);
            scope.pushAbsolute(bounds.x() + 5.0f, animatedSelectionY, selection ->
                    selection.roundRect(0.0f, 0.0f, bounds.width() - 10.0f, animatedSelectionHeight, MD3Theme.CARD_RADIUS, MD3Theme.SECONDARY_CONTAINER));

            float itemY = categoryStartY;
            for (Category category : Category.values()) {
                float currentItemY = itemY;
                UiRect itemRect = new UiRect(bounds.x() + 5.0f, currentItemY, bounds.width() - 10.0f, CATEGORY_ITEM_HEIGHT);
                boolean hovered = itemRect.contains(mouseX, mouseY);
                boolean selected = !state.isClientSettingMode() && state.getSelectedCategory() == category;
                int count = getCategoryCount(category);
                buildCategoryItem(scope, menuButton, itemRect, category, count, hovered, selected, contentProgress, itemIconScale, itemLabelScale, itemCountScale);
                itemY += CATEGORY_ITEM_SPACING;
            }

            float settingsBtnY = getSettingsButtonY();
            UiRect settingsRect = new UiRect(bounds.x() + 5.0f, settingsBtnY, bounds.width() - 10.0f, CATEGORY_ITEM_HEIGHT);
            boolean settingsHovered = settingsRect.contains(mouseX, mouseY);
            boolean settingsSelected = state.isClientSettingMode();
            float settingsHover = scope.animate(settingsHoverAnimation, settingsHovered);
            buildSettingsItem(scope, menuButton, settingsRect, settingsHovered, settingsSelected, contentProgress, settingsHover, itemIconScale, itemLabelScale);
        }));
        renderBatch.render(tree);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (bounds == null || event.button() != 0) {
            return false;
        }
        if (getMenuButtonBounds().contains(event.x(), event.y())) {
            state.toggleSidebarExpanded();
            Managers.SOUND.playInUi(state.isSidebarExpanded() ? SoundKey.SETTINGS_OPEN : SoundKey.SETTINGS_CLOSE);
            return true;
        }

        float itemY = getCategoryStartY(bounds);
        for (Category category : Category.values()) {
            UiRect itemRect = new UiRect(bounds.x() + 5.0f, itemY, bounds.width() - 10.0f, CATEGORY_ITEM_HEIGHT);
            if (itemRect.contains(event.x(), event.y())) {
                state.setClientSettingMode(false);
                state.setSelectedCategory(category);
                return true;
            }
            itemY += CATEGORY_ITEM_SPACING;
        }

        UiRect settingsRect = new UiRect(bounds.x() + 5.0f, getSettingsButtonY(), bounds.width() - 10.0f, CATEGORY_ITEM_HEIGHT);
        if (settingsRect.contains(event.x(), event.y())) {
            state.setClientSettingMode(true);
            return true;
        }

        return false;
    }

    private UiRect getMenuButtonBounds() {
        return new UiRect(bounds.x() + 4.0f + RAIL_ICON_CENTER_X_OFFSET, bounds.y() + 4.0f, 28.0f, 28.0f);
    }

    public boolean hasActiveAnimations() {
        return !expandAnimation.isFinished()
                || !contentAnimation.isFinished()
                || !menuHoverAnimation.isFinished()
                || !headerTitleAnimation.isFinished()
                || !headerSubtitleAnimation.isFinished()
                || !headerDividerAnimation.isFinished()
                || !selectionYAnimation.isFinished()
                || !selectionHeightAnimation.isFinished()
                || !hoverYAnimation.isFinished()
                || !hoverAlphaAnimation.isFinished()
                || !settingsHoverAnimation.isFinished();
    }

    private boolean mouseOver(UiRect rect, int mouseX, int mouseY) {
        return rect.contains(mouseX, mouseY);
    }

    private float getCategoryStartY(UiRect bounds) {
        // Keep category list vertically stable regardless of sidebar expansion progress.
        return bounds.y() + CATEGORY_START_Y;
    }

    private float getSettingsButtonY() {
        return bounds.bottom() - CATEGORY_ITEM_HEIGHT - 5.0f;
    }

    private void buildMenuGlyph(UiTree.Scope scope, UiRect button) {
        Color lineColor = MD3Theme.TEXT_PRIMARY;
        float glyphWidth = 12.0f;
        float glyphHeight = 10.0f;
        float x = button.width() / 2.0f - glyphWidth / 2.0f;
        float y = (button.height() - glyphHeight) / 2.0f;
        scope.rect(x, y, 12.0f, 1.6f, lineColor);
        scope.rect(x, y + 4.0f, 12.0f, 1.6f, lineColor);
        scope.rect(x, y + 8.0f, 12.0f, 1.6f, lineColor);
    }

    private void buildCategoryItem(UiTree.Scope scope, UiRect menuButton, UiRect itemRect, Category category, int count,
                                   boolean hovered, boolean selected, float contentProgress,
                                   float itemIconScale, float itemLabelScale, float itemCountScale) {
        Color background = selected ? MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 0)
                : (hovered ? MD3Theme.SURFACE_CONTAINER : MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, 0));
        Color iconColor = selected ? MD3Theme.ON_SECONDARY_CONTAINER : (hovered ? MD3Theme.TEXT_PRIMARY : MD3Theme.TEXT_SECONDARY);
        Color labelColor = selected ? MD3Theme.ON_SECONDARY_CONTAINER : MD3Theme.TEXT_PRIMARY;
        Color countColor = selected ? MD3Theme.ON_SECONDARY_CONTAINER : MD3Theme.TEXT_SECONDARY;
        float iconHeight = textRenderer.getHeight(itemIconScale, StaticFontLoader.ICONS);
        float labelHeight = textRenderer.getHeight(itemLabelScale);
        float countHeight = textRenderer.getHeight(itemCountScale);
        float iconY = (itemRect.height() - iconHeight) / 2.0f - 2.0f;
        float labelY = (itemRect.height() - labelHeight) / 2.0f;
        float countY = (itemRect.height() - countHeight) / 2.0f;

        scope.pushAbsolute(itemRect, item -> {
            item.roundRect(0.0f, 0.0f, itemRect.width(), itemRect.height(), MD3Theme.CARD_RADIUS, background);
            float iconWidth = textRenderer.getWidth(category.icon, itemIconScale, StaticFontLoader.ICONS);
            float iconX = getRailIconCenterX(menuButton) - itemRect.x() - iconWidth / 2.0f;
            item.text(category.icon, iconX, iconY, itemIconScale, iconColor, StaticFontLoader.ICONS);
            if (contentProgress > 0.02f) {
                float textOffset = (1.0f - contentProgress) * 5.0f;
                Color animatedLabel = MD3Theme.withAlpha(labelColor, (int) (255 * contentProgress));
                Color animatedCount = MD3Theme.withAlpha(countColor, (int) (220 * contentProgress));
                item.text(category.getName(), 30.0f + textOffset, labelY, itemLabelScale, animatedLabel);
                float countWidth = textRenderer.getWidth(Integer.toString(count), itemCountScale);
                item.text(Integer.toString(count), itemRect.width() - 12.0f - countWidth, countY, itemCountScale, animatedCount);
            }
        });
    }

    private void buildSettingsItem(UiTree.Scope scope, UiRect menuButton, UiRect settingsRect,
                                   boolean settingsHovered, boolean settingsSelected, float contentProgress, float settingsHover,
                                   float itemIconScale, float itemLabelScale) {
        Color settingsBg = settingsSelected ? MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 0)
                : (settingsHovered ? MD3Theme.SURFACE_CONTAINER : MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, 0));
        Color settingsIconColor = settingsSelected ? MD3Theme.ON_SECONDARY_CONTAINER : (settingsHovered ? MD3Theme.TEXT_PRIMARY : MD3Theme.TEXT_SECONDARY);
        Color settingsLabelColor = settingsSelected ? MD3Theme.ON_SECONDARY_CONTAINER : MD3Theme.TEXT_PRIMARY;
        scope.pushAbsolute(settingsRect, settings -> {
            settings.roundRect(0.0f, 0.0f, settingsRect.width(), settingsRect.height(), MD3Theme.CARD_RADIUS, settingsBg);
            float settingsIconWidth = textRenderer.getWidth(SETTINGS_ICON, itemIconScale, StaticFontLoader.ICONS);
            float settingsIconX = getRailIconCenterX(menuButton) - settingsRect.x() - settingsIconWidth / 2.0f;
            float settingsIconHeight = textRenderer.getHeight(itemIconScale, StaticFontLoader.ICONS);
            float settingsIconY = (settingsRect.height() - settingsIconHeight) / 2.0f - 2.0f;
            settings.text(SETTINGS_ICON, settingsIconX, settingsIconY, itemIconScale, settingsIconColor, StaticFontLoader.ICONS);
            if (contentProgress > 0.02f) {
                float textOffset = (1.0f - contentProgress) * 5.0f;
                Color animatedLabel = MD3Theme.withAlpha(settingsLabelColor, (int) (255 * contentProgress));
                float settingsLabelHeight = textRenderer.getHeight(itemLabelScale);
                float settingsLabelY = (settingsRect.height() - settingsLabelHeight) / 2.0f;
                settings.text(EpsilonTranslations.Gui.CLIENT_SETTINGS.getTranslatedName(), 30.0f + textOffset, settingsLabelY, itemLabelScale, animatedLabel);
            }
        });
    }

    private float getRailIconCenterX(UiRect railButton) {
        return railButton.x() + railButton.width() / 2.0f;
    }

    private int getCategoryCount(Category category) {
        return (int) ModuleHolder.INSTANCE.getModules().stream().filter(module -> module.getCategory() == category).count();
    }

}
