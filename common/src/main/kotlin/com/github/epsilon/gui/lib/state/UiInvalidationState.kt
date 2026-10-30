package com.github.epsilon.gui.lib.state

import com.github.epsilon.gui.lib.UiRect

/**
 * 记录缓存内容的输入快照，并判断下一帧是否需要重建。
 */
class UiInvalidationState {

    private var dirty = true
    private var activeAnimations = false
    private var lastMouseX = Int.MIN_VALUE
    private var lastMouseY = Int.MIN_VALUE
    private var lastGuiHeight = -1
    private var lastSignature = Long.MIN_VALUE
    private var lastBounds: UiRect? = null

    fun markDirty() {
        dirty = true
    }

    fun beginRebuild() {
        activeAnimations = false
    }

    fun noteAnimation(active: Boolean) {
        activeAnimations = activeAnimations || active
    }

    fun hasActiveAnimations(): Boolean {
        return activeAnimations
    }

    fun needsRebuild(bounds: UiRect, mouseX: Int, mouseY: Int, guiHeight: Int): Boolean {
        return needsRebuild(bounds, mouseX, mouseY, guiHeight, 0L)
    }

    fun needsRebuild(bounds: UiRect, mouseX: Int, mouseY: Int, guiHeight: Int, signature: Long): Boolean {
        return dirty ||
                activeAnimations ||
                lastBounds == null ||
                lastBounds != bounds ||
                lastGuiHeight != guiHeight ||
                lastMouseX != mouseX ||
                lastMouseY != mouseY ||
                lastSignature != signature
    }

    fun rememberSnapshot(bounds: UiRect, mouseX: Int, mouseY: Int, guiHeight: Int) {
        rememberSnapshot(bounds, mouseX, mouseY, guiHeight, 0L)
    }

    fun rememberSnapshot(bounds: UiRect, mouseX: Int, mouseY: Int, guiHeight: Int, signature: Long) {
        lastBounds = bounds
        lastMouseX = mouseX
        lastMouseY = mouseY
        lastGuiHeight = guiHeight
        lastSignature = signature
        dirty = false
    }

}
