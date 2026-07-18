package com.github.epsilon.events.bus.listeners;

/**
 * Base type for all listeners.
 */
public interface IListener {

    /**
     * Calls the listener with the specified event.
     *
     * @param event Event to pass in
     */
    void call(Object event);

    /**
     * @return The target event type this listener is for
     */
    Class<?> getTarget();

    /**
     * @return The priority for this listener
     */
    int getPriority();

    /**
     * @return True if this listener is for static methods
     * @deprecated Will be removed in a future version
     */
    @Deprecated
    boolean isStatic();

}
