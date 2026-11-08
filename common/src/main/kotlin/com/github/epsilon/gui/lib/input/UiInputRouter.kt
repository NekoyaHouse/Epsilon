package com.github.epsilon.gui.lib.input

import com.github.epsilon.graphics.schedulers.render2d.Render2DTransform
import com.github.epsilon.gui.lib.UiPointerEvent
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent

internal class UiInputSubscription(router: UiInputRouter, val key: Any, val owner: Any? = null) : AutoCloseable {
    var router: UiInputRouter? = router
    var closed = false
        private set

    override fun close() {
        closed = true
        router?.detach(this)
        router = null
    }
}

/** Scene 统一持有焦点与捕获；模态宿主可限定本次派发所属的 View。 */
class UiInputRouter internal constructor() : AutoCloseable {
    private var dispatchOwner: Any? = null
    fun <T> within(owner: Any, block: UiInputRouter.() -> T): T {
        val previous = dispatchOwner
        dispatchOwner = owner
        return try {
            block()
        } finally {
            dispatchOwner = previous
        }
    }

    private fun accepts(subscription: UiInputSubscription) =
        dispatchOwner == null || subscription.owner === dispatchOwner

    internal data class Entry(
        val node: UiTree.InteractionNode, val layer: Int, val clip: UiRect?,
        val transform: Render2DTransform = Render2DTransform.IDENTITY
    ) {
        fun hit(x: Double, y: Double): Boolean {
            val inverse = transform.inverse() ?: return false
            val px = inverse.mapX(x.toFloat(), y.toFloat()).toDouble()
            val py = inverse.mapY(x.toFloat(), y.toFloat()).toDouble()
            return node.bounds.width > 0 && node.bounds.height > 0 && node.bounds.contains(px, py) &&
                    (clip == null || clip.contains(x, y))
        }

        fun pointer(
            event: MouseButtonEvent,
            doubleClick: Boolean = false,
            dx: Double = 0.0,
            dy: Double = 0.0
        ): UiPointerEvent {
            val inverse = transform.inverse() ?: Render2DTransform.IDENTITY
            val px = inverse.mapX(event.x().toFloat(), event.y().toFloat()) - node.bounds.x
            val py = inverse.mapY(event.x().toFloat(), event.y().toFloat()) - node.bounds.y
            return UiPointerEvent(
                event, node.bounds.transformed(transform), doubleClick, dx, dy, px.toDouble() to py.toDouble(),
                MouseButtonEvent((px + node.bounds.x).toDouble(), (py + node.bounds.y).toDouble(), event.buttonInfo())
            )
        }
    }

    private var entries = emptyList<Entry>()
    private var focused: UiInputSubscription? = null
    private var captured: UiInputSubscription? = null
    private var capturedButton = -1
    private var pointerGeneration = 0L
    private fun entry(subscription: UiInputSubscription?) =
        entries.firstOrNull { it.node.subscription === subscription }

    internal fun collect(
        nodes: List<UiTree.UiNode>, layer: Int, into: MutableList<Entry>, clip: UiRect? = null,
        layered: Boolean = true, transform: Render2DTransform = Render2DTransform.IDENTITY,
    ) {
        for (node in nodes) when (node) {
            is UiTree.InteractionNode -> into.add(Entry(node, layer, clip, transform))
            is UiTree.LayerNode -> collect(
                node.children,
                if (layered) layer + node.layer else layer,
                into,
                clip,
                layered,
                transform
            )

            is UiTree.ScissorNode -> {
                val transformed = node.clip.transformed(transform)
                val intersection = if (clip == null) transformed else clip.intersect(transformed) ?: continue
                collect(node.children, layer, into, intersection, layered, transform)
            }

            is UiTree.ViewportNode -> {
                val transformed = node.viewport.transformed(transform)
                val intersection = if (clip == null) transformed else clip.intersect(transformed) ?: continue
                // 视口内部遵循相同的显式层级与声明顺序，输入顺序必须与绘制一致。
                collect(node.children, layer, into, intersection, layered = true, transform = transform)
            }

            is UiTree.CompositionNode -> {
                val render = node.composition.prepare()
                val next = transform.then(
                    Render2DTransform.translation(
                        render.currentOrigin.x - render.recordedOrigin.x,
                        render.currentOrigin.y - render.recordedOrigin.y
                    )
                )
                node.composition.view?.refreshHover(node.composition, next)
                collect(node.composition.prepare().nodes, layer, into, clip, layered, next)
            }

            is UiTree.PropertyNode -> {
                val value = node.value.get()
                val pivot = Render2DTransform.translation(node.origin.x, node.origin.y).then(value.properties.transform)
                    .then(Render2DTransform.translation(-node.origin.x, -node.origin.y))
                val next = transform.then(pivot)
                val area = value.clip?.translate(node.origin.x, node.origin.y)?.transformed(next)
                val intersection =
                    if (clip == null) area else if (area == null) clip else clip.intersect(area) ?: continue
                collect(node.children, layer, into, intersection, layered, next)
            }

            is UiTree.GeometryNode -> Unit
            else -> Unit
        }
    }

