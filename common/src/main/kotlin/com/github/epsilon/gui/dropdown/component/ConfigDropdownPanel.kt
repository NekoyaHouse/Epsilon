package com.github.epsilon.gui.dropdown.component

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.gui.dropdown.DropdownTheme
import com.github.epsilon.gui.dropdown.widget.DropdownTextField
import com.github.epsilon.gui.lib.UiTextMetrics
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.ConfigManager
import com.github.epsilon.utils.client.ConfigFolderOpener
import com.mojang.blaze3d.platform.InputConstants
import java.util.Objects

class ConfigDropdownPanel(panelIndex: Int) : AbstractDropdownPanel("config", EpsilonTranslations.Gui.TAB_CONFIG, "", panelIndex) {

    private val inputField: DropdownTextField = DropdownTextField(160)
    private var status = ""
    private var cachedConfigFrameId = Int.MIN_VALUE
    private var cachedConfigs: List<String> = emptyList()

    override fun computeContentHeight(): Float {
        val configCount = configsForFrame().size
        return PADDING * 2.0f + FIELD_HEIGHT + GAP + BUTTON_HEIGHT * 3.0f + GAP * 3.0f +
                Math.max(ROW_HEIGHT, configCount * (ROW_HEIGHT + GAP)) +
                (if (status.isEmpty()) 0.0f else ROW_HEIGHT)
    }

