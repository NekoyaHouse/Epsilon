package com.github.epsilon.gui.panel.utils;

import net.minecraft.client.gui.screens.Screen;

import static com.github.epsilon.Constants.mc;

/**
 * 管理 OS 级别文本/IME 输入焦点的工具类。
 * <p>
 * 同一时间可能有多个自定义文本框同时存在（例如 Panel GUI 中的多个搜索框），
 * 但 OS 级别的 IME 输入只能全局开启或关闭一次。
 * 通过引用计数来确保：仅第一个获取焦点的文本框开启 IME 输入，
 * 仅最后一个失去焦点的文本框关闭 IME 输入。
 */
public class IMEFocusHelper {

    public static float activeCursorX = 0.0f;
    public static float activeCursorY = 0.0f;

    /**
     * 引用计数：记录当前有多少个文本框正在请求 IME 输入焦点
     */
    private static int refCount = 0;

    private IMEFocusHelper() {
    }

    /**
     * Enables OS-level text / IME input.
     * Call this whenever a custom text field gains focus.
     */
    public static void activate() {
        // 增加引用计数；仅第一个获取焦点的文本框真正开启 IME 输入
        refCount++;
        if (refCount == 1) {
            Screen screen = mc.screen;
            if (screen != null) {
                mc.onTextInputFocusChange(screen, true);
            }
        }
    }

    /**
     * Disables OS-level text / IME input and cancels any active IME composition.
     * Call this whenever a custom text field loses focus.
     *
     * <p>Must pass the active {@link Screen} as the element so that
     * {@code KeyboardHandler.submitPreeditEvent(screen, null)} can correctly call
     * {@code screen.preeditUpdated(null)}, clearing the preedit overlay and
     * releasing the IME composition lock.</p>
     */
    public static void deactivate() {
        // 减少引用计数；仅最后一个失去焦点的文本框真正关闭 IME 输入
        refCount = Math.max(0, refCount - 1);
        if (refCount == 0) {
            Screen screen = mc.screen;
            if (screen != null) {
                mc.onTextInputFocusChange(screen, false);
            }
        }
    }

    /**
     * 强制关闭 IME 输入，忽略引用计数。
     * 在 GUI 关闭或切换屏幕时使用，确保 IME 不会保持开启状态。
     */
    public static void forceDeactivate() {
        refCount = 0;
        Screen screen = mc.screen;
        if (screen != null) {
            mc.onTextInputFocusChange(screen, false);
        }
    }

    /**
     * Updates the cursor position used for preedit overlay placement.
     *
     * @param x cursor left edge in GUI (scaled) coordinates
     * @param y cursor top edge in GUI (scaled) coordinates
     */
    public static void updateCursorPos(float x, float y) {
        activeCursorX = x;
        activeCursorY = y;
    }

}
