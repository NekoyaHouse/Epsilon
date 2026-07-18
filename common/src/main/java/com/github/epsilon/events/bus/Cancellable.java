package com.github.epsilon.events.bus;

public class Cancellable {

    private boolean cancelled = false;

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }

}
