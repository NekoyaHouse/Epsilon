package com.github.epsilon.events.bus

import com.github.epsilon.Constants
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 显式 Handler 事件总线。
 *
 * 每个 Handler 在注册时固定 owner、事件类型和优先级；owner 订阅后其 Handler 才加入分发，
 * `alwaysListening` Handler 则直接常驻。事件只按运行时 class 精确分发。
 */
object EventBus {

    private val handlersByEvent: MutableMap<Class<*>, CopyOnWriteArrayList<Handler<*>>> = ConcurrentHashMap()
    private val handlersByOwner: MutableMap<Any, MutableList<Handler<*>>> =
        Collections.synchronizedMap(IdentityHashMap())
    private val subscribedOwners: MutableSet<Any> =
        Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))
    private val ownerLock = Any()

    fun <T : Any> post(event: T): T {
        val handlers = handlersByEvent[event.javaClass] ?: return event
        for (handler in handlers) {
            try {
                handler.call(event)
            } catch (throwable: Throwable) {
                unsubscribe(handler)
                Constants.LOGGER.error(
                    "Event handler '{}' failed while handling {}; the handler was unsubscribed.",
                    handler.owner.javaClass.name,
                    event.javaClass.name,
                    throwable,
                )
            }
            if (event is Cancellable && event.isCancelled) {
                break
            }
        }
        return event
    }

    /** 注册 owner 绑定的 Handler；owner 已订阅时立即生效。 */
    fun register(owner: Any, handler: Handler<*>) {
        synchronized(ownerLock) {
            handlersByOwner.computeIfAbsent(owner) { CopyOnWriteArrayList() }.add(handler)
            if (subscribedOwners.contains(owner)) {
                subscribe(handler)
            }
        }
    }

    fun isSubscribed(owner: Any): Boolean = subscribedOwners.contains(owner)

    fun isListening(eventClass: Class<*>): Boolean = handlersByEvent[eventClass]?.isNotEmpty() == true

    fun subscribe(owner: Any) {
        synchronized(ownerLock) {
            subscribedOwners.add(owner)
            handlersByOwner[owner]?.forEach(::subscribe)
        }
    }

    fun subscribe(handler: Handler<*>) {
        val handlers = handlersByEvent.computeIfAbsent(handler.eventClass) { CopyOnWriteArrayList() }
        synchronized(handlers) {
            if (!handlers.contains(handler)) {
                val index = handlers.indexOfFirst { handler.priority > it.priority }
                if (index >= 0) {
                    handlers.add(index, handler)
                } else {
                    handlers.add(handler)
                }
            }
        }
    }

    fun unsubscribe(owner: Any) {
        synchronized(ownerLock) {
            subscribedOwners.remove(owner)
            handlersByOwner[owner]?.forEach(::unsubscribe)
        }
    }

    fun unsubscribe(handler: Handler<*>) {
        handlersByEvent[handler.eventClass]?.remove(handler)
    }

}
