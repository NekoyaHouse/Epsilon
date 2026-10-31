package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.widget.DropdownTextField
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.FriendManager
import com.mojang.blaze3d.platform.InputConstants

class FriendDropdownPanel(panelIndex: Int) :
    AbstractDropdownPanel("friend", EpsilonTranslations.Gui.TAB_FRIEND, "", panelIndex) {

    private val inputField: DropdownTextField = DropdownTextField(32)

    override fun computeContentHeight(): Float {
        val friendCount = FriendManager.getFriends().size
        return PADDING * 2.0f + FIELD_HEIGHT + GAP + maxOf(ROW_HEIGHT, friendCount * (ROW_HEIGHT + GAP))
    }

    override fun drawPanelContent(
        scope: UiTree.Scope,
        textMetrics: UiTextMetrics,
        mouseX: Int,
        mouseY: Int,
        visibleHeight: Float,
    ) {
        val fieldX = x + PADDING
        val fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll
        val fieldW = width - PADDING * 2.0f - 24.0f
        inputField.draw(
            scope, textMetrics, fieldX, fieldY, fieldW, FIELD_HEIGHT, mouseX, mouseY,
            EpsilonTranslations.Gui.FRIEND_INPUT_PLACEHOLDER.getTranslatedName(), DropdownTheme.SETTING_TEXT_SCALE
        )

        val addX = fieldX + fieldW + GAP
        scope.roundRect(
            addX, fieldY, ADD_BUTTON_WIDTH, FIELD_HEIGHT, DropdownTheme.BUTTON_RADIUS,
            if (isHovered(mouseX.toDouble(), mouseY.toDouble(), addX, fieldY, ADD_BUTTON_WIDTH, FIELD_HEIGHT)) {
                MD3Theme.SURFACE_CONTAINER_HIGH
            } else {
                MD3Theme.SURFACE_CONTAINER_LOW
            }
        )
        val plusScale = 1.0f
        val addTextX = addX + (ADD_BUTTON_WIDTH - textMetrics.textWidth("+", plusScale)) / 2.0f
        val addTextY = fieldY + (FIELD_HEIGHT - textMetrics.textHeight(plusScale)) / 2.0f
        scope.text("+", addTextX, addTextY, plusScale, MD3Theme.ON_PRIMARY_CONTAINER)

        val friends = FriendManager.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
        var rowY = fieldY + FIELD_HEIGHT + GAP
        if (friends.isEmpty()) {
            val emptyText = EpsilonTranslations.Gui.FRIEND_EMPTY.getTranslatedName()
            val emptyScale = 0.7f
            val emptyTextX =
                x + PADDING + (width - PADDING * 2.0f - textMetrics.textWidth(emptyText, emptyScale)) / 2.0f
            val emptyTextY = rowY + (ROW_HEIGHT - textMetrics.textHeight(emptyScale)) / 2.0f
            scope.text(emptyText, emptyTextX, emptyTextY, emptyScale, MD3Theme.TEXT_MUTED)
            return
        }
        for (name in friends) {
            val rowX = x + PADDING
            val rowWidth = width - PADDING * 2.0f
            val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), rowX, rowY, rowWidth, ROW_HEIGHT)
            scope.roundRect(
                rowX, rowY, rowWidth, ROW_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                if (hovered) MD3Theme.SURFACE_CONTAINER_HIGH else MD3Theme.SURFACE_CONTAINER_LOW
            )
            val removeX = x + width - PADDING - 18.0f
            val removeY = rowY + (ROW_HEIGHT - REMOVE_BUTTON_SIZE) * 0.5f
            val displayName = trimToWidth(name, DropdownTheme.SETTING_TEXT_SCALE, width - 38.0f, textMetrics)
            scope.text(
                displayName, x + PADDING + 6.0f,
                rowY + (ROW_HEIGHT - textMetrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) / 2.0f,
                DropdownTheme.SETTING_TEXT_SCALE, MD3Theme.TEXT_PRIMARY
            )
            val iconScale = 0.8f
            val removeTextX = removeX + (REMOVE_BUTTON_SIZE - textMetrics.textWidth(
                IconChars.CLOSE,
                iconScale,
                StaticFontLoader.ICONS
            )) / 2.0f
            val removeTextY =
                removeY + (REMOVE_BUTTON_SIZE - textMetrics.textHeight(iconScale, StaticFontLoader.ICONS)) / 2.0f
            scope.text(
                IconChars.CLOSE, removeTextX, removeTextY, iconScale,
                if (isHovered(
                        mouseX.toDouble(),
                        mouseY.toDouble(),
                        removeX,
                        removeY,
                        REMOVE_BUTTON_SIZE,
                        REMOVE_BUTTON_SIZE
                    )
                ) {
                    MD3Theme.ERROR
                } else {
                    MD3Theme.TEXT_MUTED
                },
                StaticFontLoader.ICONS
            )
            rowY += ROW_HEIGHT + GAP
        }
    }

    override fun mouseClickedContent(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false
        val fieldX = x + PADDING
        val fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll
        val fieldW = width - PADDING * 2.0f - 24.0f
        if (inputField.focusIfContains(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_HEIGHT)) {
            return true
        }
        if (isHovered(mouseX, mouseY, fieldX + fieldW + GAP, fieldY, ADD_BUTTON_WIDTH, FIELD_HEIGHT)) {
            addFriend()
            return true
        }
        inputField.blur()

        var rowY = fieldY + FIELD_HEIGHT + GAP
        for (name in FriendManager.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            val removeX = x + width - PADDING - 18.0f
            val removeY = rowY + (ROW_HEIGHT - REMOVE_BUTTON_SIZE) * 0.5f
            if (isHovered(mouseX, mouseY, removeX, removeY, REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE)) {
                FriendManager.removeFriend(name)
                return true
            }
            rowY += ROW_HEIGHT + GAP
        }
        return false
    }

    override fun onGlobalMouseClicked(mouseX: Double, mouseY: Double, button: Int) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return
        val fieldX = x + PADDING
        val fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll
        val fieldW = width - PADDING * 2.0f - 24.0f
        if (!isHovered(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_HEIGHT)) {
            inputField.blur()
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!inputField.isFocused) return false
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            addFriend()
            return true
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            inputField.blur()
            return true
        }
        return inputField.keyPressed(keyCode)
    }

    override fun charTyped(typedText: String): Boolean {
        return inputField.charTyped(typedText)
    }

    override fun hasActiveInput(): Boolean {
        return inputField.isFocused
    }

    private fun addFriend() {
        val name = inputField.text.trim()
        if (name.isNotEmpty() && !FriendManager.isFriend(name)) {
            FriendManager.addFriend(name)
        }
        inputField.clear()
    }

    private companion object {

        private const val ROW_HEIGHT = 20.0f
        private const val FIELD_HEIGHT = 18.0f
        private const val GAP = 4.0f
        private const val PADDING = 6.0f
        private const val ADD_BUTTON_WIDTH = 20.0f
        private const val REMOVE_BUTTON_SIZE = 16.0f

    }

}
