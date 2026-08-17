package com.github.epsilon.modules.impl.player.helper;

import com.github.epsilon.utils.rotation.Rot2f;

public abstract class HelperBase {

    public final String name;

    protected HelperBase(String name) {
        this.name = name;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onTick() {
    }

    public void onMotion() {
    }

    public void onPreMotion() {
    }

    public void onRender() {
    }

    public boolean isActive() {
        return false;
    }

    public Rot2f getTargetRotation() {
        return null;
    }

}
