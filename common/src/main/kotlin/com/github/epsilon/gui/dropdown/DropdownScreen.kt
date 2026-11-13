package com.github.epsilon.gui.dropdown

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.gui.dropdown.component.*
import com.github.epsilon.gui.dropdown.widget.DropdownTextField
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.lib.state.LuminAnimatable
import com.github.epsilon.gui.lib.state.LuminView
import com.github.epsilon.gui.lib.state.prepareLuminDataSources
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.popup.RegistryListSelectPopup
import com.github.epsilon.gui.panel.popup.StringListSelectPopup
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.managers.AssetManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.settings.impl.StringListSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.IMEPreeditOverlay
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent
import net.minecraft.network.chat.Component
import java.awt.Color
import java.util.*
import java.util.function.Consumer

class DropdownScreen private constructor() : Screen(Component.literal("DropdownGui")), ListSettingPopupScreen {

    private val panels: MutableList<DropdownPanel> = ArrayList()
    private val scene: UiScene = UiScene(EpsilonUiTheme)
    private val popupHost: PanelPopupHost = PanelPopupHost { scene.input }
    private val scrimAnim: LuminAnimatable = LuminAnimatable(0.0f, Easing.EASE_OUT_SINE, 200L)
    private val searchField: DropdownTextField = DropdownTextField(64)
    private val reisaCompanion: ReisaDropdownCompanion = ReisaDropdownCompanion()
    private var visiblePanelIds: Set<String> by mutableStateOf(emptySet())

    private var renderTarget: LuminRenderSystem.LuminRenderTarget? = null
    private var preeditOverlay: IMEPreeditOverlay? = null
    private var initialized = false
    private var currentSessionId = 0
    private var renderFrameId = 0
    private var dropdownBatch: UiRenderBatch? = null
    private var dropdownLayer = 0


