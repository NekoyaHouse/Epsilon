package com.github.epsilon.gui.panel.component.setting;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.panel.component.SettingRow;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.settings.impl.StringSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;
import java.util.Objects;

import static com.github.epsilon.Constants.mc;

public class StringSettingRow extends SettingRow<StringSetting> {

    private static final float FIELD_SCALE = 0.60f;
    private static final float FIELD_WIDTH = 120.0f;
    private static final int MAX_LENGTH = 256;

    private TextRenderer textMetrics;
    private boolean focused;
    private String inputBuffer;
    private int cursorIndex;
    private int selectionAnchor = -1;

    public StringSettingRow(StringSetting setting) {
        super(setting);
    }

    @Override
    public void buildUi(UiTree.Scope scope, GuiGraphicsExtractor guiGraphics, TextRenderer textRenderer, UiRect bounds, float hoverProgress, int mouseX, int mouseY, float partialTick) {
        this.textMetrics = textRenderer;
        float labelScale = 0.68f;
        float labelY = (bounds.height() - textRenderer.getHeight(labelScale)) / 2.0f;
        scope.roundRect(0.0f, 0.0f, bounds.width(), bounds.height(), MD3Theme.CARD_RADIUS, MD3Theme.rowSurface(hoverProgress));
        scope.text(setting.getDisplayName(), MD3Theme.ROW_CONTENT_INSET, labelY, labelScale, MD3Theme.TEXT_PRIMARY);

        UiRect fieldBounds = getFieldBounds(bounds);
        boolean hovered = fieldBounds.contains(mouseX, mouseY);
        float fieldHover = hovered ? 1.0f : hoverProgress * 0.55f;

        String displaySource = focused ? getDisplayBuffer() : normalize(setting.getValue());
        DisplaySlice slice = buildDisplaySlice(displaySource, fieldBounds, focused);
        UiTree.SelectionRange selection = null;
        if (focused && hasSelection()) {
            int selectionStart = Math.max(slice.start(), getSelectionStart());
            int selectionEnd = Math.min(slice.end(), getSelectionEnd());
            if (selectionEnd > selectionStart) {
                selection = new UiTree.SelectionRange(selectionStart - slice.start(), selectionEnd - slice.start());
            }
        }
        scope.input(fieldBounds.relativeTo(bounds), focused, fieldHover,
                0.0f, new Color(0, 0, 0, 0), 0.0f,
                slice.textX() - fieldBounds.x(), slice.text(), FIELD_SCALE, MD3Theme.filledFieldContent(focused),
                selection, selection == null ? null : MD3Theme.withAlpha(MD3Theme.filledFieldIndicator(focused, fieldHover), 90),
                focused ? slice.caretIndex() : null, focused ? MD3Theme.filledFieldCaret(focused) : null,
                null, 0.0f, null);
        if (focused) {
            float caretX = slice.textX() + textRenderer.getWidth(slice.text().substring(0, Math.min(slice.caretIndex(), slice.text().length())), FIELD_SCALE);
            caretX = Math.min(caretX, fieldBounds.right() - 5.0f);
            float textY = fieldBounds.y() + (fieldBounds.height() - textRenderer.getHeight(FIELD_SCALE)) / 2.0f;
            IMEFocusHelper.updateCursorPos(caretX, textY);
        }
    }

