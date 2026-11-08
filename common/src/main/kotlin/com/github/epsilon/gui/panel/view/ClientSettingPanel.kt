package com.github.epsilon.gui.panel.view

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminAnimatable
import com.github.epsilon.gui.lib.state.LuminView
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.view.settings.ClientSettingTabView
import com.github.epsilon.gui.panel.view.settings.ConfigClientSettingTab
import com.github.epsilon.gui.panel.view.settings.FriendClientSettingTab
import com.github.epsilon.gui.panel.view.settings.GeneralClientSettingTab
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.utils.render.animation.Easing
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.*

class ClientSettingPanel(
    protected val state: PanelState,
    popupHost: PanelPopupHost,
) : AutoCloseable {

    private val tabViews =
        EnumMap<PanelState.ClientSettingTab, ClientSettingTabView>(PanelState.ClientSettingTab::class.java)
    private val tabHoverAnimations =
        EnumMap<PanelState.ClientSettingTab, LuminAnimatable>(PanelState.ClientSettingTab::class.java)
    private val tabIndicatorAnimation =
        LuminAnimatable(getTabIndex(state.getClientSettingTab()).toFloat(), Easing.EASE_OUT_CUBIC, 200L)

    private var bounds: UiRect? = null

    init {
        tabViews[PanelState.ClientSettingTab.GENERAL] = GeneralClientSettingTab(state, popupHost)
        tabViews[PanelState.ClientSettingTab.FRIEND] = FriendClientSettingTab(state)
        tabViews[PanelState.ClientSettingTab.CONFIG] = ConfigClientSettingTab(state, popupHost)

        for (tab in PanelState.ClientSettingTab.entries) {
            tabHoverAnimations[tab] = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 120L)
        }
    }

    private val luminView = LuminView()

    fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        luminView.render(mouseX, mouseY) {
            renderContent(
                guiGraphics,
                renderBatch,
                bounds,
                mouseX,
                mouseY,
                partialTick
            )
        }
    }

    private fun renderContent(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.bounds = bounds

        val activeTab = getCurrentTabView()
        val popupConsumesHover = activeTab.consumesHover(mouseX, mouseY)
        val effectiveMouseX = if (popupConsumesHover) Int.MIN_VALUE else mouseX
        val effectiveMouseY = if (popupConsumesHover) Int.MIN_VALUE else mouseY

        // 推式动画：指示条与逐个 tab 的悬停进度在命令阶段写目标，绘制块内只读取 value。
        val tabBar = getTabBarRect()
        val segmentWidth = tabBar.width / TABS.size
        tabIndicatorAnimation.target = getTabIndex(state.getClientSettingTab()).toFloat()
        for (index in TABS.indices) {
            val tab = TABS[index]
            val tabBounds = UiRect(tabBar.x + segmentWidth * index, tabBar.y, segmentWidth, tabBar.height)
            tabHoverAnimations[tab.tab]!!.target =
                if (tabBounds.contains(effectiveMouseX.toDouble(), effectiveMouseY.toDouble())) 1.0f else 0.0f
        }

        // 标题与 tab 条只随当前 tab / 语言 / 主题变化重建；切换 tab 会同时切换下面的内容视图。

        renderBatch.LuminUIScene(bounds, key = this) {

            Text(
                EpsilonTranslations.Gui.CLIENT_SETTINGS.getTranslatedName(), MD3Theme.PANEL_TITLE_INSET,
                10.0f, 0.78f, MD3Theme.TEXT_PRIMARY
            )
            buildTabs()
        }

        activeTab.render(
            guiGraphics, renderBatch.view(10), getContentBounds(), effectiveMouseX, effectiveMouseY, partialTick
        )
    }

    fun flushContent() {
        getCurrentTabView().flushContent()
    }

    fun resetTransientState() {
        for (view in tabViews.values) {
            view.onDeactivated()
        }
    }


    private fun UiScope.buildTabs() {
        val tabBar = getTabBarRect()
        val localTabBar = tabBar.relativeTo(origin)
        val segmentWidth = tabBar.width / TABS.size
        val labelScale = 0.62f
        val textHeight = metrics.textHeight(labelScale)
        val indicatorProgress = tabIndicatorAnimation.value

        Positioned(localTabBar.x, localTabBar.y, localTabBar.width, localTabBar.height) {
            for (index in TABS.indices) {
                val tab = TABS[index]
                val tabBounds = UiRect(segmentWidth * index, 0.0f, segmentWidth, tabBar.height)
                Interaction(tab.tab, tabBounds) { onClick { switchToTab(tab.tab); true } }
                val active = tab.tab == state.getClientSettingTab()

                val hover = tabHoverAnimations[tab.tab]!!.value
                if (hover > 0.01f) {
                    RoundRect(
                        tabBounds.x, tabBounds.y, tabBounds.width, tabBounds.height, 6.0f,
                        MD3Theme.stateLayer(MD3Theme.TEXT_PRIMARY, hover, 8)
                    )
                }

                val label = tab.component.getTranslatedName()
                val textWidth = metrics.textWidth(label, labelScale)
                val textX = tabBounds.x + (tabBounds.width - textWidth) / 2.0f
                val textY = (tabBounds.height - TAB_INDICATOR_HEIGHT - textHeight) / 2.0f
                Text(label, textX, textY, labelScale, if (active) MD3Theme.PRIMARY else MD3Theme.TEXT_MUTED)
            }

            Rect(0.0f, tabBar.height - 1.0f, tabBar.width, 1.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE, 40))

            val indicatorWidth = minOf(56.0f, segmentWidth - 24.0f)
            val indicatorX = indicatorProgress * segmentWidth + (segmentWidth - indicatorWidth) / 2.0f
            val indicatorY = tabBar.height - TAB_INDICATOR_HEIGHT
            RoundRect(
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
        luminView.close()

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
