package com.github.epsilon.gui.panel.popup

import com.github.epsilon.assets.i18n.EpsilonTranslations
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
import com.github.epsilon.kernel.state.getValue
import com.github.epsilon.kernel.state.mutableStateOf
import com.github.epsilon.kernel.state.setValue
import com.github.epsilon.settings.Setting
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import kotlin.math.abs

class StringListSelectPopup(
    private val bounds: UiRect,
    private val setting: Setting<List<String>>,
    private val addFn: (String) -> Unit,
    private val removeFn: (String) -> Unit,
) : PanelPopupHost.Popup {

    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private val openAnimation = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 160L)
    private val scrollBarDrag = ScrollBarDragState()

    private var inputFocused by mutableStateOf(false)
    private var input by mutableStateOf("")
    private var scroll by mutableStateOf(0.0f)
    private var scrollVelocity = 0.0f
    private var maxScroll = 0.0f
    private var lastViewport: UiRect? = null

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
        contentBuffer.clear()
        val entries = entries()
        val contentHeight = entries.size * (ROW_HEIGHT + ROW_GAP)
        val viewport = getViewport()
        maxScroll = maxOf(0.0f, contentHeight - viewport.height)
        scroll = scroll.coerceIn(0.0f, maxScroll)
        updateSmoothScroll(partialTick)

        // 开启动画是推式动画：目标在命令阶段写入，绘制块内只读取 value。
        val popupY = bounds.y - (1.0f - openAnimation.value) * 6.0f
        openAnimation.target = 1.0f
        val animatedBounds = UiRect(bounds.x, popupY, bounds.width, bounds.height)
        val inputBounds = getInputBounds(popupY)
        val animatedViewport = getViewport(popupY)
        lastViewport = animatedViewport

        renderBatch.LuminUIScene(animatedBounds, key = this) {
            InputRegion(
                key = this@StringListSelectPopup to "input",
                onPress = ::mouseClicked,
                onDrag = ::mouseDragged,
                onRelease = ::mouseReleased,
                onScroll = ::mouseScrolled,
                onBlur = {},
                onCancel = { scrollBarDrag.reset() })

            val progress = openAnimation.value
            PopupCard(
                size, MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.POPUP_SHADOW_BLUR,
                MD3Theme.withAlpha(MD3Theme.SHADOW, (MD3Theme.POPUP_SHADOW_ALPHA * progress).toInt()),
                MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)
            )

            val titleY = centeredTextY(6.0f, TITLE_HEIGHT, 0.68f)
            val summaryScale = 0.52f
            val summary = setting.value.size.toString() + EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName()
            Text(setting.displayName, PADDING, titleY, 0.68f, MD3Theme.TEXT_PRIMARY)
            Text(
                summary, animatedBounds.width - PADDING - metrics.textWidth(summary, summaryScale),
                centeredTextY(6.0f, TITLE_HEIGHT, summaryScale), summaryScale, MD3Theme.TEXT_MUTED
            )

            val placeholder =
                if (input.isEmpty()) EpsilonTranslations.Gui.LIST_TYPE_TO_ADD.getTranslatedName() else input
            val localInputBounds = inputBounds.relativeTo(animatedBounds)
            Positioned(localInputBounds.x, localInputBounds.y, localInputBounds.width, localInputBounds.height) {
                Interaction("editor") {
                    autoFocus = true
                    onClick { true }
                    onKeyPressed(::keyPressed)
                    onTextInput(::charTyped)
                    onFocusChanged { inputFocused = it }
                }
                Input(
                    UiRect(0.0f, 0.0f, localInputBounds.width, localInputBounds.height), inputFocused, 1.0f,
                    0.0f, Color(0, 0, 0, 0), 0.0f,
                    8.0f, placeholder, 0.54f,
                    if (input.isEmpty()) MD3Theme.TEXT_MUTED else MD3Theme.TEXT_PRIMARY,
                    null, null,
                    if (inputFocused) input.length else null, MD3Theme.PRIMARY, null, 0.0f, null
                )
            }
            IMEFocusHelper.updateCursorPos(inputBounds.x + 8.0f, inputBounds.y + 4.0f)

            val localViewport = animatedViewport.relativeTo(animatedBounds)
            Viewport(
                contentBuffer,
                localViewport,
                scroll,
                maxScroll,
                contentHeight,
                mouseX,
                mouseY
            ) {
                buildEntryList(
                    entries, animatedViewport.x, animatedViewport.y - scroll,
                    localViewport, mouseX, mouseY, animatedViewport
                )
            }
        }
    }

    override fun flush(renderBatch: UiRenderBatch) {
        contentBuffer.flushAndClear()
    }

    private fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) return false
        val viewport = lastViewport ?: getViewport()
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, scroll, maxScroll)) {
            applyDraggedScroll(event.y(), viewport)
            return true
        }

        return true
    }

    private fun mouseReleased(event: MouseButtonEvent): Boolean {
        return scrollBarDrag.mouseReleased()
    }

    private fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (!scrollBarDrag.isDragging()) return false
        applyDraggedScroll(event.y(), lastViewport ?: getViewport())
        return true
    }

    private fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == InputConstants.KEY_RETURN && !input.isBlank()) {
            addFn(input.trim())
            input = ""
            resetScroll()
            return true
        }
        return when (event.key()) {
            InputConstants.KEY_BACKSPACE -> {
                if (!input.isEmpty()) input = input.substring(0, input.length - 1)
                true
            }

            InputConstants.KEY_DELETE -> {
                input = ""
                true
            }

            else -> false
        }
    }

    private fun charTyped(event: CharacterEvent): Boolean {
        if (input.length >= MAX_QUERY_LENGTH) return false
        input += event.codepointAsString()
        return true
    }

    private fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!getViewport(bounds.y).contains(mouseX, mouseY) || maxScroll <= 0.0f) return false
        scrollVelocity -= scrollY.toFloat() * SCROLL_STEP
        return true
    }

    private fun entries(): List<String> {
        return ArrayList(setting.value)
    }

    private fun UiScope.buildEntryList(
        entries: List<String>, startX: Float, startY: Float,
        localViewport: UiRect, mouseX: Int, mouseY: Int, viewport: UiRect,
    ) {
        val width = localViewport.width - (if (maxScroll > 0.0f) SCROLLBAR_GUTTER else 0.0f)
        for (i in entries.indices) {
            val entry = entries[i]
            val rowY = startY + i * (ROW_HEIGHT + ROW_GAP)
            if (rowY + ROW_HEIGHT < viewport.y || rowY > viewport.bottom()) continue

            val rowBounds = UiRect(startX, rowY, width, ROW_HEIGHT)
            val hovered = rowBounds.contains(mouseX.toDouble(), mouseY.toDouble()) &&
                    viewport.contains(mouseX.toDouble(), mouseY.toDouble())
            val canMoveUp = i > 0
            val canMoveDown = i < entries.size - 1
            val removeBounds = actionBounds(rowBounds, 0)
            val downBounds = actionBounds(rowBounds, 1)
            val upBounds = actionBounds(rowBounds, 2)
            val removeHovered = hovered && removeBounds.contains(mouseX.toDouble(), mouseY.toDouble())
            val downHovered = hovered && canMoveDown && downBounds.contains(mouseX.toDouble(), mouseY.toDouble())
            val upHovered = hovered && canMoveUp && upBounds.contains(mouseX.toDouble(), mouseY.toDouble())

            val bgHover = if (hovered) 0.45f else 0.0f
            val actionAreaWidth = ACTION_WIDTH * 3.0f + ACTION_GAP * 2.0f
            val display = trim(entry, 0.50f, rowBounds.width - actionAreaWidth - 14.0f)
            val localRowBounds = rowBounds.relativeTo(origin)
            // 逐项缓存：只有悬停/排序状态或行位置变化的行才重新捕获节点。

            LuminUIItem(setting to entry) {
                Positioned(localRowBounds.x, localRowBounds.y, localRowBounds.width, localRowBounds.height) {
                    RoundRect(
                        0.0f, 0.0f, rowBounds.width, rowBounds.height, MD3Theme.CONTROL_RADIUS,
                        MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, bgHover)
                    )
                    Text(
                        display, 6.0f, centeredTextY(0.0f, rowBounds.height, 0.50f), 0.50f,
                        MD3Theme.ON_SECONDARY_CONTAINER
                    )
                    Interaction("up", upBounds.relativeTo(rowBounds), enabled = canMoveUp) {
                        onClick { moveEntry(entry, -1); true }
                    }
                    Interaction("down", downBounds.relativeTo(rowBounds), enabled = canMoveDown) {
                        onClick { moveEntry(entry, 1); true }
                    }
                    Interaction("remove", removeBounds.relativeTo(rowBounds)) {
                        onClick { removeFn(entry); true }
                    }
                    drawAction(upBounds.relativeTo(rowBounds), "↑", canMoveUp, upHovered)
                    drawAction(downBounds.relativeTo(rowBounds), "↓", canMoveDown, downHovered)
                    drawAction(removeBounds.relativeTo(rowBounds), "-", true, removeHovered)
                }
            }
        }
    }

    private fun actionBounds(rowBounds: UiRect, indexFromRight: Int): UiRect {
        val x = rowBounds.right() - 6.0f - ACTION_WIDTH - indexFromRight * (ACTION_WIDTH + ACTION_GAP)
        return UiRect(x, rowBounds.y + 2.0f, ACTION_WIDTH, rowBounds.height - 4.0f)
    }

    private fun UiScope.drawAction(bounds: UiRect, label: String, enabled: Boolean, hovered: Boolean) {
        val textScale = 0.50f
        if (enabled && hovered) {
            RoundRect(
                bounds.x, bounds.y, bounds.width, bounds.height, 4.0f,
                MD3Theme.withAlpha(MD3Theme.ON_SECONDARY_CONTAINER, 34)
            )
        }
        Text(
            label,
            bounds.x + (bounds.width - metrics.textWidth(label, textScale)) / 2.0f,
            centeredTextY(bounds.y, bounds.height, textScale),
            textScale,
            if (enabled) MD3Theme.ON_SECONDARY_CONTAINER else MD3Theme.withAlpha(MD3Theme.ON_SECONDARY_CONTAINER, 80)
        )
    }

    private fun moveEntry(entry: String, offset: Int) {
        val next = ArrayList(setting.value)
        val index = next.indexOf(entry)
        val target = index + offset
        if (index < 0 || target < 0 || target >= next.size) {
            return
        }
        val value = next.removeAt(index)
        next.add(target, value)
        setting.value = next
    }

    private fun getInputBounds(popupY: Float): UiRect {
        return UiRect(bounds.x + PADDING, popupY + TITLE_HEIGHT + 10.0f, bounds.width - PADDING * 2.0f, INPUT_HEIGHT)
    }

    private fun getViewport(): UiRect {
        return getViewport(bounds.y)
    }

    private fun getViewport(popupY: Float): UiRect {
        val y = popupY + TITLE_HEIGHT + INPUT_HEIGHT + 20.0f
        return UiRect(bounds.x + PADDING, y, bounds.width - PADDING * 2.0f, bounds.bottom() - y - PADDING)
    }

    private fun updateSmoothScroll(partialTick: Float) {
        if (maxScroll <= 0.0f) {
            resetScroll()
            return
        }
        if (abs(scrollVelocity) <= 0.01f || partialTick <= 0.0f) return
        val nextScroll = (scroll + scrollVelocity * partialTick).coerceIn(0.0f, maxScroll)
        if (nextScroll.compareTo(scroll) == 0) {
            scrollVelocity = 0.0f
            return
        }
        scroll = nextScroll
        scrollVelocity *= SCROLL_DECAY
        if (abs(scrollVelocity) < MIN_SCROLL_VELOCITY) scrollVelocity = 0.0f
    }

    private fun resetScroll() {
        scroll = 0.0f
        scrollVelocity = 0.0f
    }

    private fun applyDraggedScroll(mouseY: Double, viewport: UiRect) {
        val newScroll = scrollBarDrag.mouseDragged(mouseY, viewport, maxScroll)
        if (newScroll >= 0.0f) {
            scroll = newScroll.coerceIn(0.0f, maxScroll)
            scrollVelocity = 0.0f
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

        contentBuffer.close()
    }

    /** 条目列表、输入缓冲、滚动位置、鼠标与主题共同决定绘制结果。 */

    private companion object {

        private const val PADDING = 8.0f
        private const val TITLE_HEIGHT = 18.0f
        private const val INPUT_HEIGHT = 18.0f
        private const val ROW_HEIGHT = 18.0f
        private const val ROW_GAP = 2.0f
        private const val ACTION_WIDTH = 14.0f
        private const val ACTION_GAP = 3.0f
        private const val SCROLL_STEP = 24.0f
        private const val SCROLL_DECAY = 0.86f
        private const val MIN_SCROLL_VELOCITY = 0.3f
        private const val MAX_QUERY_LENGTH = 64

        private val SCROLLBAR_GUTTER = ScrollBarUtils.TOTAL_WIDTH + 1.0f

    }

}
