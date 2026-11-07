package com.github.epsilon.gui.lib.state

import com.github.epsilon.utils.render.animation.Easing

/**
 * 状态化的动画：每一帧由 [LuminRuntime] 推进，并把结果写进 [state]。
 *
 * 与旧 `UiScope.animate(animation, target)` 的区别：
 * - 旧 API 是拉取式的，必须每帧调用才前进，因此在 recompose 块里会冻结；
 * - 本类型是推式的，写入 [target] 后由帧循环持续写状态，读它的 composition 在动画期间
 *   每帧重建，动画结束后自动停止写入与重建。
 */
class LuminAnimatable(
    initial: Float,
    private val easing: Easing,
    private val durationMs: Long,
) {

    private val state: MutableState<Float> = mutableStateOf(initial)

    private var startValue: Float = initial

    private var targetValue: Float = initial

    private var elapsedMs: Long = 0L

    private var running: Boolean = false

    /** 当前动画值；读取即订阅。 */
    val value: Float get() = state.value

    /** 目标值；写入即启动动画。 */
    var target: Float
        get() = targetValue
        set(newValue) {
            if (targetValue == newValue) {
                return
            }
            targetValue = newValue
            startValue = state.readUntracked()
            elapsedMs = 0L
            running = true
            LuminRuntime.registerAnimatable(this)
        }

    /** 是否正在播放。 */
    val isRunning: Boolean get() = running

    /** 立即跳到目标值并停止动画。 */
    fun snapTo(value: Float) {
        running = false
        startValue = value
        targetValue = value
        elapsedMs = 0L
        state.value = value
    }

    /** 由帧循环调用；返回是否仍在运行。 */
    internal fun advance(deltaMs: Long): Boolean {
        if (!running) {
            return false
        }
        if (durationMs <= 0L) {
            running = false
            state.value = targetValue
            return false
        }
        elapsedMs += deltaMs
        val progress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0.0f, 1.0f)
        val eased = easing.function.apply(progress)
        state.value = startValue + (targetValue - startValue) * eased
        if (progress >= 1.0f) {
            running = false
            state.value = targetValue
            return false
        }
        return true
    }

}

/** 便捷构造。 */
fun animatable(initial: Float, easing: Easing, durationMs: Long): LuminAnimatable =
    LuminAnimatable(initial, easing, durationMs)
