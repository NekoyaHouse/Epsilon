package me.sofurry.via.injection.mixin.core.access;

import io.netty.util.collection.IntObjectMap;
import me.sofurry.via.injection.access.core.bedrock.IRakSessionCodec;
import org.cloudburstmc.netty.channel.raknet.packet.EncapsulatedPacket;
import org.cloudburstmc.netty.channel.raknet.packet.RakDatagramPacket;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodec;
import org.cloudburstmc.netty.util.FastBinaryMinHeap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RakSessionCodec.class)
public abstract class MixinRakSessionCodec implements IRakSessionCodec {

    @Shadow
    private FastBinaryMinHeap<EncapsulatedPacket> outgoingPackets;

    @Shadow
    private IntObjectMap<RakDatagramPacket> sentDatagrams;

    @Override
    public int viaVersion$getOutgoingPackets() {
        return this.outgoingPackets.size();
    }

    @Override
    public int viaVersion$SentDatagrams() {
        return this.sentDatagrams.size();
    }

}
