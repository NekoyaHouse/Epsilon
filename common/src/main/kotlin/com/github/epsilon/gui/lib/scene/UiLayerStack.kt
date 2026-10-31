package com.github.epsilon.gui.lib.scene

/**
 * 将语义 layer 映射为 Render2DScheduler 使用的整数 layer。
 *
 * 每个语义层预留一段数字空间，调用方可以在该空间内继续使用相对偏移，
 * 例如 Dropdown 的多个 pass 或 popup 内部的局部浮层。
 */
class UiLayerStack {

    fun resolve(layer: UiLayer): Int {
        return layer.baseLayer()
    }

    fun resolve(layer: UiLayer, relativeLayer: Int): Int {
        if (relativeLayer <= -STRIDE || relativeLayer >= STRIDE) {
            throw IllegalArgumentException("relative layer must stay inside semantic layer stride: $relativeLayer")
        }
        return layer.baseLayer() + relativeLayer
    }

    private companion object {

        private const val STRIDE = 100

    }
}
