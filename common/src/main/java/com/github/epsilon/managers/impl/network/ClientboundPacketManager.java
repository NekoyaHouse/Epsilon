package com.github.epsilon.managers.impl.network;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.LevelUpdateEvent;
import com.github.epsilon.managers.Managers;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;

import java.util.concurrent.LinkedBlockingQueue;

import static com.github.epsilon.Constants.mc;

public class ClientboundPacketManager {

    public ClientboundPacketManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    private final LinkedBlockingQueue<Packet> packets = new LinkedBlockingQueue<>();

    private boolean tracking = false;
    private boolean shouldFlush = false;

    @EventHandler
    private void onLevelUpdate(LevelUpdateEvent event) {
        shouldFlush = true;
        tracking = false;
    }

    @SuppressWarnings("unchecked")
    public void flush() {
        while (!packets.isEmpty()) {
            try {
                packets.poll().handle(mc.getConnection().getConnection().getPacketListener());
            } catch (Exception e) {
                Managers.NOTIFICATION.error("Clientbound Packet", "Failed to flush packets: " + e.getMessage());
            }
        }
    }

    public boolean isDisallowedPacket(Packet<?> packet) {
        return !(packet instanceof ClientboundSystemChatPacket)
                && !(packet instanceof ClientboundPlayerChatPacket)
                && !(packet instanceof ClientboundSetDisplayObjectivePacket)
                && !(packet instanceof ClientboundSetEquipmentPacket)
                && !(packet instanceof ClientboundClearTitlesPacket)
                && !(packet instanceof ClientboundSetTitleTextPacket)
                && !(packet instanceof ClientboundSetSubtitleTextPacket)
                && !(packet instanceof ClientboundSetActionBarTextPacket)
                && !(packet instanceof ClientboundBossEventPacket)
                && !(packet instanceof ClientboundAddEntityPacket)
                && !(packet instanceof ClientboundRemoveEntitiesPacket)
                && !(packet instanceof ClientboundDamageEventPacket)
                && !(packet instanceof ClientboundSoundPacket)
                && !(packet instanceof ClientboundSoundEntityPacket)
                && !(packet instanceof ClientboundSetEntityDataPacket)
                && !(packet instanceof ClientboundSetHealthPacket)
                && !(packet instanceof ClientboundContainerSetContentPacket)
                && !(packet instanceof ClientboundContainerSetSlotPacket)
                && !(packet instanceof ClientboundSetObjectivePacket)
                && !(packet instanceof ClientboundResetScorePacket)
                && !(packet instanceof ClientboundSetScorePacket);
    }

    public void startTracking() {
        tracking = true;
    }

    public void stopTracking() {
        tracking = false;
    }

    public boolean onPacketReceive(Packet<?> packet) {
        if (mc.player == null || mc.level == null) return false;

        if (shouldFlush) {
            flush();
            shouldFlush = false;
            return false;
        }

        if (!isDisallowedPacket(packet) && tracking) {
            packets.add(packet);
            return true;
        }
        return false;
    }

}
