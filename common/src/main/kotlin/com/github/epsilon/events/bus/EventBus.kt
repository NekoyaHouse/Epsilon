package com.github.epsilon.events.bus

import com.github.epsilon.events.bus.listeners.IListener
import com.github.epsilon.events.bus.listeners.LambdaListener
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object EventBus {

    private val listenerCache: MutableMap<Any, MutableList<IListener>> = ConcurrentHashMap()
    private val staticListenerCache: MutableMap<Class<*>, MutableList<IListener>> = ConcurrentHashMap()

    private val listenerMap: MutableMap<Class<*>, MutableList<IListener>> = ConcurrentHashMap()

    /** owner → 绑定到该对象的 lambda 监听器；随 [subscribe] / [unsubscribe] 一起生效或失效。 */
    private val ownerListeners: MutableMap<Any, MutableList<IListener>> =
        Collections.synchronizedMap(IdentityHashMap())

    /** 当前处于已订阅状态的 owner；晚于订阅注册的监听器需要立即补订阅。 */
    private val subscribedOwners: MutableSet<Any> =
        Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    fun isListening(eventKlass: Class<*>): Boolean {
        val listeners = listenerMap[eventKlass]
        return listeners != null && listeners.isNotEmpty()
    }

    fun <T : Any> post(event: T): T {
        val listeners = listenerMap[event.javaClass]

        if (listeners != null) {
            for (listener in listeners) {
                listener.call(event as Any)
                if (event is Cancellable && event.isCancelled) {
                    break
                }
            }
        }

        return event
    }

    /**
     * 把 [listener] 绑定到 [owner]：owner 已订阅时立即生效，之后跟随 [subscribe] / [unsubscribe] 切换。
     */
    fun attach(owner: Any, listener: IListener) {
        ownerListeners.computeIfAbsent(owner) { CopyOnWriteArrayList() }.add(listener)
        if (isSubscribed(owner)) {
            subscribe(listener, false)
        }
    }

    fun isSubscribed(owner: Any): Boolean = subscribedOwners.contains(owner)

    fun subscribe(obj: Any) {
        subscribedOwners.add(obj)
        for (listener in listenersOf(obj)) {
            subscribe(listener, false)
        }
        subscribe(getListeners(obj.javaClass, obj), false)
    }

    fun subscribe(klass: Class<*>) {
        subscribe(getListeners(klass, null), true)
    }

    fun subscribe(listener: IListener) {
        subscribe(listener, false)
    }

    private fun subscribe(listeners: List<IListener>, onlyStatic: Boolean) {
        for (listener in listeners) {
            subscribe(listener, onlyStatic)
        }
    }

    private fun subscribe(listener: IListener, onlyStatic: Boolean) {
        if (onlyStatic) {
            if (listener.isStatic()) {
                insert(
                    listenerMap.computeIfAbsent(listener.target) { CopyOnWriteArrayList() },
                    listener
                )
            }
        } else {
            insert(
                listenerMap.computeIfAbsent(listener.target) { CopyOnWriteArrayList() },
                listener
            )
        }
    }

    private fun insert(listeners: MutableList<IListener>, listener: IListener) {
        var i = 0
        while (i < listeners.size) {
            if (listener.priority > listeners[i].priority) {
                break
            }
            i++
        }

        listeners.add(i, listener)
    }

    fun unsubscribe(obj: Any) {
        subscribedOwners.remove(obj)
        for (listener in listenersOf(obj)) {
            unsubscribe(listener, false)
        }
        unsubscribe(getListeners(obj.javaClass, obj), false)
    }

    fun unsubscribe(klass: Class<*>) {
        unsubscribe(getListeners(klass, null), true)
    }

    fun unsubscribe(listener: IListener) {
        unsubscribe(listener, false)
    }

    private fun unsubscribe(listeners: List<IListener>, staticOnly: Boolean) {
        for (listener in listeners) {
            unsubscribe(listener, staticOnly)
        }
    }

    private fun unsubscribe(listener: IListener, staticOnly: Boolean) {
        val list = listenerMap[listener.target]

        if (list != null) {
            if (staticOnly) {
                if (listener.isStatic()) {
                    list.remove(listener)
                }
            } else {
                list.remove(listener)
            }
        }
    }

    /** 取 [owner] 绑定的监听器快照，避免订阅/注销期间遍历并发集合。 */
    private fun listenersOf(owner: Any): List<IListener> =
        ownerListeners[owner]?.toList() ?: emptyList()

    private fun getListeners(klass: Class<*>, obj: Any?): MutableList<IListener> {
        if (obj == null) {
            return staticListenerCache.computeIfAbsent(klass) { key ->
                val listeners: MutableList<IListener> = CopyOnWriteArrayList()
                getListeners(listeners, key, obj)
                listeners
            }
        }

        // 需要判断实例是否为同一对象，避免使用 equals()/hashCode()
        for (key in listenerCache.keys) {
            if (key === obj) {
                return listenerCache[obj]!!
            }
        }

        val listeners: MutableList<IListener> = CopyOnWriteArrayList()
        getListeners(listeners, klass, obj)
        listenerCache[obj] = listeners
        return listeners
    }

    private fun getListeners(listeners: MutableList<IListener>, klass: Class<*>, obj: Any?) {
        for (method in klass.declaredMethods) {
            if (isValid(method)) {
                listeners.add(LambdaListener(klass, obj, method))
            }
        }

        val superclass = klass.superclass
        if (superclass != null) getListeners(listeners, superclass, obj)
    }

    private fun isValid(method: Method): Boolean {
        if (!method.isAnnotationPresent(EventHandler::class.java)) return false
        if (method.returnType != Void.TYPE) return false
        if (method.parameterCount != 1) return false
        return !method.parameters[0].type.isPrimitive
    }

}
