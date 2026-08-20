package me.sofurry.via.injection.mixin.features.classic.cpe_extension;

import me.sofurry.via.features.classic.cpe_extension.CPEAdditions;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.packet.ClientboundPacketsc0_30cpe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientboundPacketsc0_30cpe.class, remap = false)
public abstract class MixinClientboundPacketsc0_30cpe {

    @Inject(method = "getPacket", at = @At("HEAD"), cancellable = true)
    private static void addCustomPackets(int id, CallbackInfoReturnable<ClientboundPacketsc0_30cpe> cir) {
        if (CPEAdditions.CUSTOM_PACKETS.containsKey(id)) {
            cir.setReturnValue(CPEAdditions.CUSTOM_PACKETS.get(id));
        }
    }

}
