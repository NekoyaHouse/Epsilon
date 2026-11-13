package com.github.epsilon.kernel.ffi.smtc

import com.github.epsilon.Constants
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Windows SMTC 媒体会话轮询服务。
 *
 * 以 750ms 固定间隔在独立守护线程上刷新快照；每次启动都会递增 generation，
 * 旧批次残留的 poll 通过 generation 校验丢弃，避免停止后仍写入快照。
 */
object SmtcService {

    private val snapshotRef: AtomicReference<SmtcSnapshot> = AtomicReference(SmtcSnapshot.UNAVAILABLE)
    private val generation: AtomicLong = AtomicLong()

    private var executor: ScheduledExecutorService? = null
    private var lastError: String = ""

    @Synchronized
    fun start() {
        if (executor != null || !SmtcEngine.available) return

        val activeGeneration = generation.incrementAndGet()
        val scheduled = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "Epsilon-SMTC").apply { isDaemon = true }
        }
        executor = scheduled
        // reset 排在同一轮询线程上执行，保证 WinRT 公寓初始化与探测顺序不被破坏。
        scheduled.execute { SmtcEngine.reset() }
        scheduled.scheduleWithFixedDelay(Runnable { poll(activeGeneration) }, 0L, 750L, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun stop() {
        generation.incrementAndGet()
        val current = executor
        executor = null
        snapshotRef.set(SmtcSnapshot.UNAVAILABLE)
        lastError = ""
        current?.shutdownNow()
    }

    fun snapshot(): SmtcSnapshot {
        return snapshotRef.get()
    }

    private fun poll(activeGeneration: Long) {
        if (generation.get() != activeGeneration) return

        try {
            val result = SmtcEngine.poll()
            synchronized(this) {
                if (generation.get() != activeGeneration) return

                val error = result.error
                if (error.isNotBlank() && error != lastError) {
                    Constants.LOGGER.warn("Windows SMTC query failed: {}", error)
                }
                lastError = error
                snapshotRef.updateAndGet { previous -> SmtcSnapshot.merge(previous, result) }
            }
        } catch (e: Throwable) {
            synchronized(this) {
                if (generation.get() != activeGeneration) return

                val message = e.javaClass.name + ": " + e.message
                if (message != lastError) {
                    Constants.LOGGER.warn("Windows SMTC polling stopped by an engine error", e)
                    lastError = message
                }
                snapshotRef.set(SmtcSnapshot.UNAVAILABLE)
            }
        }
    }

}
