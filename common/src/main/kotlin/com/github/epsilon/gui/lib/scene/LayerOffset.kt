package com.github.epsilon.gui.lib.scene

/**
 * 子树相对 layer 偏移。
 *
 * 业务代码不再直接往图元里传 `layer: Int`，而是由 [com.github.epsilon.gui.lib.Layer] /
 * [com.github.epsilon.gui.lib.Above] / [com.github.epsilon.gui.lib.Below] 扩展函数把偏移
 * 合并进作用域。构造期即校验范围，越界不会再静默画错遮挡顺序。
 *
 * 取值范围与 [UiLayerStack] 的语义层 stride 一致：每个 [UiLayer] 预留 100 的区间，
 * 因此相对偏移必须落在 `(-100, 100)` 内。
 */
@JvmInline
value class LayerOffset(val value: Int) {

    init {
        require(value > -STRIDE && value < STRIDE) {
            "LayerOffset 必须落在 (-$STRIDE, $STRIDE) 内，实际为 $value；需要更细遮挡时请拆语义层"
        }
    }

    operator fun plus(other: LayerOffset): LayerOffset = LayerOffset(value + other.value)

    operator fun unaryMinus(): LayerOffset = LayerOffset(-value)

    companion object {

        /** 与 [UiLayerStack] 使用的语义层 stride 保持一致。 */
        const val STRIDE: Int = 100

        /** 相对父作用域不动，不产生 LayerNode。 */
        val Same: LayerOffset = LayerOffset(0)

        /** 投影、阴影、背景暗化。 */
        val Backdrop: LayerOffset = LayerOffset(-90)

        /** 面板本体。 */
        val Surface: LayerOffset = LayerOffset(0)

        /** 压在表面之上的描边、开关、指示器。 */
        val Edge: LayerOffset = LayerOffset(1)

        /** 常规内容。 */
        val Content: LayerOffset = LayerOffset(10)

        /** 浮层、tooltip、气泡。 */
        val Floating: LayerOffset = LayerOffset(20)

        /** popup、下拉、模态。 */
        val Overlay: LayerOffset = LayerOffset(40)

    }
}
