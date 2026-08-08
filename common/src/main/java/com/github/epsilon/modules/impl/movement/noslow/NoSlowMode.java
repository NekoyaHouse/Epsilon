package com.github.epsilon.modules.impl.movement.noslow;

import com.github.epsilon.events.impl.AfterSendPositionEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.events.impl.SlowdownEvent;
import net.minecraft.client.Minecraft;

abstract class NoSlowMode {

    protected final Minecraft mc = Minecraft.getInstance();
    protected final NoSlow noSlow;

    protected NoSlowMode(NoSlow noSlow) {
        this.noSlow = noSlow;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onGameLeft() {
    }

    public void onClientTick() {
    }

    public void onSendPosition(SendPositionEvent event) {
    }

    public void onAfterSendPosition(AfterSendPositionEvent event) {
    }

    public void onPacketReceive(PacketEvent.Receive event) {
    }

    public void onPacketSend(PacketEvent.Send event) {
    }

    public void onSlowdown(SlowdownEvent event) {
    }

    public boolean isWorking() {
        return false;
    }

    public void stopIfWorking() {
    }

}
