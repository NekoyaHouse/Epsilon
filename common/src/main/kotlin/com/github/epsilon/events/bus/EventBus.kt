package com.github.epsilon.events.bus

import com.github.epsilon.events.bus.listeners.IListener
import com.github.epsilon.events.bus.listeners.LambdaListener
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object EventBus {

    private val listenerCache: MutableMap<Any, MutableList<IListener>> = ConcurrentHashMap()
    private val staticListenerCache: MutableMap<Class<*>, MutableList<IListener>> = ConcurrentHashMap()

    private val listenerMap: MutableMap<Class<*>, MutableList<IListener>> = ConcurrentHashMap()

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

    fun subscribe(obj: Any) {
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
