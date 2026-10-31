package com.github.epsilon.events.api

import com.github.epsilon.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

open class NamedProfilerEventBus(private val profilerName: String) : ProfilerEventBus()

open class ProfilerEventBus : EventBus()

open class EventBus : IPosting {
    final override val eventBus: EventBus
        get() = this

    val busID: Int = id.getAndIncrement()

    init {
        eventBusMap[busID] = this
    }

    protected val handlers = CopyOnWriteArrayList<Handler>()
    private val parallelHandlers = CopyOnWriteArrayList<ParallelHandler>()

    override fun post(event: Any) {
        for (handler in handlers) {
            try {
                handler.function(event)
            } catch (exception: Exception) {
                unsubscribe(handler)
                logFailure(event, handler, exception)
            }
        }
        invokeParallel(event)
    }

    protected fun invokeParallel(event: Any) {
        if (parallelHandlers.isEmpty()) return
        runBlocking {
            for (handler in parallelHandlers) {
                launch(Dispatchers.Default) {
                    try {
                        handler.function(event)
                    } catch (exception: Exception) {
                        unsubscribe(handler)
                        logFailure(event, handler, exception)
                    }
                }
            }
        }
    }

    fun subscribe(handler: Handler) {
        synchronized(handlers) {
            if (handler in handlers) return
            val index = handlers.indexOfFirst { handler.priority > it.priority }
            if (index < 0) handlers.add(handler) else handlers.add(index, handler)
        }
    }

    fun subscribe(handler: ParallelHandler) {
        synchronized(parallelHandlers) {
            if (handler in parallelHandlers) return
            val index = parallelHandlers.indexOfFirst { handler.priority > it.priority }
            if (index < 0) parallelHandlers.add(handler) else parallelHandlers.add(index, handler)
        }
    }

    fun unsubscribe(handler: Handler) {
        synchronized(handlers) { handlers.remove(handler) }
    }

    fun unsubscribe(handler: ParallelHandler) {
        synchronized(parallelHandlers) { parallelHandlers.remove(handler) }
    }

    private fun logFailure(event: Any, handler: AbstractHandler<*>, exception: Exception) {
        Constants.LOGGER.error(
            "事件 {} 的监听器 {} (priority={}) 执行失败，已取消订阅",
            event.javaClass.name, handler.ownerName, handler.priority, exception
        )
    }

    companion object {
        private val id = AtomicInteger()
        private val eventBusMap = ConcurrentHashMap<Int, EventBus>()

        operator fun get(busID: Int): EventBus = checkNotNull(eventBusMap[busID]) {
            "Unknown event bus: $busID"
        }
    }
}
