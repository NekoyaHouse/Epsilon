package com.github.epsilon.gui.troll;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.widget.DropdownTextField;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.managers.FriendManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TrollFriendWindow extends TrollWindow {

    private static final float PADDING = 6.0f;
    private static final float FIELD_HEIGHT = 18.0f;
    private static final float GAP = 4.0f;
    private static final float ROW_HEIGHT = 20.0f;
    private static final float ADD_BUTTON_WIDTH = 20.0f;
    private static final float REMOVE_BUTTON_SIZE = 16.0f;
    private static final int MAX_NAME_LENGTH = 32;

    private final DropdownTextField inputField = new DropdownTextField(MAX_NAME_LENGTH);
    private final List<FriendRow> rows = new ArrayList<>();

    private UiRect inputBounds = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);
    private UiRect addBounds = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);
    private UiRect listViewport = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);

    @Override
    public void layout(TextRenderer metrics, float screenWidth, float screenHeight) {
        List<String> friends = sortedFriends();
        headerHeight = TrollTheme.headerHeight(metrics);
        float width = Math.max(190.0f, metrics.getWidth(title(), TrollTheme.TEXT_SCALE) + 30.0f);
        for (String friend : friends) {
            width = Math.max(width, metrics.getWidth(friend, TrollTheme.TEXT_SCALE) + 72.0f);
        }
        width = Math.min(Math.min(width, 280.0f), screenWidth);

        float fixedHeight = headerHeight + PADDING + FIELD_HEIGHT + GAP + PADDING;
        float listHeight = Math.max(ROW_HEIGHT, friends.size() * (ROW_HEIGHT + GAP));
        float height = Math.min(Math.min(fixedHeight + listHeight, 220.0f), screenHeight);
        size(width, height, screenWidth, screenHeight);

        float contentWidth = Math.max(1.0f, bounds.width() - PADDING * 2.0f);
        inputBounds = new UiRect(
                bounds.x() + PADDING,
                bounds.y() + headerHeight + PADDING,
                Math.max(1.0f, contentWidth - ADD_BUTTON_WIDTH - GAP),
                FIELD_HEIGHT
        );
        addBounds = new UiRect(inputBounds.right() + GAP, inputBounds.y(), ADD_BUTTON_WIDTH, FIELD_HEIGHT);
        float listTop = inputBounds.bottom() + GAP;
        listViewport = new UiRect(
                bounds.x() + PADDING,
                listTop,
                contentWidth,
                Math.max(1.0f, bounds.bottom() - PADDING - listTop)
        );

        float contentHeight = friends.size() * (ROW_HEIGHT + GAP);
        scrollPosition = scroll.update(Math.max(0.0f, contentHeight - listViewport.height()));

        rows.clear();
        float rowY = listViewport.y() - scrollPosition;
        for (String friend : friends) {
            UiRect rowBounds = new UiRect(listViewport.x(), rowY, listViewport.width(), ROW_HEIGHT);
            rows.add(new FriendRow(friend, rowBounds, removeBounds(rowBounds)));
            rowY += ROW_HEIGHT + GAP;
        }
    }

    @Override
    public void draw(UiTree.Scope scope, TextRenderer metrics, int mouseX, int mouseY) {
        TrollTheme.window(scope, bounds, headerHeight, title(), metrics);
        inputField.draw(scope, TrollTheme.metrics(metrics), inputBounds.x(), inputBounds.y(),
                inputBounds.width(), inputBounds.height(), mouseX, mouseY,
                EpsilonTranslations.Gui.FRIEND_INPUT_PLACEHOLDER.getTranslatedName(), TrollTheme.TEXT_SCALE, true);

        boolean addHovered = addBounds.contains(mouseX, mouseY);
        scope.rect(1, addBounds.x(), addBounds.y(), addBounds.width(), addBounds.height(),
                TrollTheme.alpha(TrollTheme.primary(), addHovered ? 190 : 70));
        scope.textWithShadow(3, "+", addBounds.centerX() - metrics.getWidth("+", TrollTheme.TEXT_SCALE) / 2.0f,
                addBounds.y() + (addBounds.height() - metrics.getHeight(TrollTheme.TEXT_SCALE)) / 2.0f,
                TrollTheme.TEXT_SCALE, TrollTheme.text());

        scope.scissor(listViewport, content -> {
            if (rows.isEmpty()) {
                String empty = EpsilonTranslations.Gui.FRIEND_EMPTY.getTranslatedName();
                content.textWithShadow(2, empty,
                        listViewport.centerX() - metrics.getWidth(empty, TrollTheme.VALUE_SCALE) / 2.0f,
                        listViewport.y() + (listViewport.height() - metrics.getHeight(TrollTheme.VALUE_SCALE)) / 2.0f,
                        TrollTheme.VALUE_SCALE, TrollTheme.text());
                return;
            }
            for (FriendRow row : rows) {
                boolean hovered = row.bounds().contains(mouseX, mouseY);
                content.rect(1, row.bounds().x(), row.bounds().y(), row.bounds().width(), row.bounds().height(),
                        new Color(255, 255, 255, hovered ? 30 : 16));
                content.textWithShadow(2, TrollTheme.trim(row.name(), metrics, TrollTheme.TEXT_SCALE,
                                row.bounds().width() - REMOVE_BUTTON_SIZE - 12.0f),
                        row.bounds().x() + 5.0f,
                        row.bounds().y() + (row.bounds().height() - metrics.getHeight(TrollTheme.TEXT_SCALE)) / 2.0f,
                        TrollTheme.TEXT_SCALE, TrollTheme.text());

                boolean removeHovered = row.removeBounds().contains(mouseX, mouseY);
                content.rect(3, row.removeBounds().x(), row.removeBounds().y(),
                        row.removeBounds().width(), row.removeBounds().height(),
                        TrollTheme.alpha(TrollTheme.primary(), removeHovered ? 180 : 40));
                float iconScale = TrollTheme.VALUE_SCALE;
                content.textWithShadow(4, IconChars.CLOSE,
                        row.removeBounds().centerX() - metrics.getWidth(IconChars.CLOSE, iconScale, StaticFontLoader.ICONS) / 2.0f,
                        row.removeBounds().y() + (row.removeBounds().height() - metrics.getHeight(iconScale, StaticFontLoader.ICONS)) / 2.0f,
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

        if (addBounds.contains(event.x(), event.y())) {
            addFriend();
            return true;
        }
        if (listViewport.contains(event.x(), event.y())) {
            for (FriendRow row : rows) {
                if (row.removeBounds().contains(event.x(), event.y())) {
                    FriendManager.INSTANCE.removeFriend(row.name());
                    return true;
                }
            }
        }
        return bounds.contains(event.x(), event.y());
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
        return switch (event.key()) {
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                addFriend();
                yield true;
            }
            case InputConstants.KEY_ESCAPE -> {
                inputField.blur();
                yield true;
            }
            default -> inputField.keyPressed(event);
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return inputField.charTyped(event);
    }

    @Override
    public void blurInput() {
        inputField.blur();
    }

    private void addFriend() {
        String name = inputField.getText().trim();
        if (!name.isEmpty() && !FriendManager.INSTANCE.isFriend(name)) {
            FriendManager.INSTANCE.addFriend(name);
        }
        inputField.clear();
    }

    private String title() {
        return EpsilonTranslations.Gui.TAB_FRIEND.getTranslatedName();
    }

    private List<String> sortedFriends() {
        return FriendManager.INSTANCE.getFriends().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private UiRect removeBounds(UiRect rowBounds) {
        return new UiRect(
                rowBounds.right() - REMOVE_BUTTON_SIZE - 3.0f,
                rowBounds.y() + (rowBounds.height() - REMOVE_BUTTON_SIZE) / 2.0f,
                REMOVE_BUTTON_SIZE,
                REMOVE_BUTTON_SIZE
        );
    }

    private record FriendRow(String name, UiRect bounds, UiRect removeBounds) {
    }

}
