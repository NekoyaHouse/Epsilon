package com.github.epsilon.gui.lib.input

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
        return try { block() } finally { dispatchOwner = previous }
    }
    private fun accepts(subscription: UiInputSubscription) = dispatchOwner == null || subscription.owner === dispatchOwner
    internal data class Entry(val node: UiTree.InteractionNode, val layer: Int, val clip: UiRect?) {
        fun hit(x: Double, y: Double): Boolean = node.bounds.width > 0f && node.bounds.height > 0f &&
            node.bounds.contains(x, y) && (clip == null || clip.contains(x, y))
    }

    private var entries = emptyList<Entry>()
    private var focused: UiInputSubscription? = null
    private var captured: UiInputSubscription? = null
    private var capturedButton = -1
    private var pointerGeneration = 0L
    private fun entry(subscription: UiInputSubscription?) = entries.firstOrNull { it.node.subscription === subscription }

    internal fun collect(
        nodes: List<UiTree.UiNode>, layer: Int, into: MutableList<Entry>, clip: UiRect? = null,
        layered: Boolean = true,
    ) {
        for (node in nodes) when (node) {
            is UiTree.InteractionNode -> into.add(Entry(node, layer, clip))
            is UiTree.LayerNode -> collect(node.children, if (layered) layer + node.layer else layer, into, clip, layered)
            is UiTree.ScissorNode -> {
                val intersection = if (clip == null) node.clip else clip.intersect(node.clip) ?: continue
                collect(node.children, layer, into, intersection, layered)
            }
            is UiTree.ViewportNode -> {
                val intersection = if (clip == null) node.viewport else clip.intersect(node.viewport) ?: continue
                // 内容缓冲在同一渲染层按声明顺序绘制，不应用内部 Layer 偏移。
                collect(node.children, layer, into, intersection, layered = false)
            }
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

    fun clearFocus() { setFocus(null) }

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
        val focus = entry(focused)?.node
        if (focus != null && accepts(focus.subscription) &&
            focus.handlers.previewClick?.invoke(UiPointerEvent(event, focus.bounds, doubleClick)) == true) return true
        for (item in entries.asReversed()) {
            val node = item.node
            if (node.subscription.closed || !accepts(node.subscription) || !item.hit(event.x(), event.y())) continue
            val pointer = UiPointerEvent(event, node.bounds, doubleClick)
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
        val node = entry(captured)?.node ?: return false
        if (!accepts(node.subscription)) return false
        node.handlers.drags[event.button()]?.update?.invoke(UiPointerEvent(event, node.bounds, deltaX = deltaX, deltaY = deltaY))
        return true
    }

    fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (capturedButton != event.button()) return false
        val node = entry(captured)?.node ?: return false
        if (!accepts(node.subscription)) return false
        captured = null
        capturedButton = -1
        node.handlers.drags[event.button()]?.end?.invoke(UiPointerEvent(event, node.bounds))
        return true
    }

    private fun focusedHandlers() = entry(focused)?.node?.takeIf { accepts(it.subscription) }?.handlers
    fun keyPressed(event: KeyEvent): Boolean = focusedHandlers()?.key?.invoke(event) ?: false
    fun charTyped(event: CharacterEvent): Boolean = focusedHandlers()?.text?.invoke(event) ?: false
    fun preeditUpdated(event: PreeditEvent?): Boolean = focusedHandlers()?.preedit?.invoke(event) ?: false

    fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        for (entry in entries.asReversed()) {
            if (accepts(entry.node.subscription) && entry.hit(x, y) && entry.node.handlers.scroll?.invoke(x, y, scrollX, scrollY) == true) return true
        }
        return false
    }

    fun requestFocus(key: Any): Boolean {
        val target = entries.lastOrNull { accepts(it.node.subscription) && it.node.subscription.key == key && it.node.handlers.focusable } ?: return false
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
