package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.DropdownTextField;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.holders.ConfigHolder;
import com.github.epsilon.utils.client.ConfigFolderOpener;

import java.util.List;
import java.util.Objects;

public class ConfigDropdownPanel extends AbstractDropdownPanel {

    private static final float FIELD_HEIGHT = 18.0f;
    private static final float BUTTON_HEIGHT = 17.0f;
    private static final float ROW_HEIGHT = 24.0f;
    private static final float GAP = 4.0f;
    private static final float PADDING = 6.0f;

    private final DropdownTextField inputField = new DropdownTextField(160);
    private String status = "";
    private int cachedConfigFrameId = Integer.MIN_VALUE;
    private List<String> cachedConfigs = List.of();

    public ConfigDropdownPanel(int panelIndex) {
        super("config", EpsilonTranslations.Gui.TAB_CONFIG, "", panelIndex);
    }

    @Override
    protected float computeContentHeight() {
        int configCount = configsForFrame().size();
        return PADDING * 2.0f + FIELD_HEIGHT + GAP + BUTTON_HEIGHT * 3.0f + GAP * 3.0f
                + Math.max(ROW_HEIGHT, configCount * (ROW_HEIGHT + GAP))
                + (status.isEmpty() ? 0.0f : ROW_HEIGHT);
    }

