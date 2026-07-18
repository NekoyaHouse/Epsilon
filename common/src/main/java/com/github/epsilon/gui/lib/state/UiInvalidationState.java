package com.github.epsilon.gui.lib.state;

import com.github.epsilon.gui.lib.UiRect;

/**
 * 记录缓存内容的输入快照，并判断下一帧是否需要重建。
 */
public final class UiInvalidationState {

    private boolean dirty = true;
    private boolean hasActiveAnimations;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private int lastGuiHeight = -1;
    private long lastSignature = Long.MIN_VALUE;
    private UiRect lastBounds;

    public void markDirty() {
        dirty = true;
    }

    public void beginRebuild() {
        hasActiveAnimations = false;
    }

    public void noteAnimation(boolean active) {
        hasActiveAnimations = hasActiveAnimations || active;
    }

    public boolean hasActiveAnimations() {
        return hasActiveAnimations;
    }

    public boolean needsRebuild(UiRect bounds, int mouseX, int mouseY, int guiHeight) {
        return needsRebuild(bounds, mouseX, mouseY, guiHeight, 0L);
    }

    public boolean needsRebuild(UiRect bounds, int mouseX, int mouseY, int guiHeight, long signature) {
        return dirty
                || hasActiveAnimations
                || lastBounds == null
                || !lastBounds.equals(bounds)
                || lastGuiHeight != guiHeight
                || lastMouseX != mouseX
                || lastMouseY != mouseY
                || lastSignature != signature;
    }

    public void rememberSnapshot(UiRect bounds, int mouseX, int mouseY, int guiHeight) {
        rememberSnapshot(bounds, mouseX, mouseY, guiHeight, 0L);
    }

    public void rememberSnapshot(UiRect bounds, int mouseX, int mouseY, int guiHeight, long signature) {
        lastBounds = bounds;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        lastGuiHeight = guiHeight;
        lastSignature = signature;
        dirty = false;
    }

}
