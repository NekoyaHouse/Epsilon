package com.github.epsilon.gui.troll

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.ListSettingPopupScreen
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.popup.RegistryListSelectPopup
import com.github.epsilon.gui.panel.popup.StringListSelectPopup
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.GuiSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.RegistryListSetting
import com.github.epsilon.settings.impl.StringListSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
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
import kotlin.math.roundToInt

class TrollGuiScreen private constructor() : Screen(Component.literal("TrollGui")), ListSettingPopupScreen {

    private val textMetrics: Supplier<TextRenderer> = Suppliers.memoize(Supplier { TextRenderer.create() })
    private val scene: Supplier<UiScene> = Suppliers.memoize(Supplier { UiScene(EpsilonUiTheme) })
    private val windows: MutableList<TrollWindow> = ArrayList()
    private val popupHost: PanelPopupHost = PanelPopupHost()
    private val guiOpenAnimation: Animation = Animation(Easing.EASE_OUT_CUBIC, 400L)
    private val friendToggleHover: Animation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val configToggleHover: Animation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private var friendWindow: TrollFriendWindow? = null
    private var configWindow: TrollConfigWindow? = null
    private var friendWindowOpen: Boolean = false
    private var configWindowOpen: Boolean = false
    private var guiAnimationStarted: Boolean = false
    private var searchString: String = ""
    private var searchUpdatedAt: Long = 0L
    private var friendToggleBounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var configToggleBounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var settingsWindow: TrollSettingsWindow? = null
    private var colorPicker: TrollColorPicker? = null
    private var capturedWindow: TrollWindow? = null
    private var renderTarget: LuminRenderSystem.LuminRenderTarget? = null
    private var preeditOverlay: IMEPreeditOverlay? = null
    private val pendingWindows: MutableList<WindowPass> = ArrayList()
    private var pendingScene: UiScene? = null
    private var pendingBackground: UiRenderBatch? = null
    private var pendingWindowBlurStrength: Float = 0.0f
    private var pendingFullscreenBlurStrength: Float = 0.0f
    private var guiOpenOffsetY: Float = 0.0f
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
        for (window in windows) window.layout(textMetrics.get(), screenWidth(), screenHeight())
        settingsWindow?.layout(textMetrics.get(), screenWidth(), screenHeight())
        colorPicker?.layout(textMetrics.get(), screenWidth(), screenHeight())
        updateToggleBounds()
        popupHost.setOverlayBounds(UiRect(0.0f, 0.0f, screenWidth(), screenHeight()))
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

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        endPendingFrame()
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
        layoutWindows()
        guiOpenAnimation.run(1.0f)
        val openProgress = guiOpenAnimation.value
        guiOpenOffsetY = (1.0f - openProgress) * screenHeight()
        val frame = scene.get()
        frame.beginFrame()
        pendingScene = frame
        pendingFrameWidth = screenWidth()
        pendingFrameHeight = screenHeight()
        pendingWindowBlurStrength = GuiSetting.blurStrength.toFloat() * openProgress
        pendingFullscreenBlurStrength = if (GuiSetting.backgroundBlur) {
            maxOf(1.5f, GuiSetting.blurStrength.toFloat()) * openProgress
        } else {
            0.0f
        }
        try {
            graphics.blurBeforeThisStratum()
            pendingBackground = frame.batch(UiLayer.BACKGROUND)
            if (GuiSetting.background) {
                val backgroundAlpha = (144.0f * openProgress).roundToInt()
                pendingBackground!!.render(
                    UiTree.build { scope ->
                        scope.rect(0.0f, 0.0f, pendingFrameWidth, pendingFrameHeight, Color(0, 0, 0, backgroundAlpha))
                    }
                )
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
            endPendingFrame()
            throw exception
        } catch (error: Error) {
            endPendingFrame()
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

    private fun queueWindow(frame: UiScene, layer: UiLayer, relativeLayer: Int, bounds: UiRect, tree: UiTree) {
        queueWindow(frame, layer, relativeLayer, bounds, tree, null, null, 0)
    }

    private fun queueWindow(
        frame: UiScene, layer: UiLayer, relativeLayer: Int, bounds: UiRect, tree: UiTree,
        blurRects: FloatArray?, blurRadii: FloatArray?, blurSegmentCount: Int, blur: Boolean = true,
    ) {
        val batch = frame.batch(layer, relativeLayer)
        batch.render(tree)
        pendingWindows.add(WindowPass(bounds, batch, false, blurRects, blurRadii, blurSegmentCount, blur))
    }

    private fun renderAnimatedWindow(
        frame: UiScene, layer: UiLayer, relativeLayer: Int, window: TrollWindow, mouseX: Int, mouseY: Int,
    ) {
        val windowProgress = window.getOpenProgress()
        val fullHeight = window.bounds.height
        val revealHeight = window.headerHeight + maxOf(0.0f, fullHeight - window.headerHeight) * windowProgress
        if (revealHeight <= 0.01f) return

        val clip = UiRect(window.bounds.x, window.bounds.y, window.bounds.width, revealHeight)
        val passBounds = UiRect(window.bounds.x, window.bounds.y + guiOpenOffsetY, window.bounds.width, revealHeight)
        val tree = UiTree.build { scope ->
            scope.pushAbsolute(0.0f, guiOpenOffsetY) { translated ->
                translated.scissor(clip) { content ->
                    window.draw(content, textMetrics.get(), mouseX, mouseY)
                }
            }
        }
        queueWindow(frame, layer, relativeLayer, passBounds, tree)
    }

    private fun renderToggleButtons(frame: UiScene, mouseX: Int, mouseY: Int) {
        if (friendToggleBounds.width <= 0.0f || configToggleBounds.width <= 0.0f) return
        val tree = UiTree.build { scope ->
            drawToggleButton(
                scope,
                friendToggleBounds,
                IconChars.PEOPLE,
                friendWindowOpen,
                mouseX,
                mouseY,
                friendToggleHover
            )
            drawToggleButton(
                scope,
                configToggleBounds,
                IconChars.SETTINGS,
                configWindowOpen,
                mouseX,
                mouseY,
                configToggleHover
            )
        }
        val left = minOf(friendToggleBounds.x, configToggleBounds.x)
        val top = minOf(friendToggleBounds.y, configToggleBounds.y)
        val right = maxOf(friendToggleBounds.right(), configToggleBounds.right())
        val bottom = maxOf(friendToggleBounds.bottom(), configToggleBounds.bottom())
        val blurRects = floatArrayOf(
            friendToggleBounds.x, friendToggleBounds.y, friendToggleBounds.width, friendToggleBounds.height,
            configToggleBounds.x, configToggleBounds.y, configToggleBounds.width, configToggleBounds.height
        )
        val blurRadii = floatArrayOf(
            friendToggleBounds.width * 0.5f,
            configToggleBounds.width * 0.5f
        )
        queueWindow(
            frame,
            UiLayer.FLOATING,
            -90,
            UiRect(left, top, right - left, bottom - top),
            tree,
            blurRects,
            blurRadii,
            2
        )
    }

    private fun drawToggleButton(
        scope: UiTree.Scope, bounds: UiRect, icon: String, active: Boolean,
        mouseX: Int, mouseY: Int, hoverAnimation: Animation,
    ) {
        val hover = scope.animate(hoverAnimation, bounds.contains(mouseX.toDouble(), mouseY.toDouble()))
        val radius = bounds.width * 0.5f
        val backgroundAlpha = if (active) (205 + 50 * hover).toInt() else (120 + 40 * hover).toInt()
        val background = if (active) {
            TrollTheme.alpha(TrollTheme.primary(), backgroundAlpha)
        } else {
            Color(0, 0, 0, backgroundAlpha)
        }
        scope.roundRect(2, bounds.x, bounds.y, bounds.width, bounds.height, radius, background)
        val metrics = textMetrics.get()
        val iconScale = 0.92f
        val iconWidth = metrics.getWidth(icon, iconScale, StaticFontLoader.ICONS)
        val iconHeight = metrics.getHeight(iconScale, StaticFontLoader.ICONS)
        scope.textWithShadow(
            4, icon,
            bounds.centerX() - iconWidth * 0.5f,
            bounds.centerY() - iconHeight * 0.5f,
            iconScale,
            if (active) TrollTheme.text() else TrollTheme.alpha(TrollTheme.text(), 220),
            StaticFontLoader.ICONS
        )
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

    private fun endPendingFrame() {
        val frame = pendingScene
        pendingScene = null
        pendingBackground = null
        pendingWindows.clear()
        if (frame != null) {
            frame.clear()
            frame.endFrame()
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
        for (window in windows) window.blurInput()
        colorPicker?.cancel()
        val created = TrollColorPicker(setting, x.toFloat(), y.toFloat())
        colorPicker = created
        created.startOpenAnimation()
        created.layout(textMetrics.get(), screenWidth(), screenHeight())
    }

    private fun closeSettings() {
        val previous = settingsWindow
        settingsWindow = null
        previous?.resetInput()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouse = LuminRenderSystem.toEpsilonMouseEvent(event)
        capturedWindow = null
        if (popupHost.activePopup != null) {
            popupHost.mouseClicked(mouse, doubleClick)
            return true
        }
        val colorPicker = this.colorPicker
        if (colorPicker != null) {
            if (!colorPicker.bounds.contains(mouse.x(), mouse.y())) {
                colorPicker.cancel()
            } else {
                capturedWindow = colorPicker
                colorPicker.mouseClicked(mouse)
            }
            if (colorPicker.isClosed()) this.colorPicker = null
            return true
        }
        val settingsWindow = this.settingsWindow
        if (settingsWindow != null) {
            if (settingsWindow.captureMouseBind(mouse)) return true
            if (!settingsWindow.bounds.contains(mouse.x(), mouse.y())) {
                closeSettings()
            } else {
                capturedWindow = settingsWindow
                settingsWindow.mouseClicked(mouse)
            }
            return true
        }
        if (friendToggleBounds.contains(mouse.x(), mouse.y())) {
            for (window in windows) window.blurInput()
            toggleFriendWindow()
            return true
        }
        if (configToggleBounds.contains(mouse.x(), mouse.y())) {
            for (window in windows) window.blurInput()
            toggleConfigWindow()
            return true
        }
        val window = hoveredWindow(mouse.x(), mouse.y())
        if (window != null) {
            for (other in windows) {
                if (other !== window) other.blurInput()
            }
            windows.remove(window)
            windows.add(window)
            capturedWindow = window
            window.mouseClicked(mouse)
            return true
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val captured = capturedWindow
        capturedWindow = null
        if (popupHost.activePopup != null) {
            popupHost.mouseReleased(LuminRenderSystem.toEpsilonMouseEvent(event))
        } else if (captured != null) {
            captured.mouseReleased(LuminRenderSystem.toEpsilonMouseEvent(event))
        }
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mouse = LuminRenderSystem.toEpsilonMouseEvent(event)
        if (popupHost.activePopup != null) {
            popupHost.mouseDragged(mouse, LuminRenderSystem.toEpsilonMouseX(dx), LuminRenderSystem.toEpsilonMouseY(dy))
        } else {
            capturedWindow?.mouseDragged(mouse)
        }
        return true
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val mx = LuminRenderSystem.toEpsilonMouseX(x)
        val my = LuminRenderSystem.toEpsilonMouseY(y)
        if (popupHost.activePopup != null) {
            popupHost.mouseScrolled(mx, my, scrollX, scrollY)
        } else {
            val colorPicker = this.colorPicker
            if (colorPicker != null) {
                return true
            }
            val settingsWindow = this.settingsWindow
            if (settingsWindow != null) {
                if (settingsWindow.viewport.contains(mx, my)) settingsWindow.scroll.scroll(scrollY)
            } else {
                val window = hoveredWindow(mx, my)
                window?.mouseScrolled(mx, my, scrollY)
            }
        }
        return true
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
            if (colorPicker.isClosed()) this.colorPicker = null
            return true
        }
        val settingsWindow = this.settingsWindow
        if (settingsWindow != null) {
            if (!settingsWindow.keyPressed(event) && event.isEscape) closeSettings()
            return true
        }
        for (i in windows.indices.reversed()) {
            val window = windows[i]
            if (isWindowVisible(window) && window.keyPressed(event)) return true
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
        if (colorPicker != null) return false
        val settingsWindow = this.settingsWindow
        if (settingsWindow != null) return settingsWindow.charTyped(event)
        for (i in windows.indices.reversed()) {
            val window = windows[i]
            if (isWindowVisible(window) && window.charTyped(event)) return true
        }
        if (event.codepoint() == ' '.code || Character.isLetter(event.codepoint())) {
            appendSearch(event.codepointAsString())
            return true
        }
        return false
    }

    private fun appendSearch(text: String) {
        if (text.isEmpty()) return
        searchString += text
        searchUpdatedAt = System.currentTimeMillis()
        applySearchFilter()
    }

    private fun clearSearch() {
        if (searchString.isEmpty()) return
        searchString = ""
        searchUpdatedAt = 0L
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
        val elapsed = System.currentTimeMillis() - searchUpdatedAt
        if (elapsed >= SEARCH_TEXT_FADE_MS) return
        val progress = (elapsed.toFloat() / SEARCH_TEXT_FADE_MS).coerceIn(0.0f, 1.0f)
        val alpha = (255.0f * (1.0f - Easing.EASE_IN_CUBIC.function.apply(progress))).roundToInt()
        if (alpha <= 0) return
        val metrics = textMetrics.get()
        val text = TrollTheme.trim(
            searchString,
            metrics,
            SEARCH_TEXT_SCALE,
            screenWidth() - SEARCH_TEXT_MARGIN * 2.0f
        )
        if (text.isEmpty()) return
        val width = metrics.getWidth(text, SEARCH_TEXT_SCALE)
        val height = metrics.getHeight(SEARCH_TEXT_SCALE)
        val tree = UiTree.build { scope ->
            scope.textWithShadow(
                text,
                (screenWidth() - width) * 0.5f,
                (screenHeight() - height) * 0.5f,
                SEARCH_TEXT_SCALE,
                TrollTheme.alpha(TrollTheme.text(), alpha)
            )
        }
        queueWindow(
            frame,
            UiLayer.OVERLAY,
            -1,
            UiRect(0.0f, 0.0f, screenWidth(), screenHeight()),
            tree,
            null,
            null,
            0,
            false
        )
    }

    override fun preeditUpdated(event: PreeditEvent?): Boolean {
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
        colorPicker = null
        closeSettings()
        clearSearch()
        for (window in windows) window.resetInput()
        friendWindowOpen = false
        configWindowOpen = false
        guiAnimationStarted = false
        capturedWindow = null
        preeditOverlay = null
        IMEFocusHelper.forceDeactivate()
        renderTarget?.close()
        renderTarget = null
        super.removed()
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
