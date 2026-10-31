package com.github.epsilon.events.api

import com.github.epsilon.Constants
import com.github.epsilon.kernel.collections.ArrayMap
import com.github.epsilon.kernel.threads.Coroutine
import com.github.epsilon.utils.player.ChatUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.get

open class NamedProfilerEventBus(private val profilerName: String) : ProfilerEventBus()

open class ProfilerEventBus : EventBus() {
    override fun post(event: Any) {
        for (listener in handlers) {
            runCatching {
                listener.function.invoke(event)
            }.onFailure { e ->
                unsubscribe(listener)
                ChatUtils.sendMessage("Error while posting event $event, CLion has forced unsubscribe!")
                Constants.LOGGER.fatal("Error while posting event $event, CLion has forced unsubscribe!")
                e.printStackTrace()
            }
        }
        invokeParallel(event)
    }
}


open class EventBus : IPosting {
    override val eventBus: EventBus
        get() = this
    val busID = id.getAndIncrement()

    init {
        eventBusMap[busID] = this
    }

    protected val handlers = CopyOnWriteArrayList<Handler>()
    private val parallelHandlers = CopyOnWriteArrayList<ParallelHandler>()

    override fun post(event: Any) {
        for (listener in handlers) {
            runCatching {
                listener.function.invoke(event)
            }.onFailure { e ->
                unsubscribe(listener)
                ChatUtils.sendMessage("Error while posting event $event, CLion has forced unsubscribe!")
                Constants.LOGGER.fatal("Error while posting event $event, CLion has forced unsubscribe!")
                e.printStackTrace()
            }
        }
        invokeParallel(event)
    }

    protected fun invokeParallel(event: Any) {
        if (!parallelHandlers.isEmpty()) {
            runBlocking {
                for (listener in parallelHandlers) {
                    launch(Coroutine.context) {
                        runCatching {
                            listener.function.invoke(event)
                        }.onFailure { e ->
                            unsubscribe(listener)
                            ChatUtils.sendMessage("Error while posting event $event, CLion has forced unsubscribe!")
                            Constants.LOGGER.fatal("Error while posting event $event, CLion has forced unsubscribe!")
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun subscribe(handler: Handler) {
        for (i in handlers.indices) {
            val other = handlers[i]
            if (handler == other) {
                return
            } else if (handler.priority > other.priority) {
                handlers.add(i, handler)
                return
            }
        }

        handlers.add(handler)
    }

    fun subscribe(listener: ParallelHandler) {
        for (i in parallelHandlers.indices) {
            val other = parallelHandlers[i]
            if (listener == other) {
                return
            } else if (listener.priority > other.priority) {
                parallelHandlers.add(i, listener)
                return
            }
        }

        parallelHandlers.add(listener)
    }

    fun unsubscribe(handler: Handler) {
        handlers.removeIf {
            it == handler
        }
    }

    fun unsubscribe(listener: ParallelHandler) {
        parallelHandlers.removeIf {
            it == listener
        }
    }

    companion object {
        private val id = AtomicInteger()
        private val eventBusMap = ArrayMap<EventBus>()

        operator fun get(busID: Int): EventBus {
            return eventBusMap[busID]!!
        }
    }
}