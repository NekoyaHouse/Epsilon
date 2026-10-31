package com.github.epsilon.events.api

interface IListening {
    fun register(handler: Handler)
    fun register(handler: ParallelHandler)
    fun subscribe()
    fun unsubscribe()
}

open class ListenerOwner : IListening {
    private val listeners = ArrayList<Handler>()
    private val parallelListeners = ArrayList<ParallelHandler>()
    private var subscribed = false

    @Synchronized
    override fun register(handler: Handler) {
        if (handler in listeners) return
        listeners.add(handler)
        if (subscribed) EventBus[handler.eventID].subscribe(handler)
    }

    @Synchronized
    override fun register(handler: ParallelHandler) {
        if (handler in parallelListeners) return
        parallelListeners.add(handler)
        if (subscribed) EventBus[handler.eventID].subscribe(handler)
    }

    @Synchronized
    override fun subscribe() {
        if (subscribed) return
        subscribed = true
        for (listener in listeners) EventBus[listener.eventID].subscribe(listener)
        for (listener in parallelListeners) EventBus[listener.eventID].subscribe(listener)
    }

    @Synchronized
    override fun unsubscribe() {
        if (!subscribed) return
        subscribed = false
        for (listener in listeners) EventBus[listener.eventID].unsubscribe(listener)
        for (listener in parallelListeners) EventBus[listener.eventID].unsubscribe(listener)
    }
}

interface AlwaysListening : IListening {
    override fun register(handler: Handler) {
        EventBus[handler.eventID].subscribe(handler)
    }

    override fun register(handler: ParallelHandler) {
        EventBus[handler.eventID].subscribe(handler)
    }

    override fun subscribe() {
    }

    override fun unsubscribe() {
    }
}
