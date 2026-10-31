package com.github.epsilon.gui.troll

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.widget.DropdownTextField
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.managers.FriendManager
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color

class TrollFriendWindow : TrollWindow() {

    private val inputField: DropdownTextField = DropdownTextField(MAX_NAME_LENGTH)
    private val rows: MutableList<FriendRow> = ArrayList()

    private var inputBounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var addBounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var listViewport: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)

    override fun layout(metrics: TextRenderer, screenWidth: Float, screenHeight: Float) {
        val friends = sortedFriends()
        headerHeight = TrollTheme.headerHeight(metrics)
        var width = maxOf(190.0f, metrics.getWidth(title(), TrollTheme.TEXT_SCALE) + 30.0f)
        for (friend in friends) {
            width = maxOf(width, metrics.getWidth(friend, TrollTheme.TEXT_SCALE) + 72.0f)
        }
        width = minOf(minOf(width, 280.0f), screenWidth)

        val fixedHeight = headerHeight + PADDING + FIELD_HEIGHT + GAP + PADDING
        val listHeight = maxOf(ROW_HEIGHT, friends.size * (ROW_HEIGHT + GAP))
        val height = minOf(minOf(fixedHeight + listHeight, 220.0f), screenHeight)
        size(width, height, screenWidth, screenHeight)

        val contentWidth = maxOf(1.0f, bounds.width - PADDING * 2.0f)
        inputBounds = UiRect(
            bounds.x + PADDING,
            bounds.y + headerHeight + PADDING,
            maxOf(1.0f, contentWidth - ADD_BUTTON_WIDTH - GAP),
            FIELD_HEIGHT
        )
        addBounds = UiRect(inputBounds.right() + GAP, inputBounds.y, ADD_BUTTON_WIDTH, FIELD_HEIGHT)
        val listTop = inputBounds.bottom() + GAP
        listViewport = UiRect(
            bounds.x + PADDING,
            listTop,
            contentWidth,
            maxOf(1.0f, bounds.bottom() - PADDING - listTop)
        )

        val contentHeight = friends.size * (ROW_HEIGHT + GAP)
        scrollPosition = scroll.update(maxOf(0.0f, contentHeight - listViewport.height))

        rows.clear()
        var rowY = listViewport.y - scrollPosition
        for (friend in friends) {
            val rowBounds = UiRect(listViewport.x, rowY, listViewport.width, ROW_HEIGHT)
            rows.add(FriendRow(friend, rowBounds, removeBounds(rowBounds)))
            rowY += ROW_HEIGHT + GAP
        }
    }

    override fun draw(scope: UiTree.Scope, metrics: TextRenderer, mouseX: Int, mouseY: Int) {
        TrollTheme.window(scope, bounds, headerHeight, title(), metrics)
        inputField.draw(
            scope, TrollTheme.metrics(metrics), inputBounds.x, inputBounds.y,
            inputBounds.width, inputBounds.height, mouseX, mouseY,
            EpsilonTranslations.Gui.FRIEND_INPUT_PLACEHOLDER.getTranslatedName(), TrollTheme.TEXT_SCALE, true
        )

        val addHovered = addBounds.contains(mouseX.toDouble(), mouseY.toDouble())
        scope.rect(
            1, addBounds.x, addBounds.y, addBounds.width, addBounds.height,
            TrollTheme.alpha(TrollTheme.primary(), if (addHovered) 190 else 70)
        )
        scope.textWithShadow(
            3, "+", addBounds.centerX() - metrics.getWidth("+", TrollTheme.TEXT_SCALE) / 2.0f,
            addBounds.y + (addBounds.height - metrics.getHeight(TrollTheme.TEXT_SCALE)) / 2.0f,
            TrollTheme.TEXT_SCALE, TrollTheme.text()
        )

        scope.scissor(listViewport) { content ->
            if (rows.isEmpty()) {
                val empty = EpsilonTranslations.Gui.FRIEND_EMPTY.getTranslatedName()
                content.textWithShadow(
                    2, empty,
                    listViewport.centerX() - metrics.getWidth(empty, TrollTheme.VALUE_SCALE) / 2.0f,
                    listViewport.y + (listViewport.height - metrics.getHeight(TrollTheme.VALUE_SCALE)) / 2.0f,
                    TrollTheme.VALUE_SCALE, TrollTheme.text()
                )
                return@scissor
            }
            for (row in rows) {
                val hovered = row.bounds.contains(mouseX.toDouble(), mouseY.toDouble())
                content.rect(
                    1, row.bounds.x, row.bounds.y, row.bounds.width, row.bounds.height,
                    Color(255, 255, 255, if (hovered) 30 else 16)
                )
                content.textWithShadow(
                    2,
                    TrollTheme.trim(
                        row.name,
                        metrics,
                        TrollTheme.TEXT_SCALE,
                        row.bounds.width - REMOVE_BUTTON_SIZE - 12.0f
                    ),
                    row.bounds.x + 5.0f,
                    row.bounds.y + (row.bounds.height - metrics.getHeight(TrollTheme.TEXT_SCALE)) / 2.0f,
                    TrollTheme.TEXT_SCALE,
                    TrollTheme.text()
                )

                val removeHovered = row.removeBounds.contains(mouseX.toDouble(), mouseY.toDouble())
                content.rect(
                    3, row.removeBounds.x, row.removeBounds.y,
                    row.removeBounds.width, row.removeBounds.height,
                    TrollTheme.alpha(TrollTheme.primary(), if (removeHovered) 180 else 40)
                )
                val iconScale = TrollTheme.VALUE_SCALE
                content.textWithShadow(
                    4, IconChars.CLOSE,
                    row.removeBounds.centerX() - metrics.getWidth(
                        IconChars.CLOSE,
                        iconScale,
                        StaticFontLoader.ICONS
                    ) / 2.0f,
                    row.removeBounds.y + (row.removeBounds.height - metrics.getHeight(
                        iconScale,
                        StaticFontLoader.ICONS
                    )) / 2.0f,
                    iconScale, TrollTheme.text(), StaticFontLoader.ICONS
                )
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent): Boolean {
        if (super.mouseClicked(event)) return true
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return bounds.contains(event.x(), event.y())

        if (inputField.focusIfContains(
                event.x(), event.y(), inputBounds.x, inputBounds.y,
                inputBounds.width, inputBounds.height
            )
        ) {
            return true
        }
        inputField.blur()

        if (addBounds.contains(event.x(), event.y())) {
            addFriend()
            return true
        }
        if (listViewport.contains(event.x(), event.y())) {
            for (row in rows) {
                if (row.removeBounds.contains(event.x(), event.y())) {
                    FriendManager.removeFriend(row.name)
                    return true
                }
            }
        }
        return bounds.contains(event.x(), event.y())
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (!listViewport.contains(mouseX, mouseY)) return false
        scroll.scroll(amount)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        return inputField.isFocused && when (event.key()) {
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                addFriend()
                true
            }

            InputConstants.KEY_ESCAPE -> {
                inputField.blur()
                true
            }

            else -> inputField.keyPressed(event)
        }
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        return inputField.charTyped(event)
    }

    override fun blurInput() {
        inputField.blur()
    }

    private fun addFriend() {
        val name = inputField.text.trim()
        if (name.isNotEmpty() && !FriendManager.isFriend(name)) {
            FriendManager.addFriend(name)
        }
        inputField.clear()
    }

    private fun title(): String {
        return EpsilonTranslations.Gui.TAB_FRIEND.getTranslatedName()
    }

    private fun sortedFriends(): List<String> {
        return FriendManager.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
    }

    private fun removeBounds(rowBounds: UiRect): UiRect {
        return UiRect(
            rowBounds.right() - REMOVE_BUTTON_SIZE - 3.0f,
            rowBounds.y + (rowBounds.height - REMOVE_BUTTON_SIZE) / 2.0f,
            REMOVE_BUTTON_SIZE,
            REMOVE_BUTTON_SIZE
        )
    }

    private data class FriendRow(val name: String, val bounds: UiRect, val removeBounds: UiRect)

    companion object {
        private const val PADDING = 6.0f
        private const val FIELD_HEIGHT = 18.0f
        private const val GAP = 4.0f
        private const val ROW_HEIGHT = 20.0f
        private const val ADD_BUTTON_WIDTH = 20.0f
        private const val REMOVE_BUTTON_SIZE = 16.0f
        private const val MAX_NAME_LENGTH = 32
    }

}
