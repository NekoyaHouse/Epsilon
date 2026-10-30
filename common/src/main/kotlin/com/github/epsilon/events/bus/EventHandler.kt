package com.github.epsilon.events.bus

/**
 * Used to mark listeners.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class EventHandler(val priority: Int = EventPriority.MEDIUM)
