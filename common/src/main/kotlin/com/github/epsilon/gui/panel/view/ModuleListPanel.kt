package com.github.epsilon.gui.panel.view

import com.github.epsilon.gui.lib.Interaction
import com.github.epsilon.gui.lib.InputRegion
import com.github.epsilon.kernel.state.*
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.lib.LuminUIScene
import com.github.epsilon.gui.lib.Input
import com.github.epsilon.gui.lib.Positioned
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.Viewport
import com.github.epsilon.gui.lib.animate
import com.github.epsilon.gui.lib.LuminUIItem
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.component.ModuleRow
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.modules.Module
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import kotlin.math.abs

class ModuleListPanel(protected val state: PanelState) : AutoCloseable {

    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private var bounds: UiRect? = null
    private var guiHeight = 0
    private val hoverAnimations = HashMap<Module, Animation>()
    private val selectionAnimations = HashMap<Module, Animation>()
    private val toggleAnimations = HashMap<Module, Animation>()
    private val toggleHoverAnimations = HashMap<Module, Animation>()
    private val searchHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val searchFocusAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val scrollBarDrag = ScrollBarDragState()
    private var scrollVelocity = 0.0f
    private var searchFocused by mutableStateOf(false)
    private var searchCursorIndex by mutableStateOf(0)

    init {
        this.searchHoverAnimation.startValue = 0.0f
        this.searchFocusAnimation.startValue = 0.0f
    }

    /**
     * 提取并编译模块列表面板当前帧的 UI。
     *
     * 面板标题与搜索框会直接写入主批次；滚动列表内容则写入独立的 viewport 缓冲，
     * 并在之后的统一 flush 阶段输出。
     */
    private val luminView = com.github.epsilon.gui.lib.state.LuminView()

    fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        luminView.render(mouseX, mouseY) { renderContent(guiGraphics, renderBatch, bounds, mouseX, mouseY, partialTick) }
    }

    private fun renderContent(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.bounds = bounds
        this.guiHeight = guiGraphics.guiHeight()

        if (abs(scrollVelocity) > 0.01f) {
            state.scrollModules((scrollVelocity * partialTick).toDouble())
            scrollVelocity *= 0.86f
            if (abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0.0f
            }

        }

        val viewport = getViewport()
        val modules = state.getVisibleModules()
        val contentHeight = modules.size * (ModuleRow.HEIGHT + MD3Theme.ROW_GAP)
        state.setMaxModuleScroll(contentHeight - viewport.height)
        val maxModuleScroll = maxOf(0.0f, contentHeight - viewport.height)
        val hasScrollBar = maxModuleScroll > 0
        val rowWidth = if (hasScrollBar) viewport.width - ScrollBarUtils.TOTAL_WIDTH else viewport.width

        contentBuffer.clear()

        renderBatch.LuminUIScene(bounds, key = this to "pass-1") {
            InputRegion(key = this@ModuleListPanel to "input", onPress = ::mouseClicked, onDrag = ::mouseDragged, onRelease = ::mouseReleased, onScroll = ::mouseScrolled,
                onCancel = { scrollBarDrag.reset() })

            Text(
                state.getSelectedCategory().name, MD3Theme.PANEL_TITLE_INSET, 10.0f, 0.78f,
                MD3Theme.TEXT_PRIMARY
            )
            Text(
                EpsilonTranslations.Gui.MODULES.getTranslatedName(), MD3Theme.PANEL_TITLE_INSET, 21.0f, 0.56f,
                MD3Theme.TEXT_SECONDARY
            )
            buildSearchField(mouseX, mouseY)
            val localViewport = viewport.relativeTo(bounds)
            Viewport(
                contentBuffer, localViewport, state.getModuleScroll(), maxModuleScroll, contentHeight, mouseX, mouseY
            ) {
                var y = viewport.y - state.getModuleScroll()
                for (module in modules) {
                    val row = ModuleRow(module, UiRect(viewport.x, y, rowWidth, ModuleRow.HEIGHT))
                    val localRow = row.getBounds().relativeTo(origin)
                    LuminUIItem(module) {
                        val hoverAnimation = hoverAnimations.getOrPut(module) { Animation(Easing.EASE_OUT_CUBIC, 120L) }
                        val selectionAnimation =
                            selectionAnimations.getOrPut(module) { Animation(Easing.EASE_OUT_CUBIC, 160L) }
                        val toggleAnimation =
                            toggleAnimations.getOrPut(module) { Animation(Easing.EASE_OUT_ELASTIC, 620L) }
                        val toggleHoverAnimation =
                            toggleHoverAnimations.getOrPut(module) { Animation(Easing.EASE_OUT_CUBIC, 120L) }
                        hoverAnimation.run(
                            if (row.getBounds().contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f
                        )
                        selectionAnimation.run(if (state.getSelectedModule() === module) 1.0f else 0.0f)
                        toggleAnimation.run(if (module.isEnabled) 1.0f else 0.0f)
                        toggleHoverAnimation.run(
                            if (row.getToggleBounds().contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f
                        )
                        Positioned(localRow.x, localRow.y, localRow.width, localRow.height) {
                            Interaction(key = module) {
                                onClick {
                                    scrollVelocity = 0f
                                    if (row.getToggleBounds().contains(it.x, it.y)) {
                                        module.toggle()
                                        SoundManager.playInUi(if (module.isEnabled) SoundKey.SETTINGS_OPEN else SoundKey.SETTINGS_CLOSE)
                                    } else state.setSelectedModule(module)
                                    true
                                }
                            }
                            ModuleRow(
                                row, hoverAnimation.value, selectionAnimation.value,
                                toggleAnimation.value, toggleHoverAnimation.value
                            )
                        }
                    }
                    y += ModuleRow.HEIGHT + MD3Theme.ROW_GAP
                }
            }
        }

    }

    /**
     * 输出并清空列表视口缓冲中的内容。
     */
    fun flushContent() {
        contentBuffer.flush()
    }


    fun resetTransientState() {
        scrollBarDrag.reset()
        scrollVelocity = 0.0f
        if (searchFocused) {
            searchFocused = false

        }
    }

    /**
     * 处理列表区域中的点击事件。
     *
     * 该方法会优先处理滚动条拖拽，其次处理搜索框聚焦，最后处理模块行选择与启用切换。
     */
    fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (bounds == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        scrollVelocity = 0.0f
        // Scrollbar drag
        val viewport = getViewport()
        val maxScroll = state.getMaxModuleScroll()
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, state.getModuleScroll(), maxScroll)) {
            val newScroll = scrollBarDrag.mouseDragged(event.y(), viewport, maxScroll)
            if (newScroll >= 0) {
                state.setModuleScroll(newScroll)
            }

            return true
        }
        return false
    }

    fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (scrollBarDrag.mouseReleased()) {

            return true
        }
        return false
    }

    fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (scrollBarDrag.isDragging()) {
            val viewport = getViewport()
            val newScroll = scrollBarDrag.mouseDragged(event.y(), viewport, state.getMaxModuleScroll())
            if (newScroll >= 0) {
                state.setModuleScroll(newScroll)
            }

            return true
        }
        return false
    }

    /**
     * 当鼠标位于列表视口内时，处理滚轮滚动。
     */
    fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val viewport = getViewport()
        if (bounds != null && viewport.contains(mouseX, mouseY)) {
            scrollVelocity -= scrollY.toFloat() * 24f

            return true
        }
        return false
    }

    /**
     * 处理搜索框处于焦点状态时的键盘事件。
     */
    fun keyPressed(event: KeyEvent): Boolean {
        if (!searchFocused) {
            return false
        }
        val query = state.getSearchQuery()
        return when (event.key()) {
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> true

            InputConstants.KEY_ESCAPE -> {
                searchFocused = false
                IMEFocusHelper.deactivate()
                true
            }

            InputConstants.KEY_BACKSPACE -> {
                if (searchCursorIndex > 0 && !query.isEmpty()) {
                    state.setSearchQuery(
                        query.substring(0, searchCursorIndex - 1) + query.substring(searchCursorIndex)
                    )
                    searchCursorIndex--

                }
                true
            }

            InputConstants.KEY_DELETE -> {
                if (searchCursorIndex < query.length) {
                    state.setSearchQuery(
                        query.substring(0, searchCursorIndex) + query.substring(searchCursorIndex + 1)
                    )

                }
                true
            }

            InputConstants.KEY_LEFT -> {
                searchCursorIndex = maxOf(0, searchCursorIndex - 1)

                true
            }

            InputConstants.KEY_RIGHT -> {
                searchCursorIndex = minOf(state.getSearchQuery().length, searchCursorIndex + 1)

                true
            }

            else -> false
        }
    }

    /**
     * 处理搜索框的字符输入。
     */
    fun charTyped(event: CharacterEvent): Boolean {
        if (!searchFocused) {
            return false
        }
        val query = state.getSearchQuery()
        val typed = event.codepointAsString()
        state.setSearchQuery(query.substring(0, searchCursorIndex) + typed + query.substring(searchCursorIndex))
        searchCursorIndex++

        return true
    }

    /**
     * 处理来自面板外层的全局点击通知。
     *
     * 若点击位置不在搜索框内，则会取消搜索框焦点。
     */


    private fun getViewport(): UiRect {
        val currentBounds = bounds!!
        return UiRect(
            currentBounds.x + MD3Theme.PANEL_VIEWPORT_INSET, currentBounds.y + 34.0f,
            currentBounds.width - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f, currentBounds.height - 40.0f
        )
    }

    private fun getSearchBounds(): UiRect {
        val currentBounds = bounds!!
        return UiRect(
            currentBounds.right() - MD3Theme.PANEL_TITLE_INSET - 76.0f, currentBounds.y + 8.0f, 76.0f, 18.0f
        )
    }

    private fun UiScope.buildSearchField(mouseX: Int, mouseY: Int) {
        val searchBounds = getSearchBounds()
        InputRegion("search", searchBounds.relativeTo(origin),
            onPress = { _, _ ->
                if (!searchFocused) { searchFocused = true; searchCursorIndex = state.getSearchQuery().length; IMEFocusHelper.activate() }
                true
            }, onKey = ::keyPressed, onText = ::charTyped,
            onBlur = { if (searchFocused) { searchFocused = false; IMEFocusHelper.deactivate() } })
        val localBounds = searchBounds.relativeTo(origin)
        val hoverProgress =
            animate(searchHoverAnimation, searchBounds.contains(mouseX.toDouble(), mouseY.toDouble()))
        val focusProgress = animate(searchFocusAnimation, searchFocused)
        val fieldHover = maxOf(hoverProgress, focusProgress * 0.85f)

        val query = state.getSearchQuery()
        val showPlaceholder = query.isEmpty() && !searchFocused
        val display = if (showPlaceholder) EpsilonTranslations.Gui.SEARCH.getTranslatedName() else query
        val scale = 0.52f
        val textColor: Color = if (showPlaceholder) {
            MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.filledFieldContent(searchFocused), focusProgress)
        } else {
            MD3Theme.filledFieldContent(searchFocused)
        }
        Positioned(localBounds.x, localBounds.y, localBounds.width, localBounds.height) {
            Input(
                UiRect(0.0f, 0.0f, localBounds.width, localBounds.height), searchFocused, fieldHover,
                0.0f, Color(0, 0, 0, 0), 0.0f,
                8.0f, display, scale, textColor,
                null, null,
                if (searchFocused) searchCursorIndex else null,
                if (searchFocused) MD3Theme.filledFieldCaret(true) else null,
                null, 0.0f, null
            )
        }

        if (searchFocused) {
            // IME 光标位置需要绝对坐标
            val textY = searchBounds.y + (searchBounds.height - metrics.textHeight(scale)) / 2.0f
            val textX = searchBounds.x + 8.0f
            val caretX = textX + metrics.textWidth(
                query.substring(0, minOf(searchCursorIndex, query.length)), scale
            )
            IMEFocusHelper.updateCursorPos(caretX, textY)
        }
    }

    override fun close() {
        luminView.close()

        contentBuffer.close()

    }

}
