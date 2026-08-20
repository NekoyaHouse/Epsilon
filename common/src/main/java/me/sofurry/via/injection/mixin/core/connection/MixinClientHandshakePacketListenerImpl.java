package me.sofurry.via.injection.mixin.core.connection;

import me.sofurry.via.injection.access.core.IConnection;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.release.r1_6_4tor1_7_2_5.storage.ProtocolMetadataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("DataFlowIssue")
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class MixinClientHandshakePacketListenerImpl {

    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "authenticateServer", at = @At("HEAD"), cancellable = true)
    private void onlyVerifySessionInOnlineMode(String digest, CallbackInfoReturnable<Component> cir) {
        final IConnection mixinClientConnection = (IConnection) connection;
        if (mixinClientConnection.viaVersion$getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_6_4)) {
            // We are in the 1.7 -> 1.6 protocol, so we need to skip the joinServer call
            // if the server is in offline mode, due to the packet changes <-> networking changes
            // Minecraft's networking code is bad for us.
            if (!mixinClientConnection.viaVersion$getUserConnection().get(ProtocolMetadataStorage.class).authenticate) {
                cir.setReturnValue(null);
            }
        }
    }

}
