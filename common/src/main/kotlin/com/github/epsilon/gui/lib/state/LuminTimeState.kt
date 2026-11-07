package com.github.epsilon.gui.lib.state

import com.github.epsilon.kernel.state.State

private data class ClockKey(val kind: String, val key: Any)

private class ClockState<T>(
    private val clock: LuminFrameClock,
    private val sample: (Long) -> T,
) : State<T>, LuminFrameTask, AutoCloseable {
    private val state = mutableStateOf(sample(clock.nowNanos))
    init { clock.register(this) }
    override val value: T get() = state.value
    override fun advance(nowNanos: Long): Boolean {
        state.value = sample(nowNanos)
        return true
    }
    override fun close() = clock.unregister(this)
}

/** 仅在周期翻转时失效；离开焦点分支会自动取消时钟任务。 */
fun luminBlink(key: Any, periodMs: Long = 500L): Boolean {
    require(periodMs > 0L)
    val composition = LuminRuntime.currentComposition
    if (composition == null) return ((LuminRuntime.activeRoot?.clock?.nowNanos ?: System.nanoTime()) / (periodMs * 1_000_000L)) % 2L == 0L
    return composition.retain(ClockKey("blink", key)) {
        ClockState(composition.clock) { nanos -> (nanos / (periodMs * 1_000_000L)) % 2L == 0L }
    }.value
}

/** 持续视觉效果显式订阅帧时间，不把整棵 UI 变成立即模式。 */
fun luminFrameTime(key: Any): Long {
    val composition = LuminRuntime.currentComposition
        ?: return LuminRuntime.activeRoot?.clock?.nowNanos ?: System.nanoTime()
    return composition.retain(ClockKey("frame", key)) { ClockState(composition.clock) { it } }.value
}
