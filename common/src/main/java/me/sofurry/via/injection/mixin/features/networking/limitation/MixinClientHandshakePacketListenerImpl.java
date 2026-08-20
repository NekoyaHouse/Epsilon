package me.sofurry.via.injection.mixin.features.networking.limitation;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class MixinClientHandshakePacketListenerImpl {

    @Redirect(method = "handleCompression", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;setupCompression(IZ)V"))
    private void pre1_17_1CompressionBehaviour(Connection instance, int threshold, boolean validateDecompressed) {
        instance.setupCompression(threshold, ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_17));
    }

}
