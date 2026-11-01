package com.github.epsilon.mixins;

import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.managers.network.ClientboundPacketManager;
import com.github.epsilon.managers.network.ServerboundPacketManager;
import com.github.epsilon.utils.network.ClientIdentityHider;
import com.github.epsilon.utils.network.NetworkUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(Connection.class)
public class MixinConnection {

    @WrapOperation(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"))
    @SuppressWarnings("unchecked")
    private void onReceivePacket(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        if (packet instanceof ClientboundBundlePacket bundle) {
            List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
            for (Packet<? super ClientGamePacketListener> subPacket : bundle.subPackets()) {
                if (ClientboundPacketManager.INSTANCE.onPacketReceive(subPacket)) {
                    continue;
                }
                PacketEvent.Receive event = new PacketEvent.Receive(subPacket).post();
                if (!event.isCancelled()) {
                    packets.add((Packet<? super ClientGamePacketListener>) event.getPacket());
                }
            }
            original.call(new ClientboundBundlePacket(packets), listener);
            return;
        }
        if (ClientboundPacketManager.INSTANCE.onPacketReceive(packet)) {
            return;
        }
        PacketEvent.Receive event = new PacketEvent.Receive(packet).post();
        if (!event.isCancelled()) {
            original.call(event.getPacket(), listener);
        }
    }

    @WrapOperation(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"))
    private void onSendPacket(Connection instance, Packet<?> packet, ChannelFutureListener listener, boolean flush, Operation<Void> original) {
        if (ServerboundPacketManager.INSTANCE.onPacketSend(packet)) {
            return;
        }
        if (NetworkUtils.INSTANCE.getBypassedPackets().contains(packet)) {
            NetworkUtils.INSTANCE.getBypassedPackets().remove(packet);
            Packet<?> filteredPacket = ClientIdentityHider.INSTANCE.filterServerboundPacket(packet);
            if (filteredPacket != null) {
                original.call(instance, filteredPacket, listener, flush);
            }
        } else {
            PacketEvent.Send event = new PacketEvent.Send(packet).post();
            if (!event.isCancelled()) {
                Packet<?> filteredPacket = ClientIdentityHider.INSTANCE.filterServerboundPacket(event.getPacket());
                if (filteredPacket != null) {
                    original.call(instance, filteredPacket, listener, flush);
                }
            }
        }
    }

}
