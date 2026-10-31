package com.github.epsilon.gui.panel.view

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.UiInvalidationState
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.adapter.ModuleViewModel
import com.github.epsilon.gui.panel.component.ModuleRow
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.managers.sound.SoundKey
import com.github.epsilon.managers.sound.SoundManager
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import kotlin.math.abs

/**
 * 模块列表面板。
 *
 * 负责渲染分类下的模块列表、搜索框、滚动视口与模块行缓存，
 * 并维护与列表内容相关的输入状态、滚动状态和重建签名。
 */
class ModuleListPanel(protected val state: PanelState, private val textRenderer: TextRenderer) : AutoCloseable {

    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private val contentState = UiInvalidationState()
    private var bounds: UiRect? = null
    private var guiHeight = 0
    private val rows = ArrayList<ModuleRow>()
    private val hoverAnimations = HashMap<Module, Animation>()
    private val selectionAnimations = HashMap<Module, Animation>()
    private val toggleAnimations = HashMap<Module, Animation>()
    private val toggleHoverAnimations = HashMap<Module, Animation>()
    private var lastModuleScroll = Float.NaN
    private var lastSearchQuery = ""
    private var lastSearchFocused = false
    private var lastCategorySnapshot: CategorySnapshot? = null
    private var lastSelectedModuleName = ""
    private val searchHoverAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val searchFocusAnimation = Animation(Easing.EASE_OUT_CUBIC, 120L)
    private val scrollBarDrag = ScrollBarDragState()
    private var scrollVelocity = 0.0f
    private var searchFocused = false
    private var searchCursorIndex = 0
    private var lastContentSignature = Long.MIN_VALUE

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
    fun render(
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
            markDirty()
        }

        val viewport = getViewport()
        val modules = state.getVisibleModules()
        val contentHeight = modules.size * (ModuleRow.HEIGHT + MD3Theme.ROW_GAP)
        state.setMaxModuleScroll(contentHeight - viewport.height)
        val maxModuleScroll = maxOf(0.0f, contentHeight - viewport.height)
        val hasScrollBar = maxModuleScroll > 0
        val rowWidth = if (hasScrollBar) viewport.width - ScrollBarUtils.TOTAL_WIDTH else viewport.width
        val contentSignature = buildContentSignature(modules)
        val rebuildContent = shouldRebuildContent(
            bounds, mouseX, mouseY, modules, guiGraphics.guiHeight(), contentSignature
        )

        if (rebuildContent) {
            rows.clear()
            contentBuffer.clear()
            contentState.beginRebuild()
        }

