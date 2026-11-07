package com.github.epsilon.gui.lib

import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene

/*
 * Compose 风格入口。
 *
 * 一帧的每一层由一个 lambda 描述；作用域负责主题、度量、layer 与坐标原点，
 * 调用方不再手写 UiTree.build + submit / batch.render。
 */

/**
 * 在当前批次上构建并提交一层内容。
 *
 * @param baseLayer 相对本批次基准 layer 的额外偏移
 * @param bounds 本层使用的绘制矩形；作用域内坐标相对它解析，`size` 取它的宽高
 */
fun UiRenderBatch.Content(
    bounds: UiRect,
    baseLayer: Int = 0,
    block: UiScope.() -> Unit,
) {
    val metrics = scheduler().textMetrics().asUiTextMetrics()
    val tree = UiTree.build { scope ->
        // 作用域的 origin 必须等于底层 bound，图元坐标才能与布局计算共用同一套坐标系。
        scope.pushAbsolute(bounds) {
            block(UiScope(it, metrics, LayerOffset.Same, bounds, sceneCache()))
        }
    }
    render(tree, baseLayer)
}

/**
 * 以屏幕坐标原点构建一层内容。
 *
 * 作用域 `origin` 为 `(0, 0)`，因此作用域内的坐标等同于屏幕绝对坐标，`size` 为 0；
 * 需要尺寸语义时用 [com.github.epsilon.gui.lib.Positioned] / [com.github.epsilon.gui.lib.Place] 再开一层。
 */
fun UiRenderBatch.Content(
    baseLayer: Int = 0,
    block: UiScope.() -> Unit,
) {
    Content(UiRect(0.0f, 0.0f, 0.0f, 0.0f), baseLayer, block)
}

/**
 * 在场景的某个语义层上构建并提交一层内容。
 */
fun UiScene.Content(
    bounds: UiRect,
    layer: UiLayer = UiLayer.CONTENT,
    relative: LayerOffset = LayerOffset.Same,
    block: UiScope.() -> Unit,
) {
    batch(layer, relative.value).Content(bounds, 0, block)
}
