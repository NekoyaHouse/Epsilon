package com.github.epsilon.gui.lib.state

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.kernel.state.StateNotifications
import com.github.epsilon.kernel.state.StateObservation
import com.github.epsilon.kernel.state.StateTracking

/** 当前构建上下文只在 UI 宿主线程使用；状态内核不依赖此对象。 */
object LuminRuntime {
    internal var currentComposition: LuminComposition? = null
        private set
    internal var currentView: LuminView? = null
    internal var activeRoot: LuminSceneRoot? = null
    val isRecomposing: Boolean get() = currentComposition != null

    internal fun <T> inComposition(composition: LuminComposition, block: () -> T): T {
        val previous = currentComposition
        val previousView = currentView
        currentComposition = composition
        currentView = composition.view
        return try { StateTracking.buildingUi(block) } finally {
            currentComposition = previous
            currentView = previousView
        }
    }

    internal fun requireImmediateAnimation(api: String) {
        check(currentComposition?.alwaysDirty != false) {
            "$api 不能在重组块中推进拉取动画，请使用 animateFloatAsState 或 rememberAnimatable"
        }
    }
}

/** 缓存保存嵌套的组合引用，子项失效不需要重建父级。 */
class LuminComposition internal constructor(internal val key: Any, internal val clock: LuminFrameClock) : AutoCloseable {
    internal var dirty = true
    internal var alwaysDirty = false
    internal val treeScope = UiTree.Scope()
    internal var lastBounds: UiRect? = null
    internal var inputX = 0
    internal var inputY = 0
    internal var view: LuminView? = null
    private var owner: UiScope? = null
    private var content: (UiScope.() -> Unit)? = null
    private var nodes = emptyList<UiTree.UiNode>()
    private val children = LinkedHashMap<Any, LuminComposition>()
    private val memories = LinkedHashMap<Any, Any?>()
    private var usedChildren: MutableSet<Any>? = null
    private var usedMemories: MutableSet<Any>? = null
    private val frameEffects = LinkedHashMap<Any, () -> Unit>()
    private var effects: MutableList<() -> Unit>? = null
    private var renderedFrame = Long.MIN_VALUE
    private val observation = StateObservation { dirty = true }
    internal var rebuildCount = 0L
        private set

    internal fun markDirty() { dirty = true }
    internal fun markDirtyDeep() {
        dirty = true
        children.values.forEach { it.markDirtyDeep() }
    }

    internal fun bind(scope: UiScope, block: UiScope.() -> Unit, parentRebuilt: Boolean = false) {
        if (lastBounds != scope.origin || owner?.metrics != scope.metrics || owner?.layer != scope.layer || parentRebuilt) dirty = true
        val nextView = LuminRuntime.currentView
        if (view !== nextView) {
            view?.detach(this)
            view = nextView
            nextView?.attach(this)
        }
        // 子闭包沿用父级捕获的鼠标参数；父级独立重建时不能换成本帧坐标基准。
        val parent = LuminRuntime.currentComposition?.takeIf { it.view === nextView }
        inputX = parent?.inputX ?: nextView?.mouseX ?: 0
        inputY = parent?.inputY ?: nextView?.mouseY ?: 0
        lastBounds = scope.origin
        owner = scope
        content = block
    }

    internal fun emit(into: UiTree.Scope): Boolean {
        into.appendNodes(resolve())
        return false
    }

    internal fun resolve(): List<UiTree.UiNode> {
        StateNotifications.drain()
        if (dirty || (alwaysDirty && renderedFrame != clock.frame)) recompose()
        val resolved = resolveNodes(nodes)
        StateTracking.untracked { frameEffects.values.forEach { it() } }
        return resolved
    }

