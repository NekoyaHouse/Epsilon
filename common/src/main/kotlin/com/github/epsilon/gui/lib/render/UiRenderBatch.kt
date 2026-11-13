package com.github.epsilon.gui.lib.render

import com.github.epsilon.graphics.schedulers.render2d.Render2DProperties
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import com.github.epsilon.graphics.schedulers.render2d.Render2DScissor
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTheme
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.state.LuminComposition
import com.github.epsilon.gui.lib.state.LuminSceneRoot
import java.util.*

/**
 * UI 树到 Lumin 2D 调度器之间的批次视图。
 *
 * 上层只提交 [UiTree]，本类负责把树写入共享的 layer/scheduler 管线，并维护局部视图触碰过的 layer。
 */
class UiRenderBatch private constructor(
    private val scheduler: Render2DScheduler,
    private val ownsScheduler: Boolean,
    private val baseLayer: Int,
    private val theme: UiTheme,
    /** 场景组合根由 UiScene 注入；batch 与内容缓冲共享组合生命周期和帧时钟。 */
    private val sceneRoot: LuminSceneRoot? = null,
) : AutoCloseable {

    private val touchedLayers: MutableSet<Int> = HashSet()

    constructor(theme: UiTheme) : this(Render2DScheduler(), true, 0, theme, null)

    constructor(scheduler: Render2DScheduler, baseLayer: Int, theme: UiTheme, sceneRoot: LuminSceneRoot? = null) :
            this(scheduler, false, baseLayer, theme, sceneRoot)

    init {
        Objects.requireNonNull(scheduler, "scheduler")
        Objects.requireNonNull(theme, "theme")
    }

    fun view(relativeBaseLayer: Int): UiRenderBatch {
        return UiRenderBatch(scheduler, false, baseLayer + relativeBaseLayer, theme, sceneRoot)
    }

    /** 立即 Content 每次创建临时绘制作用域，重组内容由 composition 持有作用域。 */
    internal fun nextEntryScope(bounds: UiRect): UiTree.Scope {
        return UiTree.Scope()
    }

    /** 取本次内容对应的 recompose 入口；独立批次返回 null（等价立即模式）。 */
    internal fun nextComposition(
        bounds: UiRect,
        key: Any,
    ): LuminComposition? {
        return sceneRoot?.nextEntry(bounds, key)
    }

    fun scheduler(): Render2DScheduler {
        return scheduler
    }

    fun baseLayer(): Int {
        return baseLayer
    }

    fun theme(): UiTheme {
        return theme
    }

    fun ownsScheduler(): Boolean {
        return ownsScheduler
    }

    /**
     * 返回相对当前 batch 的 layer 句柄。
     *
     * 该入口只用于底层编译器和少量需要设置 scissor 的容器，业务 UI 应优先提交 [UiTree]。
     */
    fun canvas(relativeLayer: Int): Render2DScheduler.Canvas {
        return absoluteLayer(baseLayer + relativeLayer)
    }

    internal fun absoluteLayer(layer: Int): Render2DScheduler.Canvas {
        touchedLayers.add(layer)
        return scheduler.canvas(layer)
    }

    fun render(tree: UiTree) {
        render(tree, 0)
    }

    fun render(tree: UiTree, baseLayer: Int) {
        try {
            LuminUiRenderer.renderIntoLayeredBatch(tree, this, this.baseLayer + baseLayer)
        } finally {
            UiRenderNode.release(tree.nodes)
        }
    }

    internal fun renderProperties(tree: UiTree, properties: Render2DProperties, clip: Render2DScissor?) {
        try {
            UiRetainedRenderer.render(tree.nodes, this, baseLayer, properties, clip)
        } finally {
            UiRenderNode.release(tree.nodes)
        }
    }

    fun flush() {
        if (ownsScheduler) {
            scheduler.flush()
            return
        }
        for (layer in sortedTouchedLayers()) {
            scheduler.flushLayer(layer)
        }
    }

    fun clear() {
        if (ownsScheduler) {
            scheduler.clear()
            return
        }
        for (layer in sortedTouchedLayers()) {
            scheduler.clearLayer(layer)
        }
        touchedLayers.clear()
    }

    fun flushAndClear() {
        flush()
        clear()
    }

    override fun close() {
        if (ownsScheduler) {
            scheduler.close()
        }
    }

    private fun sortedTouchedLayers(): List<Int> {
        return touchedLayers.sorted()
    }

}
