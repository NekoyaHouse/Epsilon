package me.sofurry.via.util.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;

public class DataCustomPayloadStreamCodec<B extends FriendlyByteBuf> implements StreamCodec<B, ClientboundCustomPayloadPacket> {

    private final StreamCodec<B, ClientboundCustomPayloadPacket> original;

    public DataCustomPayloadStreamCodec(final StreamCodec<B, ClientboundCustomPayloadPacket> original) {
        this.original = original;
    }

    @Override
    public void encode(final B buffer, final ClientboundCustomPayloadPacket packet) {
        this.original.encode(buffer, packet);
    }

    @Override
    public ClientboundCustomPayloadPacket decode(final B buffer) {
        buffer.markReaderIndex();
        final Identifier id = buffer.readIdentifier();
        if (id.equals(DataCustomPayload.ID.id())) {
            return new ClientboundCustomPayloadPacket(new DataCustomPayload(
                    new FriendlyByteBuf(Unpooled.copiedBuffer(buffer.readSlice(buffer.readableBytes())))
            ));
        }

        buffer.resetReaderIndex();
        return this.original.decode(buffer);
    }

}
