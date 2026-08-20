package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.protocols.v1_19_1to1_19_3.packet.ClientboundPackets1_19_3;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.Protocol1_19_3To1_19_4;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.packet.ClientboundPackets1_19_4;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.rewriter.EntityPacketRewriter1_19_4;
import com.viaversion.viaversion.rewriter.EntityRewriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityPacketRewriter1_19_4.class, remap = false)
public abstract class MixinEntityPacketRewriter1_19_4 extends EntityRewriter<ClientboundPackets1_19_3, Protocol1_19_3To1_19_4> {

    protected MixinEntityPacketRewriter1_19_4(Protocol1_19_3To1_19_4 protocol) {
        super(protocol);
    }

    @Inject(method = "registerPackets", at = @At("RETURN"))
    private void fixTeleportBehaviour(CallbackInfo ci) {
        this.protocol.registerClientbound(ClientboundPackets1_19_3.TELEPORT_ENTITY, ClientboundPackets1_19_4.TELEPORT_ENTITY, wrapper -> {
        }, true);
    }

}
