package me.sofurry.via.injection.mixin.features.movement.packet;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.rewriter.EntityPacketRewriter1_21_2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityPacketRewriter1_21_2.class, remap = false)
public abstract class MixinEntityPacketRewriter1_21_2 {

    @Redirect(method = "lambda$registerPackets$14", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;cancel()V"))
    private void dontCancelIdlePacket(PacketWrapper instance) {
    }

}
