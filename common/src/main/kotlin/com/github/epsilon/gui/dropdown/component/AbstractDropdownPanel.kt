package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.DropdownScreen
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.ReisaDropdownCompanion
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.control.UiScrollBar
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

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
    private var openedState: Boolean by mutableStateOf<Boolean>(false)
    protected var dragging: Boolean by mutableStateOf<Boolean>(false)
    protected var dragOffsetX: Float = 0.0f
    protected var dragOffsetY: Float = 0.0f
    private val scrollAnimation = Animation(0.0f, Easing.EASE_OUT_CUBIC, 160L)
    protected var scroll: Float
        get() = scrollAnimation.value
        set(value) { scrollAnimation.snapTo(value) }
    protected var targetScroll: Float by mutableStateOf<Float>(0.0f)
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

    private var visibleState by mutableStateOf(false)
    override var isVisible: Boolean
        get() = visibleState
        set(value) {
            visibleState = value
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
        ensureFrameMetrics()
        updateScroll(cachedContentHeight, cachedVisibleContentHeight, true)
    }

    override val introValue: Float
        get() {
            introAnim.run(1.0f)
            return introAnim.value
        }

    context(scope: UiScope)
    override fun drawBackground() {
        ensureFrameMetrics()
        val expand = cachedExpand
        val contentHeight = cachedContentHeight
        val visibleHeight = cachedVisibleContentHeight
        val panelHeight = cachedPanelHeight
        // 面板左上角的局部坐标：调用方用面板矩形开 LuminUIScene 作用域使其归零。
        val left = x - scope.origin.x
        val top = y - scope.origin.y
        val panelWidth = width

        with(scope) {
            InputRegion(key = this@AbstractDropdownPanel to "header",
                bounds = UiRect(left, top, panelWidth, DropdownTheme.PANEL_HEADER_HEIGHT),
                onPress = { event, _ ->
                    when (event.button()) {
                        InputConstants.MOUSE_BUTTON_LEFT -> {
                            dragging = true; dragOffsetX = (x - event.x()).toFloat(); dragOffsetY = (y - event.y()).toFloat()
                            DropdownScreen.react(ReisaDropdownCompanion.Action.DRAG); true
                        }
                        InputConstants.MOUSE_BUTTON_RIGHT -> {
                            isOpened = !isOpened
                            DropdownScreen.react(if (isOpened) ReisaDropdownCompanion.Action.PANEL_OPEN else ReisaDropdownCompanion.Action.PANEL_CLOSE); true
                        }
                        else -> false
                    }
                },
                onDrag = { event, _, _ -> mouseDragged(event.x(), event.y()) },
                onRelease = { event -> mouseReleased(event.x(), event.y(), event.button()) },
                onCancel = { dragging = false; scrollBar.reset() })
            InputRegion(key = this@AbstractDropdownPanel to "scroll", bounds = UiRect(left, top, panelWidth, panelHeight),
                onScroll = { _, _, _, amount ->
                    if (openedState) {
                        targetScroll = (targetScroll - amount.toFloat() * DropdownTheme.SCROLL_SPEED).coerceIn(0f, maxScroll)
                        true
                    } else false
                })
            Shadow(left, top, panelWidth, panelHeight, DropdownTheme.PANEL_RADIUS, DropdownTheme.PANEL_SHADOW_BLUR, DropdownTheme.panelShadow())
            RoundRect(left, top, panelWidth, panelHeight, DropdownTheme.PANEL_RADIUS, DropdownTheme.panelBackground())
            // 箭头与标题不重叠，先提交箭头，让图标和标题进入连续的文字批次。
            Triangle(
                left + panelWidth - 10.0f,
                top + DropdownTheme.PANEL_HEADER_HEIGHT * 0.5f,
                3.0f,
                expand,
                DropdownTheme.groupChevron(0.0f)
            )

            val iconX = left + 7.5f
            val textX = if (icon == null || icon.isBlank()) left + 10.0f else iconX + 16.0f
            val textY =
                top + (DropdownTheme.PANEL_HEADER_HEIGHT - metrics.textHeight(DropdownTheme.HEADER_TEXT_SCALE)) * 0.5f
            val iconText = icon
            if (iconText != null && !iconText.isBlank()) {
                val iconY = top + (DropdownTheme.PANEL_HEADER_HEIGHT - metrics.textHeight(
                    DropdownTheme.HEADER_ICON_SCALE,
                    StaticFontLoader.ICONS
                )) * 0.5f
                Text(
                    iconText,
                    iconX,
                    iconY,
                    DropdownTheme.HEADER_ICON_SCALE,
                    MD3Theme.PRIMARY,
                    StaticFontLoader.ICONS
                )
            }
            val headerTitle = getTitle()
            Text(headerTitle, textX, textY, DropdownTheme.HEADER_TEXT_SCALE, MD3Theme.TEXT_PRIMARY)
        }
    }

    context(scope: UiScope)
    override fun drawContent(mouseX: Int, mouseY: Int) {
        ensureFrameMetrics()
        if (cachedExpand < 0.01f) return

        val contentHeight = cachedContentHeight
        val visibleHeight = cachedVisibleContentHeight
        val scrollbarViewport = getScrollbarViewport()
        val scrollbarHovered = scrollBar.isHovered(
            mouseX.toDouble(),
            mouseY.toDouble(),
            scrollbarViewport,
            scroll,
            maxScroll,
            contentHeight
        )
        val contentMouseX = if (scrollbarHovered || scrollBar.isDragging()) Integer.MIN_VALUE else mouseX
        val contentMouseY = if (scrollbarHovered || scrollBar.isDragging()) Integer.MIN_VALUE else mouseY
        scope.drawPanelContent(contentMouseX, contentMouseY, visibleHeight)
        scope.InputRegion(key = this to "scrollbar", bounds = UiRect(
            scrollbarViewport.right() - UiScrollBar.HOVER_WIDTH - scope.origin.x,
            scrollbarViewport.y - scope.origin.y, UiScrollBar.HOVER_WIDTH, scrollbarViewport.height),
            onPress = { event, _ ->
                val handled = scrollBar.mouseClicked(event.x(), event.y(), scrollbarViewport, scroll, maxScroll, contentHeight)
                if (handled) scrollBar.mouseDragged(event.y(), scrollbarViewport, maxScroll, contentHeight).takeIf { it >= 0f }?.let(::setScrollImmediate)
                handled
            }, onDrag = { event, _, _ ->
                scrollBar.mouseDragged(event.y(), getScrollbarViewport(), maxScroll, cachedContentHeight).takeIf { it >= 0f }?.let(::setScrollImmediate)
                true
            }, onRelease = { scrollBar.mouseReleased() }, onCancel = { scrollBar.reset() })
        // 滚动条与内容共用同一作用域：把视口和鼠标都平移到该作用域的坐标系。
        scrollBar.draw(
            scope,
            scrollbarViewport.relativeTo(scope.origin),
            scroll,
            maxScroll,
            contentHeight,
            (mouseX - scope.origin.x).toDouble(),
            (mouseY - scope.origin.y).toDouble()
        )
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


    private fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && dragging) { dragging = false; return true }
        return false
    }

    private fun mouseDragged(mouseX: Double, mouseY: Double): Boolean {
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


    override fun setPosition(x: Float, y: Float) {
        this.x = x
        this.y = y
    }

    override fun updateMaxPanelHeight(maxPanelHeight: Float) {
        this.maxPanelHeight = maxPanelHeight
    }

    override val expandProgress: Float
        get() {
            ensureFrameMetrics()
            return cachedExpand
        }

    override val scrollOffset: Float get() = scroll

    override val targetScrollOffset: Float get() = targetScroll

    override val maxScrollOffset: Float get() = maxScroll

    protected abstract fun computeContentHeight(): Float

    /**
     * 绘制内容区；坐标相对当前作用域，命中测试仍用绝对坐标。
     */
    protected abstract fun UiScope.drawPanelContent(mouseX: Int, mouseY: Int, visibleHeight: Float)


    protected fun getTitle(): String {
        if (titleSupplier != null) return titleSupplier.get()
        return titleComponent?.getTranslatedName() ?: title ?: ""
    }

    fun interface TitleSupplier {
        fun get(): String
    }

    protected fun computeVisibleContentHeight(contentHeight: Float): Float {
        val maxContentHeight =
            maxOf(0.0f, maxPanelHeight - DropdownTheme.PANEL_HEADER_HEIGHT - DropdownTheme.PANEL_BOTTOM_PADDING)
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
        cachedPanelHeight =
            DropdownTheme.PANEL_HEADER_HEIGHT + (cachedVisibleContentHeight + DropdownTheme.PANEL_BOTTOM_PADDING) * cachedExpand
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
        return UiRect(x, y, w, h).contains(mouseX, mouseY)
    }

    /**
     * 内容局部矩形 → 屏幕绝对坐标的命中测试。
     *
     * 绘制走局部坐标，命中测试仍使用绝对坐标，因此这里补上作用域原点。
     */
    protected fun isHoveredAt(scope: UiScope, mouseX: Double, mouseY: Double, x: Float, y: Float, w: Float, h: Float): Boolean {
        return isHovered(mouseX, mouseY, scope.origin.x + x, scope.origin.y + y, w, h)
    }

    /** 内容区顶边（标题栏下方）在当前作用域中的局部 y。 */
    protected fun contentLocalTop(origin: UiRect): Float {
        return y + DropdownTheme.PANEL_HEADER_HEIGHT - origin.y
    }

    protected fun setScrollImmediate(value: Float) {
        scroll = value.coerceIn(0.0f, maxScroll)
        targetScroll = scroll
    }

    private fun updateScroll(contentHeight: Float, visibleHeight: Float, animate: Boolean) {
        maxScroll = maxOf(0.0f, contentHeight - visibleHeight)
        targetScroll = targetScroll.coerceIn(0.0f, maxScroll)
        val clamped = scroll.coerceIn(0.0f, maxScroll)
        if (scroll != clamped) scroll = clamped
        if (maxScroll <= 0.0f) {
            scrollBar.reset()
        }

        if (!animate) {
            return
        }

        scrollAnimation.target = targetScroll

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
