package com.github.epsilon.gui.dropdown

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.ttf.TtfFontLoader
import com.github.epsilon.gui.dropdown.component.*
import com.github.epsilon.gui.dropdown.widget.DropdownTextField
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.popup.RegistryListSelectPopup
import com.github.epsilon.gui.panel.popup.StringListSelectPopup
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.AssetManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.settings.impl.StringListSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
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
import java.util.function.Consumer

class DropdownScreen private constructor() : Screen(Component.literal("DropdownGui")), ListSettingPopupScreen {

    private val panels: MutableList<DropdownPanel> = ArrayList()
    private val textMetrics: TextRenderer = TextRenderer.create()
    private val uiTextMetrics: UiTextMetrics = DropdownTextMetrics()
    private val scene: UiScene = UiScene(EpsilonUiTheme)
    private val popupHost: PanelPopupHost = PanelPopupHost()
    private val scrimAnim: Animation = Animation(Easing.EASE_OUT_SINE, 200L)
    private val searchField: DropdownTextField = DropdownTextField(64)
    private val reisaCompanion: ReisaDropdownCompanion = ReisaDropdownCompanion()
    private val visiblePanelIds: MutableSet<String> = HashSet()

    private var renderTarget: LuminRenderSystem.LuminRenderTarget? = null
    private var preeditOverlay: IMEPreeditOverlay? = null
    private var initialized = false
    private var currentSessionId = 0
    private var renderFrameId = 0
    private var dropdownBatch: UiRenderBatch? = null
    private var dropdownScope: UiTree.Scope? = null
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
        scrimAnim.startValue = 0.0f
        scrimAnim.run(0.0f)
        scrimAnim.run(1.0f)

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
        scene.beginFrame()

