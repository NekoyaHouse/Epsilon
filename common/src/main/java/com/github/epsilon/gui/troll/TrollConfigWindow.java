package com.github.epsilon.gui.troll;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.widget.DropdownTextField;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.popup.ConfirmActionPopup;
import com.github.epsilon.gui.panel.popup.MessagePopup;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.utils.client.ConfigFolderOpener;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class TrollConfigWindow extends TrollWindow {

    private static final float PADDING = 6.0f;
    private static final float FIELD_HEIGHT = 18.0f;
    private static final float BUTTON_HEIGHT = 18.0f;
    private static final float ROW_HEIGHT = 22.0f;
    private static final float GAP = 4.0f;
    private static final float DELETE_BUTTON_SIZE = 16.0f;
    private static final int MAX_INPUT_LENGTH = 200;

    private final PanelPopupHost popupHost;
    private final DropdownTextField inputField = new DropdownTextField(MAX_INPUT_LENGTH);
    private final List<ActionButton> actionButtons = new ArrayList<>();
    private final List<ConfigRow> rows = new ArrayList<>();

    private UiRect inputBounds = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);
    private UiRect listViewport = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);
    private UiRect statusBounds = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);
    private TextRenderer metrics;
    private String status = "";

    public TrollConfigWindow(PanelPopupHost popupHost) {
        this.popupHost = popupHost;
    }

    @Override
    public void layout(TextRenderer metrics, float screenWidth, float screenHeight) {
        this.metrics = metrics;
        List<String> configs = ConfigManager.INSTANCE.listConfigs();
        String activeConfig = ConfigManager.INSTANCE.getActiveConfigName();
        if (!inputField.isFocused() && inputField.getText().isBlank()) {
            inputField.setText(activeConfig);
            inputField.setCursorToEnd();
        }

        headerHeight = TrollTheme.headerHeight(metrics);
        float width = Math.max(240.0f, metrics.getWidth(title(), TrollTheme.TEXT_SCALE) + 30.0f);
        for (ActionButtonType type : ActionButtonType.values()) {
            width = Math.max(width, metrics.getWidth(type.label(), TrollTheme.VALUE_SCALE) * 2.0f + 32.0f);
        }
        for (String config : configs) {
            width = Math.max(width, metrics.getWidth(config, TrollTheme.TEXT_SCALE) + 74.0f);
        }
        width = Math.min(Math.min(width, 340.0f), screenWidth);

        float contentTop = bounds.y() + headerHeight + PADDING;
        float actionTop = contentTop + FIELD_HEIGHT + GAP;
        float actionHeight = BUTTON_HEIGHT * 3.0f + GAP * 2.0f;
        float statusTop = actionTop + actionHeight + GAP;
        float statusHeight = status.isBlank() ? 0.0f : ROW_HEIGHT;
        float listTop = statusTop + statusHeight + GAP;
        float fixedHeight = listTop - bounds.y();
        float listHeight = Math.max(ROW_HEIGHT, configs.size() * (ROW_HEIGHT + GAP));
        float height = Math.min(Math.min(fixedHeight + listHeight + PADDING, 300.0f), screenHeight);
        size(width, height, screenWidth, screenHeight);

        float contentWidth = Math.max(1.0f, bounds.width() - PADDING * 2.0f);
        inputBounds = new UiRect(bounds.x() + PADDING, bounds.y() + headerHeight + PADDING, contentWidth, FIELD_HEIGHT);
        actionTop = inputBounds.bottom() + GAP;
        float buttonWidth = Math.max(1.0f, (contentWidth - GAP) / 2.0f);
        actionButtons.clear();
        ActionButtonType[] types = ActionButtonType.values();
        for (int i = 0; i < types.length; i++) {
            int column = i % 2;
            int row = i / 2;
            actionButtons.add(new ActionButton(types[i], new UiRect(
                    bounds.x() + PADDING + column * (buttonWidth + GAP),
                    actionTop + row * (BUTTON_HEIGHT + GAP),
                    buttonWidth,
                    BUTTON_HEIGHT
            )));
        }
        statusTop = actionTop + BUTTON_HEIGHT * 3.0f + GAP * 2.0f + GAP;
        statusBounds = new UiRect(bounds.x() + PADDING, statusTop, contentWidth, ROW_HEIGHT);
        listTop = statusTop + (status.isBlank() ? 0.0f : ROW_HEIGHT) + GAP;
        listViewport = new UiRect(
                bounds.x() + PADDING,
                listTop,
                contentWidth,
                Math.max(1.0f, bounds.bottom() - PADDING - listTop)
        );

        float contentHeight = configs.size() * (ROW_HEIGHT + GAP);
        scrollPosition = scroll.update(Math.max(0.0f, contentHeight - listViewport.height()));

        rows.clear();
        float rowY = listViewport.y() - scrollPosition;
        for (String config : configs) {
            UiRect rowBounds = new UiRect(listViewport.x(), rowY, listViewport.width(), ROW_HEIGHT);
            rows.add(new ConfigRow(config, rowBounds, deleteBounds(rowBounds)));
            rowY += ROW_HEIGHT + GAP;
        }
    }

    @Override
    public void draw(UiTree.Scope scope, TextRenderer metrics, int mouseX, int mouseY) {
        TrollTheme.window(scope, bounds, headerHeight, title(), metrics);
        inputField.draw(scope, TrollTheme.metrics(metrics), inputBounds.x(), inputBounds.y(),
                inputBounds.width(), inputBounds.height(), mouseX, mouseY,
                EpsilonTranslations.Gui.CONFIG_INPUT_PLACEHOLDER.getTranslatedName(), TrollTheme.TEXT_SCALE);

        for (ActionButton button : actionButtons) {
            boolean hovered = button.bounds().contains(mouseX, mouseY);
            scope.rect(1, button.bounds().x(), button.bounds().y(), button.bounds().width(), button.bounds().height(),
                    TrollTheme.alpha(TrollTheme.primary(), hovered ? 190 : 70));
            String label = TrollTheme.trim(button.type().label(), metrics, TrollTheme.VALUE_SCALE,
                    button.bounds().width() - 8.0f);
            scope.text(2, label,
                    button.bounds().centerX() - metrics.getWidth(label, TrollTheme.VALUE_SCALE) / 2.0f,
                    button.bounds().y() + (button.bounds().height() - metrics.getHeight(TrollTheme.VALUE_SCALE)) / 2.0f,
                    TrollTheme.VALUE_SCALE, TrollTheme.text());
        }

        if (!status.isBlank()) {
            scope.text(2, TrollTheme.trim(status, metrics, TrollTheme.VALUE_SCALE, statusBounds.width()),
                    statusBounds.x(), statusBounds.y() + (statusBounds.height() - metrics.getHeight(TrollTheme.VALUE_SCALE)) / 2.0f,
                    TrollTheme.VALUE_SCALE, TrollTheme.text());
        }

        String activeConfig = ConfigManager.INSTANCE.getActiveConfigName();
        scope.scissor(listViewport, content -> {
            if (rows.isEmpty()) {
                String empty = EpsilonTranslations.Gui.CONFIG_EMPTY.getTranslatedName();
                content.text(2, empty,
                        listViewport.centerX() - metrics.getWidth(empty, TrollTheme.VALUE_SCALE) / 2.0f,
                        listViewport.y() + (listViewport.height() - metrics.getHeight(TrollTheme.VALUE_SCALE)) / 2.0f,
                        TrollTheme.VALUE_SCALE, TrollTheme.text());
                return;
            }
            for (ConfigRow row : rows) {
                boolean active = Objects.equals(row.name(), activeConfig);
                boolean hovered = row.bounds().contains(mouseX, mouseY);
                content.rect(1, row.bounds().x(), row.bounds().y(), row.bounds().width(), row.bounds().height(),
                        active ? TrollTheme.alpha(TrollTheme.primary(), 110)
                                : new Color(255, 255, 255, hovered ? 30 : 16));

                float nameScale = TrollTheme.VALUE_SCALE;
                content.text(2, TrollTheme.trim(row.name(), metrics, nameScale,
                                row.bounds().width() - DELETE_BUTTON_SIZE - 18.0f),
                        row.bounds().x() + 5.0f,
                        row.bounds().y() + 2.0f,
                        nameScale, TrollTheme.text());

                String hint = active
                        ? EpsilonTranslations.Gui.CONFIG_CURRENT.getTranslatedName()
                        : EpsilonTranslations.Gui.CONFIG_SWITCH_HINT.getTranslatedName();
                content.text(2, TrollTheme.trim(hint, metrics, TrollTheme.VALUE_SCALE * 0.82f,
                                row.bounds().width() - DELETE_BUTTON_SIZE - 18.0f),
                        row.bounds().x() + 5.0f,
                        row.bounds().bottom() - metrics.getHeight(TrollTheme.VALUE_SCALE * 0.82f) - 2.0f,
                        TrollTheme.VALUE_SCALE * 0.82f, TrollTheme.alpha(TrollTheme.text(), 180));

                boolean deleteHovered = row.deleteBounds().contains(mouseX, mouseY);
                content.rect(3, row.deleteBounds().x(), row.deleteBounds().y(),
                        row.deleteBounds().width(), row.deleteBounds().height(),
                        TrollTheme.alpha(TrollTheme.primary(), deleteHovered ? 180 : 40));
                float iconScale = TrollTheme.VALUE_SCALE;
                content.text(4, IconChars.CLOSE,
                        row.deleteBounds().centerX() - metrics.getWidth(IconChars.CLOSE, iconScale, StaticFontLoader.ICONS) / 2.0f,
                        row.deleteBounds().y() + (row.deleteBounds().height() - metrics.getHeight(iconScale, StaticFontLoader.ICONS)) / 2.0f,
                        iconScale, TrollTheme.text(), StaticFontLoader.ICONS);
            }
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event) {
        if (super.mouseClicked(event)) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return bounds.contains(event.x(), event.y());

        if (inputField.focusIfContains(event.x(), event.y(), inputBounds.x(), inputBounds.y(),
                inputBounds.width(), inputBounds.height())) {
            return true;
        }
        inputField.blur();

        for (ActionButton button : actionButtons) {
            if (button.bounds().contains(event.x(), event.y())) {
                handleAction(button.type());
                return true;
            }
        }
        if (!listViewport.contains(event.x(), event.y())) {
            return bounds.contains(event.x(), event.y());
        }
        for (ConfigRow row : rows) {
            if (row.deleteBounds().contains(event.x(), event.y())) {
                openDeleteConfirmation(row.name());
                return true;
            }
            if (row.bounds().contains(event.x(), event.y())) {
                trySwitchConfig(row.name());
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!listViewport.contains(mouseX, mouseY)) return false;
        scroll.scroll(amount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!inputField.isFocused()) return false;
        if (event.isEscape()) {
            inputField.blur();
            return true;
        }
        return inputField.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return inputField.charTyped(event);
    }

    @Override
    public void blurInput() {
        inputField.blur();
    }

    private void handleAction(ActionButtonType action) {
        switch (action) {
            case SAVE_AS -> trySaveAs();
            case RELOAD -> tryReload();
            case EXPORT -> tryExport();
            case IMPORT -> tryImport();
            case NEW -> tryNewConfig();
            case OPEN_FOLDER -> tryOpenFolder();
        }
    }

    private void trySaveAs() {
        String targetName = inputField.getText().trim();
        if (targetName.isEmpty()) return;
        try {
            String savedName = ConfigManager.INSTANCE.saveAsConfig(targetName);
            inputField.setText(savedName);
            inputField.setCursorToEnd();
            scroll.stop();
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_SAVED.getTranslatedName() + " " + savedName;
        } catch (Exception exception) {
            Constants.LOGGER.error("Troll GUI 保存配置失败", exception);
            openErrorPopup(EpsilonTranslations.Gui.CONFIG_ERROR_SAVE::getTranslatedName, exception);
        }
    }

    private void tryNewConfig() {
        String targetName = inputField.getText().trim();
        if (targetName.isEmpty()) return;
        try {
            String newName = ConfigManager.INSTANCE.newDefaultConfig(targetName);
            inputField.setText(newName);
            inputField.setCursorToEnd();
            scroll.stop();
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_CREATED.getTranslatedName() + " " + newName;
        } catch (Exception exception) {
            Constants.LOGGER.error("Troll GUI 新建配置失败", exception);
            openErrorPopup(EpsilonTranslations.Gui.CONFIG_ERROR_SAVE::getTranslatedName, exception);
        }
    }

    private void tryReload() {
        try {
            ConfigManager.INSTANCE.reloadOrThrow();
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_RELOADED.getTranslatedName();
        } catch (Exception exception) {
            Constants.LOGGER.error("Troll GUI 重载配置失败", exception);
            openErrorPopup(EpsilonTranslations.Gui.CONFIG_ERROR_RELOAD::getTranslatedName, exception);
        }
    }

    private void tryExport() {
        try {
            Path exported = ConfigManager.INSTANCE.exportActiveConfigToZip(inputField.getText());
            String fileName = exported != null && exported.getFileName() != null
                    ? exported.getFileName().toString() : String.valueOf(exported);
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_EXPORTED.getTranslatedName() + " " + fileName;
            openExportSuccessPopup(exported);
        } catch (Exception exception) {
            Constants.LOGGER.error("Troll GUI 导出配置失败", exception);
            openErrorPopup(EpsilonTranslations.Gui.CONFIG_ERROR_EXPORT::getTranslatedName, exception);
        }
    }

    private void tryImport() {
        String zipPath = inputField.getText().trim();
        if (zipPath.isEmpty()) return;
        try {
            String importedName = ConfigManager.INSTANCE.importConfigFromZip(zipPath);
            inputField.setText(importedName);
            inputField.setCursorToEnd();
            scroll.stop();
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_IMPORTED.getTranslatedName() + " " + importedName;
        } catch (Exception exception) {
            Constants.LOGGER.error("Troll GUI 导入配置失败", exception);
            openErrorPopup(EpsilonTranslations.Gui.CONFIG_ERROR_IMPORT::getTranslatedName, exception);
        }
    }

    private void tryOpenFolder() {
        try {
            Path folder = ConfigFolderOpener.openConfigFolder();
            status = EpsilonTranslations.Gui.CONFIG_ACTION_OPEN_FOLDER.getTranslatedName() + " " + folder;
        } catch (Exception exception) {
            Constants.LOGGER.error("Troll GUI 打开配置文件夹失败", exception);
            openErrorPopup(EpsilonTranslations.Gui.CONFIG_ERROR_OPEN_FOLDER::getTranslatedName, exception);
        }
    }

    private void trySwitchConfig(String configName) {
        if (Objects.equals(configName, ConfigManager.INSTANCE.getActiveConfigName())) return;
        try {
            ConfigManager.INSTANCE.switchConfig(configName);
            inputField.setText(configName);
            inputField.setCursorToEnd();
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_SWITCHED.getTranslatedName() + " " + configName;
        } catch (Exception exception) {
            Constants.LOGGER.error("Troll GUI 切换配置失败", exception);
            openErrorPopup(EpsilonTranslations.Gui.CONFIG_ERROR_SWITCH::getTranslatedName, exception);
        }
    }

    private void tryDeleteConfig(String configName) {
        try {
            if (!ConfigManager.INSTANCE.deleteConfig(configName)) {
                openErrorPopup(EpsilonTranslations.Gui.CONFIG_ERROR_DELETE::getTranslatedName,
                        EpsilonTranslations.Gui.CONFIG_ERROR_DELETE_LAST.getTranslatedName());
                return;
            }
            if (Objects.equals(inputField.getText().trim(), configName)) {
                inputField.setText(ConfigManager.INSTANCE.getActiveConfigName());
                inputField.setCursorToEnd();
            }
            status = EpsilonTranslations.Gui.DROPDOWN_STATUS_DELETED.getTranslatedName() + " " + configName;
        } catch (Exception exception) {
            Constants.LOGGER.error("Troll GUI 删除配置失败", exception);
            openErrorPopup(EpsilonTranslations.Gui.CONFIG_ERROR_DELETE::getTranslatedName, exception);
        }
    }

    private void openDeleteConfirmation(String configName) {
        UiRect popupBounds = popupHost.getCenteredBounds(220.0f, 88.0f);
        String detail = metrics == null ? configName : TrollTheme.trim(configName, metrics, 0.60f, 196.0f);
        popupHost.open(new ConfirmActionPopup(
                popupBounds,
                EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_TITLE::getTranslatedName,
                EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_MESSAGE::getTranslatedName,
                detail,
                EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_CONFIRM::getTranslatedName,
                EpsilonTranslations.Gui.CONFIG_DELETE_CONFIRM_CANCEL::getTranslatedName,
                () -> tryDeleteConfig(configName)
        ));
    }

    private void openErrorPopup(Supplier<String> actionMessageSupplier, Exception exception) {
        openErrorPopup(actionMessageSupplier, errorDetail(exception));
    }

    private void openErrorPopup(Supplier<String> actionMessageSupplier, String detail) {
        String trimmedDetail = metrics == null ? detail : TrollTheme.trim(detail, metrics, 0.52f, 196.0f);
        popupHost.open(new MessagePopup(
                popupHost.getCenteredBounds(220.0f, 84.0f),
                EpsilonTranslations.Gui.CONFIG_ERROR_TITLE.getTranslatedName(),
                actionMessageSupplier.get(),
                trimmedDetail,
                EpsilonTranslations.Gui.CONFIG_ERROR_OK.getTranslatedName()
        ));
    }

    private void openExportSuccessPopup(Path exported) {
        String detail = exported == null || exported.getFileName() == null
                ? String.valueOf(exported) : exported.getFileName().toString();
        String trimmedDetail = metrics == null ? detail : TrollTheme.trim(detail, metrics, 0.52f, 196.0f);
        popupHost.open(new MessagePopup(
                popupHost.getCenteredBounds(220.0f, 84.0f),
                EpsilonTranslations.Gui.CONFIG_EXPORT_SUCCESS_TITLE.getTranslatedName(),
                EpsilonTranslations.Gui.CONFIG_EXPORT_SUCCESS_MESSAGE.getTranslatedName(),
                trimmedDetail,
                EpsilonTranslations.Gui.CONFIG_ERROR_OK.getTranslatedName()
        ));
    }

    private String errorDetail(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private String title() {
        return EpsilonTranslations.Gui.TAB_CONFIG.getTranslatedName();
    }

    private UiRect deleteBounds(UiRect rowBounds) {
        return new UiRect(
                rowBounds.right() - DELETE_BUTTON_SIZE - 3.0f,
                rowBounds.y() + (rowBounds.height() - DELETE_BUTTON_SIZE) / 2.0f,
                DELETE_BUTTON_SIZE,
                DELETE_BUTTON_SIZE
        );
    }

    private record ActionButton(ActionButtonType type, UiRect bounds) {
    }

    private record ConfigRow(String name, UiRect bounds, UiRect deleteBounds) {
    }

    private enum ActionButtonType {
        SAVE_AS,
        RELOAD,
        EXPORT,
        IMPORT,
        NEW,
        OPEN_FOLDER;

        private String label() {
            return switch (this) {
                case SAVE_AS -> EpsilonTranslations.Gui.CONFIG_ACTION_SAVE_AS.getTranslatedName();
                case RELOAD -> EpsilonTranslations.Gui.CONFIG_ACTION_RELOAD.getTranslatedName();
                case EXPORT -> EpsilonTranslations.Gui.CONFIG_ACTION_EXPORT.getTranslatedName();
                case IMPORT -> EpsilonTranslations.Gui.CONFIG_ACTION_IMPORT.getTranslatedName();
                case NEW -> EpsilonTranslations.Gui.CONFIG_ACTION_NEW.getTranslatedName();
                case OPEN_FOLDER -> EpsilonTranslations.Gui.CONFIG_ACTION_OPEN_FOLDER.getTranslatedName();
            };
        }
    }

}
