package com.github.epsilon.gui.lib.scene;

/**
 * 将语义 layer 映射为 Render2DScheduler 使用的整数 layer。
 * <p>
 * 每个语义层预留一段数字空间，调用方可以在该空间内继续使用相对偏移，
 * 例如 Dropdown 的多个 pass 或 popup 内部的局部浮层。
 */
public final class UiLayerStack {

    private static final int STRIDE = 100;

    public int resolve(UiLayer layer) {
        return layer.baseLayer();
    }

    public int resolve(UiLayer layer, int relativeLayer) {
        if (relativeLayer <= -STRIDE || relativeLayer >= STRIDE) {
            throw new IllegalArgumentException("relative layer must stay inside semantic layer stride: " + relativeLayer);
        }
        return layer.baseLayer() + relativeLayer;
    }
}
