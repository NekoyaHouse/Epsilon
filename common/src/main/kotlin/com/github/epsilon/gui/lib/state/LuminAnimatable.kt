package com.github.epsilon.gui.lib.state

import com.github.epsilon.kernel.state.State
import com.github.epsilon.kernel.state.readUntracked
import com.github.epsilon.utils.render.animation.Easing

/** 帧时钟推进数值；构建中仅声明目标，成功提交后才启动。 */
class LuminAnimatable(
    initial: Float,
    var easing: Easing,
    var duration: Long,
) : State<Float>, LuminFrameTask, AutoCloseable {
    constructor(easing: Easing, duration: Long) : this(0.0f, easing, duration)

    private val state = mutableStateOf(initial)
    private var from = initial
    private var destination = initial
    private var startedAt: Long? = null
    private var running = false
    private var clock: LuminFrameClock? = null

    override var value: Float
        get() {
            attach()
            return state.value
        }
        set(value) {
            snapTo(value)
        }

    var startValue: Float
        get() = from
        set(value) {
            snapTo(value)
        }

    var target: Float
        get() = destination
        set(value) {
            animateTo(value)
        }

    val isRunning: Boolean get() = running
    val isFinished: Boolean get() = !running

    private fun attach() {
        val composition = LuminRuntime.currentComposition
        val hostClock = composition?.clock ?: LuminRuntime.activeRoot?.clock ?: return
        if (clock !== hostClock) {
            clock?.unregister(this)
            clock = hostClock
            startedAt = null
        }
        if (composition != null) composition.retain(this) { AutoCloseable { close() } }
        if (running) hostClock.register(this)
    }

    fun animateTo(value: Float) {
        attach()
        val composition = LuminRuntime.currentComposition
        if (composition != null) {
            composition.afterCommit { animateTo(value) }
            return
        }
        if (destination == value) {
            if (running && duration <= 0L) snapTo(value)
            return
        }
        from = state.readUntracked()
        destination = value
        startedAt = clock?.nowNanos
        running = from != value
        if (duration <= 0L) snapTo(value)
        else if (running) clock?.register(this)
    }

    fun run(value: Float) {
        target = value
    }

    fun reset() {
        val composition = LuminRuntime.currentComposition
        if (composition != null) {
            composition.afterCommit { reset() }
            return
        }
        startedAt = null
        from = state.readUntracked()
    }

    fun snapTo(value: Float) {
        val composition = LuminRuntime.currentComposition
        if (composition != null) {
            composition.afterCommit { snapTo(value) }
            return
        }
        clock?.unregister(this)
        running = false
        from = value
        destination = value
        startedAt = null
        state.value = value
    }

    override fun advance(nowNanos: Long): Boolean {
        if (!running) return false
        val start = startedAt ?: nowNanos.also { startedAt = it }
        val progress = if (duration <= 0L) 1.0f else
            ((nowNanos - start).toDouble() / (duration * 1_000_000.0)).toFloat().coerceIn(0.0f, 1.0f)
        state.value = if (progress >= 1.0f) destination else
            from + (destination - from) * easing.function.apply(progress)
        if (progress >= 1.0f) running = false
        return running
    }

    override fun close() {
        clock?.unregister(this)
        clock = null
        running = false
        startedAt = null
        destination = state.readUntracked()
    }
}

fun animatable(initial: Float, easing: Easing, durationMs: Long): LuminAnimatable =
    LuminAnimatable(initial, easing, durationMs)

fun rememberAnimatable(key: Any, initial: Float, easing: Easing, durationMs: Long): LuminAnimatable =
    remember(key) { LuminAnimatable(initial, easing, durationMs) }

fun animateFloatAsState(key: Any, targetValue: Float, easing: Easing, durationMs: Long): State<Float> {
    val animation = rememberAnimatable(key, targetValue, easing, durationMs)
    checkNotNull(LuminRuntime.currentComposition).afterCommit {
        animation.duration = durationMs
        animation.easing = easing
        animation.animateTo(targetValue)
    }
    return animation
}
