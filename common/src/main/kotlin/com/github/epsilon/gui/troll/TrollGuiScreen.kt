package com.github.epsilon.gui.troll

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.schedulers.render2d.Render2DTransform
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.ListSettingPopupScreen
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.lib.state.LuminRuntime
import com.github.epsilon.gui.lib.state.LuminView
import com.github.epsilon.gui.lib.state.prepareLuminDataSources
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.popup.RegistryListSelectPopup
import com.github.epsilon.gui.panel.popup.StringListSelectPopup
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.GuiSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.settings.impl.StringListSetting
import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.systems.RenderSystem
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
import kotlin.math.roundToInt
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

class TrollGuiScreen private constructor() : Screen(Component.literal("TrollGui")), ListSettingPopupScreen {

    private val textMetrics: Supplier<UiTextMetrics> = Suppliers.memoize(Supplier { TextRenderer.create().asUiTextMetrics() })
    private val scene: Supplier<UiScene> = Suppliers.memoize(Supplier { UiScene(EpsilonUiTheme) })
    private val windows: MutableList<TrollWindow> = ArrayList()
    private val popupHost: PanelPopupHost = PanelPopupHost { scene.get().input }
    private val guiOpenAnimation: Animation = Animation(Easing.EASE_OUT_CUBIC, 400L)
    private val friendToggleHover: Animation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val configToggleHover: Animation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private var friendWindow: TrollFriendWindow? = null
    private var configWindow: TrollConfigWindow? = null
    private var friendWindowOpen: Boolean by mutableStateOf(false)
    private var configWindowOpen: Boolean by mutableStateOf(false)
    private var guiAnimationStarted: Boolean = false
    private var searchString: String by mutableStateOf("")
    private val searchFade = Animation(0.0f, Easing.EASE_IN_CUBIC, SEARCH_TEXT_FADE_MS)
    private var friendToggleBounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var configToggleBounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var settingsWindow: TrollSettingsWindow? = null
    private var colorPicker: TrollColorPicker? = null
    private var renderTarget: LuminRenderSystem.LuminRenderTarget? = null
    private var preeditOverlay: IMEPreeditOverlay? = null
    private val pendingWindows: MutableList<WindowPass> = ArrayList()
    private var pendingScene: UiScene? = null
    private var pendingBackground: UiRenderBatch? = null
    private var pendingWindowBlurStrength: Float = 0.0f
    private var pendingFullscreenBlurStrength: Float = 0.0f
    private var guiOpenOffsetY: Float by mutableStateOf(0.0f)
    private var pendingFrameWidth: Float = 0.0f
    private var pendingFrameHeight: Float = 0.0f

    init {
        guiOpenAnimation.startValue = 0.0f
        friendToggleHover.startValue = 0.0f
        configToggleHover.startValue = 0.0f
    }

    override fun init() {
        super.init()
        if (windows.isEmpty()) placeWindows()
        layoutWindows()
        if (!guiAnimationStarted) {
            guiAnimationStarted = true
            IMEFocusHelper.activate()
            guiOpenAnimation.value = 0.0f
            guiOpenAnimation.reset()
            guiOpenAnimation.run(1.0f)
            for (window in windows) window.startOpenAnimation()
        }
    }

