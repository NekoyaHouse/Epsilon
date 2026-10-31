package com.github.epsilon.gui.panel

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.panel.input.PanelInputRouter
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.panel.view.CategoryRailPanel
import com.github.epsilon.gui.panel.view.ClientSettingPanel
import com.github.epsilon.gui.panel.view.ModuleDetailPanel
import com.github.epsilon.gui.panel.view.ModuleListPanel
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.modules.impl.ClientSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.IMEPreeditOverlay
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent
import net.minecraft.network.chat.Component

/**
 * 面板 UI 的主屏幕宿主。
 *
 * 它负责维护全局状态、调度各子面板的 extract 阶段、统一 flush renderer，
 * 并将输入事件路由到 rail、模块列表、详情面板、客户端设置面板和弹窗宿主。
 */
object PanelScreen : Screen(Component.literal("PanelGui")) {

    private val state = PanelState()
    private val dirtyState = PanelDirtyState()
    private val textRenderer: TextRenderer = TextRenderer.create()
    private val scene = UiScene(EpsilonUiTheme)
    private val popupHost = PanelPopupHost()
    private val inputRouter = PanelInputRouter()
    private val categoryRailPanel = CategoryRailPanel(state, textRenderer)
    private val moduleListPanel = ModuleListPanel(state, textRenderer)
    private val moduleDetailPanel = ModuleDetailPanel(state, textRenderer, popupHost)
    private val clientSettingPanel = ClientSettingPanel(state, textRenderer, popupHost)
    private var lastWidth = -1
    private var lastHeight = -1
    private var lastSelectedCategory = ""
    private var lastSelectedModule = ""
    private var lastSearchQuery = ""
    private var lastModuleSort: ClientSetting.ModuleSort? = null
    private var lastSidebarExpanded = false
    private var lastClientSettingMode = false
    private var lastI18nRevision = Long.MIN_VALUE

    private var preeditOverlay: IMEPreeditOverlay? = null

    private var renderTarget: LuminRenderSystem.LuminRenderTarget? = null

    override fun isPauseScreen(): Boolean {
        return false
    }

    /**
     * 提取面板当前帧的渲染状态。
     *
     * 该方法会计算布局、推动动画、让各个子面板把 UI 编译进共享批次，
     * 最后在统一的 render 提交阶段执行 flush。
     */
    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val window = minecraft.window
        var target = renderTarget
        if (target == null) {
            target = LuminRenderSystem.LuminRenderTarget.create("click-gui", window.getWidth(), window.getHeight())
            renderTarget = target
        }
        target.clear()
        target.resize(window.getWidth(), window.getHeight())

        LuminRenderSystem.setActiveTarget(target)
        scene.beginFrame()

        val currentCategory = state.getSelectedCategory().name
        val currentModule = state.getSelectedModule()?.name ?: ""
        val currentQuery = state.getSearchQuery()
        val currentModuleSort = ClientSetting.moduleSort.value
        val sidebarExpanded = state.isSidebarExpanded()
        val clientSettingMode = state.isClientSettingMode()
        val currentI18nRevision = TranslationManager.getRevision()
        if (lastSelectedCategory != currentCategory ||
            lastSelectedModule != currentModule ||
            lastSearchQuery != currentQuery ||
            lastModuleSort != currentModuleSort ||
            lastSidebarExpanded != sidebarExpanded ||
            lastClientSettingMode != clientSettingMode ||
            lastI18nRevision != currentI18nRevision
        ) {
            dirtyState.markAllDirty()
            lastSelectedCategory = currentCategory
            lastSelectedModule = currentModule
            lastSearchQuery = currentQuery
            lastModuleSort = currentModuleSort
            lastSidebarExpanded = sidebarExpanded
            lastClientSettingMode = clientSettingMode
            lastI18nRevision = currentI18nRevision
        }

        if (categoryRailPanel.hasActiveAnimations() ||
            moduleListPanel.hasActiveAnimations() ||
            moduleDetailPanel.hasActiveAnimations() ||
            clientSettingPanel.hasActiveAnimations()
        ) {
            dirtyState.markAllDirty()
        }

        if (width != lastWidth || height != lastHeight) {
            dirtyState.markLayoutDirty()
            lastWidth = width
            lastHeight = height
        }

        if (dirtyState.consumeModuleListDirty()) {
            moduleListPanel.markDirty()
        }
        if (dirtyState.consumeDetailDirty()) {
            moduleDetailPanel.markDirty()
        }
        if (dirtyState.consumeClientSettingDirty()) {
            clientSettingPanel.markDirty()
        }

