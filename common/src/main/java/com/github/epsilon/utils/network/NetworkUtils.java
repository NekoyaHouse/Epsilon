package com.github.epsilon.utils.network;

import net.minecraft.network.protocol.Packet;

import java.util.HashSet;
import java.util.Set;

import static com.github.epsilon.Constants.mc;

public class NetworkUtils {

    public static final Set<Packet<?>> bypassedPackets = new HashSet<>();

    public static void sendPacketNoEvent(Packet<?> packet) {
        bypassedPackets.add(packet);
        mc.getConnection().send(packet);
    }

}
