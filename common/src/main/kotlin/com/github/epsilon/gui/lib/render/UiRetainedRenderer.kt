package com.github.epsilon.gui.lib.render

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.schedulers.render2d.Render2DProperties
import com.github.epsilon.graphics.schedulers.render2d.Render2DScissor
import com.github.epsilon.graphics.schedulers.render2d.Render2DTransform
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree

/** 只递归持久引用；连续的自身绘制记录作为独立片段编译，子节点变化不重录父片段。 */
internal object UiRetainedRenderer {
    fun render(nodes: List<UiTree.UiNode>, batch: UiRenderBatch, layer: Int,
               properties: Render2DProperties = Render2DProperties.DEFAULT,
               clip: Render2DScissor? = null, origin: UiRect = UiRect(0f, 0f, 0f, 0f), retained: Boolean = false) {
        var start = 0
        fun flush(end: Int) {
            if (start == end) return
            val from = start
            val handle = batch.absoluteLayer(layer)
            val previous = handle.scissor()
            handle.clearScissor()
            try {
                if (retained) {
                    val local = Render2DProperties(transform = Render2DTransform.translation(origin.x, origin.y))
                    batch.scheduler().retain(nodes, from, layer, properties.then(local), clip, origin.x, origin.y) {
                        for (i in from until end) LuminUiRenderer.renderPrimitive(nodes[i], batch, layer)
                    }
                } else {
                    // 即时树同样允许属性节点，临时几何不进入跨帧缓存。
                    batch.scheduler().recordTransient(layer, properties, clip) {
                        for (i in from until end) LuminUiRenderer.renderPrimitive(nodes[i], batch, layer)
                    }
                }
            } finally {
                if (previous == null) handle.clearScissor()
                else handle.setScissor(previous.x, previous.y, previous.width, previous.height)
            }
        }
        for ((index, node) in nodes.withIndex()) {
            val container = node is UiTree.CompositionNode || node is UiTree.LayerNode || node is UiTree.ScissorNode ||
                node is UiTree.PropertyNode || node is UiTree.GeometryNode || node is UiTree.ViewportNode ||
                node is UiTree.MarqueeTextNode
            if (!container) continue
            flush(index)
            when (node) {
                is UiTree.CompositionNode -> {
                    val startTime = System.nanoTime()
                    val render = try { node.composition.prepare() } finally {
                        batch.scheduler().stats.compositionNanos += System.nanoTime() - startTime
                        batch.scheduler().stats.compositionVisits++
                    }
                    val delta = Render2DProperties(transform = Render2DTransform.translation(
                        render.currentOrigin.x - render.recordedOrigin.x, render.currentOrigin.y - render.recordedOrigin.y))
                    render(render.nodes, batch, layer, properties.then(delta), clip, render.recordedOrigin, retained = true)
                }
                is UiTree.LayerNode -> render(node.children, batch, layer + node.layer, properties, clip, origin, retained)
                is UiTree.ScissorNode -> render(node.children, batch, layer, properties,
                    intersect(clip, scissor(node.clip, properties.transform)), origin, retained)
                is UiTree.PropertyNode -> {
                    val value = node.value.get()
                    val pivot = Render2DTransform.translation(node.origin.x, node.origin.y)
                        .then(value.properties.transform).then(Render2DTransform.translation(-node.origin.x, -node.origin.y))
                    val next = properties.then(value.properties.copy(transform = pivot))
                    val nextClip = value.clip?.let { intersect(clip, scissor(it.translate(node.origin.x, node.origin.y), next.transform)) } ?: clip
                    render(node.children, batch, layer, next, nextClip, origin, retained)
                }
                is UiTree.GeometryNode -> render(node.value.get(), batch, layer, properties, clip, origin, retained = true)
                is UiTree.ViewportNode -> {
                    val viewportClip = intersect(clip, scissor(node.viewport, properties.transform))
                    render(node.children, batch, layer, properties, viewportClip, origin, retained)
                    val pointer = node.pointer?.invoke() ?: (node.mouseX to node.mouseY)
                    node.buffer.queueSharedViewport(batch.view(layer - batch.baseLayer() + 8), properties, viewportClip,
                        node.viewport, node.scroll, node.maxScroll, node.contentHeight, pointer.first, pointer.second)
                }
                is UiTree.MarqueeTextNode -> {
                    val marqueeClip = intersect(clip, scissor(node.clip, properties.transform))
                    val handle = batch.absoluteLayer(layer)
                    val previous = handle.scissor()
                    handle.clearScissor()
                    try {
                        val local = Render2DProperties(transform = Render2DTransform.translation(origin.x, origin.y))
                        val record = { LuminUiRenderer.renderPrimitive(node, batch, layer) }
                        if (retained) batch.scheduler().retain(nodes, index, layer, properties.then(local), marqueeClip,
                            origin.x, origin.y, record)
                        else batch.scheduler().recordTransient(layer, properties, marqueeClip, record)
                    } finally {
                        if (previous == null) handle.clearScissor()
                        else handle.setScissor(previous.x, previous.y, previous.width, previous.height)
                    }
                }
                else -> Unit
            }
            start = index + 1
        }
        flush(nodes.size)
    }

    fun scissor(rect: UiRect, transform: Render2DTransform): Render2DScissor {
        val xs = floatArrayOf(transform.mapX(rect.x, rect.y), transform.mapX(rect.right(), rect.y),
            transform.mapX(rect.x, rect.bottom()), transform.mapX(rect.right(), rect.bottom()))
        val ys = floatArrayOf(transform.mapY(rect.x, rect.y), transform.mapY(rect.right(), rect.y),
            transform.mapY(rect.x, rect.bottom()), transform.mapY(rect.right(), rect.bottom()))
        val x = xs.min(); val y = ys.min()
        val clip = LuminRenderSystem.toFramebufferScissor(x, y, xs.max() - x, ys.max() - y)
        return Render2DScissor(clip.x, clip.y, clip.width, clip.height)
    }
    private fun intersect(a: Render2DScissor?, b: Render2DScissor): Render2DScissor {
        if (a == null) return b
        val x = maxOf(a.x, b.x); val y = maxOf(a.y, b.y)
        return Render2DScissor(x, y, maxOf(0, minOf(a.x + a.width, b.x + b.width) - x),
            maxOf(0, minOf(a.y + a.height, b.y + b.height) - y))
    }
}
