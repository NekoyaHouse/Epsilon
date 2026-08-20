package me.sofurry.via.injection.mixin.core.integration;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.core.IConnection;
import me.sofurry.via.injection.access.core.IServerData;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.multiplayer.ServerStatusPinger$1")
public abstract class MixinServerStatusPinger_1 implements ClientStatusPacketListener {

    @Final
    @Shadow
    ServerData val$data;

    @Shadow
    @Final
    Connection val$connection;

    @Inject(method = "handleStatusResponse", at = @At("HEAD"))
    private void trackTranslatingState(ClientboundStatusResponsePacket packet, CallbackInfo ci) {
        // If ViaVersion is translating the current connection, we track the target version, and it's state in the server info
        // So we can later draw this information when hovering over the ping bar in the server list
        if (val$connection instanceof IConnection mixinClientConnection) {
            ((IServerData) val$data).viaVersion$setTranslatingVersion(mixinClientConnection.viaVersion$getTargetVersion());
        }
    }

    @Inject(method = "handleStatusResponse", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V", shift = At.Shift.AFTER))
    private void fixVersionComparison(CallbackInfo ci) {
        final ProtocolVersion version = ((IConnection) this.val$connection).viaVersion$getTargetVersion();

        // If the server is compatible with the client, we set the protocol version to the client version
        if (version != null && version.getVersion() == this.val$data.protocol) {
            this.val$data.protocol = SharedConstants.getProtocolVersion();
        }
    }

}
