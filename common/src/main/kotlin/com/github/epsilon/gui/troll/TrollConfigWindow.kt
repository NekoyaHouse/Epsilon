package com.github.epsilon.gui.troll

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.text.IconChars
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.gui.dropdown.widget.DropdownTextField
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.panel.popup.ConfirmActionPopup
import com.github.epsilon.gui.panel.popup.MessagePopup
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.managers.ConfigManager
import com.github.epsilon.utils.client.ConfigFolderOpener
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import java.nio.file.Path
import java.util.function.Supplier

class TrollConfigWindow(private val popupHost: PanelPopupHost) : TrollWindow() {

    private val inputField: DropdownTextField = DropdownTextField(MAX_INPUT_LENGTH)
    private val actionButtons: MutableList<ActionButton> = ArrayList()
    private val rows: MutableList<ConfigRow> = ArrayList()

    private var inputBounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var listViewport: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var statusBounds: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var metrics: TextRenderer? = null
    private var status: String = ""

    override fun layout(metrics: TextRenderer, screenWidth: Float, screenHeight: Float) {
        this.metrics = metrics
        val configs = ConfigManager.listConfigs()
        val activeConfig = ConfigManager.activeConfigName
        if (!inputField.isFocused && inputField.text.isBlank()) {
            inputField.text = activeConfig
            inputField.setCursorToEnd()
        }

        headerHeight = TrollTheme.headerHeight(metrics)
        var width = maxOf(240.0f, metrics.getWidth(title(), TrollTheme.TEXT_SCALE) + 30.0f)
        for (type in ActionButtonType.values()) {
            width = maxOf(width, metrics.getWidth(type.label(), TrollTheme.VALUE_SCALE) * 2.0f + 32.0f)
        }
        for (config in configs) {
            width = maxOf(width, metrics.getWidth(config, TrollTheme.TEXT_SCALE) + 74.0f)
        }
        width = minOf(minOf(width, 340.0f), screenWidth)

        val contentTop = bounds.y + headerHeight + PADDING
        var actionTop = contentTop + FIELD_HEIGHT + GAP
        val actionHeight = BUTTON_HEIGHT * 3.0f + GAP * 2.0f
        var statusTop = actionTop + actionHeight + GAP
        val statusHeight = if (status.isBlank()) 0.0f else ROW_HEIGHT
        var listTop = statusTop + statusHeight + GAP
        val fixedHeight = listTop - bounds.y
        val listHeight = maxOf(ROW_HEIGHT, configs.size * (ROW_HEIGHT + GAP))
        val height = minOf(minOf(fixedHeight + listHeight + PADDING, 300.0f), screenHeight)
        size(width, height, screenWidth, screenHeight)

        val contentWidth = maxOf(1.0f, bounds.width - PADDING * 2.0f)
        inputBounds = UiRect(bounds.x + PADDING, bounds.y + headerHeight + PADDING, contentWidth, FIELD_HEIGHT)
        actionTop = inputBounds.bottom() + GAP
        val buttonWidth = maxOf(1.0f, (contentWidth - GAP) / 2.0f)
        actionButtons.clear()
        val types = ActionButtonType.values()
        for (i in types.indices) {
            val column = i % 2
            val row = i / 2
            actionButtons.add(
                ActionButton(
                    types[i],
                    UiRect(
                        bounds.x + PADDING + column * (buttonWidth + GAP),
                        actionTop + row * (BUTTON_HEIGHT + GAP),
                        buttonWidth,
                        BUTTON_HEIGHT
                    )
                )
            )
        }
        statusTop = actionTop + BUTTON_HEIGHT * 3.0f + GAP * 2.0f + GAP
        statusBounds = UiRect(bounds.x + PADDING, statusTop, contentWidth, ROW_HEIGHT)
        listTop = statusTop + (if (status.isBlank()) 0.0f else ROW_HEIGHT) + GAP
        listViewport = UiRect(
            bounds.x + PADDING,
            listTop,
            contentWidth,
            maxOf(1.0f, bounds.bottom() - PADDING - listTop)
        )

        val contentHeight = configs.size * (ROW_HEIGHT + GAP)
        scrollPosition = scroll.update(maxOf(0.0f, contentHeight - listViewport.height))

        rows.clear()
        var rowY = listViewport.y - scrollPosition
        for (config in configs) {
            val rowBounds = UiRect(listViewport.x, rowY, listViewport.width, ROW_HEIGHT)
            rows.add(ConfigRow(config, rowBounds, deleteBounds(rowBounds)))
            rowY += ROW_HEIGHT + GAP
        }
    }