        val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)
        drawGui(graphics, epsilonMouseX, epsilonMouseY, partialTick)
        scene.clear()

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
    }

    private fun drawGui(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        scrimAnim.run(1.0f)
        dropdownBatch = scene.batch(UiLayer.CONTENT)
        dropdownLayer = -10
        popupHost.setOverlayBounds(
            UiRect(0.0f, 0.0f, LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight())
        )
        updatePanelHeightLimits()
        updateVisiblePanelIds()
        beginPanelFrames()

        beginDropdownLayer()
        val scrim = DropdownTheme.scrim()
        val scrimAlpha = scrimAnim.value
        currentScope.rect(
            0.0f, 0.0f, LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight(),
            Color(scrim.red, scrim.green, scrim.blue, (scrim.alpha * scrimAlpha).toInt())
        )
        flushDropdownLayer()

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
            beginDropdownLayer()
            reisaCompanion.draw(
                currentScope,
                LuminRenderSystem.getScaledWidth(),
                LuminRenderSystem.getScaledHeight(),
                mouseX,
                mouseY,
                popupHovered || topmostHovered != null
            )
            flushDropdownLayer()
        }

        for (panel in panels) {
            if (!panel.isVisible) continue
            val intro = panel.introValue
            if (intro < 0.001f) continue

            val slideOffset = (1.0f - intro) * 10.0f
            val origY = panel.y
            panel.setPosition(panel.x, origY - slideOffset)

            val panelH = panel.panelHeight
            val revealedH = panelH * intro

            beginDropdownLayer()
            withDropdownScissor(
                intro < 1.0f,
                panel.x - shadowPad,
                panel.y - shadowPad,
                panel.width + shadowPad * 2,
                revealedH + shadowPad * 2
            ) { scope -> panel.drawBackground(scope, uiTextMetrics) }
            flushDropdownLayer()

            val clipY = panel.contentClipY
            val clipH = panel.contentClipHeight
            val revealedBottom = panel.y + revealedH
            val actualClipH = minOf(clipH, revealedBottom - clipY)
            if (actualClipH > 0.5f) {
                beginDropdownLayer()
                val hoverMouseX = if (panel === topmostHovered) backgroundMouseX else -1
                val hoverMouseY = if (panel === topmostHovered) backgroundMouseY else -1
                val requiresContentScissor = intro < 1.0f || panel.requiresContentScissor()
                withDropdownScissor(requiresContentScissor, panel.x, clipY, panel.width, actualClipH) { scope ->
                    panel.drawContent(scope, uiTextMetrics, hoverMouseX, hoverMouseY)
                }
                flushDropdownLayer()
            }

            panel.setPosition(panel.x, origY)
        }

        drawSearch(backgroundMouseX, backgroundMouseY)
        popupHost.render(graphics, scene.batch(UiLayer.POPUP), mouseX, mouseY, partialTick)
        scene.flush()
        popupHost.flush()
    }

    private fun drawSearch(mouseX: Int, mouseY: Int) {
        beginDropdownLayer()
        val searchX = searchX
        val searchY = searchY
        searchField.draw(
            currentScope, uiTextMetrics, searchX, searchY, searchWidth, searchHeight, mouseX, mouseY,
            EpsilonTranslations.Gui.SEARCH.getTranslatedName(), 0.58f
        )
        drawHints()
        flushDropdownLayer()
    }

    private fun drawHints() {
        if (ClientSetting.dropdownHints) {
            val scale = 0.62f
            val lineGap = 5.0f
            val lineHeight = uiTextMetrics.textHeight(scale)
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
                val x = xRight - uiTextMetrics.textWidth(hint, scale)
                currentScope.text(hint, x, y, scale, color)
                y += lineHeight + lineGap
            }
        }
    }

    private val currentScope: UiTree.Scope
        get() = dropdownScope!!

    private fun beginDropdownLayer() {
        dropdownLayer += 10
        dropdownScope = UiTree.Scope()
    }

    private fun flushDropdownLayer() {
        dropdownBatch!!.render(UiTree.from(currentScope), dropdownLayer)
    }

    private fun withDropdownScissor(
        required: Boolean,
        guiX: Float,
        guiY: Float,
        guiW: Float,
        guiH: Float,
        content: Consumer<UiTree.Scope>,
    ) {
        currentScope.scissorIf(required, UiRect(guiX, guiY, guiW, guiH)) { scope -> content.accept(scope) }
    }

    private inner class DropdownTextMetrics : UiTextMetrics {

        override fun textWidth(text: String, scale: Float): Float {
            return textMetrics.getWidth(text, scale)
        }

        override fun textWidth(text: String, scale: Float, fontLoader: TtfFontLoader): Float {
            return textMetrics.getWidth(text, scale, fontLoader)
        }

        override fun textHeight(scale: Float): Float {
            return textMetrics.getHeight(scale)
        }

        override fun textHeight(scale: Float, fontLoader: TtfFontLoader): Float {
            return textMetrics.getHeight(scale, fontLoader)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        val mx = epsilonEvent.x()
        val my = epsilonEvent.y()
        val button = epsilonEvent.button()

        if (popupHost.mouseClicked(epsilonEvent, isDoubleClick)) {
            react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }

        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            for (panel in panels) {
                if (panel.isVisible) {
                    panel.onGlobalMouseClicked(mx, my, button)
                }
            }
        }

        if (button == InputConstants.MOUSE_BUTTON_LEFT &&
            searchField.focusIfContains(mx, my, searchX, searchY, searchWidth, searchHeight)
        ) {
            react(ReisaDropdownCompanion.Action.TYPING)
            return true
        } else if (button == InputConstants.MOUSE_BUTTON_LEFT && searchField.isFocused) {
            searchField.blur()
        }

        for (i in panels.indices.reversed()) {
            val panel = panels[i]
            if (!panel.isVisible) continue
            val reactionRevision = reisaCompanion.getReactionRevision()
            if (panel.mouseClicked(mx, my, button)) {
                if (i < panels.size - 1) {
                    panels.removeAt(i)
                    panels.add(panel)
                }
                DropdownLayoutState.save(panels)
                if (reactionRevision == reisaCompanion.getReactionRevision()) {
                    reactMouseButton(button)
                }
                return true
            }
        }
        return super.mouseClicked(epsilonEvent, isDoubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        val mx = epsilonEvent.x()
        val my = epsilonEvent.y()
        val button = epsilonEvent.button()

        if (popupHost.mouseReleased(epsilonEvent)) {
            react(ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }

        for (panel in panels) {
            if (!panel.isVisible) continue
            if (panel.mouseReleased(mx, my, button)) {
                DropdownLayoutState.save(panels)
                react(ReisaDropdownCompanion.Action.CONFIRM)
                return true
            }
        }
        return super.mouseReleased(epsilonEvent)
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)
        if (popupHost.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY)) {
            react(ReisaDropdownCompanion.Action.DRAG)
            return true
        }
        var handled = false
        for (panel in panels) {
            if (!panel.isVisible) continue
            if (panel.mouseDragged(
                    LuminRenderSystem.toEpsilonMouseX(event.x()),
                    LuminRenderSystem.toEpsilonMouseY(event.y())
                )
            ) {
                handled = true
            }
        }
        if (handled) {
            DropdownLayoutState.save(panels)
            react(ReisaDropdownCompanion.Action.DRAG)
            return true
        }
        return super.mouseDragged(
            epsilonEvent,
            LuminRenderSystem.toEpsilonMouseX(event.x()),
            LuminRenderSystem.toEpsilonMouseY(event.y())
        )
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)
        if (popupHost.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
            reactScroll(scrollY)
            return true
        }
        for (i in panels.indices.reversed()) {
            val panel = panels[i]
            if (!panel.isVisible) continue
            if (panel.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollY)) {
                reactScroll(scrollY)
                return true
            }
        }
        return super.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (popupHost.keyPressed(event)) {
            react(if (event.isEscape) ReisaDropdownCompanion.Action.CANCEL else ReisaDropdownCompanion.Action.CONFIRM)
            return true
        }
        if (event.key() == InputConstants.KEY_F && InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)) {
            searchField.focus()
            react(ReisaDropdownCompanion.Action.TYPING)
            return true
        }
        if (searchField.isFocused) {
            if (event.isEscape) {
                searchField.blur()
                react(ReisaDropdownCompanion.Action.CANCEL)
                return true
            }
            if (searchField.keyPressed(event)) {
                syncSearchQuery()
                react(ReisaDropdownCompanion.Action.TYPING)
                return true
            }
        }

        val hasActiveInput =
            panels.stream().filter { panel -> panel.isVisible }.anyMatch { panel -> panel.hasActiveInput() }

        if (hasActiveInput) {
            for (panel in panels) {
                if (!panel.isVisible) continue
                if (panel.keyPressed(event.key(), event.keycode(), event.modifiers())) {
                    react(if (event.isEscape) ReisaDropdownCompanion.Action.CANCEL else ReisaDropdownCompanion.Action.CONFIRM)
                    return true
                }
            }
        }

        if (event.isEscape) {
            onClose()
            return true
        }

        for (panel in panels) {
            if (!panel.isVisible) continue
            if (panel.keyPressed(event.key(), event.keycode(), event.modifiers())) {
                react(if (event.isEscape) ReisaDropdownCompanion.Action.CANCEL else ReisaDropdownCompanion.Action.CONFIRM)
                return true
            }
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (popupHost.charTyped(event)) {
            react(ReisaDropdownCompanion.Action.TYPING)
            return true
        }
        if (searchField.charTyped(event)) {
            syncSearchQuery()
            react(ReisaDropdownCompanion.Action.TYPING)
            return true
        }
        for (panel in panels) {
            if (!panel.isVisible) continue
            val typed = event.codepointAsString()
            if (typed.isNotEmpty() && panel.charTyped(typed)) {
                react(ReisaDropdownCompanion.Action.TYPING)
                return true
            }
        }
        return super.charTyped(event)
    }

    override fun onClose() {
        IMEFocusHelper.forceDeactivate()
        DropdownLayoutState.save(panels)
        super.onClose()
    }

    override fun preeditUpdated(event: PreeditEvent?): Boolean {
        this.preeditOverlay = if (event != null) IMEPreeditOverlay(event, this.font, 10) else null
        return true
    }

    override fun removed() {
        super.removed()
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
        visiblePanelIds.clear()
        for (panel in panels) {
            if (panel.isVisible) {
                visiblePanelIds.add(panel.id)
            }
        }
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
            panel.beginRenderFrame(frameId)
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
