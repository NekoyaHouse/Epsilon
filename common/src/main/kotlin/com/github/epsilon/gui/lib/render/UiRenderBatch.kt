package com.github.epsilon.gui.lib.render

import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import com.github.epsilon.gui.lib.UiTheme
import com.github.epsilon.gui.lib.UiTree
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
    /** 场景级 recompose 注册表；由 UiScene 注入，用于复用长生命周期作用域（逐项缓存挂在 Scope 上）。 */
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

    /** 取本次 Content 复用的长生命周期作用域；独立批次则新建。 */
    internal fun nextEntryScope(bounds: com.github.epsilon.gui.lib.UiRect): UiTree.Scope {
        val root = sceneRoot ?: return UiTree.Scope()
        val scope = root.nextEntry(bounds, null).treeScope
        scope.clear()
        return scope
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
