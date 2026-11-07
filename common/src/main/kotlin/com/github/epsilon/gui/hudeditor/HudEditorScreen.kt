package com.github.epsilon.gui.hudeditor

import com.github.epsilon.elements.HudModule
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ListSettingPopupScreen
import com.github.epsilon.gui.dropdown.component.CategoryPanel
import com.github.epsilon.gui.lib.ClipIf
import com.github.epsilon.gui.lib.Content
import com.github.epsilon.gui.lib.Outline
import com.github.epsilon.gui.lib.Rect
import com.github.epsilon.gui.lib.RectOutline
import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.Shadow
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.popup.RegistryListSelectPopup
import com.github.epsilon.gui.panel.popup.StringListSelectPopup
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.HudElementManager
import com.github.epsilon.managers.NotificationManager
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
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import java.awt.Color
import kotlin.math.abs

object HudEditorScreen : Screen(Component.literal("HudEditor")), ListSettingPopupScreen {

    private const val SNAP_DISTANCE = 6.0f
    private const val ELEMENT_PADDING = 3.0f
    private const val LABEL_HEIGHT = 13.0f
    private const val GUIDE_ALPHA = 95.0f

    /**
     * HUD 编辑器自身的绘制层级。
     *
     * `UiLayer.CONTENT` 的基数是 200，因此这里的相对偏移最终落在 200 + offset：
     * - [Backdrop] = 110、[Guides] = 120 会进入 `UiLayer.CHROME` 的数值区间，但
     *   HudEditorScreen 不使用 CHROME 语义层，scene 里只有编辑器自己的 CONTENT 层，
     *   所以不会与其它子系统串层。
     * - 旧实现用 `editorLayer` 手写累加得到 -110 / -100 / -40 / 0 / 10 / 20 / 30；
     *   `LayerOffset` 强制落在 (-100, 100) 内，因此把越界的 -110 / -100 按「相对顺序不变」
     *   压缩到 -90 / -80（两者之间及其与 -40 之间原本没有其它内容），视觉结果一致。
     */
    private object HudEditorZ {

        /** 背景暗化。旧值 -110 越界，压缩为 -90。 */
        val Backdrop: LayerOffset = LayerOffset(-90)

        /** 中心参考线与吸附线。旧值 -100 越界，压缩为 -80。 */
        val Guides: LayerOffset = LayerOffset(-80)

        /** HUD 元素预览（元素自身的绘制批次也使用该相对层）。 */
        val Elements: LayerOffset = LayerOffset(-40)

        /** 选中 / 悬停元素的边框。 */
        val Frames: LayerOffset = LayerOffset.Same

        /** 画布标题等 chrome。 */
        val Chrome: LayerOffset = LayerOffset(10)

        /** 面板背景与阴影。 */
        val PanelSurface: LayerOffset = LayerOffset(20)

        /** 面板内容区与滚动条。 */
        val PanelContent: LayerOffset = LayerOffset(30)

    }

    private var hudPanel: CategoryPanel? = null
    private var renderTarget: LuminRenderSystem.LuminRenderTarget? = null
    private var preeditOverlay: IMEPreeditOverlay? = null
    private var renderFrameId = 0
    private var panelElementCount = -1
    private var selectedElement: HudModule? = null
    private var draggingElement: HudModule? = null
    private var dragOffsetX = 0.0f
    private var dragOffsetY = 0.0f
    private var currentSnap: SnapInfo = SnapInfo.none()

    private val textMetrics: TextRenderer = TextRenderer.create()
    private val scene: UiScene = UiScene(EpsilonUiTheme)
    private val popupHost: PanelPopupHost = PanelPopupHost()
    private var shouldRenderElements = false

    override fun init() {
        NotificationManager.clearAll()
        val panel = ensureHudPanel()
        panel.isVisible = true
        panel.isOpened = true
        panel.updateMaxPanelHeight(resolveMaxPanelHeight())
        panel.startIntro()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val window = minecraft.window
        val target = getRenderTarget(window.width, window.height)
        target.resize(window.width, window.height)
        target.clear()

        LuminRenderSystem.setActiveTarget(target)
        scene.beginFrame()

        val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)

        drawEditor(graphics, epsilonMouseX, epsilonMouseY, a)

        shouldRenderElements = true

