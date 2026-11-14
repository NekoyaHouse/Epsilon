package com.github.epsilon.gui.panel

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.gui.lib.LuminUIScene
import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.Shadow
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.lib.state.LuminAnimatable
import com.github.epsilon.gui.lib.state.prepareLuminDataSources
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.panel.view.CategoryRailPanel
import com.github.epsilon.gui.panel.view.ClientSettingPanel
import com.github.epsilon.gui.panel.view.ModuleDetailPanel
import com.github.epsilon.gui.panel.view.ModuleListPanel
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.modules.impl.ClientSetting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.IMEPreeditOverlay
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent
import net.minecraft.client.renderer.RenderPipelines
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
    private val entranceAnim = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 360L)
    private var entranceMode = ClientSetting.PanelEntranceAnimation.Focus
    private var entranceStarted = false
    private var entranceScale = 1.0f
    private var entranceOffsetY = 0.0f

    override fun init() {
        super.init()
        if (!entranceStarted) {
            entranceStarted = true
            entranceMode = ClientSetting.panelEntranceAnimation
            entranceAnim.snapTo(if (entranceMode == ClientSetting.PanelEntranceAnimation.None) 1.0f else 0.0f)
            entranceAnim.target = 1.0f
            entranceAnim.reset()
        }
    }

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
        target.resize(window.width, window.height)
        target.clear()

        LuminRenderSystem.setActiveTarget(target)
        prepareLuminDataSources()
        scene.beginFrame()
        try {
            val entrance = entranceAnim.value
            val remaining = 1.0f - entrance
            entranceScale =
                if (entranceMode == ClientSetting.PanelEntranceAnimation.Focus) 1.0f - remaining * 0.08f else 1.0f
            entranceOffsetY = when (entranceMode) {
                ClientSetting.PanelEntranceAnimation.Focus -> remaining * 14.0f
                ClientSetting.PanelEntranceAnimation.Slide -> remaining * 32.0f
                else -> 0.0f
            }

            state.ensureValidSelection()

            val railWidth = categoryRailPanel.getAnimatedWidth()
            val layout = PanelLayout.compute(
                LuminRenderSystem.getScaledWidthInt(), LuminRenderSystem.getScaledHeightInt(), railWidth
            )
            popupHost.setOverlayBounds(layout.panel)

            drawChrome(layout)
            val epsilonMouseX = panelMouseX(mouseX.toDouble()).toInt()
            val epsilonMouseY = panelMouseY(mouseY.toDouble()).toInt()
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

            if (entranceMode == ClientSetting.PanelEntranceAnimation.Focus && entrance < 1.0f) {
                val blurPad = MD3Theme.PANEL_SHADOW_BLUR
                BlurShader.render(
                    target, layout.panel.x - blurPad, layout.panel.y - blurPad,
                    layout.panel.width + blurPad * 2.0f, layout.panel.height + blurPad * 2.0f,
                    0.0f, remaining * 12.0f, preserveAlpha = true
                )
            }
            LuminRenderSystem.setActiveTarget(null)

            val overlay = preeditOverlay
            if (overlay != null) {
                val centerX = LuminRenderSystem.getScaledWidth() * 0.5f
                val centerY = LuminRenderSystem.getScaledHeight() * 0.5f
                overlay.updateInputPosition(
                    (((IMEFocusHelper.activeCursorX - centerX) * entranceScale + centerX) *
                            window.guiScaledWidth / LuminRenderSystem.getScaledWidth()).toInt(),
                    (((IMEFocusHelper.activeCursorY - centerY) * entranceScale + centerY + entranceOffsetY) *
                            window.guiScaledHeight / LuminRenderSystem.getScaledHeight()).toInt()
                )
                guiGraphics.setPreeditOverlay(overlay)
            }
            val pose = guiGraphics.pose()
            pose.pushMatrix()
            try {
                pose.translate(
                    window.guiScaledWidth * (1.0f - entranceScale) * 0.5f,
                    window.guiScaledHeight * (1.0f - entranceScale) * 0.5f +
                            entranceOffsetY * window.guiScaledHeight / LuminRenderSystem.getScaledHeight()
                )
                pose.scale(entranceScale, entranceScale)
                guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED, target.getIdentifier(), 0, 0, 0.0f, window.height.toFloat(),
                    window.guiScaledWidth, window.guiScaledHeight, window.width, -window.height,
                    window.width, window.height, ((entrance * 255).toInt() shl 24) or 0xFFFFFF
                )
                popupHost.extractOverlay(guiGraphics, epsilonMouseX, epsilonMouseY, partialTick)
            } finally {
                pose.popMatrix()
            }
            scene.endFrame()
        } finally {
            scene.abortFrame()
            LuminRenderSystem.setActiveTarget(null)
        }
    }

    private fun drawChrome(layout: PanelLayout.Layout) {
        scene.LuminUIScene(layout.panel, UiLayer.CHROME, LayerOffset(-20), key = "panel-chrome") {
            Shadow(
                0.0f, 0.0f, width, height,
                MD3Theme.PANEL_RADIUS.toFloat(), MD3Theme.PANEL_SHADOW_BLUR,
                MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.PANEL_SHADOW_ALPHA)
            )
            RoundRect(
                0.0f, 0.0f, width, height,
                MD3Theme.PANEL_RADIUS.toFloat(), MD3Theme.SURFACE
            )
        }

        // 外层 panel 尺寸不随侧栏动画变化，各栏背景必须按自身边界独立失效。
        drawSectionBackground(layout.rail, "panel-rail-background")
        if (state.isClientSettingMode()) {
            val bounds = UiRect(
                layout.modules.x, layout.modules.y,
                layout.detail.right() - layout.modules.x, layout.modules.height
            )
            drawSectionBackground(bounds, "panel-client-settings-background")
        } else {
            drawSectionBackground(layout.modules, "panel-modules-background")
            drawSectionBackground(layout.detail, "panel-detail-background")
        }
    }

    private fun drawSectionBackground(bounds: UiRect, key: String) {
        scene.LuminUIScene(bounds, UiLayer.CHROME, LayerOffset(-20), key = key) {
            RoundRect(
                0.0f, 0.0f, width, height,
                MD3Theme.SECTION_RADIUS.toFloat(), MD3Theme.SURFACE_DIM
            )
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

    private fun panelMouseX(mouseX: Double): Double {
        val center = LuminRenderSystem.getScaledWidth() * 0.5
        return (LuminRenderSystem.toEpsilonMouseX(mouseX) - center) / entranceScale + center
    }

    private fun panelMouseY(mouseY: Double): Double {
        val center = LuminRenderSystem.getScaledHeight() * 0.5
        return (LuminRenderSystem.toEpsilonMouseY(mouseY) - center - entranceOffsetY) / entranceScale + center
    }

    private fun panelMouseEvent(event: MouseButtonEvent): MouseButtonEvent {
        return MouseButtonEvent(panelMouseX(event.x()), panelMouseY(event.y()), event.buttonInfo())
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val mouse = panelMouseEvent(event)
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
            panelMouseX(mouseX), panelMouseY(mouseY), scrollX, scrollY
        )
        return scene.input.mouseScrolled(
            panelMouseX(mouseX),
            panelMouseY(mouseY),
            scrollX,
            scrollY
        )
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (popupHost.activePopup != null) return popupHost.mouseReleased(panelMouseEvent(event))

        return scene.input.mouseReleased(panelMouseEvent(event))
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (popupHost.activePopup != null) return popupHost.mouseDragged(
            panelMouseEvent(event),
            LuminRenderSystem.toEpsilonMouseX(mouseX) / entranceScale,
            LuminRenderSystem.toEpsilonMouseY(mouseY) / entranceScale
        )

        return scene.input.mouseDragged(
            panelMouseEvent(event),
            LuminRenderSystem.toEpsilonMouseX(mouseX) / entranceScale,
            LuminRenderSystem.toEpsilonMouseY(mouseY) / entranceScale
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
        entranceStarted = false
        entranceAnim.close()
        entranceScale = 1.0f
        entranceOffsetY = 0.0f
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
