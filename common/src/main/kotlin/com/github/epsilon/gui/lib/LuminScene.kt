package com.github.epsilon.gui.lib

import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.lib.state.LuminRuntime

/*
 * LuminUI 的两种绘制模式入口。
 *
 * - LuminUIScene / LuminUIGroup：recompose，只在 mutableStateOf 被写入时重建，其余帧重放缓存节点；
 * - LuminUISceneImmediate / LuminUIGroupImmediate：立即模式，每帧重建（旧的 Content 语义）。
 *
 * 两种模式可互相嵌套：recompose 内的立即子块用 LuminUIGroupImmediate 表达（它每帧重建，
 * 但仍作为独立的缓存单元参与拼装，不会污染父级缓存）。
 */

/**
 * 以 recompose 模式提交一层内容。
 *
 * 该层的 block 只在内部读取的 [com.github.epsilon.gui.lib.state.MutableState] 发生变化时执行；
 * 其余帧直接重放上一轮构建的节点。矩形变化会自动触发整层重建。
 */
fun UiScene.LuminUIScene(
    bounds: UiRect,
    layer: UiLayer = UiLayer.CONTENT,
    relative: LayerOffset = LayerOffset.Same,
    key: Any? = null,
    block: UiScope.() -> Unit,
) {
    val entry = lumin.nextEntry(bounds, key)
    val metrics = scheduler().textMetrics().asUiTextMetrics()
    // 复用入口的长生命周期 Scope：逐项缓存挂在 Scope 上，因此能跨帧命中。
    val scope = entry.treeScope
    scope.clear()
    scope.pushAbsolute(bounds) { inner ->
        entry.bind(UiScope(inner, metrics, LayerOffset.Same, bounds), block)
        if (entry.emit(inner)) {
            inner.markActiveAnimations(true)
        }
    }
    batch(layer, relative.value).render(UiTree.from(scope))
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
fun UiScope.LuminUIGroup(key: Any? = null, block: UiScope.() -> Unit) {
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
fun UiScope.LuminUIGroupImmediate(block: UiScope.() -> Unit) {
    val composition = LuminRuntime.currentComposition
    if (composition == null) {
        block()
        return
    }
    composition.group(this, null, block, alwaysDirty = true)
}
/**
 * 由场景自动分割缓存的列表项。
 *
 * 每一项独立缓存：`revision` 不变时只重放节点；列表增删或某项状态变化时只重建该项。
 * `key` 必须对列表项稳定（例如 setting 实例），布局变化时把矩形一起并入 key。
 *
 * ```kotlin
 * LuminUIItem(setting to rowBounds, row.cacheRevision()) { SettingRow(row, ...) }
 * ```
 */
fun UiScope.LuminUIItem(key: Any, revision: Long = 0L, block: UiScope.() -> Unit) {
    tree.itemCache.draw(key, revision, tree, block, metrics)
}

/**
 * 自动分割缓存的列表。
 *
 * 每项以 `key` 作为缓存键；`revision` 未变化时不重新绘制该行。
 */
fun <T> UiScope.LuminUIItems(
    items: List<T>,
    key: (T) -> Any,
    revision: (T) -> Long = { 0L },
    content: UiScope.(T) -> Unit,
) {
    for (item in items) {
        tree.itemCache.draw(key(item), revision(item), tree, { content(item) }, metrics)
    }
}