    private fun placeWindows() {
        val metrics = textMetrics.get()
        val gap = 3.0f
        val headerHeight = TrollTheme.headerHeight(metrics)
        val width = screenWidth()
        val height = screenHeight()

        val types = ArrayList<TrollWindow>()
        types.add(TrollCategoryWindow(null))
        for (category in Category.entries) types.add(TrollCategoryWindow(category))
        types.add(TrollFriendWindow())
        types.add(TrollConfigWindow(popupHost))

        val rows = ArrayList<MutableList<TrollWindow>>()
        var currentRow = ArrayList<TrollWindow>()
        var currentRowWidth = 0.0f
        for (window in types) {
            window.layout(metrics, width, height)
            windows.add(window)
            when (window) {
                is TrollFriendWindow -> friendWindow = window
                is TrollConfigWindow -> configWindow = window
                else -> {
                }
            }
            val windowWidth = window.bounds.width
            if (currentRow.isNotEmpty() && currentRowWidth + gap + windowWidth > width) {
                rows.add(currentRow)
                currentRow = ArrayList()
                currentRowWidth = 0.0f
            }
            if (currentRow.isNotEmpty()) currentRowWidth += gap
            currentRow.add(window)
            currentRowWidth += windowWidth
        }
        rows.add(currentRow)

        var nextTop = 0.0f
        var cascaded = false
        for (row in rows) {
            var rowHeight = 0.0f
            for (window in row) rowHeight = maxOf(rowHeight, window.bounds.height)
            val top = if (cascaded) nextTop else (height - rowHeight).coerceIn(0.0f, nextTop)
            var x = 0.0f
            for (window in row) {
                window.position(x, if (cascaded) top else (height - window.bounds.height).coerceIn(0.0f, nextTop))
                x += window.bounds.width + gap
            }
            val stacked = top + rowHeight + gap
            if (stacked + headerHeight <= height) {
                cascaded = false
                nextTop = stacked
            } else {
                cascaded = true
                nextTop = top + headerHeight + gap
            }
        }

        val friendWindow = this.friendWindow
        if (friendWindow != null) {
            friendWindow.position(maxOf(0.0f, width - friendWindow.bounds.width - gap), gap)
        }
        val configWindow = this.configWindow
        if (configWindow != null) {
            val configY = gap + (if (friendWindow == null) 0.0f else friendWindow.bounds.height + gap)
            configWindow.position(maxOf(0.0f, width - configWindow.bounds.width - gap), configY)
        }
    }

    private fun screenWidth(): Float {
        return maxOf(1.0f, LuminRenderSystem.getScaledWidth())
    }

    private fun screenHeight(): Float {
        return maxOf(1.0f, LuminRenderSystem.getScaledHeight())
    }

    private fun layoutWindows() {
        for (window in windows) prepareWindow(window)
        settingsWindow?.let { prepareWindow(it) }
        colorPicker?.let { prepareWindow(it) }
        updateToggleBounds()
        popupHost.setOverlayBounds(UiRect(0.0f, 0.0f, screenWidth(), screenHeight()))
    }

    private fun prepareWindow(window: TrollWindow) {
        windowViews.getOrPut(window) { LuminView() }.prepare {
            window.layout(textMetrics.get(), screenWidth(), screenHeight())
        }
    }

    private fun updateToggleBounds() {
        val screenWidth = screenWidth()
        val screenHeight = screenHeight()
        val y = maxOf(0.0f, screenHeight - TOGGLE_MARGIN - TOGGLE_SIZE)
        val configX = maxOf(0.0f, screenWidth - TOGGLE_MARGIN - TOGGLE_SIZE)
        val friendX = maxOf(0.0f, configX - TOGGLE_GAP - TOGGLE_SIZE)
        friendToggleBounds = UiRect(friendX, y, TOGGLE_SIZE, TOGGLE_SIZE)
        configToggleBounds = UiRect(configX, y, TOGGLE_SIZE, TOGGLE_SIZE)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (minecraft.level == null) extractPanorama(graphics, partialTick)
    }

