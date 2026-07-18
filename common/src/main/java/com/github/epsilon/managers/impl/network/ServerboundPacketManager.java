package com.github.epsilon.managers.impl.network;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.LevelUpdateEvent;
import com.github.epsilon.managers.Managers;
import net.minecraft.network.protocol.Packet;

import java.util.concurrent.LinkedBlockingQueue;

import static com.github.epsilon.Constants.mc;

// This is BlinkManager
public class ServerboundPacketManager {

    public ServerboundPacketManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public final LinkedBlockingQueue<Packet<?>> packets = new LinkedBlockingQueue<>();

    public boolean blinking = false;
    static boolean forceFlush;

    @EventHandler
    private void onLevelUpdate(LevelUpdateEvent event) {
        forceFlush = true;
        blinking = false;
    }

    public void flush() {
        while (!packets.isEmpty()) {
            try {
                mc.getConnection().send(packets.poll());
            } catch (Exception e) {
                Managers.NOTIFICATION.error("Serverbound Packet", "failed to flush packets: " + e.getMessage());
            }
        }
    }

    public void stopBlinking() {
        blinking = false;
    }

    public void startBlinking() {
        blinking = true;
    }

    public boolean onPacketSend(Packet<?> packet) {
        if (forceFlush) {
            flush();
            forceFlush = false;
            return false;
        }

        if (!blinking) return false;

        if (mc.player == null || mc.level == null) return false;

        packets.add(packet);
        return true;
    }

}
