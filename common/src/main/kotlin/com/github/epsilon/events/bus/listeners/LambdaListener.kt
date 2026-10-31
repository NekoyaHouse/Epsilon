package com.github.epsilon.events.bus.listeners

import com.github.epsilon.events.bus.EventHandler
import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * Default implementation of a [IListener] that creates a lambda at runtime to call the target method.
 */
class LambdaListener(klass: Class<*>, obj: Any?, method: Method) : IListener {

    override val target: Class<*> = method.parameters[0].type
    private val isStaticListener: Boolean = Modifier.isStatic(method.modifiers)
    override val priority: Int = method.getAnnotation(EventHandler::class.java)!!.priority

    private lateinit var executor: Consumer<Any>

    init {
        try {
            val name = method.name
            val lookup = lookupFor(klass)

            val methodType = MethodType.methodType(Void.TYPE, method.parameters[0].type)

            val methodHandle: MethodHandle
            val invokedType: MethodType

            if (isStaticListener) {
                methodHandle = lookup.findStatic(klass, name, methodType)
                invokedType = MethodType.methodType(Consumer::class.java)
            } else {
                methodHandle = lookup.findVirtual(klass, name, methodType)
                invokedType = MethodType.methodType(Consumer::class.java, klass)
            }

            val lambdaFactory = LambdaMetafactory.metafactory(
                lookup, "accept", invokedType,
                MethodType.methodType(Void.TYPE, Any::class.java), methodHandle, methodType
            ).target

            @Suppress("UNCHECKED_CAST")
            executor = (if (isStaticListener) lambdaFactory.invoke() else lambdaFactory.invoke(obj)) as Consumer<Any>
        } catch (throwable: Throwable) {
            throw IllegalStateException(
                "Failed to create event listener for " + klass.name + "#" + method.name, throwable
            )
        }
    }

    override fun call(event: Any) {
        executor.accept(event)
    }

    @Deprecated("Will be removed in a future version")
    override fun isStatic(): Boolean = isStaticListener

    private companion object {

        /**
         * 按事件处理类缓存 private lookup：同一类型的多个监听器实例反复订阅时不必重复提升访问权限。
         */
        private val LOOKUPS: MutableMap<Class<*>, MethodHandles.Lookup> = ConcurrentHashMap()

        /**
         * 直接为事件处理类取得带 private 访问权限的 lookup，监听类所在包不再需要预先注册。
         */
        fun lookupFor(klass: Class<*>): MethodHandles.Lookup {
            var lookup = LOOKUPS[klass]
            if (lookup == null) {
                lookup = MethodHandles.privateLookupIn(klass, MethodHandles.lookup())
                LOOKUPS[klass] = lookup
            }
            return lookup
        }
    }
}