    private val luminView = LuminView()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        luminView.render(
            LuminRenderSystem.toEpsilonMouseX(mouseX),
            LuminRenderSystem.toEpsilonMouseY(mouseY)
        ) { extractContent(graphics, mouseX, mouseY, partialTick) }
    }

    private fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        endPendingFrame(abort = true)
        windowViews.keys.toList().filter { it !in windows && it !== settingsWindow && it !== colorPicker }.forEach {
            windowViews.remove(it)?.close()
        }
        val window = minecraft.window
        var renderTarget = this.renderTarget
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("troll-gui", window.width, window.height)
            this.renderTarget = renderTarget
        }
        renderTarget.resize(window.width, window.height)
        renderTarget.clear()
        val mx = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val my = LuminRenderSystem.toEpsilonMouseY(mouseY)
        val frame = scene.get()
        prepareLuminDataSources()
        frame.beginFrame()
        pendingScene = frame
        try {
            layoutWindows()
            guiOpenAnimation.run(1.0f)
            val openProgress = guiOpenAnimation.value
            guiOpenOffsetY = (1.0f - openProgress) * screenHeight()
            pendingFrameWidth = screenWidth()
            pendingFrameHeight = screenHeight()
            pendingWindowBlurStrength = GuiSetting.blurStrength.toFloat() * openProgress
            pendingFullscreenBlurStrength = if (GuiSetting.backgroundBlur) {
                maxOf(1.5f, GuiSetting.blurStrength.toFloat()) * openProgress
            } else {
                0.0f
            }
            graphics.blurBeforeThisStratum()
            pendingBackground = frame.batch(UiLayer.BACKGROUND)
            if (GuiSetting.background) {
                pendingBackground!!.LuminUIScene(
                    UiRect(0f, 0f, pendingFrameWidth, pendingFrameHeight), key = "background"
                ) {
                    Fill(Color(0, 0, 0, (144f * guiOpenAnimation.value).roundToInt()))
                }
            }
            val modal = settingsWindow != null || colorPicker != null || popupHost.activePopup != null
            val hovered = if (modal) null else hoveredWindow(mx.toDouble(), my.toDouble())
            for (i in windows.indices) {
                val trollWindow = windows[i]
                if (!isWindowVisible(trollWindow)) continue
                val hoverX = if (hovered === trollWindow) mx else Integer.MIN_VALUE
                val hoverY = if (hovered === trollWindow) my else Integer.MIN_VALUE
                renderAnimatedWindow(frame, UiLayer.CONTENT, i * 8, trollWindow, hoverX, hoverY)
            }
            if (!modal) {
                renderToggleButtons(frame, mx, my)
                renderSearchText(frame)
            }
            val settingsWindow = this.settingsWindow
            if (settingsWindow != null) {
                val blocked = colorPicker != null || popupHost.activePopup != null
                renderAnimatedWindow(
                    frame, UiLayer.FLOATING, 0, settingsWindow,
                    if (blocked) Integer.MIN_VALUE else mx, if (blocked) Integer.MIN_VALUE else my
                )
            }
            val colorPicker = this.colorPicker
            if (colorPicker != null) {
                renderAnimatedWindow(frame, UiLayer.POPUP, 0, colorPicker, mx, my)
            }
            val popupBatch = frame.batch(UiLayer.OVERLAY)
            popupHost.render(graphics, popupBatch, mx, my, partialTick)
            val activePopup = popupHost.activePopup
            if (activePopup != null) {
                pendingWindows.add(
                    WindowPass(activePopup.getBounds(), popupBatch, true, null, null, 0)
                )
            }
        } catch (exception: RuntimeException) {
            endPendingFrame(abort = true)
            throw exception
        } catch (error: Error) {
            endPendingFrame(abort = true)
            throw error
        }
        graphics.blit(
            renderTarget.getIdentifier(),
            0,
            0,
            window.guiScaledWidth,
            window.guiScaledHeight,
            0.0f,
            1.0f,
            1.0f,
            0.0f
        )
        popupHost.extractOverlay(graphics, mx, my, partialTick)
        val preeditOverlay = this.preeditOverlay
        if (preeditOverlay != null) {
            preeditOverlay.updateInputPosition(
                LuminRenderSystem.toMinecraftGuiX(IMEFocusHelper.activeCursorX.toDouble()).toInt(),
                LuminRenderSystem.toMinecraftGuiY(IMEFocusHelper.activeCursorY.toDouble()).toInt()
            )
            graphics.setPreeditOverlay(preeditOverlay)
        }
    }

    /** 每个离屏/模糊 pass 提交一个可重组的 DSL 场景，批次继续由渲染阶段依次消费。 */
    private fun queueWindow(
        frame: UiScene, layer: UiLayer, relativeLayer: Int, bounds: UiRect, key: Any,
        blurRects: FloatArray? = null, blurRadii: FloatArray? = null, blurSegmentCount: Int = 0,
        blur: Boolean = true, content: UiScope.() -> Unit,
    ) {
        val batch = frame.batch(layer, relativeLayer)
        batch.LuminUIScene(UiRect(0f, 0f, screenWidth(), screenHeight()), key = key, block = content)
        pendingWindows.add(WindowPass(bounds, batch, false, blurRects, blurRadii, blurSegmentCount, blur))
    }

    private val windowViews = IdentityHashMap<TrollWindow, LuminView>()

    private fun renderAnimatedWindow(
        frame: UiScene, layer: UiLayer, relativeLayer: Int, window: TrollWindow, mouseX: Int, mouseY: Int,
    ) {
        windowViews.getOrPut(window) { LuminView() }.render(mouseX, mouseY) {
            val windowProgress = window.getOpenProgress()
            val revealHeight = window.headerHeight + maxOf(0f, window.bounds.height - window.headerHeight) * windowProgress
            if (revealHeight <= 0.01f) return@render
            val passBounds = UiRect(window.bounds.x, window.bounds.y + guiOpenOffsetY, window.bounds.width, revealHeight)
            queueWindow(frame, layer, relativeLayer, passBounds, window) {
                if (window === settingsWindow || window === colorPicker) {
                    Interaction(key = window to "dismiss") {
                        for (button in InputConstants.MOUSE_BUTTON_LEFT..InputConstants.MOUSE_BUTTON_8) onClick(button) {
                            if (window === colorPicker) {
                                colorPicker?.cancel()
                                closeColorPicker()
                            } else closeSettings()
                            true
                        }
                        onScroll { _, _, _, _ -> true }
                    }
                }
                LuminRuntime.currentComposition!!.retain(window.scroll) {
                    AutoCloseable { window.scroll.close() }
                }
                Transform({ Render2DTransform.translation(0f, guiOpenOffsetY) }) {
                    Clip({
                        val bounds = window.bounds
                        UiRect(bounds.x, bounds.y, bounds.width,
                            window.headerHeight + maxOf(0f, bounds.height - window.headerHeight) * window.getOpenProgress())
                    }) {
                        LuminUIGroup("content") {
                            window.drawInScope(this, mouseX, mouseY)
                        }
                    }
                }
            }
        }
    }

    private fun renderToggleButtons(frame: UiScene, mouseX: Int, mouseY: Int) {
        if (friendToggleBounds.width <= 0f || configToggleBounds.width <= 0f) return
        val left = minOf(friendToggleBounds.x, configToggleBounds.x)
        val top = minOf(friendToggleBounds.y, configToggleBounds.y)
        val right = maxOf(friendToggleBounds.right(), configToggleBounds.right())
        val bottom = maxOf(friendToggleBounds.bottom(), configToggleBounds.bottom())
        val blurRects = floatArrayOf(
            friendToggleBounds.x, friendToggleBounds.y, friendToggleBounds.width, friendToggleBounds.height,
            configToggleBounds.x, configToggleBounds.y, configToggleBounds.width, configToggleBounds.height
        )
        val blurRadii = floatArrayOf(friendToggleBounds.width * 0.5f, configToggleBounds.width * 0.5f)
        queueWindow(
            frame, UiLayer.FLOATING, -90, UiRect(left, top, right - left, bottom - top), "toggles",
            blurRects, blurRadii, 2
        ) {
            LuminUIGroup("friend") {
                ToggleButton(friendToggleBounds, IconChars.PEOPLE, friendWindowOpen, mouseX, mouseY, friendToggleHover)
            }
            LuminUIGroup("config") {
                ToggleButton(configToggleBounds, IconChars.SETTINGS, configWindowOpen, mouseX, mouseY, configToggleHover)
            }
        }
    }

    private fun UiScope.ToggleButton(
        bounds: UiRect, icon: String, active: Boolean, mouseX: Int, mouseY: Int, hoverAnimation: Animation,
    ) {
        Positioned(bounds.x, bounds.y, bounds.width, bounds.height) {
            Interaction(key = icon) {
                onClick {
                    if (icon == IconChars.PEOPLE) toggleFriendWindow() else toggleConfigWindow()
                    true
                }
            }
            val hover = animate(hoverAnimation, bounds.contains(mouseX.toDouble(), mouseY.toDouble()))
            val backgroundAlpha = if (active) (205 + 50 * hover).toInt() else (120 + 40 * hover).toInt()
            val background = if (active) TrollTheme.alpha(TrollTheme.primary(), backgroundAlpha)
                else Color(0, 0, 0, backgroundAlpha)
            Layer(TrollZ.Highlight) { RoundRect(0f, 0f, width, height, width * 0.5f, background) }
            val iconScale = 0.92f
            val iconWidth = metrics.textWidth(icon, iconScale, StaticFontLoader.ICONS)
            val iconHeight = metrics.textHeight(iconScale, StaticFontLoader.ICONS)
            Layer(TrollZ.Content) {
                TextWithShadow(
                    icon, (width - iconWidth) * 0.5f, (height - iconHeight) * 0.5f, iconScale,
                    if (active) TrollTheme.text() else TrollTheme.alpha(TrollTheme.text(), 220), StaticFontLoader.ICONS
                )
            }
        }
    }

    fun renderPendingFrame(): Boolean {
        val pendingScene = this.pendingScene ?: return false

        val previousTarget = LuminRenderSystem.getActiveTarget()
        val previousProjection = RenderSystem.getProjectionMatrixBuffer()
        val previousProjectionType = RenderSystem.getProjectionType()
        try {
            val mainTarget = minecraft.gameRenderer.mainRenderTarget()
            val source = mainTarget.getColorTexture()
            val renderTarget = this.renderTarget
            if (source == null || renderTarget == null || mainTarget.width != renderTarget.width() || mainTarget.height != renderTarget.height()) {
                return true
            }
            RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToTexture(
                    source,
                    renderTarget.colorTexture(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    mainTarget.width,
                    mainTarget.height
                )
            LuminRenderSystem.setActiveTarget(renderTarget)
            if (pendingFullscreenBlurStrength > 0.0f) {
                BlurShader.render(
                    renderTarget,
                    0.0f,
                    0.0f,
                    pendingFrameWidth,
                    pendingFrameHeight,
                    0.0f,
                    pendingFullscreenBlurStrength
                )
            }
            pendingBackground?.flushAndClear()
            for (pass in pendingWindows) {
                if (pendingWindowBlurStrength > 0.0f && pass.blur) {
                    if (pass.blurSegmentCount > 0) {
                        BlurShader.render(
                            renderTarget, pass.bounds.x, pass.bounds.y,
                            pass.bounds.width, pass.bounds.height, 0.0f, 0.0f, 0.0f, 0.0f,
                            pendingWindowBlurStrength, pass.blurRects, pass.blurRadii, pass.blurSegmentCount
                        )
                    } else {
                        BlurShader.render(
                            renderTarget, pass.bounds.x, pass.bounds.y,
                            pass.bounds.width, pass.bounds.height, 0.0f, pendingWindowBlurStrength
                        )
                    }
                }
                pass.batch.flushAndClear()
                if (pass.popup) popupHost.flush()
            }
        } finally {
            try {
                endPendingFrame()
            } finally {
                LuminRenderSystem.setActiveTarget(previousTarget)
                RenderSystem.setProjectionMatrix(previousProjection!!, previousProjectionType)
            }
        }
        return true
    }

    private fun endPendingFrame(abort: Boolean = false) {
        val frame = pendingScene
        pendingScene = null
        pendingBackground = null
        pendingWindows.clear()
        if (frame != null) {
            if (abort) frame.abortFrame() else {
                frame.clear()
                frame.endFrame()
            }
        }
    }

    private fun hoveredWindow(mouseX: Double, mouseY: Double): TrollWindow? {
        for (i in windows.indices.reversed()) {
            val window = windows[i]
            if (isWindowVisible(window) && window.bounds.contains(mouseX, mouseY)) return window
        }
        return null
    }

    private fun isWindowVisible(window: TrollWindow): Boolean {
        if (window === friendWindow && !friendWindowOpen) return false
        if (window === configWindow && !configWindowOpen) return false
        return true
    }

    private fun toggleFriendWindow() {
        val friendWindow = this.friendWindow ?: return
        friendWindowOpen = !friendWindowOpen
        if (friendWindowOpen) {
            friendWindow.startOpenAnimation()
            bringToFront(friendWindow)
        } else {
            friendWindow.resetInput()
        }
    }

    private fun toggleConfigWindow() {
        val configWindow = this.configWindow ?: return
        configWindowOpen = !configWindowOpen
        if (configWindowOpen) {
            configWindow.startOpenAnimation()
            bringToFront(configWindow)
        } else {
            configWindow.resetInput()
        }
    }

    private fun bringToFront(window: TrollWindow) {
        windows.remove(window)
        windows.add(window)
    }

    fun openSettings(module: Module, x: Double, y: Double): TrollSettingsWindow {
        scene.get().input.cancelPointer()
        scene.get().input.clearFocus()

        for (window in windows) window.blurInput()
        closeSettings()
        val created = TrollSettingsWindow(module, x.toFloat(), y.toFloat())
        settingsWindow = created
        created.startOpenAnimation()
        layoutWindows()
        created.startSettingAnimations()
        return created
    }

    fun openColorPicker(setting: ColorSetting, x: Double, y: Double) {
        scene.get().input.cancelPointer()
        scene.get().input.clearFocus()

        for (window in windows) window.blurInput()
        colorPicker?.cancel()
        closeColorPicker()
        val created = TrollColorPicker(setting, x.toFloat(), y.toFloat())
        colorPicker = created
        created.startOpenAnimation()
        created.layout(textMetrics.get(), screenWidth(), screenHeight())
    }

    private fun closeColorPicker() {
        val previous = colorPicker ?: return
        colorPicker = null
        windowViews.remove(previous)?.close()
        previous.resetInput()
    }

    private fun closeSettings() {
        val previous = settingsWindow
        settingsWindow = null
        if (previous != null) {
            windowViews.remove(previous)?.close(); previous.resetInput()
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouse = LuminRenderSystem.toEpsilonMouseEvent(event)
        if (popupHost.activePopup != null) return popupHost.mouseClicked(mouse, doubleClick)
        val modal = settingsWindow != null || colorPicker != null
        val handled = scene.get().input.mouseClicked(mouse, doubleClick)
        if (colorPicker?.isClosed() == true) closeColorPicker()
        if (!modal && settingsWindow == null && colorPicker == null) {
            hoveredWindow(mouse.x(), mouse.y())?.let { windows.remove(it); windows.add(it) }
        }
        return handled
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (popupHost.activePopup != null) return popupHost.mouseReleased(LuminRenderSystem.toEpsilonMouseEvent(event))

        return scene.get().input.mouseReleased(LuminRenderSystem.toEpsilonMouseEvent(event))
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (popupHost.activePopup != null) return popupHost.mouseDragged(
            LuminRenderSystem.toEpsilonMouseEvent(event),
            LuminRenderSystem.toEpsilonMouseX(dx),
            LuminRenderSystem.toEpsilonMouseY(dy)
        )

        return scene.get().input.mouseDragged(
            LuminRenderSystem.toEpsilonMouseEvent(event),
            LuminRenderSystem.toEpsilonMouseX(dx), LuminRenderSystem.toEpsilonMouseY(dy)
        )
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val mx = LuminRenderSystem.toEpsilonMouseX(x)
        val my = LuminRenderSystem.toEpsilonMouseY(y)
        if (popupHost.activePopup != null) return popupHost.mouseScrolled(mx, my, scrollX, scrollY)
        if (colorPicker != null) return true
        return scene.get().input.mouseScrolled(mx, my, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (popupHost.activePopup != null) {
            popupHost.keyPressed(event)
            return true
        }
        val colorPicker = this.colorPicker
        if (colorPicker != null) {
            if (event.isEscape) {
                colorPicker.cancel()
            } else if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                colorPicker.confirm()
            }
            if (colorPicker.isClosed()) closeColorPicker()
            return true
        }
        if (scene.get().input.keyPressed(event)) return true
        if (settingsWindow != null) {
            if (event.isEscape) closeSettings()
            return true
        }
        if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
            clearSearch()
            return true
        }
        if (event.isEscape) onClose()
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (popupHost.activePopup != null) return popupHost.charTyped(event)
        if (scene.get().input.charTyped(event)) return true
        if (colorPicker != null || settingsWindow != null) return false
        if (event.codepoint() == ' '.code || Character.isLetter(event.codepoint())) {
            appendSearch(event.codepointAsString())
            return true
        }
        return false
    }

    private fun appendSearch(text: String) {
        if (text.isEmpty()) return
        searchString += text
        searchFade.snapTo(1.0f)
        searchFade.target = 0.0f
        applySearchFilter()
    }

    private fun clearSearch() {
        if (searchString.isEmpty()) return
        searchString = ""
        searchFade.snapTo(0.0f)
        applySearchFilter()
    }

    private fun applySearchFilter() {
        val filter = searchString.filterNot { it.isWhitespace() }
        for (window in windows) {
            if (window is TrollCategoryWindow) window.setSearchFilter(filter)
        }
    }

    private fun renderSearchText(frame: UiScene) {
        if (searchString.isBlank()) return
        queueWindow(
            frame, UiLayer.OVERLAY, -1, UiRect(0f, 0f, screenWidth(), screenHeight()), "search", blur = false
        ) {
            val alpha = (255f * searchFade.value).roundToInt()
            if (alpha > 0) {
                val text = TrollTheme.trim(searchString, metrics, SEARCH_TEXT_SCALE, width - SEARCH_TEXT_MARGIN * 2f)
                TextWithShadow(
                    text, (width - metrics.textWidth(text, SEARCH_TEXT_SCALE)) * 0.5f,
                    (height - metrics.textHeight(SEARCH_TEXT_SCALE)) * 0.5f,
                    SEARCH_TEXT_SCALE, TrollTheme.alpha(TrollTheme.text(), alpha)
                )
            }
        }
    }

    override fun preeditUpdated(event: PreeditEvent?): Boolean {
        if (popupHost.activePopup != null) popupHost.preeditUpdated(event) else scene.get().input.preeditUpdated(event)

        preeditOverlay = event?.let { IMEPreeditOverlay(it, font, 15) }
        return true
    }

    override fun openRegistryListSettingPopup(setting: RegistryListSetting<*>) {
        popupHost.open(
            RegistryListSelectPopup.create(
                popupHost.getCenteredBounds(minOf(360.0f, screenWidth()), minOf(300.0f, screenHeight())),
                setting
            )
        )
    }

    override fun openStringListSettingPopup(setting: StringListSetting) {
        popupHost.open(
            StringListSelectPopup(
                popupHost.getCenteredBounds(minOf(300.0f, screenWidth()), minOf(260.0f, screenHeight())),
                setting,
                { value -> setting.add(value) },
                { value -> setting.remove(value) }
            )
        )
    }

    override fun removed() {
        endPendingFrame()
        popupHost.close()
        colorPicker?.cancel()
        closeColorPicker()
        closeSettings()
        clearSearch()
        for (window in windows) window.resetInput()
        friendWindowOpen = false
        configWindowOpen = false
        guiAnimationStarted = false
        preeditOverlay = null
        IMEFocusHelper.forceDeactivate()
        renderTarget?.close()
        renderTarget = null
        super.removed()
        luminView.close()
        windowViews.values.forEach { it.close() }
        windowViews.clear()
        scene.get().resetComposition()
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    private data class WindowPass(
        val bounds: UiRect,
        val batch: UiRenderBatch,
        val popup: Boolean,
        val blurRects: FloatArray?,
        val blurRadii: FloatArray?,
        val blurSegmentCount: Int,
        val blur: Boolean = true,
    )

    companion object {
        val INSTANCE: TrollGuiScreen = TrollGuiScreen()

        private const val TOGGLE_SIZE = 30.0f
        private const val TOGGLE_GAP = 8.0f
        private const val TOGGLE_MARGIN = 10.0f
        private const val SEARCH_TEXT_SCALE = 2.0f
        private const val SEARCH_TEXT_MARGIN = 8.0f
        private const val SEARCH_TEXT_FADE_MS = 5000L
    }

}
