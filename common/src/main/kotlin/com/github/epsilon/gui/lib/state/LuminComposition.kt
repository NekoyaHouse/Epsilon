package com.github.epsilon.gui.lib.state

import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.UiTree

/**
 * recompose 运行时。
 *
 * 渲染线程单线程使用：
 * - [currentComposition] 非空表示正处在一次重建中，状态读取会登记依赖；
 * - 状态写入直接把订阅者标脏，具体重建在当前帧渲染到该 composition 时惰性发生；
 * - 动画由 [advanceAnimations] 每帧推进，推进产生的状态写入同样走订阅失效。
 */
object LuminRuntime {

    internal var currentComposition: LuminComposition? = null
        private set

    private val animatables: MutableList<LuminAnimatable> = ArrayList()

    private var lastFrameMs: Long = System.currentTimeMillis()

    /** 当前是否处于 recompose（重建）过程中。 */
    val isRecomposing: Boolean get() = currentComposition != null

    internal fun recordRead(state: LuminState<*>) {
        currentComposition?.recordDependency(state)
    }

    internal fun invalidate(state: LuminState<*>) {
        state.invalidateSubscribers()
    }

    internal fun registerAnimatable(animatable: LuminAnimatable) {
        if (!animatables.contains(animatable)) {
            animatables.add(animatable)
        }
    }

    internal fun <R> inComposition(composition: LuminComposition, block: () -> R): R {
        val previous = currentComposition
        currentComposition = composition
        try {
            return block()
        } finally {
            currentComposition = previous
        }
    }

    /** 每帧推进所有运行中的动画；推进产生的写入会精确失效相关 composition。 */
    fun advanceAnimations(nowMs: Long = System.currentTimeMillis()) {
        val delta = (nowMs - lastFrameMs).coerceIn(0L, 100L)
        lastFrameMs = nowMs
        if (animatables.isEmpty()) {
            return
        }
        val iterator = animatables.iterator()
        while (iterator.hasNext()) {
            if (!iterator.next().advance(delta)) {
                iterator.remove()
            }
        }
    }

    /**
     * recompose 块内不允许使用拉取式动画。
     *
     * 旧 API 依赖每帧被调用，在只于状态变化时执行的块里会冻结，因此这里直接报错。
     */
    internal fun requireImmediateAnimation(api: String) {
        val composition = currentComposition ?: return
        if (!composition.alwaysDirty) {
            throw IllegalStateException(
                "$api 不能在 LuminUIScene / LuminUIGroup 内使用：recompose 块只在状态变化时执行，拉取式动画会冻结。" +
                        "请改用 LuminAnimatable（读取 value、写入 target），或把该段绘制放进 LuminUIGroupImmediate"
            )
        }
    }

}

/**
 * recompose 缓存单元。
 *
 * 输出被切成片段（字面节点 / 子 composition），命中缓存时只做引用拷贝；子 composition 各自维护
 * 自己的脏标记，因此一行设置变化只重建那一行。
 */
class LuminComposition internal constructor(internal val key: Any?) {

    internal var dirty: Boolean = true

    /** 每帧强制重建（用于 recompose 内的立即模式子块）。 */
    internal var alwaysDirty: Boolean = false

    /** 上次绑定时的矩形；变化意味着节点里烘焙的绝对坐标失效。 */
    internal var lastBounds: UiRect? = null

    /** 上次绑定时的外部失效键（布局签名等）；变化意味着缓存内容过期。 */
    internal var lastKey: Any? = null

    private var scope: UiScope? = null

    private var block: (UiScope.() -> Unit)? = null

    private var segments: List<Segment> = emptyList()

    private val dependencies: MutableSet<LuminState<*>> = LinkedHashSet()

    private var childrenByKey: HashMap<Any, LuminComposition>? = null

    private var childrenByIndex: ArrayList<LuminComposition>? = null

    // 重建期状态
    private var activeSession: UiTree.Scope.CaptureSession? = null

    private var activeSegments: MutableList<Segment>? = null

    private var buildingAnimations: Boolean = false

    private var ordinal: Int = 0

    internal fun markDirty() {
        dirty = true
    }