    @Override
    public boolean mouseClicked(UiRect bounds, MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0) {
            return false;
        }
        UiRect fieldBounds = getFieldBounds(bounds);
        if (!fieldBounds.contains(event.x(), event.y())) {
            return false;
        }
        boolean wasFocused = focused;
        focused = true;
        IMEFocusHelper.activate();
        if (!wasFocused || inputBuffer == null) {
            inputBuffer = normalize(setting.getValue());
        }
        cursorIndex = getCursorIndex(event.x(), fieldBounds);
        clearSelection();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!focused) {
            return false;
        }
        if (isControlDown()) {
            return handleControlShortcut(event.key());
        }
        return switch (event.key()) {
            case 257, 335 -> {
                commitInput();
                focused = false;
                IMEFocusHelper.deactivate();
                clearSelection();
                yield true;
            }
            case 256 -> {
                focused = false;
                IMEFocusHelper.deactivate();
                inputBuffer = null;
                clearSelection();
                yield true;
            }
            case 259 -> {
                deleteBackward();
                yield true;
            }
            case 261 -> {
                deleteForward();
                yield true;
            }
            case 263 -> {
                cursorIndex = Math.max(0, cursorIndex - 1);
                clearSelection();
                yield true;
            }
            case 262 -> {
                cursorIndex = Math.min(getDisplayBuffer().length(), cursorIndex + 1);
                clearSelection();
                yield true;
            }
            case 268 -> {
                cursorIndex = 0;
                clearSelection();
                yield true;
            }
            case 269 -> {
                cursorIndex = getDisplayBuffer().length();
                clearSelection();
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!focused) {
            return false;
        }
        String value = event.codepointAsString();
        if (value.isEmpty()) {
            return false;
        }
        String current = getDisplayBuffer();
        int selectionLength = hasSelection() ? getSelectionEnd() - getSelectionStart() : 0;
        if (current.length() - selectionLength >= MAX_LENGTH) {
            return false;
        }
        replaceSelection(value);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused && this.focused) {
            commitInput();
            inputBuffer = null;
            IMEFocusHelper.deactivate();
        }
        this.focused = focused;
        if (focused && inputBuffer == null) {
            inputBuffer = normalize(setting.getValue());
            cursorIndex = inputBuffer.length();
            IMEFocusHelper.activate();
        }
        if (!focused) {
            clearSelection();
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    private UiRect getFieldBounds(UiRect bounds) {
        return new UiRect(bounds.right() - MD3Theme.ROW_TRAILING_INSET - FIELD_WIDTH, bounds.y() + 4.0f, FIELD_WIDTH, 18.0f);
    }

    private String getDisplayBuffer() {
        return inputBuffer == null ? normalize(setting.getValue()) : inputBuffer;
    }

    private void commitInput() {
        String value = inputBuffer == null ? normalize(setting.getValue()) : inputBuffer;
        value = value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
        applyValue(value);
        inputBuffer = value;
        cursorIndex = inputBuffer.length();
        clearSelection();
    }

    private int getCursorIndex(double mouseX, UiRect fieldBounds) {
        String text = getDisplayBuffer();
        DisplaySlice slice = buildDisplaySlice(text, fieldBounds, true);
        TextRenderer metrics = textMetrics();
        for (int i = 0; i <= slice.text().length(); i++) {
            float width = metrics.getWidth(slice.text().substring(0, i), FIELD_SCALE);
            if (mouseX <= slice.textX() + width) {
                return slice.start() + i;
            }
        }
        return slice.start() + slice.text().length();
    }

    private DisplaySlice buildDisplaySlice(String value, UiRect fieldBounds, boolean editing) {
        String safeValue = value == null ? "" : value;
        float horizontalInset = 6.0f;
        float availableWidth = Math.max(8.0f, fieldBounds.width() - horizontalInset * 2.0f);
        if (!editing) {
            String shown = fitWithEllipsis(safeValue, availableWidth);
            return new DisplaySlice(shown, fieldBounds.x() + horizontalInset, 0, 0, shown.length());
        }

        int safeCursor = Math.clamp(cursorIndex, 0, safeValue.length());
        int start = 0;
        int end = safeValue.length();
        TextRenderer metrics = textMetrics();

        if (metrics.getWidth(safeValue, FIELD_SCALE) <= availableWidth) {
            return new DisplaySlice(safeValue, fieldBounds.x() + horizontalInset, safeCursor, 0, safeValue.length());
        }

        int low = 0;
        int high = safeCursor;
        int bestStart = safeCursor;
        while (low <= high) {
            int mid = (low + high) / 2;
            String beforeCaret = safeValue.substring(mid, safeCursor);
            if (metrics.getWidth(beforeCaret, FIELD_SCALE) <= availableWidth - 2.0f) {
                bestStart = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        start = bestStart;

        low = safeCursor;
        high = safeValue.length();
        int bestEnd = safeCursor;
        while (low <= high) {
            int mid = (low + high) / 2;
            String candidate = safeValue.substring(start, mid);
            if (metrics.getWidth(candidate, FIELD_SCALE) <= availableWidth) {
                bestEnd = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        end = bestEnd;
        String shown = safeValue.substring(start, end);
        return new DisplaySlice(shown, fieldBounds.x() + horizontalInset, safeCursor - start, start, end);
    }

    private String fitWithEllipsis(String value, float availableWidth) {
        TextRenderer metrics = textMetrics();
        if (metrics.getWidth(value, FIELD_SCALE) <= availableWidth) {
            return value;
        }
        String ellipsis = "...";
        float ellipsisWidth = metrics.getWidth(ellipsis, FIELD_SCALE);
        if (ellipsisWidth >= availableWidth) {
            return "";
        }
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = value.substring(0, mid) + ellipsis;
            if (metrics.getWidth(candidate, FIELD_SCALE) <= availableWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return value.substring(0, low) + ellipsis;
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    private boolean handleControlShortcut(int key) {
        return switch (key) {
            case 65 -> {
                selectAll();
                yield true;
            }
            case 67 -> {
                copySelection();
                yield true;
            }
            case 86 -> {
                pasteClipboard();
                yield true;
            }
            default -> false;
        };
    }

    private void selectAll() {
        String current = getDisplayBuffer();
        cursorIndex = current.length();
        selectionAnchor = 0;
    }

    private void copySelection() {
        if (!hasSelection()) {
            return;
        }
        String current = getDisplayBuffer();
        mc.keyboardHandler.setClipboard(current.substring(getSelectionStart(), getSelectionEnd()));
    }

    private void pasteClipboard() {
        String clipboard = mc.keyboardHandler.getClipboard();
        if (clipboard.isEmpty()) {
            return;
        }
        String sanitized = sanitizeClipboard(clipboard);
        if (sanitized.isEmpty()) {
            return;
        }
        String current = getDisplayBuffer();
        int selectionLength = hasSelection() ? getSelectionEnd() - getSelectionStart() : 0;
        int available = MAX_LENGTH - (current.length() - selectionLength);
        if (available <= 0) {
            return;
        }
        if (sanitized.length() > available) {
            sanitized = sanitized.substring(0, available);
        }
        replaceSelection(sanitized);
    }

    private String sanitizeClipboard(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint >= 32 && codePoint != 127) {
                builder.appendCodePoint(codePoint);
            }
        });
        return builder.toString();
    }

    private void replaceSelection(String value) {
        String current = getDisplayBuffer();
        int start = hasSelection() ? getSelectionStart() : cursorIndex;
        int end = hasSelection() ? getSelectionEnd() : cursorIndex;
        inputBuffer = current.substring(0, start) + value + current.substring(end);
        cursorIndex = start + value.length();
        clearSelection();
        previewInput();
    }

    private void deleteBackward() {
        if (hasSelection()) {
            replaceSelection("");
            return;
        }
        if (inputBuffer != null && cursorIndex > 0) {
            inputBuffer = inputBuffer.substring(0, cursorIndex - 1) + inputBuffer.substring(cursorIndex);
            cursorIndex--;
            previewInput();
        }
    }

    private void deleteForward() {
        if (hasSelection()) {
            replaceSelection("");
            return;
        }
        if (inputBuffer != null && cursorIndex < inputBuffer.length()) {
            inputBuffer = inputBuffer.substring(0, cursorIndex) + inputBuffer.substring(cursorIndex + 1);
            previewInput();
        }
    }

    private void previewInput() {
        if (!setting.isApplyWhenRelease()) {
            applyValue(getDisplayBuffer());
        }
    }

    private void applyValue(String value) {
        if (!Objects.equals(setting.getValue(), value)) {
            setting.setValue(value);
        }
    }

    private boolean hasSelection() {
        return selectionAnchor >= 0 && selectionAnchor != cursorIndex;
    }

    private int getSelectionStart() {
        return Math.min(selectionAnchor, cursorIndex);
    }

    private int getSelectionEnd() {
        return Math.max(selectionAnchor, cursorIndex);
    }

    private void clearSelection() {
        selectionAnchor = -1;
    }

    private boolean isControlDown() {
        return InputConstants.isKeyDown(mc.getWindow(), 341) || InputConstants.isKeyDown(mc.getWindow(), 345);
    }

    private TextRenderer textMetrics() {
        return textMetrics == null ? FALLBACK_TEXT_METRICS : textMetrics;
    }

    private record DisplaySlice(String text, float textX, int caretIndex, int start, int end) {
    }

}
