package com.github.epsilon.kernel.threads

import com.github.epsilon.events.api.AlwaysListening
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.kernel.DoubleBuffered
import com.github.epsilon.kernel.MinecraftWrapper
import com.github.epsilon.kernel.collections.FastObjectArrayList
import com.github.epsilon.kernel.jvm.runSynchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith

object MainThreadExecutor : AlwaysListening {
    private val jobs = DoubleBuffered { FastObjectArrayList<MainThreadJob<*>>() }
    private val signal = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    init {
        handler<ClientTickEvent.Post>(Int.MAX_VALUE, true) {
            runJobs()
        }
//        handler<RunGameLoopEvent.Render>(Int.MAX_VALUE, true) {
//            runJobs()
//        }
        handler<ClientTickEvent.Pre>(Int.MAX_VALUE, true) {
            runJobs()
        }
    }

    private fun runJobs() {
        signal.tryReceive()
        jobs.swap().front.runSynchronized {
            forEach {
                it.run()
            }
            clear()
        }
    }

    suspend fun runJobAdapter() {
        signal.receive()
        jobs.swap().front.runSynchronized {
            forEach {
                it.run()
            }
            clear()
        }
    }

    fun <T> add(block: () -> T): CompletableDeferred<T> {
        val job = MainThreadJob(block)
        if (MinecraftWrapper.mc.isSameThread) {
            job.run()
        } else {
            jobs.back.runSynchronized {
                add(job)
            }
            signal.trySend(Unit)
        }
        return job.deferred
    }

    private class MainThreadJob<T>(private val block: () -> T) {
        val deferred = CompletableDeferred<T>()

        fun run() {
            deferred.completeWith(
                runCatching { block.invoke() }
            )
        }
    }
}