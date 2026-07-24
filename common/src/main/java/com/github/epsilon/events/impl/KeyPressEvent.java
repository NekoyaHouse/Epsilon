package com.github.epsilon.events.impl;

import com.github.epsilon.events.bus.Cancellable;
import net.minecraft.client.input.KeyEvent;

public class KeyPressEvent extends Cancellable {

    private final KeyEvent keyEvent;
    private final int action;

    public KeyPressEvent(KeyEvent keyEvent, int action) {
        this.keyEvent = keyEvent;
        this.action = action;
    }

    public KeyEvent getKeyEvent() {
        return keyEvent;
    }

    public int getAction() {
        return action;
    }

    public int getKey() {
        return keyEvent.key();
    }

    public int getModifiers() {
        return keyEvent.modifiers();
    }

}
