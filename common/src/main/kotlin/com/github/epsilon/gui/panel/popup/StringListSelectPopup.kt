package com.github.epsilon.gui.panel.popup

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.panel.utils.IMEFocusHelper
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.Setting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.Mth
import java.util.ArrayList

class StringListSelectPopup(
    private val bounds: UiRect,
    private val setting: Setting<List<String>>,
    private val addFn: (String) -> Unit,
    private val removeFn: (String) -> Unit,
) : PanelPopupHost.Popup {

    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private val textRenderer: TextRenderer = TextRenderer.create()
    private val openAnimation = Animation(Easing.EASE_OUT_CUBIC, 160L)
    private val scrollBarDrag = ScrollBarDragState()

    private var input = ""
    private var scroll = 0.0f
    private var scrollVelocity = 0.0f
    private var maxScroll = 0.0f
    private var hoveredRemove: String? = null
    private var hoveredMoveUp: String? = null
    private var hoveredMoveDown: String? = null
    private var lastViewport: UiRect? = null

    init {
        this.openAnimation.startValue = 0.0f
    }

    override fun getBounds(): UiRect {
        return bounds
    }

    override fun extractGui(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        contentBuffer.clear()
        val entries = entries()
        val contentHeight = entries.size * (ROW_HEIGHT + ROW_GAP)
        val viewport = getViewport()
        maxScroll = Math.max(0.0f, contentHeight - viewport.height)
        scroll = Mth.clamp(scroll, 0.0f, maxScroll)
        updateSmoothScroll(partialTick)

        val tree = UiTree.build { scope ->
            val progress = scope.animate(openAnimation, 1.0f)
            val popupY = bounds.y - (1.0f - progress) * 6.0f
            val animatedBounds = UiRect(bounds.x, popupY, bounds.width, bounds.height)
            val inputBounds = getInputBounds(popupY)
            val animatedViewport = getViewport(popupY)
            lastViewport = animatedViewport
            scope.pushAbsolute(animatedBounds) { popup ->
                popup.popupCard(
                    animatedBounds.atOrigin(), MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.POPUP_SHADOW_BLUR,
                    MD3Theme.withAlpha(MD3Theme.SHADOW, (MD3Theme.POPUP_SHADOW_ALPHA * progress).toInt()),
                    MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)
                )

                val titleY = centeredTextY(6.0f, TITLE_HEIGHT, 0.68f)
                val summaryScale = 0.52f
                val summary = setting.getValue().size.toString() + EpsilonTranslations.Gui.LIST_ENTRIES.getTranslatedName()
                popup.text(setting.getDisplayName(), PADDING, titleY, 0.68f, MD3Theme.TEXT_PRIMARY)
                popup.text(
                    summary, animatedBounds.width - PADDING - textRenderer.getWidth(summary, summaryScale),
                    centeredTextY(6.0f, TITLE_HEIGHT, summaryScale), summaryScale, MD3Theme.TEXT_MUTED
                )

                val placeholder =
                    if (input.isEmpty()) EpsilonTranslations.Gui.LIST_TYPE_TO_ADD.getTranslatedName() else input
                popup.input(
                    inputBounds.relativeTo(animatedBounds), true, 1.0f, 8.0f,
                    placeholder, 0.54f,
                    if (input.isEmpty()) MD3Theme.TEXT_MUTED else MD3Theme.TEXT_PRIMARY,
                    input.length, MD3Theme.PRIMARY, null, 0.0f, null
                )
                IMEFocusHelper.updateCursorPos(inputBounds.x + 8.0f, inputBounds.y + 4.0f)

                hoveredRemove = null
                hoveredMoveUp = null
                hoveredMoveDown = null
                val localViewport = animatedViewport.relativeTo(animatedBounds)
                popup.viewport(contentBuffer, localViewport, scroll, maxScroll, contentHeight, mouseX, mouseY) { content ->
                    buildEntryList(
                        content, entries, animatedViewport.x, animatedViewport.y - scroll,
                        localViewport, mouseX, mouseY, animatedViewport
                    )
                }
            }
        }
        renderBatch.render(tree)
    }

    override fun flush(renderBatch: UiRenderBatch) {
        contentBuffer.flushAndClear()
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) return false
        val viewport = lastViewport ?: getViewport()
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, scroll, maxScroll)) {
            applyDraggedScroll(event.y(), viewport)
            return true
        }
        val moveUp = hoveredMoveUp
        if (moveUp != null) {
            moveEntry(moveUp, -1)
            return true
        }
        val moveDown = hoveredMoveDown
        if (moveDown != null) {
            moveEntry(moveDown, 1)
            return true
        }
        val remove = hoveredRemove
        if (remove != null) {
            removeFn(remove)
            return true
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        return scrollBarDrag.mouseReleased()
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        if (!scrollBarDrag.isDragging()) return false
        applyDraggedScroll(event.y(), lastViewport ?: getViewport())
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
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

    override fun charTyped(event: CharacterEvent): Boolean {
        if (input.length >= MAX_QUERY_LENGTH) return false
        input += event.codepointAsString()
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!getViewport(bounds.y).contains(mouseX, mouseY) || maxScroll <= 0.0f) return false
        scrollVelocity -= scrollY.toFloat() * SCROLL_STEP
        return true
    }

    private fun entries(): List<String> {
        return ArrayList(setting.getValue())
    }

    private fun buildEntryList(
        scope: UiTree.Scope, entries: List<String>, startX: Float, startY: Float,
        localViewport: UiRect, mouseX: Int, mouseY: Int, viewport: UiRect,
    ) {
        val origin = scope.bound()
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
            if (removeHovered) hoveredRemove = entry
            if (downHovered) hoveredMoveDown = entry
            if (upHovered) hoveredMoveUp = entry

            val bgHover = if (hovered) 0.45f else 0.0f
            val actionAreaWidth = ACTION_WIDTH * 3.0f + ACTION_GAP * 2.0f
            val display = trim(entry, 0.50f, rowBounds.width - actionAreaWidth - 14.0f)
            val localRowBounds = rowBounds.relativeTo(origin)
            scope.pushRelative(localRowBounds) { row ->
                row.roundRect(
                    0.0f, 0.0f, rowBounds.width, rowBounds.height, MD3Theme.CONTROL_RADIUS,
                    MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, bgHover)
                )
                row.text(
                    display, 6.0f, centeredTextY(0.0f, rowBounds.height, 0.50f), 0.50f,
                    MD3Theme.ON_SECONDARY_CONTAINER
                )
                drawAction(row, upBounds.relativeTo(rowBounds), "↑", canMoveUp, upHovered)
                drawAction(row, downBounds.relativeTo(rowBounds), "↓", canMoveDown, downHovered)
                drawAction(row, removeBounds.relativeTo(rowBounds), "-", true, removeHovered)
            }
        }
    }

    private fun actionBounds(rowBounds: UiRect, indexFromRight: Int): UiRect {
        val x = rowBounds.right() - 6.0f - ACTION_WIDTH - indexFromRight * (ACTION_WIDTH + ACTION_GAP)
        return UiRect(x, rowBounds.y + 2.0f, ACTION_WIDTH, rowBounds.height - 4.0f)
    }

    private fun drawAction(scope: UiTree.Scope, bounds: UiRect, label: String, enabled: Boolean, hovered: Boolean) {
        val textScale = 0.50f
        if (enabled && hovered) {
            scope.roundRect(
                bounds.x, bounds.y, bounds.width, bounds.height, 4.0f,
                MD3Theme.withAlpha(MD3Theme.ON_SECONDARY_CONTAINER, 34)
            )
        }
        scope.text(
            label,
            bounds.x + (bounds.width - textRenderer.getWidth(label, textScale)) / 2.0f,
            centeredTextY(bounds.y, bounds.height, textScale),
            textScale,
            if (enabled) MD3Theme.ON_SECONDARY_CONTAINER else MD3Theme.withAlpha(MD3Theme.ON_SECONDARY_CONTAINER, 80)
        )
    }

    private fun moveEntry(entry: String, offset: Int) {
        val next = ArrayList(setting.getValue())
        val index = next.indexOf(entry)
        val target = index + offset
        if (index < 0 || target < 0 || target >= next.size) {
            return
        }
        val value = next.removeAt(index)
        next.add(target, value)
        setting.setValue(next)
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
        if (Math.abs(scrollVelocity) <= 0.01f || partialTick <= 0.0f) return
        val nextScroll = Mth.clamp(scroll + scrollVelocity * partialTick, 0.0f, maxScroll)
        if (nextScroll.compareTo(scroll) == 0) {
            scrollVelocity = 0.0f
            return
        }
        scroll = nextScroll
        scrollVelocity *= SCROLL_DECAY
        if (Math.abs(scrollVelocity) < MIN_SCROLL_VELOCITY) scrollVelocity = 0.0f
    }

    private fun resetScroll() {
        scroll = 0.0f
        scrollVelocity = 0.0f
    }

    private fun applyDraggedScroll(mouseY: Double, viewport: UiRect) {
        val newScroll = scrollBarDrag.mouseDragged(mouseY, viewport, maxScroll)
        if (newScroll >= 0.0f) {
            scroll = Mth.clamp(newScroll, 0.0f, maxScroll)
            scrollVelocity = 0.0f
        }
    }

    private fun trim(value: String?, scale: Float, maxWidth: Float): String {
        if (value == null || value.isEmpty()) return ""
        val maxChars = Math.max(3, (maxWidth / (5.0f * scale)).toInt())
        return if (value.length <= maxChars) value else value.substring(0, Math.max(0, maxChars - 3)) + "..."
    }

    private fun centeredTextY(boxY: Float, boxHeight: Float, scale: Float): Float {
        return boxY + (boxHeight - textRenderer.getHeight(scale)) * 0.5f
    }

    override fun close() {
        contentBuffer.close()
    }

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