    override fun draw(scope: UiTree.Scope, metrics: TextRenderer, mouseX: Int, mouseY: Int) {
        TrollTheme.window(scope, bounds, headerHeight, title(), metrics)
        inputField.draw(
            scope, TrollTheme.metrics(metrics), inputBounds.x, inputBounds.y,
            inputBounds.width, inputBounds.height, mouseX, mouseY,
            EpsilonTranslations.Gui.CONFIG_INPUT_PLACEHOLDER.getTranslatedName(), TrollTheme.TEXT_SCALE, true
        )

        for (button in actionButtons) {
            val hovered = button.bounds.contains(mouseX.toDouble(), mouseY.toDouble())
            scope.rect(
                1, button.bounds.x, button.bounds.y, button.bounds.width, button.bounds.height,
                TrollTheme.alpha(TrollTheme.primary(), if (hovered) 190 else 70)
            )
            val label =
                TrollTheme.trim(button.type.label(), metrics, TrollTheme.VALUE_SCALE, button.bounds.width - 8.0f)
            scope.textWithShadow(
                2, label,
                button.bounds.centerX() - metrics.getWidth(label, TrollTheme.VALUE_SCALE) / 2.0f,
                button.bounds.y + (button.bounds.height - metrics.getHeight(TrollTheme.VALUE_SCALE)) / 2.0f,
                TrollTheme.VALUE_SCALE, TrollTheme.text()
            )
        }

        if (status.isNotBlank()) {
            scope.textWithShadow(
                2,
                TrollTheme.trim(status, metrics, TrollTheme.VALUE_SCALE, statusBounds.width),
                statusBounds.x,
                statusBounds.y + (statusBounds.height - metrics.getHeight(TrollTheme.VALUE_SCALE)) / 2.0f,
                TrollTheme.VALUE_SCALE,
                TrollTheme.text()
            )
        }

        val activeConfig = ConfigManager.activeConfigName
        scope.scissor(listViewport) { content ->
            if (rows.isEmpty()) {
                val empty = EpsilonTranslations.Gui.CONFIG_EMPTY.getTranslatedName()
                content.textWithShadow(
                    2, empty,
                    listViewport.centerX() - metrics.getWidth(empty, TrollTheme.VALUE_SCALE) / 2.0f,
                    listViewport.y + (listViewport.height - metrics.getHeight(TrollTheme.VALUE_SCALE)) / 2.0f,
                    TrollTheme.VALUE_SCALE, TrollTheme.text()
                )
                return@scissor
            }
            for (row in rows) {
                val active = row.name == activeConfig
                val hovered = row.bounds.contains(mouseX.toDouble(), mouseY.toDouble())
                content.rect(
                    1, row.bounds.x, row.bounds.y, row.bounds.width, row.bounds.height,
                    if (active) {
                        TrollTheme.alpha(TrollTheme.primary(), 110)
                    } else {
                        Color(255, 255, 255, if (hovered) 30 else 16)
                    }
                )

                val nameScale = TrollTheme.VALUE_SCALE
                content.textWithShadow(
                    2, TrollTheme.trim(row.name, metrics, nameScale, row.bounds.width - DELETE_BUTTON_SIZE - 18.0f),
                    row.bounds.x + 5.0f,
                    row.bounds.y + 2.0f,
                    nameScale, TrollTheme.text()
                )

                val hint = if (active) {
                    EpsilonTranslations.Gui.CONFIG_CURRENT.getTranslatedName()
                } else {
                    EpsilonTranslations.Gui.CONFIG_SWITCH_HINT.getTranslatedName()
                }
                content.textWithShadow(
                    2,
                    TrollTheme.trim(
                        hint,
                        metrics,
                        TrollTheme.VALUE_SCALE * 0.82f,
                        row.bounds.width - DELETE_BUTTON_SIZE - 18.0f
                    ),
                    row.bounds.x + 5.0f,
                    row.bounds.bottom() - metrics.getHeight(TrollTheme.VALUE_SCALE * 0.82f) - 2.0f,
                    TrollTheme.VALUE_SCALE * 0.82f,
                    TrollTheme.alpha(TrollTheme.text(), 180)
                )

                val deleteHovered = row.deleteBounds.contains(mouseX.toDouble(), mouseY.toDouble())
                content.rect(
                    3, row.deleteBounds.x, row.deleteBounds.y,
                    row.deleteBounds.width, row.deleteBounds.height,
                    TrollTheme.alpha(TrollTheme.primary(), if (deleteHovered) 180 else 40)
                )
                val iconScale = TrollTheme.VALUE_SCALE
                content.textWithShadow(
                    4, IconChars.CLOSE,
                    row.deleteBounds.centerX() - metrics.getWidth(
                        IconChars.CLOSE,
                        iconScale,
                        StaticFontLoader.ICONS
                    ) / 2.0f,
                    row.deleteBounds.y + (row.deleteBounds.height - metrics.getHeight(
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

        for (button in actionButtons) {
            if (button.bounds.contains(event.x(), event.y())) {
                handleAction(button.type)
                return true
            }
        }
        if (!listViewport.contains(event.x(), event.y())) {
            return bounds.contains(event.x(), event.y())
        }
        for (row in rows) {
            if (row.deleteBounds.contains(event.x(), event.y())) {
                openDeleteConfirmation(row.name)
                return true
            }
            if (row.bounds.contains(event.x(), event.y())) {
                trySwitchConfig(row.name)
                return true
            }
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (!listViewport.contains(mouseX, mouseY)) return false
        scroll.scroll(amount)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!inputField.isFocused) return false
        if (event.isEscape) {
            inputField.blur()
            return true
        }
        return inputField.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        return inputField.charTyped(event)
    }

    override fun blurInput() {
        inputField.blur()
    }

    private fun handleAction(action: ActionButtonType) {
        when (action) {
            ActionButtonType.SAVE_AS -> trySaveAs()
            ActionButtonType.RELOAD -> tryReload()
            ActionButtonType.EXPORT -> tryExport()
            ActionButtonType.IMPORT -> tryImport()
            ActionButtonType.NEW -> tryNewConfig()
            ActionButtonType.OPEN_FOLDER -> tryOpenFolder()
        }
    }

    private fun trySaveAs() {
        val targetName = inputField.text.trim()
        if (targetName.isEmpty()) return
        try {
            val savedName = ConfigManager.saveAsConfig(targetName)
            inputField.text = savedName
            inputField.setCursorToEnd()
            scroll.stop()
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_SAVED.getTranslatedName() + " " + savedName
        } catch (exception: Exception) {
            Constants.LOGGER.error("Troll GUI 保存配置失败", exception)
            openErrorPopup(Supplier { EpsilonTranslations.Gui.CONFIG_ERROR_SAVE.getTranslatedName() }, exception)
        }
    }

    private fun tryNewConfig() {
        val targetName = inputField.text.trim()
        if (targetName.isEmpty()) return
        try {
            val newName = ConfigManager.newDefaultConfig(targetName)
            inputField.text = newName
            inputField.setCursorToEnd()
            scroll.stop()
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_CREATED.getTranslatedName() + " " + newName
        } catch (exception: Exception) {
            Constants.LOGGER.error("Troll GUI 新建配置失败", exception)
            openErrorPopup(Supplier { EpsilonTranslations.Gui.CONFIG_ERROR_SAVE.getTranslatedName() }, exception)
        }
    }

    private fun tryReload() {
        try {
            ConfigManager.reloadOrThrow()
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_RELOADED.getTranslatedName()
        } catch (exception: Exception) {
            Constants.LOGGER.error("Troll GUI 重载配置失败", exception)
            openErrorPopup(Supplier { EpsilonTranslations.Gui.CONFIG_ERROR_RELOAD.getTranslatedName() }, exception)
        }
    }

    private fun tryExport() {
        try {
            val exported = ConfigManager.exportActiveConfigToZip(inputField.text)
            val fileName = if (exported.fileName != null) exported.fileName.toString() else exported.toString()
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_EXPORTED.getTranslatedName() + " " + fileName
            openExportSuccessPopup(exported)
        } catch (exception: Exception) {
            Constants.LOGGER.error("Troll GUI 导出配置失败", exception)
            openErrorPopup(Supplier { EpsilonTranslations.Gui.CONFIG_ERROR_EXPORT.getTranslatedName() }, exception)
        }
    }

    private fun tryImport() {
        val zipPath = inputField.text.trim()
        if (zipPath.isEmpty()) return
        try {
            val importedName = ConfigManager.importConfigFromZip(zipPath)
            inputField.text = importedName
            inputField.setCursorToEnd()
            scroll.stop()
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_IMPORTED.getTranslatedName() + " " + importedName
        } catch (exception: Exception) {
            Constants.LOGGER.error("Troll GUI 导入配置失败", exception)
            openErrorPopup(Supplier { EpsilonTranslations.Gui.CONFIG_ERROR_IMPORT.getTranslatedName() }, exception)
        }
    }

    private fun tryOpenFolder() {
        try {
            val folder = ConfigFolderOpener.openConfigFolder()
            status = EpsilonTranslations.Gui.CONFIG_ACTION_OPEN_FOLDER.getTranslatedName() + " " + folder
        } catch (exception: Exception) {
            Constants.LOGGER.error("Troll GUI 打开配置文件夹失败", exception)
            openErrorPopup(Supplier { EpsilonTranslations.Gui.CONFIG_ERROR_OPEN_FOLDER.getTranslatedName() }, exception)
        }
    }

    private fun trySwitchConfig(configName: String) {
        if (configName == ConfigManager.activeConfigName) return
        try {
            ConfigManager.switchConfig(configName)
            inputField.text = configName
            inputField.setCursorToEnd()
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_SWITCHED.getTranslatedName() + " " + configName
        } catch (exception: Exception) {
            Constants.LOGGER.error("Troll GUI 切换配置失败", exception)
            openErrorPopup(Supplier { EpsilonTranslations.Gui.CONFIG_ERROR_SWITCH.getTranslatedName() }, exception)
        }
    }

    private fun tryDeleteConfig(configName: String) {
        try {
            if (!ConfigManager.deleteConfig(configName)) {
                openErrorPopup(
                    Supplier { EpsilonTranslations.Gui.CONFIG_ERROR_DELETE.getTranslatedName() },
                    EpsilonTranslations.Gui.CONFIG_ERROR_DELETE_LAST.getTranslatedName()
                )
                return
            }
            if (inputField.text.trim() == configName) {
                inputField.text = ConfigManager.activeConfigName
                inputField.setCursorToEnd()
            }
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_DELETED.getTranslatedName() + " " + configName
        } catch (exception: Exception) {
            Constants.LOGGER.error("Troll GUI 删除配置失败", exception)
            openErrorPopup(Supplier { EpsilonTranslations.Gui.CONFIG_ERROR_DELETE.getTranslatedName() }, exception)
        }
    }

    private fun openDeleteConfirmation(configName: String) {
        val popupBounds = popupHost.getCenteredBounds(220.0f, 88.0f)
        val metrics = this.metrics
        val detail = if (metrics == null) configName else TrollTheme.trim(configName, metrics, 0.60f, 196.0f)
        popupHost.open(
            ConfirmActionPopup(
                popupBounds,
                Supplier { EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_TITLE.getTranslatedName() },
                Supplier { EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_MESSAGE.getTranslatedName() },
                detail,
                Supplier { EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_CONFIRM.getTranslatedName() },
                Supplier { EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_CANCEL.getTranslatedName() },
                Runnable { tryDeleteConfig(configName) }
            )
        )
    }

    private fun openErrorPopup(actionMessageSupplier: Supplier<String>, exception: Exception) {
        openErrorPopup(actionMessageSupplier, errorDetail(exception))
    }

    private fun openErrorPopup(actionMessageSupplier: Supplier<String>, detail: String) {
        val metrics = this.metrics
        val trimmedDetail = if (metrics == null) detail else TrollTheme.trim(detail, metrics, 0.52f, 196.0f)
        popupHost.open(
            MessagePopup(
                popupHost.getCenteredBounds(220.0f, 84.0f),
                EpsilonTranslations.Gui.CONFIG_ERROR_TITLE.getTranslatedName(),
                actionMessageSupplier.get(),
                trimmedDetail,
                EpsilonTranslations.Gui.CONFIG_ERROR_OK.getTranslatedName()
            )
        )
    }

    private fun openExportSuccessPopup(exported: Path?) {
        val detail = if (exported?.fileName == null) exported.toString() else exported.fileName.toString()
        val metrics = this.metrics
        val trimmedDetail = if (metrics == null) detail else TrollTheme.trim(detail, metrics, 0.52f, 196.0f)
        popupHost.open(
            MessagePopup(
                popupHost.getCenteredBounds(220.0f, 84.0f),
                EpsilonTranslations.Gui.CONFIG_EXPORT_SUCCESS_TITLE.getTranslatedName(),
                EpsilonTranslations.Gui.CONFIG_EXPORT_SUCCESS_MESSAGE.getTranslatedName(),
                trimmedDetail,
                EpsilonTranslations.Gui.CONFIG_ERROR_OK.getTranslatedName()
            )
        )
    }

    private fun errorDetail(exception: Exception): String {
        val message = exception.message
        return if (message == null || message.isBlank()) exception.javaClass.simpleName else message
    }

    private fun title(): String {
        return EpsilonTranslations.Gui.TAB_CONFIG.getTranslatedName()
    }

    private fun deleteBounds(rowBounds: UiRect): UiRect {
        return UiRect(
            rowBounds.right() - DELETE_BUTTON_SIZE - 3.0f,
            rowBounds.y + (rowBounds.height - DELETE_BUTTON_SIZE) / 2.0f,
            DELETE_BUTTON_SIZE,
            DELETE_BUTTON_SIZE
        )
    }

    private data class ActionButton(val type: ActionButtonType, val bounds: UiRect)

    private data class ConfigRow(val name: String, val bounds: UiRect, val deleteBounds: UiRect)

    private enum class ActionButtonType {
        SAVE_AS,
        RELOAD,
        EXPORT,
        IMPORT,
        NEW,
        OPEN_FOLDER;

        fun label(): String {
            return when (this) {
                SAVE_AS -> EpsilonTranslations.Gui.CONFIG_ACTION_SAVE_AS.getTranslatedName()
                RELOAD -> EpsilonTranslations.Gui.CONFIG_ACTION_RELOAD.getTranslatedName()
                EXPORT -> EpsilonTranslations.Gui.CONFIG_ACTION_EXPORT.getTranslatedName()
                IMPORT -> EpsilonTranslations.Gui.CONFIG_ACTION_IMPORT.getTranslatedName()
                NEW -> EpsilonTranslations.Gui.CONFIG_ACTION_NEW.getTranslatedName()
                OPEN_FOLDER -> EpsilonTranslations.Gui.CONFIG_ACTION_OPEN_FOLDER.getTranslatedName()
            }
        }
    }

    companion object {
        private const val PADDING = 6.0f
        private const val FIELD_HEIGHT = 18.0f
        private const val BUTTON_HEIGHT = 18.0f
        private const val ROW_HEIGHT = 22.0f
        private const val GAP = 4.0f
        private const val DELETE_BUTTON_SIZE = 16.0f
        private const val MAX_INPUT_LENGTH = 200
    }

}
