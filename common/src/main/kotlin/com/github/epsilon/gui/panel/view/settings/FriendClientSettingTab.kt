package com.github.epsilon.gui.panel.view.settings

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminView
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.component.RowSurface
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.abs
import com.github.epsilon.gui.lib.state.LuminAnimatable as Animation

class FriendClientSettingTab(
    private val state: PanelState,
) : ClientSettingTabView {

    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)

    private val rowHoverAnimations = HashMap<String, Animation>()
    private val removeHoverAnimations = HashMap<String, Animation>()
    private val scrollBarDrag = ScrollBarDragState()
    private val inputField = ClientSettingTextField(MAX_FRIEND_NAME_LENGTH)

    private var bounds: UiRect? = null
    private var scrollVelocity = 0.0f

    private val luminView = LuminView()

    override fun render(
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

        if (abs(scrollVelocity) > 0.01f) {
            state.scrollFriend((scrollVelocity * partialTick).toDouble())
            scrollVelocity *= 0.86f
            if (abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0.0f
            }

        }

        val inputBounds = getInputBounds(bounds)
        val listViewport = getListViewport(bounds)
        val friends = FriendManager.getFriends().sortedWith(String.CASE_INSENSITIVE_ORDER)
        val contentHeight = friends.size * (FRIEND_ROW_HEIGHT + MD3Theme.ROW_GAP)
        state.setMaxFriendScroll(contentHeight - listViewport.height)
        val maxScroll = maxOf(0.0f, contentHeight - listViewport.height)
        val hasScrollBar = maxScroll > 0.0f
        val rowWidth = if (hasScrollBar) listViewport.width - ScrollBarUtils.TOTAL_WIDTH else listViewport.width

        contentBuffer.clear()

        rowHoverAnimations.keys.removeIf { name -> !friends.contains(name) }
            removeHoverAnimations.keys.removeIf { name -> !friends.contains(name) }

        renderBatch.LuminUIScene(bounds, key = this to "pass-1") {
            InputRegion(key = this@FriendClientSettingTab to "input", onPress = ::mouseClicked, onDrag = ::mouseDragged, onRelease = ::mouseReleased, onScroll = ::mouseScrolled,
                onCancel = { scrollBarDrag.reset() })

            val localInput = inputBounds.relativeTo(bounds)
            Positioned(localInput.x, localInput.y, localInput.width, localInput.height) {
                InputRegion(inputField, onPress = { _, _ -> inputField.focus(); true },
                    onKey = ::keyPressed, onText = ::charTyped, onBlur = inputField::blur)
                ClientSettingTextField(
                    inputField, mouseX, mouseY,
                    EpsilonTranslations.Gui.FRIEND_INPUT_PLACEHOLDER.getTranslatedName(), FRIEND_INPUT_FIELD_SCALE, "↵"
                )
            }
            Viewport(
                contentBuffer,
                listViewport.relativeTo(bounds),
                state.getFriendScroll(),
                maxScroll,
                contentHeight,
                mouseX,
                mouseY
            ) {
                var rowY = listViewport.y - state.getFriendScroll()
                for (friendName in friends) {
                    val rowBounds = UiRect(listViewport.x, rowY, rowWidth, FRIEND_ROW_HEIGHT)
                    val removeBounds = getRemoveButtonBounds(rowBounds)
                    Interaction(friendName, removeBounds.relativeTo(origin)) {
                        onClick { FriendManager.removeFriend(friendName); true }
                    }

                    val hoverAnimation = rowHoverAnimations.getOrPut(friendName) { createAnimation() }
                    val removeAnimation = removeHoverAnimations.getOrPut(friendName) { createAnimation() }
                    hoverAnimation.run(if (rowBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
                    removeAnimation.run(if (removeBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)

                    val localRow = rowBounds.relativeTo(origin)
                    Positioned(localRow.x, localRow.y, localRow.width, localRow.height) {
                        buildFriendRow(friendName, rowBounds, removeBounds, hoverAnimation.value, removeAnimation.value)
                    }
                    rowY += FRIEND_ROW_HEIGHT + MD3Theme.ROW_GAP
                }

                if (friends.isEmpty()) {
                    val hintScale = 0.58f
                    val hint = EpsilonTranslations.Gui.FRIEND_EMPTY.getTranslatedName()
                    val hintWidth = metrics.textWidth(hint, hintScale)
                    val hintX = (listViewport.width - hintWidth) / 2.0f
                    val hintY = state.getFriendScroll() + listViewport.height / 2.0f -
                            metrics.textHeight(hintScale) / 2.0f
                    Text(hint, hintX, hintY, hintScale, MD3Theme.TEXT_MUTED)
                }
            }
        }

    }

    override fun flushContent() {
        contentBuffer.flush()
    }

    private fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
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

            return true
        }

        return false
    }

    private fun mouseReleased(event: MouseButtonEvent): Boolean {
        return scrollBarDrag.mouseReleased()
    }

    private fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        val currentBounds = bounds
        if (!scrollBarDrag.isDragging() || currentBounds == null) {
            return false
        }
        val listViewport = getListViewport(currentBounds)
        val newScroll = scrollBarDrag.mouseDragged(event.y(), listViewport, state.getMaxFriendScroll())
        if (newScroll >= 0.0f) {
            state.setFriendScroll(newScroll)
        }

        return true
    }

    private fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val currentBounds = bounds ?: return false
        val listViewport = getListViewport(currentBounds)
        if (listViewport.contains(mouseX, mouseY)) {
            scrollVelocity -= scrollY.toFloat() * 24.0f

            return true
        }
        return false
    }

    private fun keyPressed(event: KeyEvent): Boolean {
        return inputField.isFocused() && when (event.key()) {
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                addFriendFromInput()
                true
            }

            InputConstants.KEY_ESCAPE -> {
                inputField.blur()

                true
            }

            else -> {
                inputField.keyPressed(event)
            }
        }
    }

    private fun charTyped(event: CharacterEvent): Boolean {
        return inputField.charTyped(event)
    }

    override fun onDeactivated() {
        scrollBarDrag.reset()
        scrollVelocity = 0.0f
        inputField.blur()

    }

    private fun addFriendFromInput() {
        val name = inputField.getText().trim()
        if (!name.isEmpty() && !FriendManager.isFriend(name)) {
            FriendManager.addFriend(name)
        }
        inputField.clear()

    }

    private fun UiScope.buildFriendRow(
        name: String, bounds: UiRect, removeBounds: UiRect,
        hoverProgress: Float, removeHoverProgress: Float,
    ) {
        RowSurface(hoverProgress)

        val avatarSize = 20.0f
        val avatarX = MD3Theme.ROW_CONTENT_INSET + 2.0f
        val avatarY = (bounds.height - avatarSize) / 2.0f
        RoundRect(avatarX, avatarY, avatarSize, avatarSize, avatarSize / 2.0f, MD3Theme.SECONDARY_CONTAINER)
        val initial = if (name.isEmpty()) "?" else name.substring(0, 1).uppercase()
        val initialScale = 0.54f
        val initialWidth = metrics.textWidth(initial, initialScale)
        val initialHeight = metrics.textHeight(initialScale)
        Text(
            initial,
            avatarX + (avatarSize - initialWidth) / 2.0f,
            avatarY + (avatarSize - initialHeight) / 2.0f,
            initialScale,
            MD3Theme.ON_SECONDARY_CONTAINER
        )

        val nameScale = 0.66f
        val nameX = avatarX + avatarSize + 8.0f
        val nameY = (bounds.height - metrics.textHeight(nameScale)) / 2.0f
        Text(name, nameX, nameY, nameScale, MD3Theme.TEXT_PRIMARY)

        IconButton(removeBounds.relativeTo(bounds), "✕", 0.50f, MD3Theme.ERROR, removeHoverProgress)
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

    private fun createAnimation(): Animation {
        val animation = Animation(Easing.EASE_OUT_CUBIC, 120L)
        animation.startValue = 0.0f
        return animation
    }

    override fun close() {
        luminView.close()

        contentBuffer.close()

    }


    private companion object {

        private const val FRIEND_ROW_HEIGHT = 30.0f
        private const val FRIEND_INPUT_HEIGHT = 28.0f
        private const val FRIEND_INPUT_BOTTOM_MARGIN = 4.0f
        private const val FRIEND_INPUT_FIELD_SCALE = 0.8f
        private const val MAX_FRIEND_NAME_LENGTH = 32

    }

}
