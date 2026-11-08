package com.github.epsilon.gui.lib.state

import com.github.epsilon.graphics.schedulers.render2d.Render2DTransform
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.input.UiInputRouter
import com.github.epsilon.kernel.state.StateObservation
import com.github.epsilon.kernel.state.StateTracking
import java.util.IdentityHashMap

/** 观察宿主布局准备阶段，绘制块仍由各 composition 独立观察。 */
class LuminView : AutoCloseable {
    private val inputDelegate = lazy { UiInputRouter() }
    private var sharedInput: UiInputRouter? = null
    val input: UiInputRouter get() = sharedInput ?: inputDelegate.value
    private var inputFrame: MutableList<UiInputRouter.Entry>? = null
    private val compositions = LinkedHashSet<LuminComposition>()
    private val probes = IdentityHashMap<LuminComposition, MutableSet<HoverProbe>>()
    private val observation = StateObservation { invalidate() }
    private val preparation = StateObservation { invalidate() }

    private fun invalidate() {
        compositions.forEach { it.markDirty() }
    }

    /** 布局可早于命中路由执行，依赖仍归属实际绘制该布局的宿主。 */
    fun <T> prepare(block: () -> T): T = preparation.observeReads(compositions.isNotEmpty(), block)
    internal var mouseX = 0
        private set
    internal var mouseY = 0
        private set

    fun <T> render(mouseX: Int, mouseY: Int, block: () -> T): T {
        check(inputFrame == null) { "同一个 LuminView 不能嵌套 render" }
        sharedInput = LuminRuntime.activeRoot?.input
        this.mouseX = mouseX
        this.mouseY = mouseY
        StateTracking.untracked {
            for (group in probes.values) for (probe in group) {
                if (probe.transform == Render2DTransform.IDENTITY) probe.update()
            }
        }
        val previous = LuminRuntime.currentView
        LuminRuntime.currentView = this
        val nextInputs = ArrayList<UiInputRouter.Entry>()
        inputFrame = nextInputs
        return try {
            val result = observation.observeReads(block)
            if (LuminRuntime.activeRoot == null && inputDelegate.isInitialized()) input.publish(nextInputs)
            result
        } finally {
            inputFrame = null
            LuminRuntime.currentView = previous
            if (compositions.isEmpty()) observation.close()
        }
    }

    internal fun collectInput(nodes: List<UiTree.UiNode>, layer: Int) {
        val root = LuminRuntime.activeRoot
        if (root != null) {
            sharedInput = root.input; root.collectInput(nodes, layer)
        } else if (inputDelegate.isInitialized()) inputFrame?.let { input.collect(nodes, layer, it) }
    }

    internal fun attach(composition: LuminComposition) {
        sharedInput = LuminRuntime.activeRoot?.input
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
            HoverProbe(composition, rect, offsetX, offsetY).also {
                probes.getOrPut(composition) { LinkedHashSet() }.add(it)
            }
        }
        return probe.hovered.value
    }

    private inner class HoverProbe(
        val composition: LuminComposition,
        val rect: UiRect,
        val offsetX: Double,
        val offsetY: Double
    ) : AutoCloseable {
        var transform = Render2DTransform.IDENTITY
        val hovered = mutableStateOf(hit())
        private fun hit(): Boolean {
            if (mouseX < 0 || mouseY < 0) return false
            val inverse = transform.inverse() ?: return false
            val px = inverse.mapX((mouseX + offsetX).toFloat(), (mouseY + offsetY).toFloat())
            val py = inverse.mapY((mouseX + offsetX).toFloat(), (mouseY + offsetY).toFloat())
            return px >= rect.x && px <= rect.right() && py >= rect.y && py <= rect.bottom()
        }

        fun update() {
            hovered.value = hit()
        }

        override fun close() {
            val group = probes[composition] ?: return
            group.remove(this)
            if (group.isEmpty()) probes.remove(composition)
        }
    }

    internal fun refreshHover(composition: LuminComposition, transform: Render2DTransform) {
        // 每个组合只访问自身的探针，避免遍历整棵输入树时反复扫描 View 的全部探针。
        probes[composition]?.forEach { it.transform = transform; it.update() }
    }

    override fun close() {
        if (inputDelegate.isInitialized()) inputDelegate.value.close()
        observation.close()
        preparation.close()
        compositions.toList().forEach { it.close() }
        compositions.clear()
        probes.clear()
    }

    private data class HoverKey(val rect: UiRect, val offsetX: Double, val offsetY: Double)
}

fun afterLuminCommit(effect: () -> Unit) {
    val composition = LuminRuntime.currentComposition
    if (composition == null) effect() else composition.afterCommit(effect)
}
