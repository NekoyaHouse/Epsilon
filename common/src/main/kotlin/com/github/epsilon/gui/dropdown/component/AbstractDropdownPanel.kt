package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.control.UiScrollBar
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.util.Mth
import kotlin.math.abs

abstract class AbstractDropdownPanel protected constructor(
    override val id: String,
    private val title: String?,
    private val titleComponent: TranslateComponent?,
    private val titleSupplier: TitleSupplier?,
    protected val icon: String?,
    panelIndex: Int,
) : DropdownPanel {

    protected constructor(id: String, title: String, icon: String?, panelIndex: Int) :
            this(id, title, null, null, icon, panelIndex)

    protected constructor(id: String, titleComponent: TranslateComponent, icon: String?, panelIndex: Int) :
            this(id, null, titleComponent, null, icon, panelIndex)

    protected constructor(id: String, titleSupplier: TitleSupplier, icon: String?, panelIndex: Int) :
            this(id, null, null, titleSupplier, icon, panelIndex)

    protected val openAnim: Animation = Animation(Easing.EASE_IN_OUT_CUBIC, DropdownTheme.ANIM_OPEN)
    protected val introAnim: Animation = Animation(Easing.EASE_OUT_SINE, 120L + panelIndex * 45L)
    protected val scrollBar: UiScrollBar = UiScrollBar(EpsilonUiTheme)

    override var x: Float = 0.0f
    override var y: Float = 0.0f
    override var width: Float = DropdownTheme.PANEL_WIDTH
    private var openedState: Boolean = false
    protected var dragging: Boolean = false
    protected var dragOffsetX: Float = 0.0f
    protected var dragOffsetY: Float = 0.0f
    protected var scroll: Float = 0.0f
    protected var targetScroll: Float = 0.0f
    protected var maxScroll: Float = 0.0f
    protected var maxPanelHeight: Float = 300.0f

    private var renderFrameId: Int = Integer.MIN_VALUE
    private var cachedMetricsFrameId: Int = Integer.MIN_VALUE
    private var cachedExpand: Float = 0.0f
    private var cachedContentHeight: Float = 0.0f
    private var cachedVisibleContentHeight: Float = 0.0f
    private var cachedPanelHeight: Float = 0.0f

    override var isOpened: Boolean
        get() = openedState
        set(value) {
            openedState = value
            if (!value) {
                scrollBar.reset()
                setScrollImmediate(0.0f)
            }
        }

    override var isVisible: Boolean = false
        set(value) {
            field = value
            if (!value) {
                dragging = false
                scrollBar.reset()
            }
        }

    override fun startIntro() {
        introAnim.startValue = 0.0f
        introAnim.run(0.0f)
        introAnim.run(1.0f)
        openAnim.startValue = if (openedState) 1.0f else 0.0f
    }

    override fun beginRenderFrame(frameId: Int) {
        renderFrameId = frameId
        cachedMetricsFrameId = Integer.MIN_VALUE
    }

    override val introValue: Float
        get() {
            introAnim.run(1.0f)
            return introAnim.value
        }

    override fun drawBackground(scope: UiTree.Scope, textMetrics: UiTextMetrics) {
        ensureFrameMetrics()
        val expand = cachedExpand
        val contentHeight = cachedContentHeight
        val visibleHeight = cachedVisibleContentHeight
        updateScroll(contentHeight, visibleHeight, true)
        val panelHeight = cachedPanelHeight

        scope.shadow(x, y, width, panelHeight, DropdownTheme.PANEL_RADIUS, DropdownTheme.PANEL_SHADOW_BLUR, DropdownTheme.panelShadow())
        scope.roundRect(x, y, width, panelHeight, DropdownTheme.PANEL_RADIUS, DropdownTheme.panelBackground())

        val iconX = x + 7.5f
        val textX = if (icon == null || icon.isBlank()) x + 10.0f else iconX + 16.0f
        val textY = y + (DropdownTheme.PANEL_HEADER_HEIGHT - textMetrics.textHeight(DropdownTheme.HEADER_TEXT_SCALE)) * 0.5f
        val iconText = icon
        if (iconText != null && !iconText.isBlank()) {
            val iconY = y + (DropdownTheme.PANEL_HEADER_HEIGHT - textMetrics.textHeight(DropdownTheme.HEADER_ICON_SCALE, StaticFontLoader.ICONS)) * 0.5f
            scope.text(iconText, iconX, iconY, DropdownTheme.HEADER_ICON_SCALE, MD3Theme.PRIMARY, StaticFontLoader.ICONS)
        }
        val headerTitle = getTitle()
        scope.text(headerTitle, textX, textY, DropdownTheme.HEADER_TEXT_SCALE, MD3Theme.TEXT_PRIMARY)
        scope.triangle(x + width - 10.0f, y + DropdownTheme.PANEL_HEADER_HEIGHT * 0.5f, 3.0f, expand, DropdownTheme.groupChevron(0.0f))
    }

    override fun drawContent(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int) {
        ensureFrameMetrics()
        if (cachedExpand < 0.01f) return

        val contentHeight = cachedContentHeight
        val visibleHeight = cachedVisibleContentHeight
        updateScroll(contentHeight, visibleHeight, false)
        val scrollbarViewport = getScrollbarViewport()
        val scrollbarHovered = scrollBar.isHovered(mouseX.toDouble(), mouseY.toDouble(), scrollbarViewport, scroll, maxScroll, contentHeight)
        val contentMouseX = if (scrollbarHovered || scrollBar.isDragging()) Integer.MIN_VALUE else mouseX
        val contentMouseY = if (scrollbarHovered || scrollBar.isDragging()) Integer.MIN_VALUE else mouseY
        drawPanelContent(scope, textMetrics, contentMouseX, contentMouseY, visibleHeight)
        scrollBar.draw(scope, scrollbarViewport, scroll, maxScroll, contentHeight, mouseX.toDouble(), mouseY.toDouble())
    }

    override val contentClipY: Float
        get() = y + DropdownTheme.PANEL_HEADER_HEIGHT

    override val contentClipHeight: Float
        get() {
            ensureFrameMetrics()
            return cachedVisibleContentHeight * cachedExpand
        }

    override fun requiresContentScissor(): Boolean {
        ensureFrameMetrics()
        return cachedExpand < 1.0f || cachedContentHeight > cachedVisibleContentHeight
    }

    override val panelHeight: Float
        get() {
            ensureFrameMetrics()
            return cachedPanelHeight
        }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        ensureFrameMetrics()
        updateScroll(cachedContentHeight, cachedVisibleContentHeight, false)

        if (isHeaderHovered(mouseX, mouseY)) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                dragging = true
                dragOffsetX = (x - mouseX).toFloat()
                dragOffsetY = (y - mouseY).toFloat()
                DropdownScreen.react(ReisaDropdownCompanion.Action.DRAG)
                return true
            }
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                openedState = !openedState
                DropdownScreen.react(
                    if (openedState) ReisaDropdownCompanion.Action.PANEL_OPEN else ReisaDropdownCompanion.Action.PANEL_CLOSE
                )
                return true
            }
        }

        if (button == InputConstants.MOUSE_BUTTON_LEFT && scrollBar.mouseClicked(mouseX, mouseY, getScrollbarViewport(), scroll, maxScroll, cachedContentHeight)) {
            val newScroll = scrollBar.mouseDragged(mouseY, getScrollbarViewport(), maxScroll, cachedContentHeight)
            if (newScroll >= 0.0f) {
                setScrollImmediate(newScroll)
            }
            DropdownScreen.react(ReisaDropdownCompanion.Action.SLIDER_ADJUST)
            return true
        }

        if (openedState && cachedExpand > 0.5f && isContentHovered(mouseX, mouseY)) {
            return mouseClickedContent(mouseX, mouseY, button)
        }
        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && scrollBar.mouseReleased()) {
            return true
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            dragging = false
            return true
        }
        return mouseReleasedContent(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double): Boolean {
        if (scrollBar.isDragging()) {
            val newScroll = scrollBar.mouseDragged(mouseY, getScrollbarViewport(), maxScroll, cachedContentHeight)
            if (newScroll >= 0.0f) {
                setScrollImmediate(newScroll)
            }
            return true
        }
        if (dragging) {
            x = (mouseX + dragOffsetX).toFloat()
            y = (mouseY + dragOffsetY).toFloat()
            return true
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (!openedState) return false
        if (isPanelHovered(mouseX, mouseY)) {
            targetScroll = Mth.clamp(targetScroll - amount.toFloat() * DropdownTheme.SCROLL_SPEED, 0.0f, maxScroll)
            return true
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return false
    }

    override fun charTyped(typedText: String): Boolean {
        return false
    }

    override fun hasActiveInput(): Boolean {
        return false
    }

    override fun setPosition(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    override fun updateMaxPanelHeight(maxPanelHeight: Float) {
        this.maxPanelHeight = maxPanelHeight
    }

    protected abstract fun computeContentHeight(): Float

    protected abstract fun drawPanelContent(scope: UiTree.Scope, textMetrics: UiTextMetrics, mouseX: Int, mouseY: Int, visibleHeight: Float)

    protected open fun mouseClickedContent(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return false
    }

    protected open fun mouseReleasedContent(mouseX: Double, mouseY: Double, button: Int): Boolean {
        return false
    }

    protected fun getTitle(): String {
        if (titleSupplier != null) return titleSupplier.get()
        return titleComponent?.getTranslatedName() ?: title ?: ""
    }

    fun interface TitleSupplier {
        fun get(): String
    }

    protected fun computeVisibleContentHeight(contentHeight: Float): Float {
        val maxContentHeight = maxOf(0.0f, maxPanelHeight - DropdownTheme.PANEL_HEADER_HEIGHT - DropdownTheme.PANEL_BOTTOM_PADDING)
        return minOf(contentHeight, maxContentHeight)
    }

    protected fun getRenderFrameId(): Int {
        return renderFrameId
    }

    private fun ensureFrameMetrics() {
        if (cachedMetricsFrameId == renderFrameId) {
            return
        }
        openAnim.run(if (openedState) 1.0f else 0.0f)
        cachedExpand = openAnim.value
        cachedContentHeight = computeContentHeight()
        cachedVisibleContentHeight = computeVisibleContentHeight(cachedContentHeight)
        cachedPanelHeight = DropdownTheme.PANEL_HEADER_HEIGHT + (cachedVisibleContentHeight + DropdownTheme.PANEL_BOTTOM_PADDING) * cachedExpand
        cachedMetricsFrameId = renderFrameId
    }

    protected fun isHeaderHovered(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + DropdownTheme.PANEL_HEADER_HEIGHT
    }

    protected fun isContentHovered(mouseX: Double, mouseY: Double): Boolean {
        val clipY = contentClipY
        val clipH = contentClipHeight
        return mouseX >= x && mouseX <= x + width && mouseY >= clipY && mouseY <= clipY + clipH
    }

    protected fun isPanelHovered(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + panelHeight
    }

    protected fun isHovered(mouseX: Double, mouseY: Double, x: Float, y: Float, w: Float, h: Float): Boolean {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
    }

    protected fun setScrollImmediate(value: Float) {
        scroll = Mth.clamp(value, 0.0f, maxScroll)
        targetScroll = scroll
    }

    private fun updateScroll(contentHeight: Float, visibleHeight: Float, animate: Boolean) {
        maxScroll = maxOf(0.0f, contentHeight - visibleHeight)
        targetScroll = Mth.clamp(targetScroll, 0.0f, maxScroll)
        scroll = Mth.clamp(scroll, 0.0f, maxScroll)
        if (maxScroll <= 0.0f) {
            scrollBar.reset()
        }

        if (!animate) {
            return
        }

        if (abs(scroll - targetScroll) <= SCROLL_EPSILON) {
            scroll = targetScroll
        } else {
            scroll = Mth.lerp(SCROLL_SMOOTHING, scroll, targetScroll)
        }
    }

    private fun getScrollbarViewport(): UiRect {
        return UiRect(x, y + DropdownTheme.PANEL_HEADER_HEIGHT, width, cachedVisibleContentHeight * cachedExpand)
    }

    protected fun trimToWidth(value: String?, scale: Float, maxWidth: Float, textMetrics: UiTextMetrics): String {
        if (value == null || value.isEmpty()) return ""
        if (textMetrics.textWidth(value, scale) <= maxWidth) return value
        val ellipsis = "..."
        val ellipsisWidth = textMetrics.textWidth(ellipsis, scale)
        if (ellipsisWidth >= maxWidth) return ellipsis
        for (len in value.length - 1 downTo 0) {
            val candidate = value.substring(0, len) + ellipsis
            if (textMetrics.textWidth(candidate, scale) <= maxWidth) return candidate
        }
        return ellipsis
    }

    companion object {
        private const val SCROLL_SMOOTHING = 0.16f
        private const val SCROLL_EPSILON = 0.05f
    }

}
