package com.github.epsilon.gui.panel.view

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.view.settings.ClientSettingTabView
import com.github.epsilon.gui.panel.view.settings.ConfigClientSettingTab
import com.github.epsilon.gui.panel.view.settings.FriendClientSettingTab
import com.github.epsilon.gui.panel.view.settings.GeneralClientSettingTab
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.util.*

class ClientSettingPanel(
    protected val state: PanelState,
    private val textRenderer: TextRenderer,
    popupHost: PanelPopupHost,
) : AutoCloseable {

    private val tabViews = EnumMap<PanelState.ClientSettingTab, ClientSettingTabView>(PanelState.ClientSettingTab::class.java)
    private val tabHoverAnimations =
        EnumMap<PanelState.ClientSettingTab, Animation>(PanelState.ClientSettingTab::class.java)
    private val tabIndicatorAnimation = Animation(Easing.EASE_OUT_CUBIC, 200L)

    private var bounds: UiRect? = null

    init {
        tabViews[PanelState.ClientSettingTab.GENERAL] = GeneralClientSettingTab(state, textRenderer, popupHost)
        tabViews[PanelState.ClientSettingTab.FRIEND] = FriendClientSettingTab(state, textRenderer)
        tabViews[PanelState.ClientSettingTab.CONFIG] = ConfigClientSettingTab(state, textRenderer, popupHost)

        for (tab in PanelState.ClientSettingTab.entries) {
            val animation = Animation(Easing.EASE_OUT_CUBIC, 120L)
            animation.startValue = 0.0f
            tabHoverAnimations[tab] = animation
        }
        tabIndicatorAnimation.startValue = getTabIndex(state.getClientSettingTab()).toFloat()
    }

    fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.bounds = bounds

        val activeTab = getCurrentTabView()
        val popupConsumesHover = activeTab.consumesHover(mouseX, mouseY)
        val effectiveMouseX = if (popupConsumesHover) Int.MIN_VALUE else mouseX
        val effectiveMouseY = if (popupConsumesHover) Int.MIN_VALUE else mouseY

        val tree = UiTree.build { scope ->
            scope.pushAbsolute(bounds) { panel ->
                panel.text(
                    EpsilonTranslations.Gui.CLIENT_SETTINGS.getTranslatedName(), MD3Theme.PANEL_TITLE_INSET,
                    10.0f, 0.78f, MD3Theme.TEXT_PRIMARY
                )
            }
            buildTabs(scope, effectiveMouseX, effectiveMouseY)
        }
        renderBatch.render(tree)

        activeTab.render(
            guiGraphics, renderBatch.view(10), getContentBounds(), effectiveMouseX, effectiveMouseY, partialTick
        )
    }

    fun flushContent() {
        getCurrentTabView().flushContent()
    }

    fun markDirty() {
        for (view in tabViews.values) {
            view.markDirty()
        }
    }

    fun hasActiveAnimations(): Boolean {
        val tabsAnimating = !tabIndicatorAnimation.isFinished ||
                tabHoverAnimations.values.any { animation -> !animation.isFinished }
        return tabsAnimating || getCurrentTabView().hasActiveAnimations()
    }

    fun resetTransientState() {
        for (view in tabViews.values) {
            view.onDeactivated()
        }
    }

    fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (bounds == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }

        if (state.getListeningKeybindSetting() != null) {
            state.setListeningKeybindSetting(null)
            markDirty()
        }

        val tabBar = getTabBarRect()
        if (tabBar.contains(event.x(), event.y())) {
            switchToTab(resolveClickedTab(event.x(), tabBar))
            return true
        }

        return getCurrentTabView().mouseClicked(event, isDoubleClick)
    }

    fun mouseReleased(event: MouseButtonEvent): Boolean {
        return getCurrentTabView().mouseReleased(event)
    }

    fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        return getCurrentTabView().mouseDragged(event, mouseX, mouseY)
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        return bounds != null && getCurrentTabView().mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    fun keyPressed(event: KeyEvent): Boolean {
        return getCurrentTabView().keyPressed(event)
    }

    fun charTyped(event: CharacterEvent): Boolean {
        return getCurrentTabView().charTyped(event)
    }

    private fun buildTabs(scope: UiTree.Scope, mouseX: Int, mouseY: Int) {
        val tabBar = getTabBarRect()
        val segmentWidth = tabBar.width / TABS.size
        val labelScale = 0.62f
        val textHeight = textRenderer.getHeight(labelScale)
        val activeIndex = getTabIndex(state.getClientSettingTab()).toFloat()
        val indicatorProgress = scope.animate(tabIndicatorAnimation, activeIndex)

        scope.pushAbsolute(tabBar) { tabs ->
            for (index in TABS.indices) {
                val tab = TABS[index]
                val tabBounds = UiRect(segmentWidth * index, 0.0f, segmentWidth, tabBar.height)
                val absoluteTabBounds = UiRect(tabBar.x + tabBounds.x, tabBar.y, tabBounds.width, tabBounds.height)
                val active = tab.tab == state.getClientSettingTab()

                val hoverAnimation = tabHoverAnimations[tab.tab]!!
                val hover = tabs.animate(hoverAnimation, absoluteTabBounds.contains(mouseX.toDouble(), mouseY.toDouble()))
                if (hover > 0.01f) {
                    tabs.roundRect(
                        tabBounds.x, tabBounds.y, tabBounds.width, tabBounds.height, 6.0f,
                        MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hover, 8)
                    )
                }

                val label = tab.component.getTranslatedName()
                val textWidth = textRenderer.getWidth(label, labelScale)
                val textX = tabBounds.x + (tabBounds.width - textWidth) / 2.0f
                val textY = (tabBounds.height - TAB_INDICATOR_HEIGHT - textHeight) / 2.0f
                tabs.text(label, textX, textY, labelScale, if (active) MD3Theme.PRIMARY else MD3Theme.TEXT_MUTED)
            }

            tabs.rect(0.0f, tabBar.height - 1.0f, tabBar.width, 1.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE, 40))

            val indicatorWidth = minOf(56.0f, segmentWidth - 24.0f)
            val indicatorX = indicatorProgress * segmentWidth + (segmentWidth - indicatorWidth) / 2.0f
            val indicatorY = tabBar.height - TAB_INDICATOR_HEIGHT
            tabs.roundRect(
                indicatorX, indicatorY, indicatorWidth, TAB_INDICATOR_HEIGHT,
                TAB_INDICATOR_HEIGHT / 2.0f, MD3Theme.PRIMARY
            )
        }
    }

    private fun switchToTab(targetTab: PanelState.ClientSettingTab) {
        if (targetTab == state.getClientSettingTab()) {
            return
        }
        getCurrentTabView().onDeactivated()
        state.setClientSettingTab(targetTab)
        getCurrentTabView().onActivated()
        markDirty()
    }

    private fun resolveClickedTab(mouseX: Double, tabBar: UiRect): PanelState.ClientSettingTab {
        val segmentWidth = tabBar.width / TABS.size
        val index = ((mouseX - tabBar.x) / segmentWidth).toInt().coerceIn(0, TABS.size - 1)
        return TABS[index].tab
    }

    private fun getCurrentTabView(): ClientSettingTabView {
        return tabViews[state.getClientSettingTab()]!!
    }

    private fun getTabIndex(tab: PanelState.ClientSettingTab): Int {
        return when (tab) {
            PanelState.ClientSettingTab.GENERAL -> 0
            PanelState.ClientSettingTab.FRIEND -> 1
            PanelState.ClientSettingTab.CONFIG -> 2
        }
    }

    private fun getTabBarRect(): UiRect {
        val currentBounds = bounds!!
        return UiRect(
            currentBounds.x + MD3Theme.PANEL_VIEWPORT_INSET,
            currentBounds.y + 28.0f,
            currentBounds.width - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f,
            TAB_BAR_HEIGHT
        )
    }

    private fun getContentBounds(): UiRect {
        val currentBounds = bounds!!
        val tabBottom = currentBounds.y + 28.0f + TAB_BAR_HEIGHT + 4.0f
        return UiRect(
            currentBounds.x + MD3Theme.PANEL_VIEWPORT_INSET,
            tabBottom,
            currentBounds.width - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f,
            currentBounds.bottom() - tabBottom - 6.0f
        )
    }

    private data class TabDefinition(val tab: PanelState.ClientSettingTab, val component: TranslateComponent)

    override fun close() {
        for (view in tabViews.values) {
            view.close()
        }
    }

    private companion object {

        private const val TAB_BAR_HEIGHT = 26.0f
        private const val TAB_INDICATOR_HEIGHT = 2.5f

        private val TABS = listOf(
            TabDefinition(PanelState.ClientSettingTab.GENERAL, EpsilonTranslations.Gui.TAB_GENERAL),
            TabDefinition(PanelState.ClientSettingTab.FRIEND, EpsilonTranslations.Gui.TAB_FRIEND),
            TabDefinition(PanelState.ClientSettingTab.CONFIG, EpsilonTranslations.Gui.TAB_CONFIG)
        )

    }

}
