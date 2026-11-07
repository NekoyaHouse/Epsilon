package com.github.epsilon.gui.lib.state

import com.github.epsilon.kernel.state.StateNotifications

/** 返回 true 表示下一帧仍需推进。任务始终在宿主线程执行。 */
fun interface LuminFrameTask {
    fun advance(nowNanos: Long): Boolean
}

class LuminFrameClock(private val timeSource: () -> Long = System::nanoTime) {
    private val tasks = LinkedHashSet<LuminFrameTask>()
    var frame = 0L
        private set
    var nowNanos = timeSource()
        private set
    val taskCount: Int get() = tasks.size

    fun register(task: LuminFrameTask) { tasks.add(task) }
    fun unregister(task: LuminFrameTask) { tasks.remove(task) }

    fun advance(nowNanos: Long = timeSource()) {
        check(nowNanos >= this.nowNanos) { "帧时钟必须单调递增" }
        this.nowNanos = nowNanos
        frame++
        StateNotifications.drain()
        tasks.toList().forEach { if (it in tasks && !it.advance(nowNanos)) tasks.remove(it) }
        StateNotifications.drain()
    }

    fun clear() {
        val owned = tasks.toList()
        tasks.clear()
        owned.forEach { (it as? AutoCloseable)?.close() }
    }
}
