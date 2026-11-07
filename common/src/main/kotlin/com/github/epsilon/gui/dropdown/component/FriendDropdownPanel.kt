package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.gui.lib.Interaction
import com.github.epsilon.gui.lib.InputRegion
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.widget.DropdownTextField
import com.github.epsilon.gui.lib.LuminUIItem
import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiScope
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

    override fun UiScope.drawPanelContent(
        mouseX: Int,
        mouseY: Int,
        visibleHeight: Float,
    ) {
        val top = contentLocalTop(origin)
        val fieldX = PADDING
        val fieldY = top + PADDING - scroll
        val fieldW = width - PADDING * 2.0f - 24.0f
        InputRegion(inputField, UiRect(fieldX, fieldY, fieldW, FIELD_HEIGHT),
            onPress = { _, _ -> inputField.focus(); true },
            onKey = { keyPressed(it.key(), it.keycode(), it.modifiers()) },
            onText = { charTyped(it.codepointAsString()) }, onBlur = inputField::blur)
        inputField.draw(
            this, fieldX, fieldY, fieldW, FIELD_HEIGHT, mouseX, mouseY,
            EpsilonTranslations.Gui.FRIEND_INPUT_PLACEHOLDER.getTranslatedName(), DropdownTheme.SETTING_TEXT_SCALE
        )

        val addX = fieldX + fieldW + GAP
        val addHovered = isHoveredAt(this, mouseX.toDouble(), mouseY.toDouble(), addX, fieldY, ADD_BUTTON_WIDTH, FIELD_HEIGHT)
        LuminUIItem(ADD_BUTTON_KEY) {
            Interaction("add", UiRect(addX, fieldY, ADD_BUTTON_WIDTH, FIELD_HEIGHT)) { onClick { addFriend(); true } }
            RoundRect(
                addX, fieldY, ADD_BUTTON_WIDTH, FIELD_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                if (addHovered) MD3Theme.SURFACE_CONTAINER_HIGH else MD3Theme.SURFACE_CONTAINER_LOW
            )
            val plusScale = 1.0f
            val addTextX = addX + (ADD_BUTTON_WIDTH - metrics.textWidth("+", plusScale)) / 2.0f
            val addTextY = fieldY + (FIELD_HEIGHT - metrics.textHeight(plusScale)) / 2.0f
            Text("+", addTextX, addTextY, plusScale, MD3Theme.ON_PRIMARY_CONTAINER)
        }

        val friends = FriendManager.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
        var rowY = fieldY + FIELD_HEIGHT + GAP
        if (friends.isEmpty()) {
            val emptyText = EpsilonTranslations.Gui.FRIEND_EMPTY.getTranslatedName()
            val emptyScale = 0.7f
            val emptyTextX =
                PADDING + (width - PADDING * 2.0f - metrics.textWidth(emptyText, emptyScale)) / 2.0f
            val emptyTextY = rowY + (ROW_HEIGHT - metrics.textHeight(emptyScale)) / 2.0f
            Text(emptyText, emptyTextX, emptyTextY, emptyScale, MD3Theme.TEXT_MUTED)
            return
        }
        for (name in friends) {
            val currentRowY = rowY
            val rowX = PADDING
            val rowWidth = width - PADDING * 2.0f
            val hovered = isHoveredAt(this, mouseX.toDouble(), mouseY.toDouble(), rowX, currentRowY, rowWidth, ROW_HEIGHT)
            val removeX = width - PADDING - 18.0f
            val removeY = currentRowY + (ROW_HEIGHT - REMOVE_BUTTON_SIZE) * 0.5f
            val removeHovered = isHoveredAt(
                this, mouseX.toDouble(), mouseY.toDouble(), removeX, removeY, REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE
            )
            // 逐项缓存：好友行位置、悬停状态或翻译变化时才重新捕获节点。
            LuminUIItem("friend-row:$name") {
                Interaction("remove", UiRect(removeX, removeY, REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE)) {
                    onClick { FriendManager.removeFriend(name); true }
                }
                RoundRect(
                    rowX, currentRowY, rowWidth, ROW_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                    if (hovered) MD3Theme.SURFACE_CONTAINER_HIGH else MD3Theme.SURFACE_CONTAINER_LOW
                )
                val displayName = trimToWidth(name, DropdownTheme.SETTING_TEXT_SCALE, width - 38.0f, metrics)
                Text(
                    displayName, PADDING + 6.0f,
                    currentRowY + (ROW_HEIGHT - metrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) / 2.0f,
                    DropdownTheme.SETTING_TEXT_SCALE, MD3Theme.TEXT_PRIMARY
                )
                val iconScale = 0.8f
                val removeTextX = removeX + (REMOVE_BUTTON_SIZE - metrics.textWidth(
                    IconChars.CLOSE,
                    iconScale,
                    StaticFontLoader.ICONS
                )) / 2.0f
                val removeTextY =
                    removeY + (REMOVE_BUTTON_SIZE - metrics.textHeight(iconScale, StaticFontLoader.ICONS)) / 2.0f
                Text(
                    IconChars.CLOSE, removeTextX, removeTextY, iconScale,
                    if (removeHovered) MD3Theme.ERROR else MD3Theme.TEXT_MUTED,
                    StaticFontLoader.ICONS
                )
            }
            rowY += ROW_HEIGHT + GAP
        }
    }


    private fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
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

    private fun charTyped(typedText: String): Boolean {
        return inputField.charTyped(typedText)
    }


    private fun addFriend() {
        val name = inputField.text.trim()
        if (name.isNotEmpty() && !FriendManager.isFriend(name)) {
            FriendManager.addFriend(name)
        }
        inputField.clear()
    }

    private companion object {

        private const val ADD_BUTTON_KEY = "friend-add"

        private const val ROW_HEIGHT = 20.0f
        private const val FIELD_HEIGHT = 18.0f
        private const val GAP = 4.0f
        private const val PADDING = 6.0f
        private const val ADD_BUTTON_WIDTH = 20.0f
        private const val REMOVE_BUTTON_SIZE = 16.0f

    }

}
