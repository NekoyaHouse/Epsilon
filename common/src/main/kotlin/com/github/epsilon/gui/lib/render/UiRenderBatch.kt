package com.github.epsilon.gui.lib.render

import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import com.github.epsilon.gui.lib.UiTheme
import com.github.epsilon.gui.lib.UiTree
import java.util.HashSet
import java.util.Objects

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
) : AutoCloseable {

    private val touchedLayers: MutableSet<Int> = HashSet()

    constructor(theme: UiTheme) : this(Render2DScheduler(), true, 0, theme)

    constructor(scheduler: Render2DScheduler, baseLayer: Int, theme: UiTheme) : this(scheduler, false, baseLayer, theme)

    init {
        Objects.requireNonNull(scheduler, "scheduler")
        Objects.requireNonNull(theme, "theme")
    }

    fun view(relativeBaseLayer: Int): UiRenderBatch {
        return UiRenderBatch(scheduler, false, baseLayer + relativeBaseLayer, theme)
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
    fun layerHandle(relativeLayer: Int): Render2DScheduler.LayerHandle {
        return absoluteLayer(baseLayer + relativeLayer)
    }

    internal fun absoluteLayer(layer: Int): Render2DScheduler.LayerHandle {
        touchedLayers.add(layer)
        return scheduler.layer(layer)
    }

    fun render(tree: UiTree) {
        render(tree, 0)
    }

    fun render(tree: UiTree, baseLayer: Int) {
        LuminUiRenderer.renderIntoLayeredBatch(tree, this, this.baseLayer + baseLayer)
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
