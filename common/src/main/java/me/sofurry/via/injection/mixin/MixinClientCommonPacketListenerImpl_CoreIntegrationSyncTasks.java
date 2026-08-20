package me.sofurry.via.injection.mixin;

import me.sofurry.via.util.network.DataCustomPayload;
import me.sofurry.via.util.network.SyncTasks;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientCommonPacketListenerImpl.class, priority = 1 /* Has to be applied before Fabric's Networking API, so it doesn't cancel our custom-payload packets */)
public abstract class MixinClientCommonPacketListenerImpl_CoreIntegrationSyncTasks {

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At("HEAD"), cancellable = true)
    private void handleSyncTask(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.payload() instanceof DataCustomPayload(FriendlyByteBuf buf)) {
            SyncTasks.handleSyncTask(buf);
            ci.cancel(); // Cancel the packet, so it doesn't get processed by the client
        }
    }

}
