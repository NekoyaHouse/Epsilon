package com.github.epsilon.gui.lib.render

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.kernel.state.StateObservation
import com.github.epsilon.kernel.state.StateTracking
import java.util.concurrent.atomic.AtomicLong

/** 组合身份跨帧稳定，重建后只替换自己的记录，子组合仍以引用存在。 */
internal class UiRenderNode : AutoCloseable {
    val generation = generations.incrementAndGet()
    var revision = 0L; private set
    var nodes: List<UiTree.UiNode> = emptyList(); private set
    var recordedOrigin = UiRect(0f, 0f, 0f, 0f); private set
    var currentOrigin = recordedOrigin
    var closed = false; private set

    fun commit(next: List<UiTree.UiNode>, origin: UiRect) {
        release(nodes)
        nodes = next
        recordedOrigin = origin
        currentOrigin = origin
        revision++
    }
    override fun close() {
        if (closed) return
        release(nodes); nodes = emptyList(); closed = true
    }
    companion object {
        private val generations = AtomicLong()
        internal fun release(nodes: List<UiTree.UiNode>) {
            for (node in nodes) when (node) {
                is UiTree.PropertyNode -> { node.value.close(); release(node.children) }
                is UiTree.GeometryNode -> node.value.close()
                is UiTree.LayerNode -> release(node.children)
                is UiTree.ScissorNode -> release(node.children)
                is UiTree.ViewportNode -> release(node.children)
                else -> Unit
            }
        }
    }
}

/** 每个阶段独立观察读取；失败时保留上次成功值与订阅。 */
internal class UiPhaseValue<T>(private val calculate: () -> T, private val dispose: (T) -> Unit = {}) : AutoCloseable {
    private var dirty = true
    private var initialized = false
    private var cached: T? = null
    var revision = 0L; private set
    private val observation = StateObservation { dirty = true }
    @Suppress("UNCHECKED_CAST")
    fun get(): T {
        if (dirty) {
            val result = observation.observeReads { StateTracking.buildingUi(calculate) }
            if (initialized) dispose(cached as T)
            cached = result
            initialized = true
            dirty = false
            revision++
        }
        return cached as T
    }
    @Suppress("UNCHECKED_CAST")
    override fun close() { observation.close(); if (initialized) dispose(cached as T); initialized = false; dirty = true; cached = null }
}