    override fun drawPanelContent(
        scope: UiTree.Scope,
        textMetrics: UiTextMetrics,
        mouseX: Int,
        mouseY: Int,
        visibleHeight: Float,
    ) {
        var currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll
        val contentX = x + PADDING
        val contentW = width - PADDING * 2.0f

        val placeholder = ConfigManager.INSTANCE.activeConfigName
        if (!inputField.isFocused && inputField.text.isEmpty()) {
            inputField.text = placeholder
        }
        inputField.draw(
            scope, textMetrics, contentX, currentY, contentW, FIELD_HEIGHT, mouseX, mouseY,
            placeholder, DropdownTheme.SETTING_TEXT_SCALE
        )
        currentY += FIELD_HEIGHT + GAP

        val actions = arrayOf(
            EpsilonTranslations.Gui.CONFIG_ACTION_SAVE_AS.getTranslatedName(),
            EpsilonTranslations.Gui.CONFIG_ACTION_RELOAD.getTranslatedName(),
            EpsilonTranslations.Gui.CONFIG_ACTION_EXPORT.getTranslatedName(),
            EpsilonTranslations.Gui.CONFIG_ACTION_IMPORT.getTranslatedName(),
            EpsilonTranslations.Gui.CONFIG_ACTION_NEW.getTranslatedName(),
            EpsilonTranslations.Gui.CONFIG_ACTION_OPEN_FOLDER.getTranslatedName()
        )
        for (row in 0 until 3) {
            for (col in 0 until 2) {
                val index = row * 2 + col
                val btnW = (contentW - GAP) * 0.5f
                val btnX = contentX + col * (btnW + GAP)
                val btnY = currentY + row * (BUTTON_HEIGHT + GAP)
                val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), btnX, btnY, btnW, BUTTON_HEIGHT)
                scope.roundRect(
                    btnX, btnY, btnW, BUTTON_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                    if (hovered) MD3Theme.PRIMARY_CONTAINER else MD3Theme.SURFACE_CONTAINER_HIGH
                )
                val labelScale = 0.48f
                val labelW = textMetrics.textWidth(actions[index], labelScale)
                scope.text(
                    actions[index], btnX + (btnW - labelW) * 0.5f,
                    getCenteredTextY(textMetrics, btnY, BUTTON_HEIGHT, labelScale), labelScale, MD3Theme.TEXT_PRIMARY
                )
            }
        }
        currentY += BUTTON_HEIGHT * 3.0f + GAP * 3.0f

        if (status.isNotEmpty()) {
            val statusScale = 0.50f
            scope.text(
                trimToWidth(status, statusScale, contentW, textMetrics), contentX,
                getCenteredTextY(textMetrics, currentY, ROW_HEIGHT, statusScale), statusScale, MD3Theme.TEXT_MUTED
            )
            currentY += ROW_HEIGHT
        }

        val active = ConfigManager.INSTANCE.activeConfigName
        val configs = configsForFrame()
        if (configs.isEmpty()) {
            val emptyScale = 0.55f
            scope.text(
                EpsilonTranslations.Gui.CONFIG_EMPTY.getTranslatedName(), contentX,
                getCenteredTextY(textMetrics, currentY, ROW_HEIGHT, emptyScale), emptyScale, MD3Theme.TEXT_MUTED
            )
            return
        }
        for (name in configs) {
            val activeRow = name == active
            val hovered = isHovered(mouseX.toDouble(), mouseY.toDouble(), contentX, currentY, contentW, ROW_HEIGHT)
            scope.roundRect(
                contentX, currentY, contentW, ROW_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                if (activeRow) {
                    MD3Theme.PRIMARY_CONTAINER
                } else if (hovered) {
                    MD3Theme.SURFACE_CONTAINER_HIGH
                } else {
                    MD3Theme.SURFACE_CONTAINER_LOW
                }
            )
            val nameScale = 0.56f
            scope.text(
                trimToWidth(name, nameScale, contentW - 28.0f, textMetrics), contentX + 6.0f,
                getCenteredTextY(textMetrics, currentY, ROW_HEIGHT, nameScale), nameScale,
                if (activeRow) MD3Theme.ON_PRIMARY_CONTAINER else MD3Theme.TEXT_PRIMARY
            )
            val deleteX = contentX + contentW - 18.0f
            val deleteScale = 0.52f
            scope.text(
                "x", deleteX + 5.0f, getCenteredTextY(textMetrics, currentY + 3.0f, 16.0f, deleteScale), deleteScale,
                if (isHovered(mouseX.toDouble(), mouseY.toDouble(), deleteX, currentY + 3.0f, 16.0f, 16.0f)) {
                    MD3Theme.ERROR
                } else {
                    MD3Theme.TEXT_MUTED
                }
            )
            currentY += ROW_HEIGHT + GAP
        }
    }

    override fun mouseClickedContent(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false
        var currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll
        val contentX = x + PADDING
        val contentW = width - PADDING * 2.0f
        if (inputField.focusIfContains(mouseX, mouseY, contentX, currentY, contentW, FIELD_HEIGHT)) {
            return true
        }
        inputField.blur()
        currentY += FIELD_HEIGHT + GAP

        for (row in 0 until 3) {
            for (col in 0 until 2) {
                val index = row * 2 + col
                val btnW = (contentW - GAP) * 0.5f
                val btnX = contentX + col * (btnW + GAP)
                val btnY = currentY + row * (BUTTON_HEIGHT + GAP)
                if (isHovered(mouseX, mouseY, btnX, btnY, btnW, BUTTON_HEIGHT)) {
                    runAction(index)
                    return true
                }
            }
        }
        currentY += BUTTON_HEIGHT * 3.0f + GAP * 3.0f
        if (status.isNotEmpty()) currentY += ROW_HEIGHT

        for (name in configsForFrame()) {
            val deleteX = contentX + contentW - 18.0f
            if (isHovered(mouseX, mouseY, deleteX, currentY + 3.0f, 16.0f, 16.0f)) {
                try {
                    ConfigManager.INSTANCE.deleteConfig(name)
                    status = EpsilonTranslations.Gui.DROPDOWN_STATUS_DELETED.getTranslatedName() + " " + name
                } catch (e: Exception) {
                    status = errorText(e)
                }
                return true
            }
            if (isHovered(mouseX, mouseY, contentX, currentY, contentW, ROW_HEIGHT)) {
                try {
                    ConfigManager.INSTANCE.switchConfig(name)
                    inputField.text = name
                    inputField.setCursorToEnd()
                    status = EpsilonTranslations.Gui.DROPDOWN_STATUS_SWITCHED.getTranslatedName() + " " + name
                } catch (e: Exception) {
                    status = errorText(e)
                }
                return true
            }
            currentY += ROW_HEIGHT + GAP
        }
        return false
    }

    override fun onGlobalMouseClicked(mouseX: Double, mouseY: Double, button: Int) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return
        val fieldX = x + PADDING
        val fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll
        val fieldW = width - PADDING * 2.0f
        if (!isHovered(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_HEIGHT)) {
            inputField.blur()
        }
    }

    private fun runAction(index: Int) {
        val value = inputField.text.trim()
        try {
            when (index) {
                0 -> {
                    if (value.isNotEmpty()) {
                        val saved = ConfigManager.INSTANCE.saveAsConfig(value)
                        inputField.text = saved
                        inputField.setCursorToEnd()
                        status = EpsilonTranslations.Gui.DROPDOWN_STATUS_SAVED.getTranslatedName() + " " + saved
                    }
                }

                1 -> {
                    ConfigManager.INSTANCE.reloadOrThrow()
                    status = EpsilonTranslations.Gui.DROPDOWN_STATUS_RELOADED.getTranslatedName()
                }

                2 -> {
                    status = EpsilonTranslations.Gui.DROPDOWN_STATUS_EXPORTED.getTranslatedName() + " " +
                            ConfigManager.INSTANCE.exportActiveConfigToZip(value).fileName
                }

                3 -> {
                    if (value.isNotEmpty()) {
                        val imported = ConfigManager.INSTANCE.importConfigFromZip(value)
                        inputField.text = imported
                        inputField.setCursorToEnd()
                        status = EpsilonTranslations.Gui.DROPDOWN_STATUS_IMPORTED.getTranslatedName() + " " + imported
                    }
                }

                4 -> {
                    if (value.isNotEmpty()) {
                        val created = ConfigManager.INSTANCE.newDefaultConfig(value)
                        inputField.text = created
                        inputField.setCursorToEnd()
                        status = EpsilonTranslations.Gui.DROPDOWN_STATUS_CREATED.getTranslatedName() + " " + created
                    }
                }

                5 -> status = EpsilonTranslations.Gui.CONFIG_ACTION_OPEN_FOLDER.getTranslatedName() + " " +
                        ConfigFolderOpener.openConfigFolder()

                else -> {
                }
            }
        } catch (e: Exception) {
            status = errorText(e)
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!inputField.isFocused) return false
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

    private fun errorText(e: Exception): String {
        val message = e.message
        return EpsilonTranslations.Gui.CONFIG_ERROR_TITLE.getTranslatedName() + ": " +
                if (message == null || message.isBlank()) e.javaClass.simpleName else message
    }

    private fun getCenteredTextY(textMetrics: UiTextMetrics, boxY: Float, boxH: Float, scale: Float): Float {
        return boxY + (boxH - textMetrics.textHeight(scale)) / 2.0f
    }

    private fun configsForFrame(): List<String> {
        val frameId = getRenderFrameId()
        if (cachedConfigFrameId != frameId) {
            cachedConfigs = ConfigManager.INSTANCE.listConfigs()
            cachedConfigFrameId = frameId
        }
        return cachedConfigs
    }

    private companion object {

        private const val FIELD_HEIGHT = 18.0f
        private const val BUTTON_HEIGHT = 17.0f
        private const val ROW_HEIGHT = 24.0f
        private const val GAP = 4.0f
        private const val PADDING = 6.0f

    }

}
