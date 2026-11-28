package com.github.epsilon.events.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        invokeParallel(event)
    }

    protected fun invokeParallel(event: Any) {
        if (parallelHandlers.isEmpty()) return
        runBlocking {
            for (handler in parallelHandlers) {
                launch(Dispatchers.Default) {
                    handler.function(event)
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

    companion object {
        private val id = AtomicInteger()
        private val eventBusMap = ConcurrentHashMap<Int, EventBus>()

        operator fun get(busID: Int): EventBus = checkNotNull(eventBusMap[busID]) {
            "Unknown event bus: $busID"
        }
    }
}
