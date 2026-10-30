package com.github.epsilon.gui.lib.scene

/**
 * GUI 的语义层级。
 *
 * 业务代码只选择语义层，具体数字 layer 由 [UiLayerStack] 统一分配，
 * 避免不同 GUI 子系统各自猜测绘制顺序。
 */
enum class UiLayer(private val baseLayer: Int) {
    BACKGROUND(0),
    CHROME(100),
    CONTENT(200),
    FLOATING(300),
    POPUP(400),
    OVERLAY(500);

    fun baseLayer(): Int {
        return baseLayer
    }
}
