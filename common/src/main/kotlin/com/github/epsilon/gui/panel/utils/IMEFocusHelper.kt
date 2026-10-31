package com.github.epsilon.gui.panel.utils

import com.github.epsilon.kernel.MinecraftWrapper.mc
import net.minecraft.client.gui.screens.Screen

/**
 * 管理 OS 级别文本/IME 输入焦点的工具类。
 *
 * 同一时间可能有多个自定义文本框同时存在（例如 Panel GUI 中的多个搜索框），
 * 但 OS 级别的 IME 输入只能全局开启或关闭一次。
 * 通过引用计数来确保：仅第一个获取焦点的文本框开启 IME 输入，
 * 仅最后一个失去焦点的文本框关闭 IME 输入。
 */
object IMEFocusHelper {

    var activeCursorX = 0.0f
    var activeCursorY = 0.0f

    /**
     * 引用计数：记录当前有多少个文本框正在请求 IME 输入焦点
     */
    private var refCount = 0

    /**
     * Enables OS-level text / IME input.
     * Call this whenever a custom text field gains focus.
     */
    fun activate() {
        // 增加引用计数；仅第一个获取焦点的文本框真正开启 IME 输入
        refCount++
        if (refCount == 1) {
            val screen: Screen? = mc.gui.screen()
            if (screen != null) {
                mc.onTextInputFocusChange(screen, true)
            }
        }
    }

    /**
     * Disables OS-level text / IME input and cancels any active IME composition.
     * Call this whenever a custom text field loses focus.
     *
     * 必须传入当前活动的 [Screen]，这样 `KeyboardHandler.submitPreeditEvent(screen, null)`
     * 才能正确调用 `screen.preeditUpdated(null)`，清空 preedit overlay 并释放 IME 组合锁。
     */
    fun deactivate() {
        // 减少引用计数；仅最后一个失去焦点的文本框真正关闭 IME 输入
        refCount = maxOf(0, refCount - 1)
        if (refCount == 0) {
            val screen: Screen? = mc.gui.screen()
            if (screen != null) {
                mc.onTextInputFocusChange(screen, false)
            }
        }
    }

    /**
     * 强制关闭 IME 输入，忽略引用计数。
     * 在 GUI 关闭或切换屏幕时使用，确保 IME 不会保持开启状态。
     */
    fun forceDeactivate() {
        refCount = 0
        val screen: Screen? = mc.gui.screen()
        if (screen != null) {
            mc.onTextInputFocusChange(screen, false)
        }
    }

    /**
     * Updates the cursor position used for preedit overlay placement.
     *
     * @param x cursor left edge in GUI (scaled) coordinates
     * @param y cursor top edge in GUI (scaled) coordinates
     */
    fun updateCursorPos(x: Float, y: Float) {
        activeCursorX = x
        activeCursorY = y
    }

}
