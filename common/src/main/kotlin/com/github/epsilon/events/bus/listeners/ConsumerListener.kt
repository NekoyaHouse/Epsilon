package com.github.epsilon.events.bus.listeners

import com.github.epsilon.events.bus.EventPriority
import java.util.function.Consumer

/**
 * Listener that takes in a [Consumer].
 */
class ConsumerListener<T>(
    override val target: Class<T>,
    override val priority: Int,
    private val executor: Consumer<T>,
) : IListener {

    constructor(target: Class<T>, executor: Consumer<T>) : this(target, EventPriority.MEDIUM, executor)

    @Suppress("UNCHECKED_CAST")
    override fun call(event: Any) {
        executor.accept(event as T)
    }

    @Deprecated("Will be removed in a future version")
    override fun isStatic(): Boolean = false

}
