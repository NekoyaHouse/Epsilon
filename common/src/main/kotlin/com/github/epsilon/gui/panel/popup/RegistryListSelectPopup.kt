package com.github.epsilon.gui.panel.popup

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminAnimatable
import com.github.epsilon.gui.lib.state.LuminView
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.MinecraftWrapper.mc
import com.github.epsilon.kernel.gameplay.world.BlockRegistryUtils
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.RegistryListSetting
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.Registry
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import java.awt.Color
import java.util.*
import java.util.function.Predicate
import kotlin.math.abs

class RegistryListSelectPopup<T : Any> private constructor(
    private val bounds: UiRect,
    private val setting: Setting<List<T>>,
    entries: List<T>,
    private val displayNameFn: (T) -> String,
    private val iconProvider: ((T) -> ItemStack)?,
    private val categories: List<Category<T>>,
    private val addFn: (T) -> Unit,
    private val removeFn: (T) -> Unit,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : PanelPopupHost.Popup {

    private val allEntries: List<T> = entries.toList()
    private val availableBuffer = UiContentBuffer(EpsilonUiTheme)
    private val selectedBuffer = UiContentBuffer(EpsilonUiTheme)
    private val textRenderer: TextRenderer = TextRenderer.create()
    private val openAnimation = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 160L)
    private val availableScrollBarDrag = ScrollBarDragState()
    private val selectedScrollBarDrag = ScrollBarDragState()

    private var inputFocused by mutableStateOf(false)
    private var query by mutableStateOf("")
    private var selectedCategory by mutableStateOf(-1) // -1 = all
    private var availableScroll by mutableStateOf(0.0f)
    private var selectedScroll by mutableStateOf(0.0f)
    private var availableScrollVelocity = 0.0f
    private var selectedScrollVelocity = 0.0f
    private var maxAvailableScroll = 0.0f
    private var maxSelectedScroll = 0.0f
    private var lastAvailableViewport: UiRect? = null
    private var lastSelectedViewport: UiRect? = null

    constructor(
        bounds: UiRect, setting: Setting<List<T>>, registry: Registry<T>,
        displayNameFn: (T) -> String, addFn: (T) -> Unit, removeFn: (T) -> Unit,
    ) : this(
        bounds,
        setting,
        collectRegistryEntries(registry, setting),
        displayNameFn,
        null,
        emptyList(),
        addFn,
        removeFn,
        Unit
    )

    constructor(
        bounds: UiRect, setting: Setting<List<T>>, registry: Registry<T>,
        displayNameFn: (T) -> String, iconProvider: ((T) -> ItemStack)?,
        addFn: (T) -> Unit, removeFn: (T) -> Unit,
    ) : this(
        bounds,
        setting,
        collectRegistryEntries(registry, setting),
        displayNameFn,
        iconProvider,
        emptyList(),
        addFn,
        removeFn,
        Unit
    )

    constructor(
        bounds: UiRect, setting: Setting<List<T>>, registry: Registry<T>,
        displayNameFn: (T) -> String, iconProvider: ((T) -> ItemStack)?,
        categories: List<Category<T>>, addFn: (T) -> Unit, removeFn: (T) -> Unit,
    ) : this(
        bounds, setting, collectRegistryEntries(registry, setting),
        displayNameFn, iconProvider, categories, addFn, removeFn, Unit
    )

    override fun getBounds(): UiRect {
        return bounds
    }

    override val inputView = LuminView()

    override fun extractGui(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        inputView.render(mouseX, mouseY) { renderContent(guiGraphics, renderBatch, mouseX, mouseY, partialTick) }
    }

    private fun renderContent(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        availableBuffer.clear()
        selectedBuffer.clear()
        val available = filteredAvailable()
        val selected = filteredSelected()
        val availableContentHeight = available.size * (ROW_HEIGHT + ROW_GAP)
        val selectedContentHeight = selected.size * (ROW_HEIGHT + ROW_GAP)

        // 开启动画是推式动画：目标在命令阶段写入，绘制块内只读取 value。
        val popupY = bounds.y - (1.0f - openAnimation.value) * 6.0f
        openAnimation.target = 1.0f
        val animatedBounds = UiRect(bounds.x, popupY, bounds.width, bounds.height)
        val searchBounds = getSearchBounds(popupY)
        val animatedViewport = getViewport(popupY)

        // 布局与滚动推进必须在命令阶段完成：recompose 命中缓存时块内不再执行。
        val columnViewportWidth = (animatedViewport.width - COLUMN_GAP) / 2.0f
        val leftX = animatedViewport.x
        val rightX = leftX + columnViewportWidth + COLUMN_GAP
        val categoryY = searchBounds.bottom() + 4.0f
        var viewportY = animatedViewport.y
        if (categories.isNotEmpty()) {
            viewportY = categoryY + CATEGORY_TAB_HEIGHT + HEADER_HEIGHT + 4.0f
        }
        val effectiveViewportY = viewportY
        val viewportHeight = animatedViewport.bottom() - effectiveViewportY
        val availableViewport = UiRect(leftX, effectiveViewportY, columnViewportWidth, viewportHeight)
        val selectedViewport = UiRect(rightX, effectiveViewportY, columnViewportWidth, viewportHeight)
        lastAvailableViewport = availableViewport
        lastSelectedViewport = selectedViewport


        maxAvailableScroll = maxOf(0.0f, availableContentHeight - availableViewport.height)
        maxSelectedScroll = maxOf(0.0f, selectedContentHeight - selectedViewport.height)
        availableScroll = availableScroll.coerceIn(0.0f, maxAvailableScroll)
        selectedScroll = selectedScroll.coerceIn(0.0f, maxSelectedScroll)
        updateAvailableSmoothScroll(partialTick)
        updateSelectedSmoothScroll(partialTick)

        IMEFocusHelper.updateCursorPos(searchBounds.x + 8.0f, searchBounds.y + 4.0f)

        renderBatch.LuminUIScene(animatedBounds, key = this) {
            InputRegion(
                key = this@RegistryListSelectPopup to "input",
                onPress = ::mouseClicked,
                onDrag = ::mouseDragged,
                onRelease = ::mouseReleased,
                onScroll = ::mouseScrolled,
                onBlur = {},
                onCancel = { availableScrollBarDrag.reset(); selectedScrollBarDrag.reset() })

            val progress = openAnimation.value
            PopupCard(
                size, MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.POPUP_SHADOW_BLUR,
                MD3Theme.withAlpha(MD3Theme.SHADOW, (MD3Theme.POPUP_SHADOW_ALPHA * progress).toInt()),
                MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)
            )

            val titleY = centeredTextY(6.0f, TITLE_HEIGHT, 0.68f)
            val summaryScale = 0.52f
            val summary = setting.value.size.toString() + EpsilonTranslations.Gui.LIST_SELECTED.getTranslatedName()
            Text(setting.displayName, PADDING, titleY, 0.68f, MD3Theme.TEXT_PRIMARY)
            Text(
                summary, animatedBounds.width - PADDING - metrics.textWidth(summary, summaryScale),
                centeredTextY(6.0f, TITLE_HEIGHT, summaryScale), summaryScale, MD3Theme.TEXT_MUTED
            )
            val localSearchBounds = searchBounds.relativeTo(animatedBounds)
            Positioned(localSearchBounds.x, localSearchBounds.y, localSearchBounds.width, localSearchBounds.height) {
                Interaction("editor") {
                    autoFocus = true
                    onClick { true }
                    onKeyPressed(::keyPressed)
                    onTextInput(::charTyped)
                    onFocusChanged { inputFocused = it }
                }
                Input(
                    UiRect(0.0f, 0.0f, localSearchBounds.width, localSearchBounds.height), inputFocused, 1.0f,
                    0.0f, Color(0, 0, 0, 0), 0.0f,
                    8.0f,
                    if (query.isEmpty()) EpsilonTranslations.Gui.LIST_SEARCH.getTranslatedName() else query, 0.54f,
                    if (query.isEmpty()) MD3Theme.TEXT_MUTED else MD3Theme.TEXT_PRIMARY,
                    null, null,
                    if (inputFocused) query.length else null, MD3Theme.PRIMARY, null, 0.0f, null
                )
            }

            // Category tabs
            if (categories.isNotEmpty()) {
                var catTabX = leftX
                for (ci in -1 until categories.size) {
                    val catName =
                        if (ci < 0) EpsilonTranslations.Gui.LIST_ALL.getTranslatedName() else categories[ci].name
                    val catTextW = metrics.textWidth(catName, 0.44f) + 10.0f
                    val catSelected = selectedCategory == ci
                    val catBounds = UiRect(catTabX, categoryY, catTextW, CATEGORY_TAB_HEIGHT)
                    Interaction("category" to ci, catBounds.relativeTo(animatedBounds)) {
                        onClick { selectedCategory = ci; resetScroll(); true }
                    }
                    RoundRect(
                        catBounds.x - animatedBounds.x, catBounds.y - animatedBounds.y,
                        catBounds.width, catBounds.height, CATEGORY_TAB_HEIGHT / 2.0f,
                        if (catSelected) MD3Theme.PRIMARY else MD3Theme.SURFACE_CONTAINER_HIGH
                    )
                    Text(
                        catName,
                        catBounds.x - animatedBounds.x + 5.0f,
                        catBounds.y - animatedBounds.y + (catBounds.height - metrics.textHeight(0.44f)) * 0.5f,
                        0.44f, if (catSelected) MD3Theme.ON_PRIMARY else MD3Theme.TEXT_SECONDARY
                    )
                    catTabX += catTextW + CATEGORY_TAB_GAP
                }
            }

            val headerY = effectiveViewportY - HEADER_HEIGHT
            val headerTextY = centeredTextY(headerY, HEADER_HEIGHT, 0.50f)
            Text(
                EpsilonTranslations.Gui.LIST_AVAILABLE.getTranslatedName(),
                leftX - animatedBounds.x + 4.0f, headerTextY - animatedBounds.y, 0.50f, MD3Theme.TEXT_SECONDARY
            )
            Text(
                EpsilonTranslations.Gui.LIST_SELECTED_HEADER.getTranslatedName(),
                rightX - animatedBounds.x + 4.0f, headerTextY - animatedBounds.y, 0.50f, MD3Theme.TEXT_SECONDARY
            )

            val localAvailableViewport = availableViewport.relativeTo(animatedBounds)
            Viewport(
                availableBuffer, localAvailableViewport, availableScroll,
                maxAvailableScroll, availableContentHeight, mouseX, mouseY
            ) {
                buildColumn(
                    available, availableViewport.x, availableViewport.y - availableScroll,
                    availableViewport.width - (if (maxAvailableScroll > 0.0f) SCROLLBAR_GUTTER else 0.0f),
                    mouseX, mouseY, true, availableViewport
                )
            }
            val localSelectedViewport = selectedViewport.relativeTo(animatedBounds)
            Viewport(
                selectedBuffer, localSelectedViewport, selectedScroll,
                maxSelectedScroll, selectedContentHeight, mouseX, mouseY
            ) {
                buildColumn(
                    selected, selectedViewport.x, selectedViewport.y - selectedScroll,
                    selectedViewport.width - (if (maxSelectedScroll > 0.0f) SCROLLBAR_GUTTER else 0.0f),
                    mouseX, mouseY, false, selectedViewport
                )
            }
        }
    }

    override fun flush(renderBatch: UiRenderBatch) {
        availableBuffer.flushAndClear()
        selectedBuffer.flushAndClear()
    }

    private fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) return false
        val availableViewport = lastAvailableViewport ?: getAvailableViewport()
        val selectedViewport = lastSelectedViewport ?: getSelectedViewport()
        if (availableScrollBarDrag.mouseClicked(
                event.x(),
                event.y(),
                availableViewport,
                availableScroll,
                maxAvailableScroll
            )
        ) {
            applyDraggedAvailableScroll(event.y(), availableViewport)
            return true
        }
        if (selectedScrollBarDrag.mouseClicked(
                event.x(),
                event.y(),
                selectedViewport,
                selectedScroll,
                maxSelectedScroll
            )
        ) {
            applyDraggedSelectedScroll(event.y(), selectedViewport)
            return true
        }
        return true
    }

    private fun mouseReleased(event: MouseButtonEvent): Boolean {
        return availableScrollBarDrag.mouseReleased() or selectedScrollBarDrag.mouseReleased()
    }

    private fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        var handled = false
        if (availableScrollBarDrag.isDragging()) {
            applyDraggedAvailableScroll(event.y(), lastAvailableViewport ?: getAvailableViewport())
            handled = true
        }
        if (selectedScrollBarDrag.isDragging()) {
            applyDraggedSelectedScroll(event.y(), lastSelectedViewport ?: getSelectedViewport())
            handled = true
        }
        return handled
    }

    private fun keyPressed(event: KeyEvent): Boolean {
        return when (event.key()) {
            InputConstants.KEY_BACKSPACE -> {
                if (!query.isEmpty()) {
                    query = query.substring(0, query.length - 1)
                    resetScroll()
                }
                true
            }

            InputConstants.KEY_DELETE -> {
                query = ""
                resetScroll()
                true
            }

            else -> false
        }
    }

    private fun charTyped(event: CharacterEvent): Boolean {
        if (query.length >= MAX_QUERY_LENGTH) return false
        query += event.codepointAsString()
        resetScroll()
        return true
    }

    private fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val availableViewport = lastAvailableViewport ?: getAvailableViewport()
        if (availableViewport.contains(mouseX, mouseY) && maxAvailableScroll > 0.0f) {
            availableScrollVelocity -= scrollY.toFloat() * SCROLL_STEP
            return true
        }
        val selectedViewport = lastSelectedViewport ?: getSelectedViewport()
        if (selectedViewport.contains(mouseX, mouseY) && maxSelectedScroll > 0.0f) {
            selectedScrollVelocity -= scrollY.toFloat() * SCROLL_STEP
            return true
        }
        return false
    }

    private fun filteredAvailable(): List<T> {
        val needle = query.lowercase(Locale.ROOT).trim()
        val result = ArrayList<T>()
        for (entry in allEntries) {
            if (setting.value.contains(entry)) continue
            if (selectedCategory >= 0 && selectedCategory < categories.size) {
                if (!categories[selectedCategory].predicate.test(entry)) continue
            }
            val text = displayNameFn(entry).lowercase(Locale.ROOT)
            if (needle.isEmpty() || text.contains(needle)) result.add(entry)
        }
        return result
    }

    private fun filteredSelected(): List<T> {
        val needle = query.lowercase(Locale.ROOT).trim()
        val result = ArrayList<T>()
        for (entry in setting.value) {
            val text = displayNameFn(entry).lowercase(Locale.ROOT)
            if (needle.isEmpty() || text.contains(needle)) result.add(entry)
        }
        return result
    }

    private fun UiScope.buildColumn(
        entries: List<T>, columnX: Float, startY: Float,
        columnWidth: Float, mouseX: Int, mouseY: Int, addColumn: Boolean, viewport: UiRect,
    ) {
        val iconProvider = this@RegistryListSelectPopup.iconProvider
        val hasIcons = iconProvider != null
        for (i in entries.indices) {
            val entry = entries[i]
            val rowY = startY + i * (ROW_HEIGHT + ROW_GAP)
            if (rowY + ROW_HEIGHT < viewport.y || rowY > viewport.bottom()) continue

            val rowBounds = UiRect(columnX, rowY, columnWidth, ROW_HEIGHT)
            val hovered = rowBounds.contains(mouseX.toDouble(), mouseY.toDouble()) &&
                    viewport.contains(mouseX.toDouble(), mouseY.toDouble())

            val bgHover = if (addColumn) (if (hovered) 1.0f else 0.0f) else (if (hovered) 0.45f else 0.0f)
            val rawName = displayNameFn(entry)
            val actionX = rowBounds.right() - 12.0f
            var textMaxWidth = rowBounds.width - 22.0f
            val previewStack = if (hasIcons) iconProvider(entry) else ItemStack.EMPTY
            if (hasIcons) {
                if (!previewStack.isEmpty) {
                    val previewX = actionX - ITEM_PREVIEW_GAP - ITEM_PREVIEW_SIZE
                    textMaxWidth = previewX - rowBounds.x - 12.0f
                }
            }
            val display = trim(rawName, 0.50f, textMaxWidth)
            val textY = centeredTextY(0.0f, rowBounds.height, 0.50f)

            val localRowBounds = rowBounds.relativeTo(origin)
            // 逐项缓存：只有悬停/预览宽度/行位置变化的行才重新捕获节点。

            LuminUIItem(Triple(setting, addColumn, entry)) {
                Positioned(localRowBounds.x, localRowBounds.y, localRowBounds.width, localRowBounds.height) {
                    Interaction("entry") {
                        onClick { if (addColumn) addFn(entry) else removeFn(entry); true }
                    }
                    if (addColumn) {
                        RoundRect(
                            0.0f, 0.0f, rowBounds.width, rowBounds.height, MD3Theme.CONTROL_RADIUS,
                            MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER, MD3Theme.SURFACE_CONTAINER_HIGH, bgHover)
                        )
                        Text(
                            display, 6.0f, textY, 0.50f,
                            if (hovered) MD3Theme.TEXT_PRIMARY else MD3Theme.TEXT_SECONDARY
                        )
                        Text(
                            "+", rowBounds.width - 12.0f, textY, 0.54f,
                            if (hovered) MD3Theme.TEXT_PRIMARY else MD3Theme.TEXT_SECONDARY
                        )
                    } else {
                        RoundRect(
                            0.0f, 0.0f, rowBounds.width, rowBounds.height, MD3Theme.CONTROL_RADIUS,
                            MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, bgHover)
                        )
                        Text(display, 6.0f, textY, 0.50f, MD3Theme.ON_SECONDARY_CONTAINER)
                        Text("-", rowBounds.width - 12.0f, textY, 0.54f, MD3Theme.ON_SECONDARY_CONTAINER)
                    }
                    if (!previewStack.isEmpty) {
                        Item(
                            previewStack, rowBounds.width - 12f - ITEM_PREVIEW_GAP - ITEM_PREVIEW_SIZE,
                            (rowBounds.height - ITEM_PREVIEW_SIZE) * 0.5f, ITEM_PREVIEW_SIZE
                        )
                    }
                }
            }
        }
    }

    private fun getSearchBounds(popupY: Float): UiRect {
        return UiRect(bounds.x + PADDING, popupY + TITLE_HEIGHT + 10.0f, bounds.width - PADDING * 2.0f, SEARCH_HEIGHT)
    }

    private fun getViewport(): UiRect {
        return getViewport(bounds.y)
    }

    private fun getViewport(popupY: Float): UiRect {
        val y = popupY + TITLE_HEIGHT + SEARCH_HEIGHT + HEADER_HEIGHT + 18.0f
        return UiRect(bounds.x + PADDING, y, bounds.width - PADDING * 2.0f, bounds.bottom() - y - PADDING)
    }

    private fun getAvailableViewport(): UiRect {
        return getColumnViewports(getViewport(bounds.y))[0]
    }

    private fun getSelectedViewport(): UiRect {
        return getColumnViewports(getViewport(bounds.y))[1]
    }

    private fun getColumnViewports(viewport: UiRect): Array<UiRect> {
        val columnViewportWidth = (viewport.width - COLUMN_GAP) / 2.0f
        val availableViewport = UiRect(viewport.x, viewport.y, columnViewportWidth, viewport.height)
        val selectedViewport = UiRect(
            availableViewport.right() + COLUMN_GAP, viewport.y, columnViewportWidth, viewport.height
        )
        return arrayOf(availableViewport, selectedViewport)
    }

    private fun updateAvailableSmoothScroll(partialTick: Float) {
        if (maxAvailableScroll <= 0.0f) {
            resetAvailableScroll()
            return
        }
        if (abs(availableScrollVelocity) <= 0.01f || partialTick <= 0.0f) return
        val nextScroll = (availableScroll + availableScrollVelocity * partialTick).coerceIn(0.0f, maxAvailableScroll)
        if (nextScroll.compareTo(availableScroll) == 0) {
            availableScrollVelocity = 0.0f
            return
        }
        availableScroll = nextScroll
        availableScrollVelocity *= SCROLL_DECAY
        if (abs(availableScrollVelocity) < MIN_SCROLL_VELOCITY) availableScrollVelocity = 0.0f
    }

    private fun updateSelectedSmoothScroll(partialTick: Float) {
        if (maxSelectedScroll <= 0.0f) {
            resetSelectedScroll()
            return
        }
        if (abs(selectedScrollVelocity) <= 0.01f || partialTick <= 0.0f) return
        val nextScroll = (selectedScroll + selectedScrollVelocity * partialTick).coerceIn(0.0f, maxSelectedScroll)
        if (nextScroll.compareTo(selectedScroll) == 0) {
            selectedScrollVelocity = 0.0f
            return
        }
        selectedScroll = nextScroll
        selectedScrollVelocity *= SCROLL_DECAY
        if (abs(selectedScrollVelocity) < MIN_SCROLL_VELOCITY) selectedScrollVelocity = 0.0f
    }

    private fun resetScroll() {
        resetAvailableScroll()
        resetSelectedScroll()
    }

    private fun resetAvailableScroll() {
        availableScroll = 0.0f
        availableScrollVelocity = 0.0f
    }

    private fun resetSelectedScroll() {
        selectedScroll = 0.0f
        selectedScrollVelocity = 0.0f
    }

    private fun applyDraggedAvailableScroll(mouseY: Double, viewport: UiRect) {
        val newScroll = availableScrollBarDrag.mouseDragged(mouseY, viewport, maxAvailableScroll)
        if (newScroll >= 0.0f) {
            availableScroll = newScroll.coerceIn(0.0f, maxAvailableScroll)
            availableScrollVelocity = 0.0f
        }
    }

    private fun applyDraggedSelectedScroll(mouseY: Double, viewport: UiRect) {
        val newScroll = selectedScrollBarDrag.mouseDragged(mouseY, viewport, maxSelectedScroll)
        if (newScroll >= 0.0f) {
            selectedScroll = newScroll.coerceIn(0.0f, maxSelectedScroll)
            selectedScrollVelocity = 0.0f
        }
    }

    private fun trim(value: String?, scale: Float, maxWidth: Float): String {
        if (value == null || value.isEmpty()) return ""
        val maxChars = maxOf(3, (maxWidth / (5.0f * scale)).toInt())
        return if (value.length <= maxChars) value else value.substring(0, maxOf(0, maxChars - 3)) + "..."
    }

    private fun UiScope.centeredTextY(boxY: Float, boxHeight: Float, scale: Float): Float {
        return boxY + (boxHeight - metrics.textHeight(scale)) * 0.5f
    }

    override fun close() {
        inputView.close()

        availableBuffer.close()
        selectedBuffer.close()
        textRenderer.close()
    }


    /** 列表内容、滚动位置、鼠标与主题参与 recompose 失效判定。 */

    data class Category<T : Any>(val name: String, val predicate: Predicate<T>)

    companion object {

        private const val PADDING = 8.0f
        private const val TITLE_HEIGHT = 18.0f
        private const val SEARCH_HEIGHT = 18.0f
        private const val HEADER_HEIGHT = 14.0f
        private const val ROW_HEIGHT = 18.0f
        private const val ROW_GAP = 2.0f
        private const val COLUMN_GAP = 6.0f
        private const val SCROLL_STEP = 24.0f
        private const val SCROLL_DECAY = 0.86f
        private const val MIN_SCROLL_VELOCITY = 0.3f
        private const val MAX_QUERY_LENGTH = 64
        private const val ITEM_PREVIEW_SIZE = 14.0f
        private const val ITEM_PREVIEW_GAP = 4.0f
        private const val CATEGORY_TAB_HEIGHT = 16.0f
        private const val CATEGORY_TAB_GAP = 4.0f

        private val SCROLLBAR_GUTTER = ScrollBarUtils.TOTAL_WIDTH + 1.0f

        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> collectRegistryEntries(registry: Registry<T>, setting: Setting<List<T>>): List<T> {
            val entries = ArrayList<T>()
            val filter = if (setting is RegistryListSetting<*>) {
                (setting as RegistryListSetting<T>).getFilter()
            } else {
                null
            }
            for (entry in registry) {
                if (filter == null || filter.test(entry)) {
                    entries.add(entry)
                }
            }
            entries.sortWith(Comparator.comparing { entry: T ->
                val key = registry.getKey(entry)
                key?.toString() ?: ""
            })
            return entries
        }

        @Suppress("UNCHECKED_CAST")
        fun create(bounds: UiRect, setting: RegistryListSetting<*>): PanelPopupHost.Popup {
            return when (setting.registryType) {
                RegistryListSetting.Type.BLOCK -> blockPopup(bounds, setting as RegistryListSetting<Block>)
                RegistryListSetting.Type.ITEM -> itemPopup(bounds, setting as RegistryListSetting<Item>)
                RegistryListSetting.Type.ENTITY_TYPE ->
                    entityTypePopup(bounds, setting as RegistryListSetting<EntityType<*>>)

                RegistryListSetting.Type.SOUND_EVENT ->
                    soundEventPopup(bounds, setting as RegistryListSetting<SoundEvent>)

                RegistryListSetting.Type.PARTICLE_TYPE ->
                    particleTypePopup(bounds, setting as RegistryListSetting<ParticleType<*>>)

                RegistryListSetting.Type.ENCHANTMENT ->
                    enchantmentPopup(bounds, setting as RegistryListSetting<String>)
            }
        }

        private fun blockPopup(bounds: UiRect, setting: RegistryListSetting<Block>): RegistryListSelectPopup<Block> {
            return RegistryListSelectPopup(
                bounds, setting, BuiltInRegistries.BLOCK,
                { block -> BlockRegistryUtils.displayName(block) },
                { block -> blockPreviewStack(block) },
                { value -> setting.add(value) }, { value -> setting.remove(value) }
            )
        }

        private fun itemPopup(bounds: UiRect, setting: RegistryListSetting<Item>): RegistryListSelectPopup<Item> {
            return RegistryListSelectPopup(
                bounds, setting, BuiltInRegistries.ITEM,
                { item -> itemDisplayName(item) },
                { item -> itemPreviewStack(item) },
                { value -> setting.add(value) }, { value -> setting.remove(value) }
            )
        }

        private fun entityTypePopup(
            bounds: UiRect, setting: RegistryListSetting<EntityType<*>>,
        ): RegistryListSelectPopup<EntityType<*>> {
            return RegistryListSelectPopup(
                bounds, setting, BuiltInRegistries.ENTITY_TYPE,
                { entityType -> entityType.description.string },
                { entityType -> entityTypePreviewStack(entityType) },
                { value -> setting.add(value) }, { value -> setting.remove(value) }
            )
        }

        private fun soundEventPopup(
            bounds: UiRect,
            setting: RegistryListSetting<SoundEvent>
        ): RegistryListSelectPopup<SoundEvent> {
            return RegistryListSelectPopup(
                bounds, setting, BuiltInRegistries.SOUND_EVENT,
                { sound ->
                    val key = BuiltInRegistries.SOUND_EVENT.getKey(sound)
                    key?.path ?: ""
                },
                { value -> setting.add(value) }, { value -> setting.remove(value) }
            )
        }

        private fun particleTypePopup(
            bounds: UiRect,
            setting: RegistryListSetting<ParticleType<*>>
        ): RegistryListSelectPopup<ParticleType<*>> {
            return RegistryListSelectPopup(
                bounds, setting, BuiltInRegistries.PARTICLE_TYPE,
                { particle -> formatPath(BuiltInRegistries.PARTICLE_TYPE.getKey(particle)?.path ?: "") },
                { value -> setting.add(value) }, { value -> setting.remove(value) }
            )
        }

        private fun <T : Any> ofEntries(
            bounds: UiRect, setting: Setting<List<T>>, entries: List<T>,
            displayNameFn: (T) -> String, iconProvider: ((T) -> ItemStack)?,
            categories: List<Category<T>>, addFn: (T) -> Unit, removeFn: (T) -> Unit,
        ): RegistryListSelectPopup<T> {
            return RegistryListSelectPopup(
                bounds, setting, entries, displayNameFn, iconProvider, categories, addFn, removeFn, Unit
            )
        }

        private fun enchantmentPopup(
            bounds: UiRect,
            setting: RegistryListSetting<String>
        ): RegistryListSelectPopup<String> {
            return ofEntries(
                bounds, setting, collectEnchantments(),
                { id: String -> enchantmentDisplayName(id) }, null, emptyList(),
                { value: String -> setting.add(value) }, { value: String -> setting.remove(value) }
            )
        }

        private fun collectEnchantments(): List<String> {
            val ids = ArrayList<String>()
            val level = mc.level
            if (level != null) {
                level.registryAccess().lookup(Registries.ENCHANTMENT).ifPresent { registry ->
                    registry.listElementIds()
                        .map { key -> key.identifier() }
                        .map { identifier -> identifier.toString() }
                        .forEach { id -> ids.add(id) }
                }
            }
            ids.sortWith(String.CASE_INSENSITIVE_ORDER)
            return ids
        }

        private fun enchantmentDisplayName(id: String): String {
            val identifier = Identifier.tryParse(id)
            val level = mc.level
            if (identifier == null || level == null) {
                return formatPath(id)
            }
            return level.registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap { registry -> registry.get(ResourceKey.create(Registries.ENCHANTMENT, identifier)) }
                .map { holder -> holder.value().description().string }
                .orElseGet { formatPath(identifier.path) }
        }

        private fun itemDisplayName(item: Item?): String {
            if (item == null) {
                return ""
            }
            if (item.builtInRegistryHolder().areComponentsBound()) {
                return item.defaultInstance.hoverName.string
            }
            val key = BuiltInRegistries.ITEM.getKey(item)
            return if (key != null) formatPath(key.path) else ""
        }

        private fun blockPreviewStack(block: Block?): ItemStack {
            if (block == null) {
                return ItemStack.EMPTY
            }
            return itemPreviewStack(block.asItem())
        }

        private fun itemPreviewStack(item: Item?): ItemStack {
            if (item == null || item === Items.AIR || !item.builtInRegistryHolder().areComponentsBound()) {
                return ItemStack.EMPTY
            }
            return item.defaultInstance
        }

        private fun entityTypePreviewStack(entityType: EntityType<*>): ItemStack {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType)
            val eggId = Identifier.tryParse(key.namespace + ":" + key.path + "_spawn_egg")
            if (eggId != null) {
                val egg = BuiltInRegistries.ITEM.getOptional(eggId).orElse(null)
                val eggStack = itemPreviewStack(egg)
                if (!eggStack.isEmpty) {
                    return eggStack
                }
            }
            return itemPreviewStack(BuiltInRegistries.ITEM.getOptional(key).orElse(null))
        }

        private fun formatPath(value: String?): String {
            if (value == null || value.isBlank()) {
                return ""
            }
            val path = if (value.contains(":")) value.substring(value.indexOf(':') + 1) else value
            val parts = path.split("_")
            val builder = StringBuilder()
            for (part in parts) {
                if (part.isEmpty()) {
                    continue
                }
                if (builder.isNotEmpty()) {
                    builder.append(' ')
                }
                builder.append(Character.toUpperCase(part[0]))
                if (part.length > 1) {
                    builder.append(part.substring(1))
                }
            }
            return builder.toString()
        }

    }

}
