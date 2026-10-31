package com.github.epsilon.events.bus.listeners

/**
 * Base type for all listeners.
 */
interface IListener {

    /**
     * Calls the listener with the specified event.
     *
     * @param event Event to pass in
     */
    fun call(event: Any)

    /**
     * @return The target event type this listener is for
     */
    val target: Class<*>

    /**
     * @return The priority for this listener
     */
    val priority: Int

    /**
     * @return True if this listener is for static methods
     */
    @Deprecated("Will be removed in a future version")
    fun isStatic(): Boolean

}
