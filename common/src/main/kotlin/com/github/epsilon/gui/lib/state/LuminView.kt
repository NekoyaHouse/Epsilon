package com.github.epsilon.gui.lib.state

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.kernel.state.StateObservation
import com.github.epsilon.kernel.state.StateTracking

/** 观察宿主布局准备阶段，绘制块仍由各 composition 独立观察。 */
class LuminView : AutoCloseable {
    private val compositions = LinkedHashSet<LuminComposition>()
    private val inputs = LinkedHashSet<PointerInput>()
    private val probes = LinkedHashSet<HoverProbe>()
    private val observation = StateObservation { invalidate() }
    private val preparation = StateObservation { invalidate() }

    private fun invalidate() { compositions.forEach { it.markDirty() } }

    /** 布局可早于命中路由执行，依赖仍归属实际绘制该布局的宿主。 */
    fun <T> prepare(block: () -> T): T = preparation.observeReads(compositions.isNotEmpty(), block)
    internal var mouseX = 0
        private set
    internal var mouseY = 0
        private set

    fun <T> render(mouseX: Int, mouseY: Int, block: () -> T): T {
        this.mouseX = mouseX
        this.mouseY = mouseY
        StateTracking.untracked {
            inputs.toList().forEach { it.callback(mouseX, mouseY) }
            probes.toList().forEach { it.update() }
        }
        val previous = LuminRuntime.currentView
        LuminRuntime.currentView = this
        return try { observation.observeReads(block) } finally {
            LuminRuntime.currentView = previous
            if (compositions.isEmpty()) observation.close()
        }
    }

    internal fun attach(composition: LuminComposition) {
        compositions.add(composition)
        preparation.activate()
    }
    internal fun detach(composition: LuminComposition) {
        compositions.remove(composition)
        if (compositions.isEmpty()) {
            observation.close()
            preparation.close()
        }
    }

    internal fun contains(composition: LuminComposition, rect: UiRect, px: Double, py: Double): Boolean {
        val offsetX = px - composition.inputX
        val offsetY = py - composition.inputY
        val key = HoverKey(rect, offsetX, offsetY)
        val probe = composition.retain(key) {
            HoverProbe(rect, offsetX, offsetY).also { probes.add(it) }
        }
        return probe.hovered.value
    }

    internal fun input(composition: LuminComposition, key: Any, callback: (Int, Int) -> Unit) {
        val input = composition.retain(InputKey(key)) { PointerInput(callback) }
        composition.afterCommit {
            input.callback = callback
            inputs.add(input)
            callback(mouseX, mouseY)
        }
    }

    private inner class PointerInput(var callback: (Int, Int) -> Unit) : AutoCloseable {
        override fun close() { inputs.remove(this) }
    }

    private data class InputKey(val key: Any)

    private inner class HoverProbe(val rect: UiRect, val offsetX: Double, val offsetY: Double) : AutoCloseable {
        val hovered = mutableStateOf(hit())
        private fun hit(): Boolean {
            if (mouseX < 0 || mouseY < 0) return false
            val px = mouseX + offsetX
            val py = mouseY + offsetY
            return px >= rect.x && px <= rect.right() && py >= rect.y && py <= rect.bottom()
        }
        fun update() { hovered.value = hit() }
        override fun close() { probes.remove(this) }
    }

    override fun close() {
        observation.close()
        preparation.close()
        compositions.toList().forEach { it.close() }
        compositions.clear()
        probes.clear()
        inputs.clear()
    }

    private data class HoverKey(val rect: UiRect, val offsetX: Double, val offsetY: Double)
}

/** 在宿主输入阶段运行，组件消失时自动解除。 */
fun luminPointerInput(key: Any, callback: (Int, Int) -> Unit) {
    val composition = LuminRuntime.currentComposition ?: return
    checkNotNull(composition.view).input(composition, key, callback)
}

fun afterLuminCommit(effect: () -> Unit) {
    val composition = LuminRuntime.currentComposition
    if (composition == null) effect() else composition.afterCommit(effect)
}
