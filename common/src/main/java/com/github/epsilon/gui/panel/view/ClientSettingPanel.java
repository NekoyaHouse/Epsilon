package com.github.epsilon.gui.panel.view;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.render.UiRenderBatch;
import com.github.epsilon.gui.panel.PanelState;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.view.settings.*;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.EnumMap;
import java.util.List;

public class ClientSettingPanel implements AutoCloseable {

    private static final List<TabDefinition> TABS = List.of(
            new TabDefinition(PanelState.ClientSettingTab.GENERAL, EpsilonTranslations.Gui.TAB_GENERAL),
            new TabDefinition(PanelState.ClientSettingTab.FRIEND, EpsilonTranslations.Gui.TAB_FRIEND),
            new TabDefinition(PanelState.ClientSettingTab.CONFIG, EpsilonTranslations.Gui.TAB_CONFIG),
            new TabDefinition(PanelState.ClientSettingTab.ADDON, EpsilonTranslations.Gui.TAB_ADDON)
    );

    private static final float TAB_BAR_HEIGHT = 26.0f;
    private static final float TAB_INDICATOR_HEIGHT = 2.5f;

    protected final PanelState state;
    private final TextRenderer textRenderer;
    private final EnumMap<PanelState.ClientSettingTab, ClientSettingTabView> tabViews = new EnumMap<>(PanelState.ClientSettingTab.class);
    private final EnumMap<PanelState.ClientSettingTab, Animation> tabHoverAnimations = new EnumMap<>(PanelState.ClientSettingTab.class);
    private final Animation tabIndicatorAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200L);

    private UiRect bounds;

    public ClientSettingPanel(PanelState state, TextRenderer textRenderer, PanelPopupHost popupHost) {
        this.state = state;
        this.textRenderer = textRenderer;

        tabViews.put(PanelState.ClientSettingTab.GENERAL, new GeneralClientSettingTab(state, textRenderer, popupHost));
        tabViews.put(PanelState.ClientSettingTab.FRIEND, new FriendClientSettingTab(state, textRenderer));
        tabViews.put(PanelState.ClientSettingTab.CONFIG, new ConfigClientSettingTab(state, textRenderer, popupHost));
        tabViews.put(PanelState.ClientSettingTab.ADDON, new AddonClientSettingTab(state, textRenderer, popupHost));

        for (PanelState.ClientSettingTab tab : PanelState.ClientSettingTab.values()) {
            Animation animation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
            animation.setStartValue(0.0f);
            tabHoverAnimations.put(tab, animation);
        }
        tabIndicatorAnimation.setStartValue(getTabIndex(state.getClientSettingTab()));
    }

    public void render(GuiGraphicsExtractor guiGraphics, UiRenderBatch renderBatch, UiRect bounds, int mouseX, int mouseY, float partialTick) {
        this.bounds = bounds;

        ClientSettingTabView activeTab = getCurrentTabView();
        boolean popupConsumesHover = activeTab.consumesHover(mouseX, mouseY);
        int effectiveMouseX = popupConsumesHover ? Integer.MIN_VALUE : mouseX;
        int effectiveMouseY = popupConsumesHover ? Integer.MIN_VALUE : mouseY;

        UiTree tree = UiTree.build(scope -> {
            scope.pushAbsolute(bounds, panel ->
                    panel.text(EpsilonTranslations.Gui.CLIENT_SETTINGS.getTranslatedName(), MD3Theme.PANEL_TITLE_INSET, 10.0f, 0.78f, MD3Theme.TEXT_PRIMARY));
            buildTabs(scope, effectiveMouseX, effectiveMouseY);
        });
        renderBatch.render(tree);

        activeTab.render(guiGraphics, renderBatch.view(10), getContentBounds(), effectiveMouseX, effectiveMouseY, partialTick);
    }

    public void flushContent() {
        getCurrentTabView().flushContent();
    }

    public void markDirty() {
        tabViews.values().forEach(ClientSettingTabView::markDirty);
    }

    public boolean hasActiveAnimations() {
        boolean tabsAnimating = !tabIndicatorAnimation.isFinished()
                || tabHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished());
        return tabsAnimating || getCurrentTabView().hasActiveAnimations();
    }

    public void resetTransientState() {
        tabViews.values().forEach(ClientSettingTabView::onDeactivated);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (bounds == null || event.button() != 0) {
            return false;
        }

        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null);
            markDirty();
        }

        UiRect tabBar = getTabBarRect();
        if (tabBar.contains(event.x(), event.y())) {
            switchToTab(resolveClickedTab(event.x(), tabBar));
            return true;
        }

        return getCurrentTabView().mouseClicked(event, isDoubleClick);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return getCurrentTabView().mouseReleased(event);
    }

    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        return getCurrentTabView().mouseDragged(event, mouseX, mouseY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bounds == null) {
            return false;
        }
        return getCurrentTabView().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean keyPressed(KeyEvent event) {
        return getCurrentTabView().keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return getCurrentTabView().charTyped(event);
    }

    private void buildTabs(UiTree.Scope scope, int mouseX, int mouseY) {
        UiRect tabBar = getTabBarRect();
        float segmentWidth = tabBar.width() / TABS.size();
        float labelScale = 0.62f;
        float textHeight = textRenderer.getHeight(labelScale);
        int activeIndex = getTabIndex(state.getClientSettingTab());
        float indicatorProgress = scope.animate(tabIndicatorAnimation, activeIndex);

        scope.pushAbsolute(tabBar, tabs -> {
            for (int index = 0; index < TABS.size(); index++) {
                TabDefinition tab = TABS.get(index);
                UiRect tabBounds = new UiRect(segmentWidth * index, 0.0f, segmentWidth, tabBar.height());
                UiRect absoluteTabBounds = new UiRect(tabBar.x() + tabBounds.x(), tabBar.y(), tabBounds.width(), tabBounds.height());
                boolean active = tab.tab() == state.getClientSettingTab();

                Animation hoverAnimation = tabHoverAnimations.get(tab.tab());
                float hover = tabs.animate(hoverAnimation, absoluteTabBounds.contains(mouseX, mouseY));
                if (hover > 0.01f) {
                    tabs.roundRect(tabBounds.x(), tabBounds.y(), tabBounds.width(), tabBounds.height(), 6.0f,
                            MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hover, 8));
                }

                String label = tab.component().getTranslatedName();
                float textWidth = textRenderer.getWidth(label, labelScale);
                float textX = tabBounds.x() + (tabBounds.width() - textWidth) / 2.0f;
                float textY = (tabBounds.height() - TAB_INDICATOR_HEIGHT - textHeight) / 2.0f;
                tabs.text(label, textX, textY, labelScale, active ? MD3Theme.PRIMARY : MD3Theme.TEXT_MUTED);
            }

            tabs.rect(0.0f, tabBar.height() - 1.0f, tabBar.width(), 1.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE, 40));

            float indicatorWidth = Math.min(56.0f, segmentWidth - 24.0f);
            float indicatorX = indicatorProgress * segmentWidth + (segmentWidth - indicatorWidth) / 2.0f;
            float indicatorY = tabBar.height() - TAB_INDICATOR_HEIGHT;
            tabs.roundRect(indicatorX, indicatorY, indicatorWidth, TAB_INDICATOR_HEIGHT,
                    TAB_INDICATOR_HEIGHT / 2.0f, MD3Theme.PRIMARY);
        });
    }

    private void switchToTab(PanelState.ClientSettingTab targetTab) {
        if (targetTab == state.getClientSettingTab()) {
            return;
        }
        getCurrentTabView().onDeactivated();
        state.setClientSettingTab(targetTab);
        getCurrentTabView().onActivated();
        markDirty();
    }

    private PanelState.ClientSettingTab resolveClickedTab(double mouseX, UiRect tabBar) {
        float segmentWidth = tabBar.width() / TABS.size();
        int index = Math.clamp((int) ((mouseX - tabBar.x()) / segmentWidth), 0, TABS.size() - 1);
        return TABS.get(index).tab();
    }

    private ClientSettingTabView getCurrentTabView() {
        return tabViews.get(state.getClientSettingTab());
    }


    private int getTabIndex(PanelState.ClientSettingTab tab) {
        return switch (tab) {
            case GENERAL -> 0;
            case FRIEND -> 1;
            case CONFIG -> 2;
            case ADDON -> 3;
        };
    }

    private UiRect getTabBarRect() {
        return new UiRect(
                bounds.x() + MD3Theme.PANEL_VIEWPORT_INSET,
                bounds.y() + 28.0f,
                bounds.width() - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f,
                TAB_BAR_HEIGHT
        );
    }

    private UiRect getContentBounds() {
        float tabBottom = bounds.y() + 28.0f + TAB_BAR_HEIGHT + 4.0f;
        return new UiRect(
                bounds.x() + MD3Theme.PANEL_VIEWPORT_INSET,
                tabBottom,
                bounds.width() - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f,
                bounds.bottom() - tabBottom - 6.0f
        );
    }

    private record TabDefinition(PanelState.ClientSettingTab tab, TranslateComponent component) {
    }

    @Override
    public void close() {
        tabViews.values().forEach(ClientSettingTabView::close);
    }

}