    internal fun group(scope: UiScope, key: Any, block: UiScope.() -> Unit, alwaysDirty: Boolean = false) {
        check(usedChildren?.add(key) == true) { "同一组合内重复的组件 key: $key" }
        val child = children.getOrPut(key) { LuminComposition(key, clock) }
        child.alwaysDirty = alwaysDirty
        child.bind(scope, block, parentRebuilt = true)
        scope.tree.appendNodes(listOf(UiTree.CompositionNode(child::resolve)))
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T> remember(key: Any, factory: () -> T): T {
        check(usedMemories?.add(key) == true) { "同一组合内重复的 remember key: $key" }
        if (!memories.containsKey(key)) memories[key] = factory()
        return memories[key] as T
    }

    internal fun <T> retain(key: Any, factory: () -> T): T {
        checkNotNull(usedMemories).add(key)
        if (!memories.containsKey(key)) memories[key] = factory()
        @Suppress("UNCHECKED_CAST")
        return memories[key] as T
    }

    internal fun onFrame(key: Any, effect: () -> Unit) { frameEffects[key] = effect }

    internal fun afterCommit(effect: () -> Unit) {
        checkNotNull(effects) { "提交操作必须在组合构建中声明" }.add(effect)
    }

    private fun recompose() {
        val scope = owner ?: return
        val block = content ?: return
        val oldChildKeys = children.keys.toSet()
        val oldMemoryKeys = memories.keys.toSet()
        val nextChildren = LinkedHashSet<Any>()
        val nextMemories = LinkedHashSet<Any>()
        val nextEffects = ArrayList<() -> Unit>()
        val previousFrameEffects = frameEffects.toMap()
        frameEffects.clear()
        usedChildren = nextChildren
        usedMemories = nextMemories
        effects = nextEffects
        treeScope.clear()
        dirty = false
        try {
            val result = observation.observeReads {
                LuminRuntime.inComposition(this) {
                    treeScope.pushAbsolute(scope.origin) {
                        block(UiScope(treeScope, scope.metrics, scope.layer, scope.origin))
                    }
                    treeScope.snapshot().nodes
                }
            }
            nodes = result
            children.keys.filter { it !in nextChildren }.forEach { children.remove(it)?.close() }
            memories.keys.filter { it !in nextMemories }.forEach { (memories.remove(it) as? AutoCloseable)?.close() }
            renderedFrame = clock.frame
            rebuildCount++
        } catch (failure: Throwable) {
            frameEffects.clear()
            frameEffects.putAll(previousFrameEffects)
            dirty = true
            children.keys.filter { it !in oldChildKeys }.forEach { children.remove(it)?.close() }
            memories.keys.filter { it !in oldMemoryKeys }.forEach { (memories.remove(it) as? AutoCloseable)?.close() }
            throw failure
        } finally {
            usedChildren = null
            usedMemories = null
            effects = null
        }
        StateTracking.untracked { nextEffects.forEach { it() } }
    }

    override fun close() {
        view?.detach(this)
        observation.close()
        children.values.forEach { it.close() }
        children.clear()
        memories.values.forEach { (it as? AutoCloseable)?.close() }
        memories.clear()
        frameEffects.clear()
        nodes = emptyList()
        owner = null
        content = null
    }

    companion object {
        private fun resolveNodes(nodes: List<UiTree.UiNode>): List<UiTree.UiNode> = buildList {
            for (node in nodes) when (node) {
                is UiTree.CompositionNode -> addAll(node.resolve())
                is UiTree.LayerNode -> add(node.copy(children = resolveNodes(node.children)))
                is UiTree.ScissorNode -> add(node.copy(children = resolveNodes(node.children)))
                is UiTree.ViewportNode -> add(node.copy(children = resolveNodes(node.children)))
                else -> add(node)
            }
        }
    }
}

/** 根身份由稳定 key 决定；帧结束清理消失的入口。 */
class LuminSceneRoot internal constructor(val clock: LuminFrameClock = LuminFrameClock()) : AutoCloseable {
    private val entries = LinkedHashMap<Any, LuminComposition>()
    private val visited = LinkedHashSet<Any>()
    internal var inFrame = false
        private set

    internal fun beginFrame() {
        check(!inFrame) { "beginFrame/endFrame 必须配对" }
        inFrame = true
        visited.clear()
        LuminRuntime.activeRoot = this
        clock.advance()
    }

    internal fun nextEntry(bounds: UiRect, key: Any): LuminComposition {
        val identity = key
        check(visited.add(identity)) { "同一帧重复的场景 key: $identity" }
        return entries.getOrPut(identity) { LuminComposition(identity, clock) }
    }

    internal fun endFrame() {
        if (!inFrame) return
        entries.keys.filter { it !in visited }.forEach { entries.remove(it)?.close() }
        inFrame = false
        if (LuminRuntime.activeRoot === this) LuminRuntime.activeRoot = null
    }

    internal fun abortFrame() {
        inFrame = false
        visited.clear()
        if (LuminRuntime.activeRoot === this) LuminRuntime.activeRoot = null
    }

    fun invalidateAll() { entries.values.forEach { it.markDirtyDeep() } }

    override fun close() {
        entries.values.forEach { it.close() }
        entries.clear()
        visited.clear()
        clock.clear()
        inFrame = false
        if (LuminRuntime.activeRoot === this) LuminRuntime.activeRoot = null
    }

}

fun <T> remember(key: Any, factory: () -> T): T =
    checkNotNull(LuminRuntime.currentComposition) { "remember 必须在 LuminUI 组合内调用" }.remember(key, factory)
