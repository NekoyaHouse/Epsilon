package me.sofurry.via.injection.mixin.features.networking.config_state;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener extends ClientCommonPacketListenerImpl {

    protected MixinClientPacketListener(Minecraft client, Connection connection, CommonListenerCookie connectionState) {
        super(client, connection, connectionState);
    }

    @Inject(method = "handleConfigurationStart", at = @At("HEAD"))
    private void disableAutoRead(ClientboundStartConfigurationPacket packet, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_3)) {
            this.connection.channel.config().setAutoRead(false);
        }
    }

    @Inject(method = "handleConfigurationStart", at = @At("RETURN"))
    private void enableAutoRead(ClientboundStartConfigurationPacket packet, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_3)) {
            this.connection.channel.config().setAutoRead(true);
        }
    }

}