        val tree = UiTree.build { scope ->
            scope.pushAbsolute(bounds) { panel ->
                panel.text(
                    state.getSelectedCategory().name, MD3Theme.PANEL_TITLE_INSET, 10.0f, 0.78f,
                    MD3Theme.TEXT_PRIMARY
                )
                panel.text(
                    EpsilonTranslations.Gui.MODULES.getTranslatedName(), MD3Theme.PANEL_TITLE_INSET, 21.0f, 0.56f,
                    MD3Theme.TEXT_SECONDARY
                )
            }
            buildSearchField(scope, mouseX, mouseY)
            scope.viewport(
                contentBuffer, viewport, state.getModuleScroll(), maxModuleScroll, contentHeight, mouseX, mouseY
            ) { content ->
                if (!rebuildContent) {
                    return@viewport
                }
                var y = viewport.y - state.getModuleScroll()
                for (module in modules) {
                    val row = ModuleRow(ModuleViewModel.from(module), UiRect(viewport.x, y, rowWidth, ModuleRow.HEIGHT))
                    rows.add(row)
                    val hoverAnimation = hoverAnimations.getOrPut(module) { Animation(Easing.EASE_OUT_CUBIC, 120L) }
                    val selectionAnimation =
                        selectionAnimations.getOrPut(module) { Animation(Easing.EASE_OUT_CUBIC, 160L) }
                    val toggleAnimation =
                        toggleAnimations.getOrPut(module) { Animation(Easing.EASE_OUT_ELASTIC, 620L) }
                    val toggleHoverAnimation =
                        toggleHoverAnimations.getOrPut(module) { Animation(Easing.EASE_OUT_CUBIC, 120L) }
                    hoverAnimation.run(if (row.getBounds().contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
                    selectionAnimation.run(if (state.getSelectedModule() === module) 1.0f else 0.0f)
                    toggleAnimation.run(if (module.isEnabled) 1.0f else 0.0f)
                    toggleHoverAnimation.run(
                        if (row.getToggleBounds().contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f
                    )
                    val marqueeActive = row.hasOverflowingKeybind(textRenderer)
                    contentState.noteAnimation(
                        !hoverAnimation.isFinished ||
                                !selectionAnimation.isFinished ||
                                !toggleAnimation.isFinished ||
                                !toggleHoverAnimation.isFinished ||
                                marqueeActive
                    )
                    content.pushAbsolute(row.getBounds()) { rowScope ->
                        row.buildUi(
                            rowScope, textRenderer, hoverAnimation.value, selectionAnimation.value,
                            toggleAnimation.value, toggleHoverAnimation.value
                        )
                    }
                    y += ModuleRow.HEIGHT + MD3Theme.ROW_GAP
                }
            }
        }
        renderBatch.render(tree)

        if (rebuildContent) {
            rememberSnapshot(bounds, mouseX, mouseY, modules, guiGraphics.guiHeight(), contentSignature)
        }
    }

    /**
     * 输出并清空列表视口缓冲中的内容。
     */
    fun flushContent() {
        contentBuffer.flush()
    }

    /**
     * 将列表内容标记为脏，以便在下次渲染时触发重建。
     */
    fun markDirty() {
        contentState.markDirty()
    }

    /**
     * 返回列表内容是否仍包含未结束的动画。
     */
    fun hasActiveAnimations(): Boolean {
        return contentState.hasActiveAnimations()
    }

    fun resetTransientState() {
        scrollBarDrag.reset()
        scrollVelocity = 0.0f
        if (searchFocused) {
            searchFocused = false
            markDirty()
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
            markDirty()
            return true
        }
        val searchBounds = getSearchBounds()
        if (searchBounds.contains(event.x(), event.y())) {
            searchFocused = true
            searchCursorIndex = state.getSearchQuery().length
            IMEFocusHelper.activate()
            markDirty()
            return true
        }
        for (row in rows) {
            if (!row.getBounds().contains(event.x(), event.y())) {
                continue
            }
            if (row.getToggleBounds().contains(event.x(), event.y())) {
                row.getModule().module.toggle()
                SoundManager.playInUi(
                    if (row.getModule().module.isEnabled) SoundKey.SETTINGS_OPEN else SoundKey.SETTINGS_CLOSE
                )
            } else {
                state.setSelectedModule(row.getModule().module)
            }
            markDirty()
            return true
        }
        return false
    }

    fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (scrollBarDrag.mouseReleased()) {
            markDirty()
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
            markDirty()
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
            markDirty()
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
                    markDirty()
                }
                true
            }

            InputConstants.KEY_DELETE -> {
                if (searchCursorIndex < query.length) {
                    state.setSearchQuery(
                        query.substring(0, searchCursorIndex) + query.substring(searchCursorIndex + 1)
                    )
                    markDirty()
                }
                true
            }

            InputConstants.KEY_LEFT -> {
                searchCursorIndex = maxOf(0, searchCursorIndex - 1)
                markDirty()
                true
            }

            InputConstants.KEY_RIGHT -> {
                searchCursorIndex = minOf(state.getSearchQuery().length, searchCursorIndex + 1)
                markDirty()
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
        markDirty()
        return true
    }

    /**
     * 处理来自面板外层的全局点击通知。
     *
     * 若点击位置不在搜索框内，则会取消搜索框焦点。
     */
    fun handleGlobalClick(mouseX: Double, mouseY: Double) {
        if (bounds == null) {
            return
        }
        if (!getSearchBounds().contains(mouseX, mouseY)) {
            searchFocused = false
            IMEFocusHelper.deactivate()
            markDirty()
        }
    }

    private fun shouldRebuildContent(
        bounds: UiRect, mouseX: Int, mouseY: Int, modules: List<Module>,
        currentGuiHeight: Int, contentSignature: Long,
    ): Boolean {
        if (contentState.needsRebuild(bounds, mouseX, mouseY, currentGuiHeight, contentSignature)) {
            return true
        }
        if (lastModuleScroll.compareTo(state.getModuleScroll()) != 0) {
            return true
        }
        if (lastSearchQuery != state.getSearchQuery() || lastSearchFocused != searchFocused) {
            return true
        }
        val selectedModuleName = state.getSelectedModule()?.name ?: ""
        if (lastSelectedModuleName != selectedModuleName) {
            return true
        }
        if (lastCategorySnapshot != CategorySnapshot.of(state.getSelectedCategory().name, modules)) {
            return true
        }
        return lastContentSignature != contentSignature
    }

    private fun rememberSnapshot(
        bounds: UiRect, mouseX: Int, mouseY: Int, modules: List<Module>,
        currentGuiHeight: Int, contentSignature: Long,
    ) {
        contentState.rememberSnapshot(bounds, mouseX, mouseY, currentGuiHeight, contentSignature)
        lastModuleScroll = state.getModuleScroll()
        lastSearchQuery = state.getSearchQuery()
        lastSearchFocused = searchFocused
        lastCategorySnapshot = CategorySnapshot.of(state.getSelectedCategory().name, modules)
        lastSelectedModuleName = state.getSelectedModule()?.name ?: ""
        lastContentSignature = contentSignature
    }

    private fun buildContentSignature(modules: List<Module>): Long {
        var signature = 17L
        signature = signature * 31L + TranslationManager.getRevision()
        signature = signature * 31L + state.getSelectedCategory().name.hashCode()
        signature = signature * 31L + state.getSearchQuery().hashCode()
        signature = signature * 31L + (if (searchFocused) 1 else 0)
        signature = signature * 31L + state.getSelectedModule()?.name.hashCode()
        signature = signature * 31L + java.lang.Float.floatToIntBits(state.getModuleScroll())
        for (module in modules) {
            signature = signature * 31L + module.name.hashCode()
            signature = signature * 31L + module.keyBind
            signature = signature * 31L + (if (module.isEnabled) 1 else 0)
        }
        return signature
    }

    private data class CategorySnapshot(val categoryName: String, val moduleIds: List<String>) {

        companion object {

            fun of(categoryName: String, modules: List<Module>): CategorySnapshot {
                val ids = modules.map { it.name }
                return CategorySnapshot(categoryName, ids)
            }

        }
    }

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

    private fun buildSearchField(scope: UiTree.Scope, mouseX: Int, mouseY: Int) {
        val searchBounds = getSearchBounds()
        val hoverProgress = scope.animate(searchHoverAnimation, searchBounds.contains(mouseX.toDouble(), mouseY.toDouble()))
        val focusProgress = scope.animate(searchFocusAnimation, searchFocused)
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
        scope.pushAbsolute(searchBounds) { search ->
            search.input(
                searchBounds.atOrigin(), searchFocused, fieldHover,
                8.0f, display, scale, textColor,
                if (searchFocused) searchCursorIndex else null,
                if (searchFocused) MD3Theme.filledFieldCaret(true) else null,
                null, 0.0f, null
            )
        }

        if (searchFocused) {
            val textY = searchBounds.y + (searchBounds.height - textRenderer.getHeight(scale)) / 2.0f
            val textX = searchBounds.x + 8.0f
            val caretX = textX + textRenderer.getWidth(
                query.substring(0, minOf(searchCursorIndex, query.length)), scale
            )
            IMEFocusHelper.updateCursorPos(caretX, textY)
        }
    }

    override fun close() {
        contentBuffer.close()
        markDirty()
    }

}
