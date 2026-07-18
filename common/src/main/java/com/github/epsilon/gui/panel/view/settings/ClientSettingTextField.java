package com.github.epsilon.gui.panel.view.settings;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

import static com.github.epsilon.Constants.mc;

public class ClientSettingTextField {

    private static final long HOVER_DURATION = 120L;
    private final int maxLength;
    private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, HOVER_DURATION);
    private final Animation focusAnimation = new Animation(Easing.EASE_OUT_CUBIC, HOVER_DURATION);

    private boolean focused;
    private String text = "";
    private int cursor;

    public ClientSettingTextField(int maxLength) {
        this.maxLength = maxLength;
        hoverAnimation.setStartValue(0.0f);
        focusAnimation.setStartValue(0.0f);
    }

    public void buildUi(UiTree.Scope scope, UiRect bounds, int mouseX, int mouseY,
                        TextRenderer textRenderer, String placeholder, float textScale, String trailingHint) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        float hoverProgress = scope.animate(hoverAnimation, hovered);
        float focusProgress = scope.animate(focusAnimation, focused);
        float textInset = 10.0f;
        float textHeight = textRenderer.getHeight(textScale);
        float textX = bounds.x() + textInset;
        float textY = bounds.y() + (bounds.height() - textHeight) / 2.0f;

        boolean showPlaceholder = text.isEmpty() && !focused;
        String display = showPlaceholder ? placeholder : text;
        Color textColor = showPlaceholder ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY;
        scope.input(bounds, focused, hovered ? 0.6f : 0.0f,
                focusProgress, MD3Theme.PRIMARY, 1.0f,
                textInset, display, textScale, textColor,
                null, null,
                focused ? Math.min(cursor, text.length()) : null, focused ? MD3Theme.TEXT_PRIMARY : null,
                focused && trailingHint != null && !trailingHint.isBlank() && !text.isEmpty() ? trailingHint : null,
                0.56f,
                focused && trailingHint != null && !trailingHint.isBlank() && !text.isEmpty() ? MD3Theme.TEXT_MUTED : null);

        if (focused) {
            int safeCursor = Math.min(cursor, text.length());
            float caretX = textX + textRenderer.getWidth(text.substring(0, safeCursor), textScale);
            IMEFocusHelper.updateCursorPos(caretX, textY);
        }
    }

    public boolean focusIfContains(UiRect bounds, double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }
        focused = true;
        cursor = text.length();
        IMEFocusHelper.activate();
        return true;
    }

    public void blur() {
        if (focused) {
            focused = false;
            IMEFocusHelper.deactivate();
        }
    }

    public boolean keyPressed(KeyEvent event) {
        if (!focused) {
            return false;
        }

        if (isControlDown()) {
            return handleControlShortcut(event.key());
        }

        return switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0 && !text.isEmpty()) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                }
                yield true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                cursor = Math.max(0, cursor - 1);
                yield true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                cursor = Math.min(text.length(), cursor + 1);
                yield true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                yield true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = text.length();
                yield true;
            }
            default -> false;
        };
    }

    public boolean charTyped(CharacterEvent event) {
        if (!focused) {
            return false;
        }
        String typed = event.codepointAsString();
        if (typed.isEmpty()) {
            return true;
        }
        insertText(typed);
        return true;
    }

    public boolean hasActiveAnimations() {
        return !hoverAnimation.isFinished() || !focusAnimation.isFinished();
    }

    public boolean isFocused() {
        return focused;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : clampToMaxLength(text);
        this.cursor = Math.min(this.cursor, this.text.length());
    }

    public void clear() {
        text = "";
        cursor = 0;
    }

    public void setCursorToEnd() {
        cursor = text.length();
    }

    private boolean handleControlShortcut(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_A -> {
                cursor = text.length();
                yield true;
            }
            case GLFW.GLFW_KEY_V -> {
                String clipboard = mc.keyboardHandler.getClipboard();
                if (!clipboard.isEmpty()) {
                    String sanitized = clipboard.codePoints()
                            .filter(cp -> cp >= 32 && cp != 127)
                            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                            .toString();
                    if (!sanitized.isEmpty()) {
                        insertText(sanitized);
                    }
                }
                yield true;
            }
            default -> false;
        };
    }

    private void insertText(String inserted) {
        if (inserted == null || inserted.isEmpty()) {
            return;
        }
        int available = maxLength - text.length();
        if (available <= 0) {
            return;
        }
        String safeInsert = inserted.length() > available ? inserted.substring(0, available) : inserted;
        text = text.substring(0, cursor) + safeInsert + text.substring(cursor);
        cursor += safeInsert.length();
    }

    private String clampToMaxLength(String value) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private boolean isControlDown() {
        var window = mc.getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}