    @Override
    protected void drawPanelContent(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float visibleHeight) {
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float contentX = x + PADDING;
        float contentW = width - PADDING * 2.0f;

        String placeholder = ConfigHolder.INSTANCE.getActiveConfigName();
        if (!inputField.isFocused() && inputField.getText().isEmpty()) {
            inputField.setText(placeholder);
        }
        inputField.draw(scope, textMetrics, contentX, currentY, contentW, FIELD_HEIGHT, mouseX, mouseY, placeholder, DropdownTheme.SETTING_TEXT_SCALE);
        currentY += FIELD_HEIGHT + GAP;

        String[] actions = {
                EpsilonTranslations.Gui.CONFIG_ACTION_SAVE_AS.getTranslatedName(),
                EpsilonTranslations.Gui.CONFIG_ACTION_RELOAD.getTranslatedName(),
                EpsilonTranslations.Gui.CONFIG_ACTION_EXPORT.getTranslatedName(),
                EpsilonTranslations.Gui.CONFIG_ACTION_IMPORT.getTranslatedName(),
                EpsilonTranslations.Gui.CONFIG_ACTION_NEW.getTranslatedName(),
                EpsilonTranslations.Gui.CONFIG_ACTION_OPEN_FOLDER.getTranslatedName()
        };
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                if (index >= actions.length) continue;
                float btnW = (contentW - GAP) * 0.5f;
                float btnX = contentX + col * (btnW + GAP);
                float btnY = currentY + row * (BUTTON_HEIGHT + GAP);
                boolean hovered = isHovered(mouseX, mouseY, btnX, btnY, btnW, BUTTON_HEIGHT);
                scope.roundRect(btnX, btnY, btnW, BUTTON_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                        hovered ? MD3Theme.PRIMARY_CONTAINER : MD3Theme.SURFACE_CONTAINER_HIGH);
                float labelScale = 0.48f;
                float labelW = textMetrics.textWidth(actions[index], labelScale);
                scope.text(actions[index], btnX + (btnW - labelW) * 0.5f, getCenteredTextY(textMetrics, btnY, BUTTON_HEIGHT, labelScale), labelScale, MD3Theme.TEXT_PRIMARY);
            }
        }
        currentY += BUTTON_HEIGHT * 3.0f + GAP * 3.0f;

        if (!status.isEmpty()) {
            float statusScale = 0.50f;
            scope.text(trimToWidth(status, statusScale, contentW, textMetrics), contentX, getCenteredTextY(textMetrics, currentY, ROW_HEIGHT, statusScale), statusScale, MD3Theme.TEXT_MUTED);
            currentY += ROW_HEIGHT;
        }

        String active = ConfigHolder.INSTANCE.getActiveConfigName();
        List<String> configs = configsForFrame();
        if (configs.isEmpty()) {
            float emptyScale = 0.55f;
            scope.text(EpsilonTranslations.Gui.CONFIG_EMPTY.getTranslatedName(), contentX, getCenteredTextY(textMetrics, currentY, ROW_HEIGHT, emptyScale), emptyScale, MD3Theme.TEXT_MUTED);
            return;
        }
        for (String name : configs) {
            boolean activeRow = Objects.equals(name, active);
            boolean hovered = isHovered(mouseX, mouseY, contentX, currentY, contentW, ROW_HEIGHT);
            scope.roundRect(contentX, currentY, contentW, ROW_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                    activeRow ? MD3Theme.PRIMARY_CONTAINER : (hovered ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER_LOW));
            float nameScale = 0.56f;
            scope.text(trimToWidth(name, nameScale, contentW - 28.0f, textMetrics), contentX + 6.0f, getCenteredTextY(textMetrics, currentY, ROW_HEIGHT, nameScale), nameScale,
                    activeRow ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY);
            float deleteX = contentX + contentW - 18.0f;
            float deleteScale = 0.52f;
            scope.text("x", deleteX + 5.0f, getCenteredTextY(textMetrics, currentY + 3.0f, 16.0f, deleteScale), deleteScale,
                    isHovered(mouseX, mouseY, deleteX, currentY + 3.0f, 16.0f, 16.0f) ? MD3Theme.ERROR : MD3Theme.TEXT_MUTED);
            currentY += ROW_HEIGHT + GAP;
        }
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float contentX = x + PADDING;
        float contentW = width - PADDING * 2.0f;
        if (inputField.focusIfContains(mouseX, mouseY, contentX, currentY, contentW, FIELD_HEIGHT)) {
            return true;
        }
        inputField.blur();
        currentY += FIELD_HEIGHT + GAP;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                if (index >= 6) continue;
                float btnW = (contentW - GAP) * 0.5f;
                float btnX = contentX + col * (btnW + GAP);
                float btnY = currentY + row * (BUTTON_HEIGHT + GAP);
                if (isHovered(mouseX, mouseY, btnX, btnY, btnW, BUTTON_HEIGHT)) {
                    runAction(index);
                    return true;
                }
            }
        }
        currentY += BUTTON_HEIGHT * 3.0f + GAP * 3.0f;
        if (!status.isEmpty()) currentY += ROW_HEIGHT;

        for (String name : configsForFrame()) {
            float deleteX = contentX + contentW - 18.0f;
            if (isHovered(mouseX, mouseY, deleteX, currentY + 3.0f, 16.0f, 16.0f)) {
                try {
                    ConfigHolder.INSTANCE.deleteConfig(name);
                    status = EpsilonTranslations.Gui.DROPDOWN_STATUS_DELETED.getTranslatedName() + " " + name;
                } catch (Exception e) {
                    status = errorText(e);
                }
                return true;
            }
            if (isHovered(mouseX, mouseY, contentX, currentY, contentW, ROW_HEIGHT)) {
                try {
                    ConfigHolder.INSTANCE.switchConfig(name);
                    inputField.setText(name);
                    inputField.setCursorToEnd();
                    status = EpsilonTranslations.Gui.DROPDOWN_STATUS_SWITCHED.getTranslatedName() + " " + name;
                } catch (Exception e) {
                    status = errorText(e);
                }
                return true;
            }
            currentY += ROW_HEIGHT + GAP;
        }
        return false;
    }

    private void runAction(int index) {
        String value = inputField.getText().trim();
        try {
            switch (index) {
                case 0 -> {
                    if (!value.isEmpty()) {
                        String saved = ConfigHolder.INSTANCE.saveAsConfig(value);
                        inputField.setText(saved);
                        inputField.setCursorToEnd();
                        status = EpsilonTranslations.Gui.DROPDOWN_STATUS_SAVED.getTranslatedName() + " " + saved;
                    }
                }
                case 1 -> {
                    ConfigHolder.INSTANCE.reloadOrThrow();
                    status = EpsilonTranslations.Gui.DROPDOWN_STATUS_RELOADED.getTranslatedName();
                }
                case 2 -> {
                    status = EpsilonTranslations.Gui.DROPDOWN_STATUS_EXPORTED.getTranslatedName() + " " + ConfigHolder.INSTANCE.exportActiveConfigToZip(value).getFileName();
                }
                case 3 -> {
                    if (!value.isEmpty()) {
                        String imported = ConfigHolder.INSTANCE.importConfigFromZip(value);
                        inputField.setText(imported);
                        inputField.setCursorToEnd();
                        status = EpsilonTranslations.Gui.DROPDOWN_STATUS_IMPORTED.getTranslatedName() + " " + imported;
                    }
                }
                case 4 -> {
                    if (!value.isEmpty()) {
                        String created = ConfigHolder.INSTANCE.newDefaultConfig(value);
                        inputField.setText(created);
                        inputField.setCursorToEnd();
                        status = EpsilonTranslations.Gui.DROPDOWN_STATUS_CREATED.getTranslatedName() + " " + created;
                    }
                }
                case 5 ->
                        status = EpsilonTranslations.Gui.CONFIG_ACTION_OPEN_FOLDER.getTranslatedName() + " " + ConfigFolderOpener.openConfigFolder();
                default -> {
                }
            }
        } catch (Exception e) {
            status = errorText(e);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!inputField.isFocused()) return false;
        if (keyCode == 256) {
            inputField.blur();
            return true;
        }
        return inputField.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(String typedText) {
        return inputField.charTyped(typedText);
    }

    @Override
    public boolean hasActiveInput() {
        return inputField.isFocused();
    }

    private String errorText(Exception e) {
        String message = e.getMessage();
        return EpsilonTranslations.Gui.CONFIG_ERROR_TITLE.getTranslatedName() + ": " + (message == null || message.isBlank() ? e.getClass().getSimpleName() : message);
    }

    private float getCenteredTextY(UiTextMetrics textMetrics, float boxY, float boxH, float scale) {
        return boxY + (boxH - textMetrics.textHeight(scale)) / 2.0f;
    }

    private List<String> configsForFrame() {
        int frameId = getRenderFrameId();
        if (cachedConfigFrameId != frameId) {
            cachedConfigs = ConfigHolder.INSTANCE.listConfigs();
            cachedConfigFrameId = frameId;
        }
        return cachedConfigs;
    }

}
