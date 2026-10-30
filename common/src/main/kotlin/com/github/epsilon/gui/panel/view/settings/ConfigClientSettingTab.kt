package com.github.epsilon.gui.panel.view.settings

import com.github.epsilon.Constants
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiContentBuffer
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.UiInvalidationState
import com.github.epsilon.gui.panel.PanelState
import com.github.epsilon.gui.panel.popup.ConfirmActionPopup
import com.github.epsilon.gui.panel.popup.MessagePopup
import com.github.epsilon.gui.panel.popup.PanelPopupHost
import com.github.epsilon.gui.panel.utils.ScrollBarDragState
import com.github.epsilon.gui.panel.utils.ScrollBarUtils
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.managers.ConfigManager
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.utils.client.ConfigFolderOpener
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import java.awt.Color
import java.nio.file.Path
import java.util.ArrayList
import java.util.HashMap
import java.util.function.Supplier

class ConfigClientSettingTab(
    private val state: PanelState,
    private val textRenderer: TextRenderer,
    private val popupHost: PanelPopupHost,
) : ClientSettingTabView {

    private val contentBuffer = UiContentBuffer(EpsilonUiTheme)
    private val contentState = UiInvalidationState()
    private val scrollBarDrag = ScrollBarDragState()
    private val inputField = ClientSettingTextField(MAX_INPUT_LENGTH)
    private val rowHoverAnimations = HashMap<String, Animation>()
    private val deleteHoverAnimations = HashMap<String, Animation>()
    private val buttonHoverAnimations = HashMap<String, Animation>()
    private val rowEntries = ArrayList<ConfigRowEntry>()

    private var bounds: UiRect? = null
    private var lastScroll = Float.NaN
    private var lastConfigList: List<String> = emptyList()
    private var lastActiveConfig = ""
    private var lastContentSignature = Long.MIN_VALUE
    private var scrollVelocity = 0.0f

    override fun render(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch, bounds: UiRect,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        this.bounds = bounds

        if (Math.abs(scrollVelocity) > 0.01f) {
            state.scrollConfig((scrollVelocity * partialTick).toDouble())
            scrollVelocity *= 0.86f
            if (Math.abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0.0f
            }
            markDirty()
        }

        val configs = ConfigManager.INSTANCE.listConfigs()
        val activeConfig = ConfigManager.INSTANCE.getActiveConfigName()
        val inputSection = getInputSectionBounds(bounds)
        val listViewport = getListViewport(bounds)
        val contentHeight = configs.size * (ROW_HEIGHT + MD3Theme.ROW_GAP)
        state.setMaxConfigScroll(contentHeight - listViewport.height)
        val maxScroll = Math.max(0.0f, contentHeight - listViewport.height)
        val hasScrollBar = maxScroll > 0.0f
        val rowWidth = if (hasScrollBar) listViewport.width - ScrollBarUtils.TOTAL_WIDTH else listViewport.width
        val contentSignature = buildContentSignature(configs, activeConfig)
        val rebuildContent = shouldRebuild(
            listViewport, mouseX, mouseY, configs, activeConfig, guiGraphics.guiHeight(), contentSignature
        )

        if (rebuildContent) {
            contentBuffer.clear()
            contentState.beginRebuild()
            rowEntries.clear()
            rowHoverAnimations.keys.removeIf { name -> !configs.contains(name) }
            deleteHoverAnimations.keys.removeIf { name -> !configs.contains(name) }
        }

        val tree = UiTree.build { scope ->
            inputField.buildUi(
                scope, getInputFieldBounds(inputSection), mouseX, mouseY, textRenderer,
                EpsilonTranslations.Gui.CONFIG_INPUT_PLACEHOLDER.getTranslatedName(), FIELD_SCALE, null
            )
            for (button in getActionButtons(inputSection)) {
                buildActionButton(scope, button, mouseX, mouseY)
            }
            scope.viewport(
                contentBuffer, listViewport, state.getConfigScroll(), maxScroll, contentHeight, mouseX, mouseY
            ) { content ->
                if (!rebuildContent) {
                    return@viewport
                }
                var rowY = listViewport.y - state.getConfigScroll()
                for (configName in configs) {
                    val rowBounds = UiRect(listViewport.x, rowY, rowWidth, ROW_HEIGHT)
                    val deleteBounds = getDeleteButtonBounds(rowBounds)
                    rowEntries.add(ConfigRowEntry(configName, rowBounds, deleteBounds))

                    val rowHover = rowHoverAnimations.getOrPut(configName) { createAnimation() }
                    val deleteHover = deleteHoverAnimations.getOrPut(configName) { createAnimation() }
                    rowHover.run(if (rowBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
                    deleteHover.run(if (deleteBounds.contains(mouseX.toDouble(), mouseY.toDouble())) 1.0f else 0.0f)
                    contentState.noteAnimation(!rowHover.isFinished || !deleteHover.isFinished)

                    content.pushAbsolute(rowBounds) { rowScope ->
                        buildConfigRow(
                            rowScope, configName, activeConfig, rowBounds, deleteBounds,
                            rowHover.value, deleteHover.value
                        )
                    }
                    rowY += ROW_HEIGHT + MD3Theme.ROW_GAP
                }

                if (configs.isEmpty()) {
                    val hintScale = 0.58f
                    val hint = EpsilonTranslations.Gui.CONFIG_EMPTY.getTranslatedName()
                    val hintWidth = textRenderer.getWidth(hint, hintScale)
                    val hintX = (listViewport.width - hintWidth) / 2.0f
                    val hintY = state.getConfigScroll() + listViewport.height / 2.0f -
                            textRenderer.getHeight(hintScale) / 2.0f
                    content.text(hint, hintX, hintY, hintScale, MD3Theme.TEXT_MUTED)
                }
            }
        }
        renderBatch.render(tree)

        if (rebuildContent) {
            rememberSnapshot(
                listViewport, mouseX, mouseY, configs, activeConfig, guiGraphics.guiHeight(), contentSignature
            )
        }
    }

    override fun flushContent() {
        contentBuffer.flush()
    }

    override fun markDirty() {
        contentState.markDirty()
    }

    override fun hasActiveAnimations(): Boolean {
        val rowAnimations = rowHoverAnimations.values.any { animation -> !animation.isFinished } ||
                deleteHoverAnimations.values.any { animation -> !animation.isFinished }
        val buttonAnimations = buttonHoverAnimations.values.any { animation -> !animation.isFinished }
        return contentState.hasActiveAnimations() ||
                rowAnimations ||
                buttonAnimations ||
                inputField.hasActiveAnimations()
    }

    override fun consumesHover(mouseX: Int, mouseY: Int): Boolean {
        val popup = popupHost.activePopup ?: return false
        return popup.getBounds().contains(mouseX.toDouble(), mouseY.toDouble())
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        val currentBounds = bounds
        if (currentBounds == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }

        scrollVelocity = 0.0f

        val listViewport = getListViewport(currentBounds)
        val maxScroll = state.getMaxConfigScroll()
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), listViewport, state.getConfigScroll(), maxScroll)) {
            val newScroll = scrollBarDrag.mouseDragged(event.y(), listViewport, maxScroll)
            if (newScroll >= 0.0f) {
                state.setConfigScroll(newScroll)
            }
            markDirty()
            return true
        }

        val inputSection = getInputSectionBounds(currentBounds)
        val inputBounds = getInputFieldBounds(inputSection)
        if (inputBounds.contains(event.x(), event.y())) {
            inputField.focusIfContains(inputBounds, event.x(), event.y())
            markDirty()
            return true
        }

        inputField.blur()

        for (button in getActionButtons(inputSection)) {
            if (button.bounds.contains(event.x(), event.y())) {
                handleAction(button.type)
                markDirty()
                return true
            }
        }

        for (entry in rowEntries) {
            if (entry.deleteBounds.contains(event.x(), event.y())) {
                openDeleteConfirmation(entry.name)
                markDirty()
                return true
            }
            if (entry.rowBounds.contains(event.x(), event.y())) {
                trySwitchConfig(entry.name)
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
        val newScroll = scrollBarDrag.mouseDragged(event.y(), listViewport, state.getMaxConfigScroll())
        if (newScroll >= 0.0f) {
            state.setConfigScroll(newScroll)
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
        if (event.key() == InputConstants.KEY_ESCAPE && inputField.isFocused()) {
            inputField.blur()
            markDirty()
            return true
        }
        if (inputField.keyPressed(event)) {
            markDirty()
            return true
        }
        return false
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (inputField.charTyped(event)) {
            markDirty()
            return true
        }
        return false
    }

    override fun onActivated() {
        if (inputField.getText().isBlank()) {
            inputField.setText(ConfigManager.INSTANCE.getActiveConfigName())
            inputField.setCursorToEnd()
        }
        markDirty()
    }

    override fun onDeactivated() {
        scrollBarDrag.reset()
        scrollVelocity = 0.0f
        inputField.blur()
        markDirty()
    }

    private fun buildActionButton(scope: UiTree.Scope, button: ActionButton, mouseX: Int, mouseY: Int) {
        val hoverAnimation = buttonHoverAnimations.getOrPut(button.type.name) { createAnimation() }
        val hovered = button.bounds.contains(mouseX.toDouble(), mouseY.toDouble())
        val hover = scope.animate(hoverAnimation, hovered)

        val baseColor: Color = when (button.type) {
            ActionButtonType.SAVE_AS -> MD3Theme.PRIMARY_CONTAINER
            ActionButtonType.RELOAD -> MD3Theme.SECONDARY_CONTAINER
            ActionButtonType.EXPORT, ActionButtonType.IMPORT, ActionButtonType.NEW,
            ActionButtonType.OPEN_FOLDER -> MD3Theme.SURFACE_CONTAINER_HIGH
        }
        val hoverColor: Color = when (button.type) {
            ActionButtonType.SAVE_AS -> MD3Theme.PRIMARY
            ActionButtonType.RELOAD -> MD3Theme.SECONDARY
            ActionButtonType.EXPORT, ActionButtonType.IMPORT, ActionButtonType.NEW,
            ActionButtonType.OPEN_FOLDER -> MD3Theme.SURFACE_CONTAINER_HIGHEST
        }
        val textColor: Color = when (button.type) {
            ActionButtonType.SAVE_AS -> MD3Theme.ON_PRIMARY_CONTAINER
            ActionButtonType.RELOAD -> MD3Theme.ON_SECONDARY_CONTAINER
            ActionButtonType.EXPORT, ActionButtonType.IMPORT, ActionButtonType.NEW,
            ActionButtonType.OPEN_FOLDER -> MD3Theme.TEXT_PRIMARY
        }

        scope.pushAbsolute(button.bounds) { buttonScope ->
            buttonScope.roundRect(
                0.0f, 0.0f, button.bounds.width, button.bounds.height,
                button.bounds.height / 2.0f, MD3Theme.lerp(baseColor, hoverColor, hover * 0.35f)
            )

            val labelScale = 0.56f
            val labelWidth = textRenderer.getWidth(button.label, labelScale)
            val labelHeight = textRenderer.getHeight(labelScale)
            buttonScope.text(
                button.label,
                (button.bounds.width - labelWidth) / 2.0f,
                (button.bounds.height - labelHeight) / 2.0f,
                labelScale,
                textColor
            )
        }
    }

    private fun buildConfigRow(
        scope: UiTree.Scope, configName: String, activeConfig: String,
        rowBounds: UiRect, deleteBounds: UiRect, hover: Float, deleteHover: Float,
    ) {
        val active = configName == activeConfig

        val baseColor = MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER, MD3Theme.SURFACE_CONTAINER_HIGH, hover)
        val rowColor = if (active) MD3Theme.lerp(baseColor, MD3Theme.PRIMARY_CONTAINER, 0.28f) else baseColor
        scope.roundRect(
            0.0f, 0.0f, rowBounds.width, rowBounds.height, MD3Theme.CARD_RADIUS.toFloat(), rowColor
        )

        val nameScale = 0.66f
        val subScale = 0.52f
        val textX = MD3Theme.ROW_CONTENT_INSET + 1.0f
        val nameY = 7.0f
        scope.text(
            trimToWidth(configName, nameScale, rowBounds.width - 72.0f), textX, nameY, nameScale,
            if (active) MD3Theme.ON_PRIMARY_CONTAINER else MD3Theme.TEXT_PRIMARY
        )

        val subtitle = if (active) {
            EpsilonTranslations.Gui.CONFIG_CURRENT.getTranslatedName()
        } else {
            EpsilonTranslations.Gui.CONFIG_SWITCH_HINT.getTranslatedName()
        }
        val subtitleColor = if (active) MD3Theme.ON_PRIMARY_CONTAINER else MD3Theme.TEXT_MUTED
        scope.text(subtitle, textX, nameY + 12.0f, subScale, subtitleColor)

        if (active) {
            val chipText = EpsilonTranslations.Gui.CONFIG_CURRENT.getTranslatedName()
            val chipScale = 0.48f
            val chipWidth = textRenderer.getWidth(chipText, chipScale) + 10.0f
            val chipHeight = 14.0f
            val localDeleteBounds = deleteBounds.relativeTo(rowBounds)
            val chipX = localDeleteBounds.x - chipWidth - 6.0f
            val chipY = (rowBounds.height - chipHeight) / 2.0f
            scope.roundRect(chipX, chipY, chipWidth, chipHeight, chipHeight / 2.0f, MD3Theme.PRIMARY)
            scope.text(
                chipText,
                chipX + (chipWidth - textRenderer.getWidth(chipText, chipScale)) / 2.0f,
                chipY + (chipHeight - textRenderer.getHeight(chipScale)) / 2.0f,
                chipScale,
                MD3Theme.ON_PRIMARY
            )
        }

        val localDeleteBounds = deleteBounds.relativeTo(rowBounds)
        scope.roundRect(
            localDeleteBounds.x, localDeleteBounds.y, localDeleteBounds.width, localDeleteBounds.height,
            localDeleteBounds.height / 2.0f,
            MD3Theme.lerp(
                MD3Theme.withAlpha(MD3Theme.ERROR, 0), MD3Theme.withAlpha(MD3Theme.ERROR, 32), deleteHover
            )
        )
        val removeScale = 0.50f
        val removeIcon = "✕"
        scope.text(
            removeIcon,
            localDeleteBounds.x + (localDeleteBounds.width - textRenderer.getWidth(removeIcon, removeScale)) / 2.0f,
            localDeleteBounds.y + (localDeleteBounds.height - textRenderer.getHeight(removeScale)) / 2.0f,
            removeScale,
            MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.ERROR, deleteHover)
        )
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
        val targetName = inputField.getText().trim()
        if (targetName.isEmpty()) {
            return
        }
        try {
            val savedName = ConfigManager.INSTANCE.saveAsConfig(targetName)
            inputField.setText(savedName)
            inputField.setCursorToEnd()
            state.setConfigScroll(0.0f)
        } catch (exception: Exception) {
            Constants.LOGGER.error("保存配置失败", exception)
            openErrorPopup({ EpsilonTranslations.Gui.CONFIG_ERROR_SAVE.getTranslatedName() }, exception)
        }
    }

    private fun tryNewConfig() {
        val targetName = inputField.getText().trim()
        if (targetName.isEmpty()) {
            return
        }
        try {
            val newName = ConfigManager.INSTANCE.newDefaultConfig(targetName)
            inputField.setText(newName)
            inputField.setCursorToEnd()
            state.setConfigScroll(0.0f)
        } catch (exception: Exception) {
            Constants.LOGGER.error("新建配置失败", exception)
            openErrorPopup({ EpsilonTranslations.Gui.CONFIG_ERROR_SAVE.getTranslatedName() }, exception)
        }
    }

    private fun tryReload() {
        try {
            ConfigManager.INSTANCE.reloadOrThrow()
            markDirty()
        } catch (exception: Exception) {
            Constants.LOGGER.error("重载配置失败", exception)
            openErrorPopup({ EpsilonTranslations.Gui.CONFIG_ERROR_RELOAD.getTranslatedName() }, exception)
        }
    }

    private fun tryExport() {
        try {
            val exported = ConfigManager.INSTANCE.exportActiveConfigToZip(inputField.getText())
            openExportSuccessPopup(exported)
        } catch (exception: Exception) {
            Constants.LOGGER.error("导出配置失败", exception)
            openErrorPopup({ EpsilonTranslations.Gui.CONFIG_ERROR_EXPORT.getTranslatedName() }, exception)
        }
    }

    private fun tryImport() {
        val zipPath = inputField.getText().trim()
        if (zipPath.isEmpty()) {
            return
        }
        try {
            val importedName = ConfigManager.INSTANCE.importConfigFromZip(zipPath)
            inputField.setText(importedName)
            inputField.setCursorToEnd()
            state.setConfigScroll(0.0f)
        } catch (exception: Exception) {
            openErrorPopup({ EpsilonTranslations.Gui.CONFIG_ERROR_IMPORT.getTranslatedName() }, exception)
        }
    }

    private fun tryOpenFolder() {
        try {
            ConfigFolderOpener.openConfigFolder()
        } catch (exception: Exception) {
            Constants.LOGGER.error("打开配置文件夹失败", exception)
            openErrorPopup({ EpsilonTranslations.Gui.CONFIG_ERROR_OPEN_FOLDER.getTranslatedName() }, exception)
        }
    }

    private fun trySwitchConfig(configName: String) {
        if (configName == ConfigManager.INSTANCE.getActiveConfigName()) {
            return
        }
        try {
            ConfigManager.INSTANCE.switchConfig(configName)
            inputField.setText(configName)
            inputField.setCursorToEnd()
        } catch (exception: Exception) {
            Constants.LOGGER.error("切换配置失败", exception)
            openErrorPopup({ EpsilonTranslations.Gui.CONFIG_ERROR_SWITCH.getTranslatedName() }, exception)
        }
    }

    private fun tryDeleteConfig(configName: String) {
        try {
            if (!ConfigManager.INSTANCE.deleteConfig(configName)) {
                openErrorPopup(
                    { EpsilonTranslations.Gui.CONFIG_ERROR_DELETE.getTranslatedName() },
                    EpsilonTranslations.Gui.CONFIG_ERROR_DELETE_LAST.getTranslatedName()
                )
                return
            }
            if (inputField.getText().trim() == configName) {
                inputField.setText(ConfigManager.INSTANCE.getActiveConfigName())
                inputField.setCursorToEnd()
            }
        } catch (exception: Exception) {
            Constants.LOGGER.error("删除配置失败", exception)
            openErrorPopup({ EpsilonTranslations.Gui.CONFIG_ERROR_DELETE.getTranslatedName() }, exception)
        }
    }

    private fun openDeleteConfirmation(configName: String) {
        val popupWidth = 198.0f
        val popupBounds = popupHost.getCenteredBounds(popupWidth, 82.0f)
        popupHost.open(
            ConfirmActionPopup(
                popupBounds,
                EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_TITLE.getTranslatedName(),
                EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_MESSAGE.getTranslatedName(),
                trimToWidth(configName, 0.60f, popupWidth - 24.0f),
                EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_CONFIRM.getTranslatedName(),
                EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_CANCEL.getTranslatedName(),
                {
                    tryDeleteConfig(configName)
                    markDirty()
                }
            )
        )
    }

    private fun openErrorPopup(actionMessageSupplier: Supplier<String>, exception: Exception) {
        openErrorPopup(actionMessageSupplier, buildErrorDetail(exception))
    }

    private fun openErrorPopup(actionMessageSupplier: Supplier<String>, detail: String) {
        val popupWidth = 220.0f
        val popupHeight = 84.0f
        popupHost.open(
            MessagePopup(
                popupHost.getCenteredBounds(popupWidth, popupHeight),
                supplierOf { EpsilonTranslations.Gui.CONFIG_ERROR_TITLE.getTranslatedName() },
                actionMessageSupplier,
                trimToWidth(detail, 0.52f, popupWidth - 24.0f),
                supplierOf { EpsilonTranslations.Gui.CONFIG_ERROR_OK.getTranslatedName() }
            )
        )
    }

    private fun openExportSuccessPopup(exported: Path?) {
        val popupWidth = 220.0f
        val popupHeight = 84.0f
        var detail = ""
        if (exported != null) {
            val fileName = exported.fileName
            detail = fileName?.toString() ?: exported.toString()
        }
        popupHost.open(
            MessagePopup(
                popupHost.getCenteredBounds(popupWidth, popupHeight),
                EpsilonTranslations.Gui.CONFIG_EXPORT_SUCCESS_TITLE.getTranslatedName(),
                EpsilonTranslations.Gui.CONFIG_EXPORT_SUCCESS_MESSAGE.getTranslatedName(),
                trimToWidth(detail, 0.52f, popupWidth - 24.0f),
                EpsilonTranslations.Gui.CONFIG_ERROR_OK.getTranslatedName()
            )
        )
    }

    private fun supplierOf(block: () -> String): Supplier<String> = Supplier { block() }

    private fun buildErrorDetail(exception: Exception): String {
        val message = exception.message
        return if (message == null || message.isBlank()) exception.javaClass.simpleName else message
    }

    private fun shouldRebuild(
        listViewport: UiRect, mouseX: Int, mouseY: Int, configs: List<String>,
        activeConfig: String, currentGuiHeight: Int, contentSignature: Long,
    ): Boolean {
        if (contentState.needsRebuild(listViewport, mouseX, mouseY, currentGuiHeight, contentSignature)) {
            return true
        }
        if (lastScroll.compareTo(state.getConfigScroll()) != 0) {
            return true
        }
        if (lastConfigList != configs) {
            return true
        }
        if (lastActiveConfig != activeConfig) {
            return true
        }
        return lastContentSignature != contentSignature
    }

    private fun rememberSnapshot(
        listViewport: UiRect, mouseX: Int, mouseY: Int, configs: List<String>,
        activeConfig: String, currentGuiHeight: Int, contentSignature: Long,
    ) {
        contentState.rememberSnapshot(listViewport, mouseX, mouseY, currentGuiHeight, contentSignature)
        lastScroll = state.getConfigScroll()
        lastConfigList = ArrayList(configs)
        lastActiveConfig = activeConfig
        lastContentSignature = contentSignature
    }

    private fun buildContentSignature(configs: List<String>, activeConfig: String): Long {
        var signature = 17L
        signature = signature * 31L + TranslationManager.INSTANCE.getRevision()
        signature = signature * 31L + java.lang.Float.floatToIntBits(state.getConfigScroll())
        signature = signature * 31L + activeConfig.hashCode()
        for (config in configs) {
            signature = signature * 31L + config.hashCode()
        }
        return signature
    }

    private fun getInputSectionBounds(bounds: UiRect): UiRect {
        val inputHeight = FIELD_HEIGHT + BUTTON_HEIGHT * 2.0f + SECTION_GAP * 3.0f
        return UiRect(bounds.x, bounds.bottom() - inputHeight, bounds.width, inputHeight)
    }

    private fun getListViewport(bounds: UiRect): UiRect {
        val inputBounds = getInputSectionBounds(bounds)
        val y = bounds.y
        val bottom = inputBounds.y - SECTION_GAP
        return UiRect(bounds.x, y, bounds.width, Math.max(0.0f, bottom - y))
    }

    private fun getInputFieldBounds(inputBounds: UiRect): UiRect {
        return UiRect(inputBounds.x, inputBounds.y, inputBounds.width, FIELD_HEIGHT)
    }

    private fun getActionButtons(inputBounds: UiRect): List<ActionButton> {
        val y = getInputFieldBounds(inputBounds).bottom() + SECTION_GAP
        val gap = 4.0f
        val width = (inputBounds.width - gap * 3.0f) / 4.0f
        val secondRowWidth = (inputBounds.width - gap) / 2.0f
        val secondRowY = y + BUTTON_HEIGHT + SECTION_GAP
        return listOf(
            ActionButton(
                ActionButtonType.SAVE_AS, EpsilonTranslations.Gui.CONFIG_ACTION_SAVE_AS.getTranslatedName(),
                UiRect(inputBounds.x, y, width, BUTTON_HEIGHT)
            ),
            ActionButton(
                ActionButtonType.RELOAD, EpsilonTranslations.Gui.CONFIG_ACTION_RELOAD.getTranslatedName(),
                UiRect(inputBounds.x + width + gap, y, width, BUTTON_HEIGHT)
            ),
            ActionButton(
                ActionButtonType.EXPORT, EpsilonTranslations.Gui.CONFIG_ACTION_EXPORT.getTranslatedName(),
                UiRect(inputBounds.x + (width + gap) * 2.0f, y, width, BUTTON_HEIGHT)
            ),
            ActionButton(
                ActionButtonType.IMPORT, EpsilonTranslations.Gui.CONFIG_ACTION_IMPORT.getTranslatedName(),
                UiRect(inputBounds.x + (width + gap) * 3.0f, y, width, BUTTON_HEIGHT)
            ),
            ActionButton(
                ActionButtonType.NEW, EpsilonTranslations.Gui.CONFIG_ACTION_NEW.getTranslatedName(),
                UiRect(inputBounds.x, secondRowY, secondRowWidth, BUTTON_HEIGHT)
            ),
            ActionButton(
                ActionButtonType.OPEN_FOLDER, EpsilonTranslations.Gui.CONFIG_ACTION_OPEN_FOLDER.getTranslatedName(),
                UiRect(inputBounds.x + secondRowWidth + gap, secondRowY, secondRowWidth, BUTTON_HEIGHT)
            )
        )
    }

    private fun getDeleteButtonBounds(rowBounds: UiRect): UiRect {
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

    private fun trimToWidth(value: String?, scale: Float, width: Float): String {
        if (value == null || value.isEmpty()) {
            return ""
        }
        if (textRenderer.getWidth(value, scale) <= width) {
            return value
        }
        val ellipsis = "..."
        val ellipsisWidth = textRenderer.getWidth(ellipsis, scale)
        if (ellipsisWidth >= width) {
            return ellipsis
        }
        for (length in value.length - 1 downTo 0) {
            val candidate = value.substring(0, length) + ellipsis
            if (textRenderer.getWidth(candidate, scale) <= width) {
                return candidate
            }
        }
        return ellipsis
    }

    override fun close() {
        contentBuffer.close()
        markDirty()
    }

    private data class ConfigRowEntry(val name: String, val rowBounds: UiRect, val deleteBounds: UiRect)

    private data class ActionButton(val type: ActionButtonType, val label: String, val bounds: UiRect)

    private enum class ActionButtonType {
        SAVE_AS,
        RELOAD,
        EXPORT,
        IMPORT,
        NEW,
        OPEN_FOLDER
    }

    private companion object {

        private const val ROW_HEIGHT = 36.0f
        private const val FIELD_HEIGHT = 28.0f
        private const val BUTTON_HEIGHT = 26.0f
        private const val SECTION_GAP = 6.0f
        private const val FIELD_SCALE = 0.78f
        private const val MAX_INPUT_LENGTH = 200

    }

}
