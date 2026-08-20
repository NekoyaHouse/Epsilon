/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaFabricPlus
 * Copyright (C) 2021-2026 the original authors
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package me.sofurry.via.injection.mixin.core.integration.sync_tasks;

import io.netty.buffer.Unpooled;
import me.sofurry.via.util.network.DataCustomPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientboundCustomPayloadPacket.class)
public abstract class MixinClientboundCustomPayloadPacket {

    @Shadow
    @Final
    @Mutable
    private static StreamCodec<RegistryFriendlyByteBuf, ClientboundCustomPayloadPacket> GAMEPLAY_STREAM_CODEC;

    @Shadow
    @Final
    @Mutable
    private static StreamCodec<FriendlyByteBuf, ClientboundCustomPayloadPacket> CONFIG_STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void viaVersion$registerSyncPayloadCodecs(final CallbackInfo ci) {
        GAMEPLAY_STREAM_CODEC = wrap(GAMEPLAY_STREAM_CODEC);
        CONFIG_STREAM_CODEC = wrap(CONFIG_STREAM_CODEC);
    }

    private static <B extends FriendlyByteBuf> StreamCodec<B, ClientboundCustomPayloadPacket> wrap(
            final StreamCodec<B, ClientboundCustomPayloadPacket> original
    ) {
        return new StreamCodec<>() {
            @Override
            public void encode(final B buffer, final ClientboundCustomPayloadPacket packet) {
                original.encode(buffer, packet);
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
                return original.decode(buffer);
            }
        };
    }

}
