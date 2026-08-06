package com.github.epsilon.gui.lib.control;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.StaticFontLoader;
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

public class UiTextField {

    private static final long ANIM_DURATION = 120L;
    private static final float DEFAULT_TEXT_INSET = 10.0f;
    private static final float ICON_GAP = 6.0f;

    private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, ANIM_DURATION);
    private final Animation focusAnimation = new Animation(Easing.EASE_OUT_CUBIC, ANIM_DURATION);

    private int maxLength;
    private boolean focused;
    private String text = "";
    private int cursor;

    private String leadingIcon;
    private float leadingIconScale = 1.0f;

    public UiTextField(int maxLength) {
        this.maxLength = maxLength;
        hoverAnimation.setStartValue(0.0f);
        focusAnimation.setStartValue(0.0f);
    }

    public UiTextField leadingIcon(String glyph, float scale) {
        this.leadingIcon = glyph;
        this.leadingIconScale = scale;
        return this;
    }

    public void buildUi(UiTree.Scope scope, UiRect bounds, double mouseX, double mouseY, TextRenderer textRenderer, String placeholder, float textScale) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        float hoverProgress = scope.animate(hoverAnimation, hovered);
        float focusProgress = scope.animate(focusAnimation, focused);

        float iconWidth = leadingIcon == null
                ? 0.0f
                : textRenderer.getWidth(leadingIcon, leadingIconScale, StaticFontLoader.ICONS) + ICON_GAP;
        float textInset = DEFAULT_TEXT_INSET + iconWidth;

        float textHeight = textRenderer.getHeight(textScale);
        float textX = bounds.x() + textInset;
        float textY = bounds.y() + (bounds.height() - textHeight) / 2.0f;

        boolean showPlaceholder = text.isEmpty() && !focused;
        String display = showPlaceholder ? placeholder : text;
        Color textColor = showPlaceholder ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY;

        scope.input(bounds, focused, hoverProgress,
                focusProgress, MD3Theme.PRIMARY, 1.0f,
                textInset, display, textScale, textColor,
                null, null,
                focused ? safeCursor() : null, focused ? MD3Theme.TEXT_PRIMARY : null,
                null, 0.0f, null);

        if (leadingIcon != null) {
            float glyphHeight = textRenderer.getHeight(leadingIconScale, StaticFontLoader.ICONS);
            Color iconColor = focused ? MD3Theme.PRIMARY : MD3Theme.TEXT_MUTED;
            scope.text(leadingIcon, bounds.x() + DEFAULT_TEXT_INSET, bounds.y() + (bounds.height() - glyphHeight) / 2.0f, leadingIconScale, iconColor, StaticFontLoader.ICONS);
        }

        if (focused) {
            float caretX = textX + textRenderer.getWidth(text.substring(0, safeCursor()), textScale);
            IMEFocusHelper.updateCursorPos(caretX, textY);
        }
    }

    public boolean focusIfContains(UiRect bounds, double mouseX, double mouseY) {
        if (bounds == null || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        focus();
        return true;
    }

    public void focus() {
        focused = true;
        cursor = text.length();
        IMEFocusHelper.activate();
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

    public boolean isEmpty() {
        return text.isEmpty();
    }

    public String getText() {
        return text;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }
        cursor = Math.min(cursor, text.length());
    }

    public void clear() {
        text = "";
        cursor = 0;
    }

    private int safeCursor() {
        return Math.clamp(cursor, 0, text.length());
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

    private static boolean isControlDown() {
        var window = mc.getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

}
