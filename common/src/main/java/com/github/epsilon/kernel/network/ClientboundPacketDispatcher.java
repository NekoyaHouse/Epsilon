package com.github.epsilon.kernel.network;

import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.kernel.MinecraftWrapper;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;

import java.util.function.BooleanSupplier;

public class ClientboundPacketDispatcher {
    private ClientboundPacketDispatcher() {
    }

    public static void receive(Packet<?> packet, ClientGamePacketListener listener) {
        PacketEvent.ReceiveNetty event = new PacketEvent.ReceiveNetty(packet);
        event.post();
        if (!event.isCancelled()) {
            replay(event.getPacket(), listener);
        }
    }

    public static void replay(Packet<?> packet, ClientGamePacketListener listener) {
        replay(packet, listener, MinecraftWrapper.mc.packetProcessor(), () -> listener instanceof ClientPacketListener gameListener && gameListener.getConnection().getPacketListener() == listener && gameListener.isAcceptingMessages());
    }

    private static void replay(
            Packet<?> packet, ClientGamePacketListener listener, PacketProcessor processor, BooleanSupplier currentConnection
    ) {
        MainThreadPacket queued = new MainThreadPacket(packet, processor, currentConnection);
        if (processor.isSameThread()) queued.handle(listener);
        else processor.scheduleIfPossible(listener, queued);
    }

    private record MainThreadPacket(
            Packet<?> packet, PacketProcessor processor, BooleanSupplier currentConnection
    ) implements Packet<ClientGamePacketListener> {
        @Override
        public PacketType<? extends Packet<ClientGamePacketListener>> type() {
            return (PacketType<? extends Packet<ClientGamePacketListener>>) (PacketType<?>) packet.type();
        }

        @Override
        public boolean isSkippable() {
            return packet.isSkippable();
        }

        @Override
        public boolean isTerminal() {
            return packet.isTerminal();
        }

        @Override
        public void handle(ClientGamePacketListener listener) {
            PacketUtils.ensureRunningOnSameThread(this, listener, processor);
            if (!currentConnection.getAsBoolean()) return;
            handleAccepted(packet, listener, currentConnection);
        }
    }

    private static void handleAccepted(Packet<?> packet, ClientGamePacketListener listener, BooleanSupplier currentConnection) {
        if (!currentConnection.getAsBoolean() || !listener.shouldHandleMessage(packet)) return;
        if (packet instanceof ClientboundBundlePacket bundle) {
            for (var subPacket : bundle.subPackets()) {
                handleAccepted(subPacket, listener, currentConnection);
            }
            return;
        }
        new PacketEvent.ReceiveMain(packet).post();
        if (currentConnection.getAsBoolean() && listener.shouldHandleMessage(packet)) {
            ((Packet<ClientGamePacketListener>) packet).handle(listener);
        }
    }
}