    internal fun markDirtyDeep() {
        dirty = true
        childrenByKey?.values?.forEach { it.markDirtyDeep() }
        childrenByIndex?.forEach { it.markDirtyDeep() }
    }

    internal fun bind(scope: UiScope, block: UiScope.() -> Unit) {
        this.scope = scope
        this.block = block
    }

    internal fun recordDependency(state: LuminState<*>) {
        if (dependencies.add(state)) {
            state.subscribe(this)
        }
    }

    /**
     * 把本单元的输出写进目标作用域，必要时先重建；返回本单元是否包含活动动画。
     */
    internal fun emit(into: UiTree.Scope): Boolean {
        if (scope == null) {
            return false
        }
        if (dirty || alwaysDirty) {
            recompose()
        }
        var animated = buildingAnimations
        for (segment in segments) {
            when (segment) {
                is Segment.Nodes -> into.appendNodes(segment.nodes)
                is Segment.Child -> if (segment.child.emit(into)) animated = true
            }
        }
        return animated
    }

    /**
     * 声明一个子缓存单元；重建期把当前字面节点切成片段，非重建期交给运行时惰性处理。
     */
    internal fun group(owner: UiScope, key: Any?, block: UiScope.() -> Unit, alwaysDirty: Boolean = false) {
        val segments = activeSegments
        if (segments == null) {
            // 不在重建期（例如立即模式里嵌套调用），直接执行即可。
            block(owner)
            return
        }
        flushLiteral()
        ordinal += 1
        val child = childAt(ordinal - 1, key)
        child.alwaysDirty = alwaysDirty
        child.bind(owner, block)
        if (child.dirty || child.alwaysDirty) {
            child.recompose()
        }
        segments.add(Segment.Child(child))
    }

    private fun recompose() {
        val owner = scope ?: return
        val content = block ?: return
        dirty = false
        dependencies.forEach { it.unsubscribe(this) }
        dependencies.clear()

        val session = owner.tree.openCapture()
        val built = ArrayList<Segment>()
        activeSession = session
        activeSegments = built
        buildingAnimations = false
        ordinal = 0
        try {
            LuminRuntime.inComposition(this) { content(owner) }
            flushLiteral()
        } finally {
            activeSession = null
            activeSegments = null
            session.close()
        }
        segments = built
    }

    private fun flushLiteral() {
        val session = activeSession ?: return
        val segments = activeSegments ?: return
        val chunk = session.flush()
        if (chunk.activeAnimations) {
            buildingAnimations = true
        }
        if (chunk.nodes.isNotEmpty()) {
            segments.add(Segment.Nodes(chunk.nodes))
        }
    }

    private fun childAt(ordinal: Int, key: Any?): LuminComposition {
        if (key != null) {
            val byKey = childrenByKey ?: HashMap<Any, LuminComposition>().also { childrenByKey = it }
            return byKey.getOrPut(key) { LuminComposition(key) }
        }
        val byIndex = childrenByIndex ?: ArrayList<LuminComposition>().also { childrenByIndex = it }
        while (byIndex.size <= ordinal) {
            byIndex.add(LuminComposition(null))
        }
        return byIndex[ordinal]
    }

    private sealed interface Segment {

        class Nodes(val nodes: List<UiTree.UiNode>) : Segment

        class Child(val child: LuminComposition) : Segment

    }

}

/**
 * 场景级的 recompose 注册表。
 *
 * 入口按出现顺序编号，编号在帧内稳定即可（与 Compose 的位置化记忆一致）。
 */
class LuminSceneRoot internal constructor() {

    private val entries: MutableList<LuminComposition> = ArrayList()

    private var nextIndex: Int = 0

    internal fun beginFrame() {
        nextIndex = 0
    }

    internal fun nextEntry(bounds: UiRect, key: Any?): LuminComposition {
        val index = nextIndex++
        while (entries.size <= index) {
            entries.add(LuminComposition(null))
        }
        val entry = entries[index]
        if (entry.lastBounds != bounds || entry.lastKey != key) {
            entry.lastBounds = bounds
            entry.lastKey = key
            entry.markDirtyDeep()
        }
        return entry
    }

    /** 配置加载、语言切换等场景变化后强制全部重建。 */
    fun invalidateAll() {
        entries.forEach { it.markDirtyDeep() }
    }

}