        LuminRenderSystem.setActiveTarget(null)
        val overlay = preeditOverlay
        if (overlay != null) {
            overlay.updateInputPosition(IMEFocusHelper.activeCursorX.toInt(), IMEFocusHelper.activeCursorY.toInt())
            graphics.setPreeditOverlay(overlay)
        }
        graphics.blit(
            RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
            target.getIdentifier(),
            0,
            0,
            0.0f,
            window.height.toFloat(),
            window.guiScaledWidth,
            window.guiScaledHeight,
            window.width,
            -window.height,
            window.width,
            window.height
        )
        if (popupHost.activePopup == null) {
            drawElementOverlays(graphics)
        }
        popupHost.extractOverlay(graphics, epsilonMouseX, epsilonMouseY, a)
    }

    private fun drawEditor(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        ensureHudPanel()

        for (element in HudElementManager.getElements()) {
            element.updateLayout()
        }

        validateSelection()

        val screenW = LuminRenderSystem.getScaledWidth()
        val screenH = LuminRenderSystem.getScaledHeight()
        // 编辑画布覆盖整屏：作用域原点就是屏幕原点，画布坐标因此保持屏幕坐标
        val screenBounds = UiRect(0.0f, 0.0f, screenW, screenH)
        popupHost.setOverlayBounds(screenBounds)

        scene.Content(screenBounds, UiLayer.CONTENT, HudEditorZ.Backdrop) {
            Rect(0.0f, 0.0f, screenW, screenH, MD3Theme.withAlpha(MD3Theme.SURFACE_DIM, 72))
        }

        scene.Content(screenBounds, UiLayer.CONTENT, HudEditorZ.Guides) {
            val centerX = screenW / 2.0f
            val centerY = screenH / 2.0f
            val centerGuide = MD3Theme.withAlpha(MD3Theme.OUTLINE, 52)
            Rect(centerX - 0.5f, 0.0f, 1.0f, screenH, centerGuide)
            Rect(0.0f, centerY - 0.5f, screenW, 1.0f, centerGuide)
            drawSnapGuides(screenW, screenH)
        }

        val popupActive = popupHost.activePopup != null
        val backgroundMouseX = if (popupActive) Int.MIN_VALUE else mouseX
        val backgroundMouseY = if (popupActive) Int.MIN_VALUE else mouseY

        val hovered = findElementAt(backgroundMouseX.toDouble(), backgroundMouseY.toDouble(), true)
        scene.Content(screenBounds, UiLayer.CONTENT, HudEditorZ.Frames) {
            for (element in HudElementManager.getElements()) {
                if (!element.isEnabled) continue
                val selected = element === selectedElement
                val hover = element === hovered
                if (!selected && !hover) continue
                drawElementFrame(element, selected, hover)
            }
        }

        drawCanvasChrome()

        drawPanel(backgroundMouseX, backgroundMouseY)
        popupHost.render(graphics, scene.batch(UiLayer.POPUP), mouseX, mouseY, partialTick)
    }

    /**
     * 在世界画面提交后执行 HUD 预览，确保背景模糊能读取到主渲染目标的有效内容。
     */
    fun renderPendingHudElements() {
        val target = renderTarget
        if (!shouldRenderElements || minecraft.gui.screen() !== this || target == null) {
            return
        }

        val previousTarget = LuminRenderSystem.getActiveTarget()
        LuminRenderSystem.setActiveTarget(target)

        for (element in HudElementManager.getElements()) {
            if (element.isEnabled) {
                element.renderWithBatch(
                    minecraft.deltaTracker,
                    scene.batch(UiLayer.CONTENT, HudEditorZ.Elements.value)
                )
            }
        }
        scene.flush()
        popupHost.flush()
        scene.clear()

        shouldRenderElements = false
        LuminRenderSystem.setActiveTarget(previousTarget)
    }

    private fun getRenderTarget(width: Int, height: Int): LuminRenderSystem.LuminRenderTarget {
        val current = renderTarget
        if (current != null) {
            return current
        }
        val created = LuminRenderSystem.LuminRenderTarget.create("hud-editor-gui", width, height)
        renderTarget = created
        return created
    }

    private fun drawElementOverlays(graphics: GuiGraphicsExtractor) {
        val overlayScale = (LuminRenderSystem.getGuiScale() / minecraft.window.guiScale).toFloat()
        graphics.pose().pushMatrix()
        graphics.pose().scale(overlayScale, overlayScale)
        for (element in HudElementManager.getElements()) {
            if (!element.isEnabled) continue
            element.renderOverlay(graphics, minecraft.deltaTracker)
        }
        graphics.pose().popMatrix()
    }

    /**
     * 绘制 HUD 元素列表面板。
     *
     * 面板的作用域原点就是面板矩形：面板内部用 `x - scope.origin.x` 换算自身局部坐标，
     * 因此这里只需要把面板矩形交给 [Content]，不要再写绝对定位入口。
     */
    private fun drawPanel(mouseX: Int, mouseY: Int) {
        val panel = ensureHudPanel()
        panel.updateMaxPanelHeight(resolveMaxPanelHeight())
        panel.beginRenderFrame(++renderFrameId)

        val intro = panel.introValue
        if (intro <= 0.001f) return

        val shadowPad = DropdownTheme.PANEL_SHADOW_BLUR + 4.0f
        val slideOffset = (1.0f - intro) * 10.0f
        val origY = panel.y
        panel.setPosition(panel.x, origY - slideOffset)
        try {
            val panelH = panel.panelHeight
            val revealedH = panelH * intro

            val surfaceBounds = UiRect(panel.x, panel.y, panel.width, panelH)
            scene.Content(surfaceBounds, UiLayer.CONTENT, HudEditorZ.PanelSurface) {
                // 入场动画期间裁掉尚未展开的部分；scissor 矩形可以超出作用域矩形
                ClipIf(intro < 1.0f, -shadowPad, -shadowPad, panel.width + shadowPad * 2.0f, revealedH + shadowPad * 2.0f) {
                    panel.drawBackground()
                }
            }

            val clipY = panel.contentClipY
            val clipH = panel.contentClipHeight
            val revealedBottom = panel.y + revealedH
            val actualClipH = minOf(clipH, revealedBottom - clipY)
            if (actualClipH > 0.5f) {
                val contentBounds = UiRect(panel.x, clipY, panel.width, actualClipH)
                val requiresContentScissor = intro < 1.0f || panel.requiresContentScissor()
                scene.Content(contentBounds, UiLayer.CONTENT, HudEditorZ.PanelContent) {
                    ClipIf(requiresContentScissor, 0.0f, 0.0f, panel.width, actualClipH) {
                        panel.drawContent(mouseX, mouseY)
                    }
                }
            }
        } finally {
            panel.setPosition(panel.x, origY)
        }
    }

    private fun drawCanvasChrome() {
        val title = "HUD Editor"
        val subtitle = selectedElement?.translatedName ?: "Select and drag an element"
        val titleScale = 0.64f
        val subtitleScale = 0.56f
        val titleW = textMetrics.getWidth(title, titleScale)
        val subW = textMetrics.getWidth(subtitle, subtitleScale)
        val titleH = textMetrics.getHeight(titleScale)
        val subtitleH = textMetrics.getHeight(subtitleScale)
        val boxW = maxOf(titleW, subW) + 24.0f
        val boxH = 32.0f
        val radius = 8.0f
        val middlePadding = 3.0f
        val labelX = (LuminRenderSystem.getScaledWidth() - boxW) * 0.5f
        val labelY = DropdownTheme.PANEL_MARGIN_Y + 2.0f
        val titleY = labelY + (boxH - titleH - middlePadding - subtitleH) * 0.5f
        val subtitleY = titleY + titleH + middlePadding

        val screenBounds = UiRect(0.0f, 0.0f, LuminRenderSystem.getScaledWidth(), LuminRenderSystem.getScaledHeight())
        scene.Content(screenBounds, UiLayer.CONTENT, HudEditorZ.Chrome) {
            Shadow(
                labelX, labelY, boxW, boxH, radius, MD3Theme.FLOATING_LABEL_SHADOW_BLUR,
                MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.FLOATING_LABEL_SHADOW_ALPHA)
            )
            RoundRect(labelX, labelY, boxW, boxH, radius, MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, 238))
            Text(title, labelX + 12.0f, titleY, titleScale, MD3Theme.TEXT_PRIMARY)
            Text(subtitle, labelX + 12.0f, subtitleY, subtitleScale, MD3Theme.TEXT_MUTED)
        }
    }

    private fun UiScope.drawSnapGuides(screenW: Float, screenH: Float) {
        if (currentSnap.hasAny()) {
            val guideColor = MD3Theme.withAlpha(MD3Theme.PRIMARY, GUIDE_ALPHA.toInt())
            val verticalLineX = currentSnap.verticalLineX
            if (!verticalLineX.isNaN()) {
                Rect(verticalLineX - 0.5f, 0.0f, 1.0f, screenH, guideColor)
            }
            val horizontalLineY = currentSnap.horizontalLineY
            if (!horizontalLineY.isNaN()) {
                Rect(0.0f, horizontalLineY - 0.5f, screenW, 1.0f, guideColor)
            }
        }
    }

    private fun UiScope.drawElementFrame(element: HudModule, selected: Boolean, hover: Boolean) {
        val x = element.x - ELEMENT_PADDING
        val y = element.y - ELEMENT_PADDING
        val w = element.width + ELEMENT_PADDING * 2.0f
        val h = element.height + ELEMENT_PADDING * 2.0f
        val frameColor: Color = if (selected) MD3Theme.PRIMARY else MD3Theme.withAlpha(MD3Theme.OUTLINE, 150)
        val fillColor: Color = if (selected) {
            MD3Theme.withAlpha(MD3Theme.PRIMARY_CONTAINER, 44)
        } else {
            MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGH, if (hover) 48 else 24)
        }

        Rect(x, y, w, h, fillColor)
        RectOutline(x, y, w, h, if (selected) 1.2f else 0.8f, frameColor)

        if (selected) {
            drawAnchorMarker(element, frameColor)
            drawElementLabel(element, x, y)
        }
    }

    private fun UiScope.drawAnchorMarker(element: HudModule, color: Color) {
        val anchorX = HudLayoutHelper.getAnchorPointX(element.horizontalAnchor, element.x, element.width)
        val anchorY = HudLayoutHelper.getAnchorPointY(element.verticalAnchor, element.y, element.height)
        Rect(anchorX - 2.5f, anchorY - 2.5f, 5.0f, 5.0f, color)
    }

    private fun UiScope.drawElementLabel(element: HudModule, frameX: Float, frameY: Float) {
        val label = element.translatedName
        val scale = 0.48f
        val textW = metrics.textWidth(label, scale)
        val labelW = textW + 10.0f
        val labelY = frameY - LABEL_HEIGHT - 3.0f
        val textY = labelY + (LABEL_HEIGHT - metrics.textHeight(scale)) * 0.5f
        RoundRect(frameX, labelY, labelW, LABEL_HEIGHT, 6.5f, MD3Theme.PRIMARY_CONTAINER)
        Text(label, frameX + (labelW - textW) / 2.0f, textY, scale, MD3Theme.ON_PRIMARY_CONTAINER)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (popupHost.keyPressed(event)) {
            return true
        }
        val panel = hudPanel
        if (panel != null && panel.hasActiveInput() && panel.keyPressed(
                event.key(),
                event.keycode(),
                event.modifiers()
            )
        ) {
            return true
        }
        if (event.isEscape) {
            onClose()
            return true
        }
        if (handleEditorKey(event)) {
            return true
        }
        if (panel != null && panel.keyPressed(event.key(), event.keycode(), event.modifiers())) {
            return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (popupHost.charTyped(event)) {
            return true
        }
        val typed = event.codepointAsString()
        val panel = hudPanel
        return panel != null && !typed.isEmpty() && panel.charTyped(typed) || super.charTyped(event)
    }

    override fun preeditUpdated(event: PreeditEvent?): Boolean {
        preeditOverlay = if (event == null) null else IMEPreeditOverlay(event, font, 10)
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        if (popupHost.mouseClicked(epsilonEvent, isDoubleClick)) {
            return true
        }
        val panel = hudPanel
        if (panel != null && panel.mouseClicked(epsilonEvent.x(), epsilonEvent.y(), epsilonEvent.button())) {
            validateSelection()
            return true
        }
        if (epsilonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            val element = findElementAt(epsilonEvent.x(), epsilonEvent.y(), false)
            if (element != null) {
                selectedElement = element
                draggingElement = element
                dragOffsetX = epsilonEvent.x().toFloat() - element.x
                dragOffsetY = epsilonEvent.y().toFloat() - element.y
                currentSnap = SnapInfo.none()
                return true
            }
            selectedElement = null
            currentSnap = SnapInfo.none()
            return true
        }
        return super.mouseClicked(epsilonEvent, isDoubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        if (popupHost.mouseReleased(epsilonEvent)) {
            return true
        }
        if (draggingElement != null && epsilonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            draggingElement = null
            currentSnap = SnapInfo.none()
            return true
        }
        val panel = hudPanel
        return panel != null && panel.mouseReleased(
            epsilonEvent.x(),
            epsilonEvent.y(),
            epsilonEvent.button()
        ) || super.mouseReleased(epsilonEvent)
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)
        if (popupHost.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY)) {
            return true
        }
        val dragging = draggingElement
        if (dragging != null) {
            val epsilonEventX = LuminRenderSystem.toEpsilonMouseX(event.x())
            val epsilonEventY = LuminRenderSystem.toEpsilonMouseY(event.y())
            moveElementTo(dragging, epsilonEventX.toFloat() - dragOffsetX, epsilonEventY.toFloat() - dragOffsetY, true)
            return true
        }
        hudPanel?.mouseDragged(
            LuminRenderSystem.toEpsilonMouseX(event.x()),
            LuminRenderSystem.toEpsilonMouseY(event.y())
        )
        return super.mouseDragged(epsilonEvent, epsilonMouseX, epsilonMouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)
        if (popupHost.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)) {
            return true
        }
        if (hudPanel?.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollY) == true) {
            return true
        }
        return super.mouseScrolled(epsilonMouseX, epsilonMouseY, scrollX, scrollY)
    }

    private fun handleEditorKey(event: KeyEvent): Boolean {
        val selected = selectedElement ?: return false

        val step = if (event.hasShiftDown()) 10.0f else 1.0f
        return when (event.key()) {
            InputConstants.KEY_LEFT -> {
                moveElementTo(selected, selected.x - step, selected.y, false)
                true
            }

            InputConstants.KEY_RIGHT -> {
                moveElementTo(selected, selected.x + step, selected.y, false)
                true
            }

            InputConstants.KEY_UP -> {
                moveElementTo(selected, selected.x, selected.y - step, false)
                true
            }

            InputConstants.KEY_DOWN -> {
                moveElementTo(selected, selected.x, selected.y + step, false)
                true
            }

            InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> {
                selected.isEnabled = false
                selectedElement = null
                true
            }

            else -> false
        }
    }

    private fun moveElementTo(element: HudModule, targetX: Float, targetY: Float, snap: Boolean) {
        val snapInfo = if (snap) {
            computeSnap(element, targetX, targetY)
        } else {
            SnapInfo(targetX, targetY, Float.NaN, Float.NaN)
        }
        currentSnap = snapInfo
        element.moveTo(snapInfo.resolvedX(), snapInfo.resolvedY())
    }

    private fun computeSnap(element: HudModule, targetX: Float, targetY: Float): SnapInfo {
        val screenW = LuminRenderSystem.getScaledWidth()
        val screenH = LuminRenderSystem.getScaledHeight()
        var snappedX = targetX
        var snappedY = targetY
        var verticalGuide = Float.NaN
        var horizontalGuide = Float.NaN

        val leftDelta = abs(targetX)
        val centerDelta = abs(targetX + element.width / 2.0f - screenW / 2.0f)
        val rightDelta = abs(targetX + element.width - screenW)
        var bestX = leftDelta
        var bestXIndex = 0
        if (centerDelta < bestX) {
            bestX = centerDelta
            bestXIndex = 1
        }
        if (rightDelta < bestX) {
            bestX = rightDelta
            bestXIndex = 2
        }
        if (bestX <= SNAP_DISTANCE) {
            if (bestXIndex == 0) {
                snappedX = 0.0f
                verticalGuide = 0.0f
            } else if (bestXIndex == 1) {
                snappedX = screenW / 2.0f - element.width / 2.0f
                verticalGuide = screenW / 2.0f
            } else {
                snappedX = screenW - element.width
                verticalGuide = screenW
            }
        }

        val topDelta = abs(targetY)
        val middleDelta = abs(targetY + element.height / 2.0f - screenH / 2.0f)
        val bottomDelta = abs(targetY + element.height - screenH)
        var bestY = topDelta
        var bestYIndex = 0
        if (middleDelta < bestY) {
            bestY = middleDelta
            bestYIndex = 1
        }
        if (bottomDelta < bestY) {
            bestY = bottomDelta
            bestYIndex = 2
        }
        if (bestY <= SNAP_DISTANCE) {
            if (bestYIndex == 0) {
                snappedY = 0.0f
                horizontalGuide = 0.0f
            } else if (bestYIndex == 1) {
                snappedY = screenH / 2.0f - element.height / 2.0f
                horizontalGuide = screenH / 2.0f
            } else {
                snappedY = screenH - element.height
                horizontalGuide = screenH
            }
        }

        return SnapInfo(snappedX, snappedY, verticalGuide, horizontalGuide)
    }

    private fun findElementAt(mouseX: Double, mouseY: Double, includePanelArea: Boolean): HudModule? {
        if (!includePanelArea && isOverPanel(mouseX, mouseY)) {
            return null
        }
        val elements = HudElementManager.getElements()
        for (i in elements.size - 1 downTo 0) {
            val element = elements[i]
            if (!element.isEnabled) continue
            if (element.contains(mouseX, mouseY)) {
                return element
            }
        }
        return null
    }

    private fun isOverPanel(mouseX: Double, mouseY: Double): Boolean {
        val panel = hudPanel ?: return false
        return mouseX >= panel.x &&
                mouseX <= panel.x + panel.width &&
                mouseY >= panel.y &&
                mouseY <= panel.y + panel.panelHeight
    }

    private fun validateSelection() {
        val selected = selectedElement
        if (selected != null && !selected.isEnabled) {
            selectedElement = null
            draggingElement = null
            currentSnap = SnapInfo.none()
        }
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    override fun onClose() {
        IMEFocusHelper.forceDeactivate()
        super.onClose()

        minecraft.gui.setScreen(ClientSetting.getGuiScreen())
    }

    override fun removed() {
        super.removed()
        popupHost.close()
        shouldRenderElements = false
        scene.clear()
        draggingElement = null
        currentSnap = SnapInfo.none()
        IMEFocusHelper.forceDeactivate()
        preeditOverlay = null
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        if (this.minecraft.level == null) {
            this.extractPanorama(graphics, a)
        }
    }

    private fun resolveMaxPanelHeight(): Float {
        return minOf(LuminRenderSystem.getScaledHeight() * 0.72f, 350.0f)
    }

    override fun openRegistryListSettingPopup(setting: RegistryListSetting<*>) {
        val bounds = popupHost.getCenteredBounds(
            minOf(360.0f, LuminRenderSystem.getScaledWidth() - 28.0f),
            minOf(300.0f, LuminRenderSystem.getScaledHeight() - 28.0f)
        )
        popupHost.open(RegistryListSelectPopup.create(bounds, setting))
    }

    override fun openStringListSettingPopup(setting: StringListSetting) {
        val bounds = popupHost.getCenteredBounds(
            minOf(300.0f, LuminRenderSystem.getScaledWidth() - 28.0f),
            minOf(260.0f, LuminRenderSystem.getScaledHeight() - 28.0f)
        )
        popupHost.open(
            StringListSelectPopup(
                bounds,
                setting,
                { value -> setting.add(value) },
                { value -> setting.remove(value) })
        )
    }

    private fun ensureHudPanel(): CategoryPanel {
        val elementCount = HudElementManager.getElements().size
        val existing = hudPanel
        if (existing != null && panelElementCount == elementCount) return existing

        val x = existing?.x ?: DropdownTheme.PANEL_MARGIN_X
        val y = existing?.y ?: DropdownTheme.PANEL_MARGIN_Y
        val panel = CategoryPanel("hud_elements", "HUD", IconChars.WIDGETS, 0, HudElementManager.getElements())
        panel.isVisible = true
        panel.isOpened = true
        panel.setPosition(x, y)
        panel.updateMaxPanelHeight(resolveMaxPanelHeight())
        hudPanel = panel
        panelElementCount = elementCount
        return panel
    }

    private data class SnapInfo(
        val x: Float, val y: Float, val verticalLineX: Float, val horizontalLineY: Float,
    ) {

        fun hasAny(): Boolean {
            return !verticalLineX.isNaN() || !horizontalLineY.isNaN()
        }

        fun resolvedX(): Float {
            return if (x.isNaN()) 0.0f else x
        }

        fun resolvedY(): Float {
            return if (y.isNaN()) 0.0f else y
        }

        companion object {

            fun none(): SnapInfo {
                return SnapInfo(Float.NaN, Float.NaN, Float.NaN, Float.NaN)
            }

        }
    }

}
