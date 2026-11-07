package com.github.epsilon.gui.lib

import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.lib.state.LuminRuntime

/** 供需要先排队树快照、再执行后处理的宿主使用，生命周期仍属于 scene。 */
internal fun UiScene.LuminUITree(bounds: UiRect, key: Any, inputLayer: Int = UiLayer.CONTENT.baseLayer(), immediate: Boolean = false, block: (UiTree.Scope) -> Unit): UiTree {
    val entry = lumin.nextEntry(bounds, key)
    entry.alwaysDirty = immediate
    val metrics = scheduler().textMetrics().asUiTextMetrics()
    entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), { block(tree) })
    val output = UiTree.Scope()
    entry.emit(output, inputLayer)
    return UiTree.from(output)
}

/*
 * LuminUI 的两种绘制模式入口。
 *
 * - LuminUIScene / LuminUIGroup：recompose，观察状态与几何输入，失效时重建，其余帧重放缓存节点；
 * - LuminUISceneImmediate / LuminUIGroupImmediate：立即模式，每帧重建（旧的 Content 语义）。
 *
 * 两种模式可互相嵌套：recompose 内的立即子块用 LuminUIGroupImmediate 表达（它每帧重建，
 * 但仍作为独立的缓存单元参与拼装，不会污染父级缓存）。
 */

/**
 * 以 recompose 模式提交一层内容。
 *
 * 该层观察内部读取的 State，并在状态、几何或绘制上下文变化时重新执行；
 * 其余帧直接重放上一轮构建的节点。矩形变化会自动触发整层重建。
 */
fun UiScene.LuminUIScene(
    bounds: UiRect,
    layer: UiLayer = UiLayer.CONTENT,
    relative: LayerOffset = LayerOffset.Same,
    key: Any,
    immediate: Boolean = false,
    block: UiScope.() -> Unit,
) {
    val entry = lumin.nextEntry(bounds, key)
    entry.alwaysDirty = immediate
    val metrics = scheduler().textMetrics().asUiTextMetrics()
    entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), block)
    val output = UiTree.Scope()
    entry.emit(output, layer.baseLayer() + relative.value)
    batch(layer, relative.value).render(UiTree.from(output))
}

/** 以立即模式提交一层内容（每帧重建，等价于旧的 `Content`）。 */
fun UiScene.LuminUISceneImmediate(
    bounds: UiRect,
    layer: UiLayer = UiLayer.CONTENT,
    relative: LayerOffset = LayerOffset.Same,
    block: UiScope.() -> Unit,
) {
    batch(layer, relative.value).Content(bounds, 0, block)
}

/** 以立即模式在批次上提交一层内容。 */
fun UiRenderBatch.LuminUISceneImmediate(
    bounds: UiRect,
    baseLayer: Int = 0,
    block: UiScope.() -> Unit,
) {
    Content(bounds, baseLayer, block)
}

/**
 * recompose 场景内的子缓存单元。
 *
 * `key` 必须对列表项稳定（例如 setting 实例），否则列表增删会错配缓存。
 */
fun UiScope.LuminUIGroup(key: Any, block: UiScope.() -> Unit) {
    val composition = LuminRuntime.currentComposition
    if (composition == null) {
        block()
        return
    }
    composition.group(this, key, block)
}

/**
 * recompose 场景内的立即模式子块：每帧重建，但仍作为独立单元参与拼装。
 *
 * 适合逐帧变化且不值得缓存的内容（世界数据、自绘图表、仍在使用拉取式动画的代码）。
 */
fun UiScope.LuminUIGroupImmediate(key: Any, block: UiScope.() -> Unit) {
    val composition = LuminRuntime.currentComposition
    if (composition == null) {
        block()
        return
    }
    composition.group(this, key, block, alwaysDirty = true)
}
/** 列表项与 Group 共用独立的状态观察和记忆生命周期。 */
fun UiScope.LuminUIItem(key: Any, block: UiScope.() -> Unit) = LuminUIGroup(key, block)

fun <T> UiScope.LuminUIItems(items: List<T>, key: (T) -> Any, content: UiScope.(T) -> Unit) {
    for (item in items) LuminUIItem(key(item)) { content(item) }
}

/**
 * 以 recompose 模式在批次上提交一层内容（组件拿到的都是批次而非场景时使用）。
 *
 * 与 [UiScene.LuminUIScene] 语义一致；独立批次没有跨帧身份，会退化为立即模式。
 */
fun UiRenderBatch.LuminUIScene(
    bounds: UiRect,
    baseLayer: Int = 0,
    key: Any,
    block: UiScope.() -> Unit,
) {
    val metrics = scheduler().textMetrics().asUiTextMetrics()
    val entry = nextComposition(bounds, key)
    if (entry == null) {
        Content(bounds, baseLayer, block)
        return
    }
    entry.bind(UiScope(entry.treeScope, metrics, LayerOffset.Same, bounds), block)
    val output = UiTree.Scope()
    entry.emit(output, baseLayer() + baseLayer)
    render(UiTree.from(output), baseLayer)
}
