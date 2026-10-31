package com.github.epsilon.gui.panel.view.settings

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.UiInvalidationState
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.component.PanelElements
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.abs
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.util.ArrayList
import java.util.HashMap

class FriendClientSettingTab(
    private val state: PanelState,
    private val textRenderer: TextRenderer,
) : ClientSettingTabView {

    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private val contentState = UiInvalidationState()
    private val rowHoverAnimations = HashMap<String, Animation>()
    private val removeHoverAnimations = HashMap<String, Animation>()
    private val scrollBarDrag = ScrollBarDragState()
    private val rowEntries = ArrayList<FriendRowEntry>()
    private val inputField = ClientSettingTextField(MAX_FRIEND_NAME_LENGTH)

    private var bounds: UiRect? = null
    private var lastScroll = Float.NaN
    private var lastFriendList: List<String> = emptyList()
    private var lastContentSignature = Long.MIN_VALUE
    private var scrollVelocity = 0.0f

    override fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.bounds = bounds

        if (abs(scrollVelocity) > 0.01f) {
            state.scrollFriend((scrollVelocity * partialTick).toDouble())
            scrollVelocity *= 0.86f
            if (abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0.0f
            }
            markDirty()
        }

        val inputBounds = getInputBounds(bounds)
        val listViewport = getListViewport(bounds)
        val friends = FriendManager.getFriends().sortedWith(String.CASE_INSENSITIVE_ORDER)
        val contentHeight = friends.size * (FRIEND_ROW_HEIGHT + MD3Theme.ROW_GAP)
        state.setMaxFriendScroll(contentHeight - listViewport.height)
        val maxScroll = maxOf(0.0f, contentHeight - listViewport.height)
        val hasScrollBar = maxScroll > 0.0f
        val rowWidth = if (hasScrollBar) listViewport.width - ScrollBarUtils.TOTAL_WIDTH else listViewport.width
        val contentSignature = buildContentSignature(friends)
        val rebuildContent = shouldRebuild(listViewport, mouseX, mouseY, friends, guiGraphics.guiHeight(), contentSignature)

        if (rebuildContent) {
            contentBuffer.clear()
            contentState.beginRebuild()
            rowEntries.clear()
            rowHoverAnimations.keys.removeIf { name -> !friends.contains(name) }
            removeHoverAnimations.keys.removeIf { name -> !friends.contains(name) }
        }

        val tree = UiTree.build { scope ->
            inputField.buildUi(
                scope, inputBounds, mouseX, mouseY, textRenderer,
                EpsilonTranslations.Gui.FRIEND_INPUT_PLACEHOLDER.getTranslatedName(), FRIEND_INPUT_FIELD_SCALE, "↵"
            )
            scope.viewport(contentBuffer, listViewport, state.getFriendScroll(), maxScroll, contentHeight, mouseX, mouseY) { content ->
                if (!rebuildContent) {
                    return@viewport
                }
                var rowY = listViewport.y - state.getFriendScroll()
                for (friendName in friends) {
                    val rowBounds = UiRect(listViewport.x, rowY, rowWidth, FRIEND_ROW_HEIGHT)
                    val removeBounds = getRemoveButtonBounds(rowBounds)
                    rowEntries.add(FriendRowEntry(friendName, rowBounds, removeBounds))

                    val hoverAnimation = rowHoverAnimations.getOrPut(friendName) { createAnimation() }
                    val removeAnimation = removeHoverAnimations.getOrPut(friendName) { createAnimation() }
                    hoverAnimation.run(if (rowBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
                    removeAnimation.run(if (removeBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
                    contentState.noteAnimation(!hoverAnimation.isFinished || !removeAnimation.isFinished)

                    content.pushAbsolute(rowBounds) { rowScope ->
                        buildFriendRow(
                            rowScope, friendName, rowBounds, removeBounds,
                            hoverAnimation.value, removeAnimation.value
                        )
                    }
                    rowY += FRIEND_ROW_HEIGHT + MD3Theme.ROW_GAP
                }

                if (friends.isEmpty()) {
                    val hintScale = 0.58f
                    val hint = EpsilonTranslations.Gui.FRIEND_EMPTY.getTranslatedName()
                    val hintWidth = textRenderer.getWidth(hint, hintScale)
                    val hintX = (listViewport.width - hintWidth) / 2.0f
                    val hintY = state.getFriendScroll() + listViewport.height / 2.0f -
                            textRenderer.getHeight(hintScale) / 2.0f
                    content.text(hint, hintX, hintY, hintScale, MD3Theme.TEXT_MUTED)
                }
            }
        }
        renderBatch.render(tree)

        if (rebuildContent) {
            rememberSnapshot(listViewport, mouseX, mouseY, friends, guiGraphics.guiHeight(), contentSignature)
        }
    }

    override fun flushContent() {
        contentBuffer.flush()
    }

    override fun markDirty() {
        contentState.markDirty()
    }

    override fun hasActiveAnimations(): Boolean {
        val hoveringRows = rowHoverAnimations.values.any { animation -> !animation.isFinished } ||
                removeHoverAnimations.values.any { animation -> !animation.isFinished }
        return contentState.hasActiveAnimations() || hoveringRows || inputField.hasActiveAnimations()
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val currentBounds = bounds
        if (currentBounds == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }

        scrollVelocity = 0.0f

        val listViewport = getListViewport(currentBounds)
        val maxScroll = state.getMaxFriendScroll()
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), listViewport, state.getFriendScroll(), maxScroll)) {
            val newScroll = scrollBarDrag.mouseDragged(event.y(), listViewport, maxScroll)
            if (newScroll >= 0.0f) {
                state.setFriendScroll(newScroll)
            }
            markDirty()
            return true
        }

        val inputBounds = getInputBounds(currentBounds)
        if (inputField.focusIfContains(inputBounds, event.x(), event.y())) {
            markDirty()
            return true
        }
        inputField.blur()

        for (entry in rowEntries) {
            if (entry.removeBounds.contains(event.x(), event.y())) {
                FriendManager.removeFriend(entry.name)
                markDirty()
                return true
            }
        }

        return false
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (scrollBarDrag.mouseReleased()) {
            markDirty()
            return true
        }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        val currentBounds = bounds
        if (!scrollBarDrag.isDragging() || currentBounds == null) {
            return false
        }
        val listViewport = getListViewport(currentBounds)
        val newScroll = scrollBarDrag.mouseDragged(event.y(), listViewport, state.getMaxFriendScroll())
        if (newScroll >= 0.0f) {
            state.setFriendScroll(newScroll)
        }
        markDirty()
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val currentBounds = bounds ?: return false
        val listViewport = getListViewport(currentBounds)
        if (listViewport.contains(mouseX, mouseY)) {
            scrollVelocity -= scrollY.toFloat() * 24.0f
            markDirty()
            return true
        }
        return false
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!inputField.isFocused()) {
            return false
        }

        return when (event.key()) {
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                addFriendFromInput()
                true
            }

            InputConstants.KEY_ESCAPE -> {
                inputField.blur()
                markDirty()
                true
            }

            else -> {
                if (inputField.keyPressed(event)) {
                    markDirty()
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (inputField.charTyped(event)) {
            markDirty()
            return true
        }
        return false
    }

    override fun onDeactivated() {
        scrollBarDrag.reset()
        scrollVelocity = 0.0f
        inputField.blur()
        markDirty()
    }

    private fun addFriendFromInput() {
        val name = inputField.getText().trim()
        if (!name.isEmpty() && !FriendManager.isFriend(name)) {
            FriendManager.addFriend(name)
        }
        inputField.clear()
        markDirty()
    }

    private fun buildFriendRow(
        scope: UiTree.Scope, name: String, bounds: UiRect, removeBounds: UiRect,
        hoverProgress: Float, removeHoverProgress: Float,
    ) {
        PanelElements.buildRowSurface(scope, bounds, hoverProgress)

        val avatarSize = 20.0f
        val avatarX = MD3Theme.ROW_CONTENT_INSET + 2.0f
        val avatarY = (bounds.height - avatarSize) / 2.0f
        scope.roundRect(avatarX, avatarY, avatarSize, avatarSize, avatarSize / 2.0f, MD3Theme.SECONDARY_CONTAINER)
        val initial = if (name.isEmpty()) "?" else name.substring(0, 1).uppercase()
        val initialScale = 0.54f
        val initialWidth = textRenderer.getWidth(initial, initialScale)
        val initialHeight = textRenderer.getHeight(initialScale)
        scope.text(
            initial,
            avatarX + (avatarSize - initialWidth) / 2.0f,
            avatarY + (avatarSize - initialHeight) / 2.0f,
            initialScale,
            MD3Theme.ON_SECONDARY_CONTAINER
        )

        val nameScale = 0.66f
        val nameX = avatarX + avatarSize + 8.0f
        val nameY = (bounds.height - textRenderer.getHeight(nameScale)) / 2.0f
        scope.text(name, nameX, nameY, nameScale, MD3Theme.TEXT_PRIMARY)

        PanelElements.buildIconButton(
            scope, textRenderer, removeBounds.relativeTo(bounds), "✕", 0.50f, MD3Theme.ERROR, removeHoverProgress
        )
    }

    private fun getListViewport(bounds: UiRect): UiRect {
        return UiRect(
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height - FRIEND_INPUT_HEIGHT - FRIEND_INPUT_BOTTOM_MARGIN * 2.0f
        )
    }

    private fun getInputBounds(bounds: UiRect): UiRect {
        return UiRect(
            bounds.x + 2.0f,
            bounds.bottom() - FRIEND_INPUT_HEIGHT - FRIEND_INPUT_BOTTOM_MARGIN,
            bounds.width - 4.0f,
            FRIEND_INPUT_HEIGHT
        )
    }

    private fun getRemoveButtonBounds(rowBounds: UiRect): UiRect {
        val buttonSize = 20.0f
        return UiRect(
            rowBounds.right() - MD3Theme.ROW_TRAILING_INSET - buttonSize,
            rowBounds.y + (rowBounds.height - buttonSize) / 2.0f,
            buttonSize,
            buttonSize
        )
    }

    private fun shouldRebuild(
        listViewport: UiRect, mouseX: Int, mouseY: Int, friends: List<String>,
        guiHeight: Int, contentSignature: Long,
    ): Boolean {
        if (contentState.needsRebuild(listViewport, mouseX, mouseY, guiHeight, contentSignature)) {
            return true
        }
        if (lastScroll.compareTo(state.getFriendScroll()) != 0) {
            return true
        }
        if (lastFriendList != friends) {
            return true
        }
        return lastContentSignature != contentSignature
    }

    private fun rememberSnapshot(
        listViewport: UiRect, mouseX: Int, mouseY: Int, friends: List<String>,
        guiHeight: Int, contentSignature: Long,
    ) {
        contentState.rememberSnapshot(listViewport, mouseX, mouseY, guiHeight, contentSignature)
        lastScroll = state.getFriendScroll()
        lastFriendList = ArrayList(friends)
        lastContentSignature = contentSignature
    }

    private fun buildContentSignature(friends: List<String>): Long {
        var signature = 17L
        signature = signature * 31L + TranslationManager.getRevision()
        signature = signature * 31L + java.lang.Float.floatToIntBits(state.getFriendScroll())
        for (friend in friends) {
            signature = signature * 31L + friend.hashCode()
        }
        return signature
    }

    private fun createAnimation(): Animation {
        val animation = Animation(Easing.EASE_OUT_CUBIC, 120L)
        animation.startValue = 0.0f
        return animation
    }

    override fun close() {
        contentBuffer.close()
        markDirty()
    }

    private data class FriendRowEntry(val name: String, val rowBounds: UiRect, val removeBounds: UiRect)

    private companion object {

        private const val FRIEND_ROW_HEIGHT = 30.0f
        private const val FRIEND_INPUT_HEIGHT = 28.0f
        private const val FRIEND_INPUT_BOTTOM_MARGIN = 4.0f
        private const val FRIEND_INPUT_FIELD_SCALE = 0.8f
        private const val MAX_FRIEND_NAME_LENGTH = 32

    }

}
