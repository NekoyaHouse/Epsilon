package com.github.epsilon.gui.panel.view

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.gui.lib.LuminUIScene
import com.github.epsilon.gui.lib.Positioned
import com.github.epsilon.gui.lib.Rect
import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminAnimatable
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.view.settings.ClientSettingTabView
import com.github.epsilon.gui.panel.view.settings.ConfigClientSettingTab
import com.github.epsilon.gui.panel.view.settings.FriendClientSettingTab
import com.github.epsilon.gui.panel.view.settings.GeneralClientSettingTab
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
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

    fun render(
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
        val contentKey = ContentKey(
            state.getClientSettingTab(), TranslationManager.getRevision(), themeFingerprint()
        )
        renderBatch.LuminUIScene(bounds, key = contentKey) {
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

    fun markDirty() {
        for (view in tabViews.values) {
            view.markDirty()
        }
    }

    fun hasActiveAnimations(): Boolean {
        val tabsAnimating = tabIndicatorAnimation.isRunning ||
                tabHoverAnimations.values.any { animation -> animation.isRunning }
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

    /** 主题指纹：tab 条颜色来自可变主题，主题切换后缓存必须失效。 */
    private fun themeFingerprint(): Int = MD3Theme.SURFACE.rgb * 31 + MD3Theme.PRIMARY.rgb

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

    /** tab 条与标题的静态输入：当前 tab、翻译版本与主题。 */
    private data class ContentKey(val tab: PanelState.ClientSettingTab, val i18nRevision: Long, val theme: Int)

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