        val railWidth = categoryRailPanel.getAnimatedWidth()
        val layout = PanelLayout.compute(
            LuminRenderSystem.getScaledWidthInt(), LuminRenderSystem.getScaledHeightInt(), railWidth
        )
        popupHost.setOverlayBounds(layout.panel)

        drawChrome(layout)
        val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)
        val popupActive = popupHost.activePopup != null
        val panelMouseX = if (popupActive) Int.MIN_VALUE else epsilonMouseX
        val panelMouseY = if (popupActive) Int.MIN_VALUE else epsilonMouseY
        categoryRailPanel.render(
            guiGraphics, scene.batch(UiLayer.CONTENT, -20), layout.rail, panelMouseX, panelMouseY, partialTick
        )
        if (state.isClientSettingMode()) {
            val clientSettingsBounds = UiRect(
                layout.modules.x, layout.modules.y,
                layout.detail.right() - layout.modules.x,
                layout.modules.height
            )
            clientSettingPanel.render(
                guiGraphics, scene.batch(UiLayer.CONTENT, 10), clientSettingsBounds,
                panelMouseX, panelMouseY, partialTick
            )
        } else {
            moduleListPanel.render(
                guiGraphics, scene.batch(UiLayer.CONTENT, 0), layout.modules, panelMouseX, panelMouseY, partialTick
            )
            moduleDetailPanel.render(
                guiGraphics, scene.batch(UiLayer.CONTENT, 20), layout.detail, panelMouseX, panelMouseY, partialTick
            )
        }

        scene.flush()
        flushQueuedContentBuffers()
        scene.clear()
        renderPopup(guiGraphics, epsilonMouseX, epsilonMouseY, partialTick)

        LuminRenderSystem.setActiveTarget(null)

        val overlay = preeditOverlay
        if (overlay != null) {
            overlay.updateInputPosition(IMEFocusHelper.activeCursorX.toInt(), IMEFocusHelper.activeCursorY.toInt())
            guiGraphics.setPreeditOverlay(overlay)
        }
        guiGraphics.blit(
            target.getIdentifier(), 0, 0, window.guiScaledWidth, window.guiScaledHeight, 0.0f, 1.0f, 1.0f, 0.0f
        )
        popupHost.extractOverlay(guiGraphics, epsilonMouseX, epsilonMouseY, partialTick)
    }

    private fun drawChrome(layout: PanelLayout.Layout) {
        val tree = UiTree.build { scope ->
            scope.pushAbsolute(layout.panel) { panel ->
                panel.shadow(
                    0.0f, 0.0f, layout.panel.width, layout.panel.height,
                    MD3Theme.PANEL_RADIUS.toFloat(), MD3Theme.PANEL_SHADOW_BLUR,
                    MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.PANEL_SHADOW_ALPHA)
                )
                panel.roundRect(
                    0.0f, 0.0f, layout.panel.width, layout.panel.height,
                    MD3Theme.PANEL_RADIUS.toFloat(), MD3Theme.SURFACE
                )
            }
            scope.pushAbsolute(layout.rail) { rail ->
                rail.roundRect(
                    0.0f, 0.0f, layout.rail.width, layout.rail.height,
                    MD3Theme.SECTION_RADIUS.toFloat(), MD3Theme.SURFACE_DIM
                )
            }
            if (state.isClientSettingMode()) {
                val csW = layout.detail.right() - layout.modules.x
                val csH = layout.modules.height
                scope.pushAbsolute(layout.modules.x, layout.modules.y) { clientSettings ->
                    clientSettings.roundRect(
                        0.0f, 0.0f, csW, csH, MD3Theme.SECTION_RADIUS.toFloat(), MD3Theme.SURFACE_DIM
                    )
                }
            } else {
                scope.pushAbsolute(layout.modules) { modules ->
                    modules.roundRect(
                        0.0f, 0.0f, layout.modules.width, layout.modules.height,
                        MD3Theme.SECTION_RADIUS.toFloat(), MD3Theme.SURFACE_DIM
                    )
                }
                scope.pushAbsolute(layout.detail) { detail ->
                    detail.roundRect(
                        0.0f, 0.0f, layout.detail.width, layout.detail.height,
                        MD3Theme.SECTION_RADIUS.toFloat(), MD3Theme.SURFACE_DIM
                    )
                }
            }
        }
        scene.submit(UiLayer.CHROME, -20, tree)
    }

    private fun flushQueuedContentBuffers() {
        if (state.isClientSettingMode()) {
            clientSettingPanel.flushContent()
        } else {
            moduleListPanel.flushContent()
            moduleDetailPanel.flushContent()
        }
    }

    private fun renderPopup(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (popupHost.activePopup == null) {
            return
        }
        popupHost.render(guiGraphics, scene.batch(UiLayer.POPUP), mouseX, mouseY, partialTick)
        scene.flush()
        popupHost.flush()
        scene.clear()
    }

    fun openClientSettings(): PanelScreen {
        state.setClientSettingMode(true)
        state.setClientSettingTab(PanelState.ClientSettingTab.GENERAL)
        dirtyState.markAllDirty()
        return this
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        val mouseX = epsilonEvent.x()
        val mouseY = epsilonEvent.y()
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            if (state.getListeningKeyBindModule() != null &&
                moduleDetailPanel.mouseClicked(epsilonEvent, isDoubleClick)
            ) {
                dirtyState.markAllDirty()
                return true
            }
            if (state.getListeningKeybindSetting() != null) {
                val handledListening = if (state.isClientSettingMode()) {
                    clientSettingPanel.mouseClicked(epsilonEvent, isDoubleClick)
                } else {
                    moduleDetailPanel.mouseClicked(epsilonEvent, isDoubleClick)
                }
                if (handledListening) {
                    dirtyState.markAllDirty()
                    return true
                }
            }
            return super.mouseClicked(epsilonEvent, isDoubleClick)
        }

        if (popupHost.activePopup != null) {
            return inputRouter.routeMouseClicked(
                epsilonEvent, isDoubleClick, popupHost, moduleDetailPanel, moduleListPanel,
                categoryRailPanel, clientSettingPanel, state.isClientSettingMode()
            ) || super.mouseClicked(epsilonEvent, isDoubleClick)
        }

        val layout = PanelLayout.compute(
            LuminRenderSystem.getScaledWidthInt(), LuminRenderSystem.getScaledHeightInt(),
            categoryRailPanel.getAnimatedWidth()
        )
        if (!layout.panel.contains(mouseX, mouseY)) {
            if (ClientSetting.closeOnOutside) minecraft.gui.setScreen(null)
            return true
        }
        if (!state.isClientSettingMode()) {
            moduleListPanel.handleGlobalClick(mouseX, mouseY)
        }
        val handled = inputRouter.routeMouseClicked(
            epsilonEvent, isDoubleClick, popupHost, moduleDetailPanel, moduleListPanel,
            categoryRailPanel, clientSettingPanel, state.isClientSettingMode()
        )
        if (handled) {
            dirtyState.markAllDirty()
        }
        return handled || super.mouseClicked(epsilonEvent, isDoubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)
        if (popupHost.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
            dirtyState.markAllDirty()
            return true
        }
        if (state.isClientSettingMode()) {
            if (clientSettingPanel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
                dirtyState.markClientSettingDirty()
                return true
            }
        } else {
            if (moduleListPanel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
                dirtyState.markModuleListDirty()
                return true
            }
            if (moduleDetailPanel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
                dirtyState.markDetailDirty()
                return true
            }
        }
        return super.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        if (inputRouter.routeMouseReleased(
                epsilonEvent, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel,
                state.isClientSettingMode()
            )
        ) {
            dirtyState.markAllDirty()
            return true
        }
        return super.mouseReleased(epsilonEvent)
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)
        if (inputRouter.routeMouseDragged(
                epsilonEvent, epsilonMouseX, epsilonMouseY, popupHost, moduleDetailPanel, moduleListPanel,
                clientSettingPanel, state.isClientSettingMode()
            )
        ) {
            dirtyState.markAllDirty()
            return true
        }
        return super.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (inputRouter.routeKeyPressed(
                event, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel,
                state.isClientSettingMode()
            )
        ) {
            dirtyState.markAllDirty()
            return true
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (inputRouter.routeCharTyped(
                event, popupHost, moduleDetailPanel, moduleListPanel, clientSettingPanel,
                state.isClientSettingMode()
            )
        ) {
            dirtyState.markAllDirty()
            return true
        }
        return super.charTyped(event)
    }

    override fun preeditUpdated(event: PreeditEvent?): Boolean {
        this.preeditOverlay = if (event != null) IMEPreeditOverlay(event, this.font, 10) else null
        return true
    }

    override fun onClose() {
        IMEFocusHelper.forceDeactivate()
        super.onClose()
    }

    override fun removed() {
        super.removed()
        popupHost.close()
        moduleListPanel.resetTransientState()
        state.setSearchQuery("")
        moduleDetailPanel.resetTransientState()
        clientSettingPanel.resetTransientState()
        state.setListeningKeyBindModule(null)
        state.setListeningKeybindSetting(null)
        IMEFocusHelper.forceDeactivate()
        preeditOverlay = null
    }

    /**
     * 返回当前面板使用的离屏渲染目标。
     *
     * @return 当前渲染目标；首次渲染前可能为 {@code null}
     */
    fun getRenderTarget(): LuminRenderSystem.LuminRenderTarget? {
        return renderTarget
    }

}
