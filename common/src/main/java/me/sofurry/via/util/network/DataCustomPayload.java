package me.sofurry.via.util.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DataCustomPayload(FriendlyByteBuf buf) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DataCustomPayload> ID = new CustomPacketPayload.Type<>(Identifier.parse(SyncTasks.PACKET_SYNC_IDENTIFIER));

    public static void init() {
        // The common custom-payload codecs are wrapped by the packet mixin so the
        // same implementation works with Fabric and NeoForge codec registries.
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

}