    internal fun publish(next: List<Entry>) {
        val active = next.filter { !it.node.subscription.closed }.sortedBy { it.layer }
        val previousAutoFocus = entries.filter { it.node.handlers.autoFocus }.mapTo(HashSet()) { it.node.subscription }
        val retained = active.mapTo(HashSet()) { it.node.subscription }
        entries.map { it.node.subscription }.filter { it !in retained }.forEach { detach(it) }
        for (item in active) {
            val subscription = item.node.subscription
            if (subscription.router !== this) subscription.router?.detach(subscription)
            subscription.router = this
        }
        val capture = active.firstOrNull { it.node.subscription === captured }
        if (captured != null && capture?.node?.handlers?.drags?.containsKey(capturedButton) != true) cancelPointer()
        if (active.firstOrNull { it.node.subscription === focused }?.node?.handlers?.focusable != true) clearFocus()
        entries = active.filter { !it.node.subscription.closed }
        entries.lastOrNull { it.node.handlers.autoFocus && it.node.handlers.focusable && it.node.subscription !in previousAutoFocus }
            ?.let { setFocus(it.node.subscription) }
    }

    internal fun detach(subscription: UiInputSubscription) {
        if (captured === subscription) cancelPointer()
        if (focused === subscription) clearFocus()
        entries = entries.filter { it.node.subscription !== subscription }
        if (subscription.router === this) subscription.router = null
    }

    private fun setFocus(next: UiInputSubscription?) {
        if (focused === next) return
        val previous = entry(focused)
        focused = null
        previous?.node?.handlers?.focus?.invoke(false)
        if (next != null && !next.closed && entry(next) != null) {
            focused = next
            entry(next)?.node?.handlers?.focus?.invoke(true)
        }
    }

    fun clearFocus() {
        setFocus(null)
    }

    fun cancelPointer() {
        pointerGeneration++
        val previous = entry(captured)
        val button = capturedButton
        captured = null
        capturedButton = -1
        previous?.node?.handlers?.drags?.get(button)?.cancel?.invoke()
    }

    fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (captured != null && capturedButton != event.button()) return false
        cancelPointer()
        val generation = pointerGeneration
        val focusEntry = entry(focused)
        val focus = focusEntry?.node
        if (focus != null && accepts(focus.subscription) &&
            focus.handlers.previewClick?.invoke(focusEntry.pointer(event, doubleClick)) == true
        ) return true
        for (item in entries.asReversed()) {
            val node = item.node
            if (node.subscription.closed || !accepts(node.subscription) || !item.hit(event.x(), event.y())) continue
            val pointer = item.pointer(event, doubleClick)
            val drag = node.handlers.drags[event.button()]
            val acceptedDrag = drag?.start?.invoke(pointer) == true
            val acceptedClick = !acceptedDrag && node.handlers.clicks[event.button()]?.invoke(pointer) == true
            if (!acceptedDrag && !acceptedClick) continue
            if (generation != pointerGeneration || node.subscription.closed || entry(node.subscription) == null) {
                if (acceptedDrag) drag.cancel()
                return true
            }
            setFocus(node.subscription.takeIf { node.handlers.focusable })
            if (acceptedDrag) {
                if (!node.subscription.closed && entry(node.subscription) != null) {
                    captured = node.subscription
                    capturedButton = event.button()
                } else drag.cancel()
            }
            return true
        }
        clearFocus()
        return false
    }

    fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (capturedButton != event.button()) return false
        val capturedEntry = entry(captured) ?: return false
        val node = capturedEntry.node
        if (!accepts(node.subscription)) return false
        node.handlers.drags[event.button()]?.update?.invoke(capturedEntry.pointer(event, dx = deltaX, dy = deltaY))
        return true
    }

    fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (capturedButton != event.button()) return false
        val capturedEntry = entry(captured) ?: return false
        val node = capturedEntry.node
        if (!accepts(node.subscription)) return false
        captured = null
        capturedButton = -1
        node.handlers.drags[event.button()]?.end?.invoke(capturedEntry.pointer(event))
        return true
    }

    private fun focusedHandlers() = entry(focused)?.node?.takeIf { accepts(it.subscription) }?.handlers
    fun keyPressed(event: KeyEvent): Boolean = focusedHandlers()?.key?.invoke(event) ?: false
    fun charTyped(event: CharacterEvent): Boolean = focusedHandlers()?.text?.invoke(event) ?: false
    fun preeditUpdated(event: PreeditEvent?): Boolean = focusedHandlers()?.preedit?.invoke(event) ?: false

    fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        for (entry in entries.asReversed()) {
            if (accepts(entry.node.subscription) && entry.hit(x, y) && entry.node.handlers.scroll?.invoke(
                    x,
                    y,
                    scrollX,
                    scrollY
                ) == true
            ) return true
        }
        return false
    }

    fun requestFocus(key: Any): Boolean {
        val target =
            entries.lastOrNull { accepts(it.node.subscription) && it.node.subscription.key == key && it.node.handlers.focusable }
                ?: return false
        setFocus(target.node.subscription)
        return true
    }

    fun hitTest(x: Double, y: Double): Boolean = entries.any { accepts(it.node.subscription) && it.hit(x, y) }

    override fun close() {
        cancelPointer()
        clearFocus()
        entries.forEach { if (it.node.subscription.router === this) it.node.subscription.router = null }
        entries = emptyList()
    }
}