    override fun init() {
        super.init()
        currentSessionId++
        if (ClientSetting.showReisaInDropdown && !AssetManager.isReisaReady()) {
            AssetManager.requestDownload(listOf(AssetManager.Asset.REISA))
        }
        if (isReisaCompanionEnabled()) {
            reisaCompanion.open(currentSessionId)
        }
        scrimAnim.snapTo(0.0f)
        scrimAnim.target = 1.0f

        if (!initialized) {
            buildPanels()
            initialized = true
        }

        for (panel in panels) {
            panel.updateMaxPanelHeight(resolveMaxPanelHeight(panel))
            panel.startIntro()
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val window = minecraft.window
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("dropdown-gui", window.width, window.height)
        }
        val target = renderTarget!!
        target.resize(window.width, window.height)
        target.clear()
        LuminRenderSystem.setActiveTarget(target)
        prepareLuminDataSources()
        scene.beginFrame()
        try {

            val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
            val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)
            drawGui(graphics, epsilonMouseX, epsilonMouseY, partialTick)
            scene.endFrame()

            LuminRenderSystem.setActiveTarget(null)
            val overlay = preeditOverlay
            if (overlay != null) {
                overlay.updateInputPosition(IMEFocusHelper.activeCursorX.toInt(), IMEFocusHelper.activeCursorY.toInt())
                graphics.setPreeditOverlay(overlay)
            }
            graphics.blit(
                target.getIdentifier(), 0, 0, window.guiScaledWidth, window.guiScaledHeight, 0.0f, 1.0f, 1.0f, 0.0f
            )
            popupHost.extractOverlay(graphics, epsilonMouseX, epsilonMouseY, partialTick)
        } finally {
            scene.abortFrame()
            LuminRenderSystem.setActiveTarget(null)
        }
    }

    private val luminView = LuminView()
    private val panelViews = IdentityHashMap<DropdownPanel, LuminView>()

    private fun drawGui(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        luminView.render(mouseX, mouseY) { drawContent(graphics, mouseX, mouseY, partialTick) }
    }

    private fun drawContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        dropdownBatch = scene.batch(UiLayer.CONTENT)
        dropdownLayer = -10
        val screenBounds = UiRect(
            0.0f, 0.0f,
            LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight()
        )
        popupHost.setOverlayBounds(screenBounds)
        updatePanelHeightLimits()
        updateVisiblePanelIds()
        beginPanelFrames()

        // 遮罩层：只有淡入进度变化时才重建。
        dropdownContent(screenBounds, "scrim") {
            val scrim = DropdownTheme.scrim()
            val scrimAlpha = scrimAnim.value
            Rect(
                0.0f, 0.0f, width, height,
                Color(scrim.red, scrim.green, scrim.blue, (scrim.alpha * scrimAlpha).toInt())
            )
        }

        val shadowPad = DropdownTheme.PANEL_SHADOW_BLUR + 4.0f
        val popupHovered = popupHost.activePopup != null &&
                popupHost.activePopup!!.getBounds().contains(mouseX.toDouble(), mouseY.toDouble())
        val backgroundMouseX = if (popupHovered) Int.MIN_VALUE else mouseX
        val backgroundMouseY = if (popupHovered) Int.MIN_VALUE else mouseY

        var topmostHovered: DropdownPanel? = null
        if (!popupHovered) {
            for (i in panels.indices.reversed()) {
                val p = panels[i]
                if (!p.isVisible) continue
                val ph = p.panelHeight
                if (mouseX >= p.x && mouseX <= p.x + p.width && mouseY >= p.y && mouseY <= p.y + ph) {
                    topmostHovered = p
                    break
                }
            }
        }

        if (isReisaCompanionEnabled()) {
            reisaCompanion.open(currentSessionId)
            // 立绘由本地时间驱动（眨眼、脉冲、漂浮），几乎每帧都不同，保持立即模式。
            dropdownContentImmediate(screenBounds) {
                reisaCompanion.draw(
                    this,
                    screenBounds.width,
                    screenBounds.height,
                    mouseX,
                    mouseY,
                    popupHovered || topmostHovered != null
                )
            }
        }

        for (panel in panels) {
            panelViews.getOrPut(panel) { LuminView() }.render(
                if (panel === topmostHovered) backgroundMouseX else -1,
                if (panel === topmostHovered) backgroundMouseY else -1
            ) {
                if (!panel.isVisible) return@render
                val intro = panel.introValue
                if (intro < 0.001f) return@render

                val slideOffset = (1.0f - intro) * 10.0f
                val origY = panel.y
                panel.setPosition(panel.x, origY - slideOffset)

                val panelH = panel.panelHeight
                val revealedH = panelH * intro

                val panelBounds = UiRect(panel.x, panel.y, panel.width, panelH)
                // 背景层：位置、高度、展开/淡入进度、滚动平滑位置任一变化才重建。
                dropdownContent(
                    panelBounds,
                    panel.id to "background"
                ) {
                    ClipIf(
                        intro < 1.0f,
                        -shadowPad,
                        -shadowPad,
                        panel.width + shadowPad * 2.0f,
                        revealedH + shadowPad * 2.0f
                    ) {
                        panel.drawBackground()
                    }
                }

                val clipY = panel.contentClipY
                val clipH = panel.contentClipHeight
                val revealedBottom = panel.y + revealedH
                val actualClipH = minOf(clipH, revealedBottom - clipY)
                if (actualClipH > 0.5f) {
                    val hoverMouseX = if (panel === topmostHovered) backgroundMouseX else -1
                    val hoverMouseY = if (panel === topmostHovered) backgroundMouseY else -1
                    val requiresContentScissor = intro < 1.0f || panel.requiresContentScissor()
                    val contentBounds = UiRect(panel.x, clipY, panel.width, actualClipH)

                    // 内部重复项再通过 LuminUIItem 逐项缓存。
                    dropdownContent(
                        contentBounds,
                        panel.id to "content"
                    ) {
                        ClipIf(requiresContentScissor, 0.0f, 0.0f, panel.width, actualClipH) {
                            panel.drawContent(hoverMouseX, hoverMouseY)
                        }
                    }
                }

                panel.setPosition(panel.x, origY)

            }
        }

        dropdownContent(screenBounds, "search") {
            InputRegion(
                key = searchField,
                bounds = UiRect(searchX, searchY, searchWidth, searchHeight),
                onPress = { event, _ ->
                    searchField.focusIfContains(
                        event.x(),
                        event.y(),
                        searchX,
                        searchY,
                        searchWidth,
                        searchHeight
                    )
                },
                onKey = { event ->
                    if (event.isEscape) {
                        searchField.blur(); scene.input.clearFocus(); true
                    } else searchField.keyPressed(event).also { if (it) syncSearchQuery() }
                },
                onText = { event -> searchField.charTyped(event).also { if (it) syncSearchQuery() } },
                onBlur = searchField::blur
            )
            searchField.draw(
                this, searchX, searchY, searchWidth, searchHeight, mouseX, mouseY,
                EpsilonTranslations.Gui.SEARCH.getTranslatedName(), 0.58f
            )
            drawHints()
        }

        popupHost.render(graphics, scene.batch(UiLayer.POPUP), mouseX, mouseY, partialTick)
        // endFrame 仍会提交剩余命令；这里消费主批次，避免重复绘制并覆盖弹窗附加缓冲。
        scene.flushAndClear()
        popupHost.flush()
    }

    /**
     * 以 recompose 模式在当前批次上追加一层内容。
     *
     * 每个绘制 pass 占用一个相对 layer；层内坐标相对 [bounds] 解析，稳定 [key] 维持身份。
     */
    private fun dropdownContent(bounds: UiRect, key: Any, content: UiScope.() -> Unit) {
        dropdownLayer += 10
        dropdownBatch!!.LuminUIScene(
            bounds,
            dropdownLayer,
            key,
            retainPosition = key is Pair<*, *> && key.second == "background",
            block = content
        )
    }

    /** 立即模式的绘制 pass（每帧重建）。 */
    private fun dropdownContentImmediate(bounds: UiRect, content: UiScope.() -> Unit) {
        dropdownLayer += 10
        dropdownBatch!!.LuminUISceneImmediate(bounds, dropdownLayer, content)
    }

    private fun UiScope.drawHints() {
        if (!ClientSetting.dropdownHints) return
        val scale = 0.62f
        val lineGap = 5.0f
        val lineHeight = metrics.textHeight(scale)
        val hints = arrayOf(
            EpsilonTranslations.Gui.DROPDOWN_HINT_SEARCH.getTranslatedName(),
            EpsilonTranslations.Gui.DROPDOWN_HINT_PANELS.getTranslatedName(),
            EpsilonTranslations.Gui.DROPDOWN_HINT_DRAG.getTranslatedName()
        )
        val screenWidth = LuminRenderSystem.getScaledWidth()
        var xRight = screenWidth - DropdownTheme.PANEL_MARGIN_X
        if (isReisaCompanionEnabled()) {
            val companionLeft = reisaCompanion.getLeftEdge(screenWidth, LuminRenderSystem.getScaledHeight())
            xRight = minOf(xRight, companionLeft - 8.0f)
        }
        xRight = maxOf(searchX + searchWidth, xRight)
        var y = LuminRenderSystem.getScaledHeight() - DropdownTheme.PANEL_MARGIN_Y -
                hints.size * lineHeight - (hints.size - 1) * lineGap
        val alpha = (255 * scrimAnim.value).toInt()
        if (alpha <= 0) {
            return
        }
        val color = MD3Theme.withAlpha(Color.WHITE, alpha)
        for (hint in hints) {
            val x = xRight - metrics.textWidth(hint, scale)
            Text(hint, x, y, scale, color)
            y += lineHeight + lineGap
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val mouse = LuminRenderSystem.toEpsilonMouseEvent(event)
        if (popupHost.activePopup != null) return popupHost.mouseClicked(mouse, isDoubleClick)
        val handled = scene.input.mouseClicked(mouse, isDoubleClick)
        if (handled) {
            val panel = panels.lastOrNull {
                it.isVisible && UiRect(it.x, it.y, it.width, it.panelHeight).contains(
                    mouse.x(),
                    mouse.y()
                )
            }
            if (panel != null) {
                panels.remove(panel); panels.add(panel)
            }
            DropdownLayoutState.save(panels)
            reactMouseButton(mouse.button())
        }
        return handled || super.mouseClicked(mouse, isDoubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (popupHost.activePopup != null) return popupHost.mouseReleased(LuminRenderSystem.toEpsilonMouseEvent(event))

        val handled = scene.input.mouseReleased(LuminRenderSystem.toEpsilonMouseEvent(event))
        if (handled) {
            DropdownLayoutState.save(panels); react(ReisaDropdownCompanion.Action.CONFIRM)
        }
        return handled
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (popupHost.activePopup != null) return popupHost.mouseDragged(
            LuminRenderSystem.toEpsilonMouseEvent(event),
            LuminRenderSystem.toEpsilonMouseX(mouseX),
            LuminRenderSystem.toEpsilonMouseY(mouseY)
        )

        val handled = scene.input.mouseDragged(
            LuminRenderSystem.toEpsilonMouseEvent(event),
            LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY)
        )
        if (handled) DropdownLayoutState.save(panels)
        return handled
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val mx = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val my = LuminRenderSystem.toEpsilonMouseY(mouseY)
        if (popupHost.activePopup != null) return popupHost.mouseScrolled(mx, my, scrollX, scrollY)
        return scene.input.mouseScrolled(mx, my, scrollX, scrollY).also { if (it) reactScroll(scrollY) }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (popupHost.activePopup != null) return popupHost.keyPressed(event)
        if (event.key() == InputConstants.KEY_F && InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)) {
            if (scene.input.requestFocus(searchField)) searchField.focus()
            return true
        }
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

    override fun onClose() {
        IMEFocusHelper.forceDeactivate()
        DropdownLayoutState.save(panels)
        super.onClose()
    }

    override fun preeditUpdated(event: PreeditEvent?): Boolean {
        if (popupHost.activePopup != null) popupHost.preeditUpdated(event) else scene.input.preeditUpdated(event)

        this.preeditOverlay = if (event != null) IMEPreeditOverlay(event, this.font, 10) else null
        return true
    }

    override fun removed() {
        super.removed()
        panelViews.values.forEach { it.close() }
        panelViews.clear()
        luminView.close()
        scene.resetComposition()
        popupHost.close()
        searchField.clear()
        syncSearchQuery()
        searchField.blur()
        IMEFocusHelper.forceDeactivate()
        preeditOverlay = null
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    private fun buildPanels() {
        panels.clear()
        var index = 0
        val mainPanel = MainDropdownPanel(
            index++,
            Consumer { panelId -> handleMainPanelAction(panelId) },
            { anySubPanelVisible() },
            { panelId -> isPanelVisible(panelId) }
        )
        mainPanel.setPosition(DropdownTheme.PANEL_MARGIN_X, DropdownTheme.PANEL_MARGIN_Y)
        panels.add(mainPanel)

        val x = DropdownTheme.PANEL_MARGIN_X + mainPanel.width + DropdownTheme.PANEL_GAP
        var y = DropdownTheme.PANEL_MARGIN_Y
        for (category in Category.entries) {
            panels.add(createSubPanel(CategoryPanel(category, index++), x, y))
            y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP
        }

        panels.add(createSubPanel(FriendDropdownPanel(index++), x, y))
        y += DropdownTheme.PANEL_HEADER_HEIGHT + DropdownTheme.PANEL_GAP
        panels.add(createSubPanel(ConfigDropdownPanel(index++), x, y))

        DropdownLayoutState.load(panels)
    }

    private fun createSubPanel(panel: DropdownPanel, x: Float, y: Float): DropdownPanel {
        panel.setPosition(x, y)
        panel.isVisible = false
        panel.isOpened = false
        return panel
    }

    private fun handleMainPanelAction(panelId: String) {
        if ("__collapse_all__" == panelId) {
            for (panel in panels) {
                if ("main" != panel.id) {
                    panel.isVisible = false
                    panel.isOpened = false
                }
            }
            DropdownLayoutState.save(panels)
            react(ReisaDropdownCompanion.Action.PANEL_CLOSE)
            return
        }

        for (panel in panels) {
            if (panel.id == panelId) {
                panel.isVisible = !panel.isVisible
                panel.isOpened = false
                DropdownLayoutState.save(panels)
                react(
                    if (panel.isVisible) ReisaDropdownCompanion.Action.PANEL_OPEN else ReisaDropdownCompanion.Action.PANEL_CLOSE
                )
                return
            }
        }
    }

    private fun anySubPanelVisible(): Boolean {
        return panels.stream().anyMatch { panel -> "main" != panel.id && panel.isVisible }
    }

    private fun isPanelVisible(panelId: String): Boolean {
        return visiblePanelIds.contains(panelId)
    }

    private fun updateVisiblePanelIds() {
        visiblePanelIds = panels.filter { it.isVisible }.map { it.id }.toSet()
    }

    private fun resolveMaxPanelHeight(panel: DropdownPanel): Float {
        return resolveMaxPanelHeight(panel, LuminRenderSystem.getScaledHeight() * 0.72f)
    }

    private fun resolveMaxPanelHeight(panel: DropdownPanel, screenLimited: Float): Float {
        return when (panel.id) {
            "main" -> minOf(screenLimited, 260.0f)
            "friend", "config" -> minOf(screenLimited, 220.0f)
            else -> minOf(screenLimited, 350.0f)
        }
    }

    private fun updatePanelHeightLimits() {
        val screenLimited = LuminRenderSystem.getScaledHeight() * 0.72f
        for (panel in panels) {
            panel.updateMaxPanelHeight(resolveMaxPanelHeight(panel, screenLimited))
        }
    }

    private fun beginPanelFrames() {
        val frameId = ++renderFrameId
        for (panel in panels) {
            panelViews.getOrPut(panel) { LuminView() }.prepare {
                panel.beginRenderFrame(frameId)
            }
        }
    }

    private fun syncSearchQuery() {
        val query = searchField.text
        for (panel in panels) {
            if (panel is CategoryPanel) {
                panel.setSearchQuery(query)
            }
        }
    }

    private val searchX: Float
        get() = DropdownTheme.PANEL_MARGIN_X

    private val searchY: Float
        get() = LuminRenderSystem.getScaledHeight() - DropdownTheme.PANEL_MARGIN_Y - searchHeight

    private val searchWidth: Float
        get() = (LuminRenderSystem.getScaledWidth() - DropdownTheme.PANEL_MARGIN_X * 2.0f).coerceIn(140.0f, 200.0f)

    private val searchHeight: Float
        get() = 20.0f

    private fun reactInstance(action: ReisaDropdownCompanion.Action?) {
        if (isReisaCompanionEnabled()) {
            reisaCompanion.open(currentSessionId)
            reisaCompanion.react(action)
        }
    }

    private fun isReisaCompanionEnabled(): Boolean {
        return ClientSetting.showReisaInDropdown && AssetManager.isReisaReady()
    }

    override fun openRegistryListSettingPopup(setting: RegistryListSetting<*>) {
        val bounds = popupHost.getCenteredBounds(
            minOf(360.0f, LuminRenderSystem.getScaledWidth() - 28.0f),
            minOf(300.0f, LuminRenderSystem.getScaledHeight() - 28.0f)
        )
        popupHost.open(RegistryListSelectPopup.create(bounds, setting))
        react(ReisaDropdownCompanion.Action.PANEL_OPEN)
    }

    override fun openStringListSettingPopup(setting: StringListSetting) {
        val bounds = popupHost.getCenteredBounds(
            minOf(300.0f, LuminRenderSystem.getScaledWidth() - 28.0f),
            minOf(260.0f, LuminRenderSystem.getScaledHeight() - 28.0f)
        )
        popupHost.open(StringListSelectPopup(bounds, setting, { setting.add(it) }, { setting.remove(it) }))
        react(ReisaDropdownCompanion.Action.PANEL_OPEN)
    }

    private fun reactMouseButton(button: Int) {
        react(
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                ReisaDropdownCompanion.Action.SECONDARY_CLICK
            } else {
                ReisaDropdownCompanion.Action.PRIMARY_CLICK
            }
        )
    }

    private fun reactScroll(scrollY: Double) {
        react(
            if (scrollY >= 0.0) ReisaDropdownCompanion.Action.SCROLL_UP else ReisaDropdownCompanion.Action.SCROLL_DOWN
        )
    }

    companion object {

        val INSTANCE: DropdownScreen = DropdownScreen()

        /** 光标闪烁周期（与 [DropdownTextField] 内部实现一致：每 500ms 翻转一次）。 */
        private const val CARET_BLINK_PERIOD_MS = 500L

        /** 供 Kotlin 调用方使用的静态入口（原 Java 的 `DropdownScreen.INSTANCE.react(...)`）。 */
        fun react(action: ReisaDropdownCompanion.Action?) {
            INSTANCE.reactInstance(action)
        }

        /** 当前界面会话 id，供输入状态去重。 */
        fun getSessionId(): Int {
            return INSTANCE.currentSessionId
        }

    }

}
