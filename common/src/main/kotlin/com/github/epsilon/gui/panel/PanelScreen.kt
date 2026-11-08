package com.github.epsilon.gui.panel

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.lib.state.prepareLuminDataSources
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.panel.view.CategoryRailPanel
import com.github.epsilon.gui.panel.view.ClientSettingPanel
import com.github.epsilon.gui.panel.view.ModuleDetailPanel
import com.github.epsilon.gui.panel.view.ModuleListPanel
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.modules.impl.ClientSetting
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
    private val scene = UiScene(EpsilonUiTheme)
    private val popupHost = PanelPopupHost { scene.input }
    private val categoryRailPanel = CategoryRailPanel(state)
    private val moduleListPanel = ModuleListPanel(state)
    private val moduleDetailPanel = ModuleDetailPanel(state, popupHost)
    private val clientSettingPanel = ClientSettingPanel(state, popupHost)
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
            target = LuminRenderSystem.LuminRenderTarget.create("click-gui", window.width, window.height)
            renderTarget = target
        }
        target.clear()
        target.resize(window.width, window.height)

        LuminRenderSystem.setActiveTarget(target)
        prepareLuminDataSources()
        scene.beginFrame()
        try {

            state.ensureValidSelection()

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
            scene.endFrame()
        } finally {
            scene.abortFrame()
            LuminRenderSystem.setActiveTarget(null)
        }
    }

    private fun drawChrome(layout: PanelLayout.Layout) {

        scene.LuminUIScene(layout.panel, UiLayer.CHROME, LayerOffset(-20), key = "panel-chrome") {
            Shadow(
                0.0f, 0.0f, layout.panel.width, layout.panel.height,
                MD3Theme.PANEL_RADIUS.toFloat(), MD3Theme.PANEL_SHADOW_BLUR,
                MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.PANEL_SHADOW_ALPHA)
            )
            RoundRect(
                0.0f, 0.0f, layout.panel.width, layout.panel.height,
                MD3Theme.PANEL_RADIUS.toFloat(), MD3Theme.SURFACE
            )
            val localRail = layout.rail.relativeTo(layout.panel)
            Positioned(localRail.x, localRail.y, localRail.width, localRail.height) {
                RoundRect(
                    0.0f, 0.0f, layout.rail.width, layout.rail.height,
                    MD3Theme.SECTION_RADIUS.toFloat(), MD3Theme.SURFACE_DIM
                )
            }
            if (state.isClientSettingMode()) {
                val csW = layout.detail.right() - layout.modules.x
                val csH = layout.modules.height
                Positioned(
                    layout.modules.x - layout.panel.x, layout.modules.y - layout.panel.y, csW, csH
                ) {
                    RoundRect(0.0f, 0.0f, csW, csH, MD3Theme.SECTION_RADIUS.toFloat(), MD3Theme.SURFACE_DIM)
                }
            } else {
                val localModules = layout.modules.relativeTo(layout.panel)
                Positioned(localModules.x, localModules.y, localModules.width, localModules.height) {
                    RoundRect(
                        0.0f, 0.0f, layout.modules.width, layout.modules.height,
                        MD3Theme.SECTION_RADIUS.toFloat(), MD3Theme.SURFACE_DIM
                    )
                }
                val localDetail = layout.detail.relativeTo(layout.panel)
                Positioned(localDetail.x, localDetail.y, localDetail.width, localDetail.height) {
                    RoundRect(
                        0.0f, 0.0f, layout.detail.width, layout.detail.height,
                        MD3Theme.SECTION_RADIUS.toFloat(), MD3Theme.SURFACE_DIM
                    )
                }
            }
        }
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

        return this
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val mouse = LuminRenderSystem.toEpsilonMouseEvent(event)
        if (popupHost.activePopup != null) return popupHost.mouseClicked(mouse, isDoubleClick)
        val layout = PanelLayout.compute(
            LuminRenderSystem.getScaledWidthInt(),
            LuminRenderSystem.getScaledHeightInt(),
            categoryRailPanel.getAnimatedWidth()
        )
        if (!layout.panel.contains(mouse.x(), mouse.y())) {
            scene.input.clearFocus()
            if (ClientSetting.closeOnOutside) minecraft.gui.setScreen(null)
            return true
        }
        return scene.input.mouseClicked(mouse, isDoubleClick) || super.mouseClicked(mouse, isDoubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (popupHost.activePopup != null) return popupHost.mouseScrolled(
            LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY), scrollX, scrollY
        )
        return scene.input.mouseScrolled(
            LuminRenderSystem.toEpsilonMouseX(mouseX),
            LuminRenderSystem.toEpsilonMouseY(mouseY),
            scrollX,
            scrollY
        )
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (popupHost.activePopup != null) return popupHost.mouseReleased(LuminRenderSystem.toEpsilonMouseEvent(event))

        return scene.input.mouseReleased(LuminRenderSystem.toEpsilonMouseEvent(event))
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (popupHost.activePopup != null) return popupHost.mouseDragged(
            LuminRenderSystem.toEpsilonMouseEvent(event),
            LuminRenderSystem.toEpsilonMouseX(mouseX),
            LuminRenderSystem.toEpsilonMouseY(mouseY)
        )

        return scene.input.mouseDragged(
            LuminRenderSystem.toEpsilonMouseEvent(event),
            LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY)
        )
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (popupHost.activePopup != null) return popupHost.keyPressed(event)
        if (scene.input.keyPressed(event)) return true
        if (event.isEscape) {
            onClose(); return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (popupHost.activePopup != null) return popupHost.charTyped(event)
        return scene.input.charTyped(event) || super.charTyped(event)
    }

    override fun preeditUpdated(event: PreeditEvent?): Boolean {
        if (popupHost.activePopup != null) popupHost.preeditUpdated(event) else scene.input.preeditUpdated(event)

        this.preeditOverlay = if (event != null) IMEPreeditOverlay(event, this.font, 10) else null
        return true
    }

    override fun onClose() {
        IMEFocusHelper.forceDeactivate()
        super.onClose()
    }

    override fun removed() {
        super.removed()
        scene.resetComposition()
        popupHost.close()
        moduleListPanel.resetTransientState()
        state.setSearchQuery("")
        moduleDetailPanel.resetTransientState()
        clientSettingPanel.resetTransientState()
        state.setListeningKeyBindModule(null)
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
