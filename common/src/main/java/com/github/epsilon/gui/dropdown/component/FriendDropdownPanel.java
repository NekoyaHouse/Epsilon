package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.DropdownTextField;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.managers.FriendManager;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class FriendDropdownPanel extends AbstractDropdownPanel {

    private static final float ROW_HEIGHT = 20.0f;
    private static final float FIELD_HEIGHT = 18.0f;
    private static final float GAP = 4.0f;
    private static final float PADDING = 6.0f;
    private static final float ADD_BUTTON_WIDTH = 20.0f;
    private static final float REMOVE_BUTTON_SIZE = 16.0f;

    private final DropdownTextField inputField = new DropdownTextField(32);

    public FriendDropdownPanel(int panelIndex) {
        super("friend", EpsilonTranslations.Gui.TAB_FRIEND, "", panelIndex);
    }

    @Override
    protected float computeContentHeight() {
        int friendCount = FriendManager.INSTANCE.getFriends().size();
        return PADDING * 2.0f + FIELD_HEIGHT + GAP + Math.max(ROW_HEIGHT, friendCount * (ROW_HEIGHT + GAP));
    }

    @Override
    protected void drawPanelContent(UiTree.Scope scope, UiTextMetrics textMetrics, int mouseX, int mouseY, float visibleHeight) {
        float fieldX = x + PADDING;
        float fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float fieldW = width - PADDING * 2.0f - 24.0f;
        inputField.draw(scope, textMetrics, fieldX, fieldY, fieldW, FIELD_HEIGHT, mouseX, mouseY, EpsilonTranslations.Gui.FRIEND_INPUT_PLACEHOLDER.getTranslatedName(), DropdownTheme.SETTING_TEXT_SCALE);

        float addX = fieldX + fieldW + GAP;
        scope.roundRect(addX, fieldY, ADD_BUTTON_WIDTH, FIELD_HEIGHT, DropdownTheme.BUTTON_RADIUS, isHovered(mouseX, mouseY, addX, fieldY, ADD_BUTTON_WIDTH, FIELD_HEIGHT) ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER_LOW);
        float plusScale = 1.0f;
        float addTextX = addX + (ADD_BUTTON_WIDTH - textMetrics.textWidth("+", plusScale)) / 2.0f;
        float addTextY = fieldY + (FIELD_HEIGHT - textMetrics.textHeight(plusScale)) / 2.0f;
        scope.text("+", addTextX, addTextY, plusScale, MD3Theme.ON_PRIMARY_CONTAINER);

        List<String> friends = FriendManager.INSTANCE.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        float rowY = fieldY + FIELD_HEIGHT + GAP;
        if (friends.isEmpty()) {
            String emptyText = EpsilonTranslations.Gui.FRIEND_EMPTY.getTranslatedName();
            float emptyScale = 0.7f;
            float emptyTextX = x + PADDING + (width - PADDING * 2.0f - textMetrics.textWidth(emptyText, emptyScale)) / 2.0f;
            float emptyTextY = rowY + (ROW_HEIGHT - textMetrics.textHeight(emptyScale)) / 2.0f;
            scope.text(emptyText, emptyTextX, emptyTextY, emptyScale, MD3Theme.TEXT_MUTED);
            return;
        }
        for (String name : friends) {
            float rowX = x + PADDING;
            float rowWidth = width - PADDING * 2.0f;
            boolean hovered = isHovered(mouseX, mouseY, rowX, rowY, rowWidth, ROW_HEIGHT);
            scope.roundRect(rowX, rowY, rowWidth, ROW_HEIGHT, DropdownTheme.BUTTON_RADIUS, hovered ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER_LOW);
            float removeX = x + width - PADDING - 18.0f;
            float removeY = rowY + (ROW_HEIGHT - REMOVE_BUTTON_SIZE) * 0.5f;
            String displayName = trimToWidth(name, DropdownTheme.SETTING_TEXT_SCALE, width - 38.0f, textMetrics);
            scope.text(displayName, x + PADDING + 6.0f, rowY + (ROW_HEIGHT - textMetrics.textHeight(DropdownTheme.SETTING_TEXT_SCALE)) / 2.0f, DropdownTheme.SETTING_TEXT_SCALE, MD3Theme.TEXT_PRIMARY);
            float iconScale = 0.8f;
            float removeTextX = removeX + (REMOVE_BUTTON_SIZE - textMetrics.textWidth(IconChars.CLOSE, iconScale, StaticFontLoader.ICONS)) / 2.0f;
            float removeTextY = removeY + (REMOVE_BUTTON_SIZE - textMetrics.textHeight(iconScale, StaticFontLoader.ICONS)) / 2.0f;
            scope.text(IconChars.CLOSE, removeTextX, removeTextY, iconScale, isHovered(mouseX, mouseY, removeX, removeY, REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE) ? MD3Theme.ERROR : MD3Theme.TEXT_MUTED, StaticFontLoader.ICONS);
            rowY += ROW_HEIGHT + GAP;
        }
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        float fieldX = x + PADDING;
        float fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float fieldW = width - PADDING * 2.0f - 24.0f;
        if (inputField.focusIfContains(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_HEIGHT)) {
            return true;
        }
        if (isHovered(mouseX, mouseY, fieldX + fieldW + GAP, fieldY, ADD_BUTTON_WIDTH, FIELD_HEIGHT)) {
            addFriend();
            return true;
        }
        inputField.blur();

        float rowY = fieldY + FIELD_HEIGHT + GAP;
        for (String name : FriendManager.INSTANCE.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            float removeX = x + width - PADDING - 18.0f;
            float removeY = rowY + (ROW_HEIGHT - REMOVE_BUTTON_SIZE) * 0.5f;
            if (isHovered(mouseX, mouseY, removeX, removeY, REMOVE_BUTTON_SIZE, REMOVE_BUTTON_SIZE)) {
                FriendManager.INSTANCE.removeFriend(name);
                return true;
            }
            rowY += ROW_HEIGHT + GAP;
        }
        return false;
    }

    @Override
    public void onGlobalMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        float fieldX = x + PADDING;
        float fieldY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float fieldW = width - PADDING * 2.0f - 24.0f;
        if (!isHovered(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_HEIGHT)) {
            inputField.blur();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!inputField.isFocused()) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            addFriend();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
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

    private void addFriend() {
        String name = inputField.getText().trim();
        if (!name.isEmpty() && !FriendManager.INSTANCE.isFriend(name)) {
            FriendManager.INSTANCE.addFriend(name);
        }
        inputField.clear();
    }

}
