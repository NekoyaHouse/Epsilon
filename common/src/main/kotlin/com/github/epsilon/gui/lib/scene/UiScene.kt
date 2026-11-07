package com.github.epsilon.gui.lib.scene

import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import com.github.epsilon.gui.lib.UiTheme
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminSceneRoot
import java.util.*

/**
 * 单帧 GUI 渲染场景。
 *
 * 一个 scene 拥有一个 [Render2DScheduler]。Panel、Dropdown、popup 只向 scene
 * 提交 UI 树，最后由 scene 统一 flush，从而减少 scheduler/buffer 的创建和帧内 draw 次数。
 */
class UiScene(theme: UiTheme) : AutoCloseable {

    private val scheduler = Render2DScheduler()
    private val layers = UiLayerStack()
    private val theme: UiTheme = Objects.requireNonNull(theme, "theme")

    /** LuminUI recompose 注册表：跨帧保存各层的缓存节点。 */
    internal val lumin: LuminSceneRoot = LuminSceneRoot()

    fun beginFrame() {
        // 每个 Screen/HUD editor 帧从一个干净的命令流开始，具体 renderer 由 scheduler 池化复用。
        scheduler.clear()
        lumin.beginFrame()
    }

    fun batch(layer: UiLayer): UiRenderBatch {
        return UiRenderBatch(scheduler, layers.resolve(layer), theme, lumin)
    }

    fun batch(layer: UiLayer, relativeLayer: Int): UiRenderBatch {
        // 返回同一个 scheduler 的局部视图，调用方只通过 UI 树写入自己的 base layer。
        return UiRenderBatch(scheduler, layers.resolve(layer, relativeLayer), theme, lumin)
    }

    fun submit(layer: UiLayer, tree: UiTree) {
        batch(layer).render(tree)
    }

    fun submit(layer: UiLayer, relativeLayer: Int, tree: UiTree) {
        batch(layer, relativeLayer).render(tree)
    }

    fun flush() {
        scheduler.flush()
    }

    fun flushAndClear() {
        scheduler.flushAndClear()
    }

    fun clear() {
        scheduler.clear()
    }

    fun endFrame() {
        try { flush() } finally {
            scheduler.clear()
            lumin.endFrame()
        }
    }

    /** 异常帧只丢弃当帧命令，保留上一轮组合与订阅供下帧重试。 */
    fun abortFrame() {
        if (!lumin.inFrame) return
        scheduler.clear()
        lumin.abortFrame()
    }

    fun resetComposition() { lumin.close() }

    fun scheduler(): Render2DScheduler {
        return scheduler
    }

    fun layers(): UiLayerStack {
        return layers
    }

    override fun close() {
        lumin.close()
        scheduler.close()
    }

}
