package me.sofurry.via.injection.mixin;

import com.github.epsilon.modules.impl.ClientSetting;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.BooleanSupplier;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class MixinClientCommonPacketListenerImpl_FeaturesNetworkingPacketHandling {

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    protected abstract void sendWhen(Packet<? extends ServerboundPacketListener> packet, BooleanSupplier sendCondition, Duration expiry);

    @Shadow
    public abstract void send(Packet<?> packet);

    @Shadow
    @Final
    protected Connection connection;

    @Shadow
    private static URL parseResourcePackUrl(String urlString) {
        return null;
    }

    @Inject(method = "storeDisconnectionReport", at = @At("HEAD"), cancellable = true)
    private void dontCreatePacketErrorCrashReports(CallbackInfoReturnable<Optional<Path>> cir) {
        if (ClientSetting.INSTANCE.dontCreatePacketErrorCrashReports.isEnabled()) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @WrapWithCondition(method = "onPacketError", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;disconnect(Lnet/minecraft/network/DisconnectionDetails;)V"))
    private boolean dontDisconnectOnPacketException(Connection instance, DisconnectionDetails details) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_20_3);
    }

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void validateUrlInNetworkThread(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            if (parseResourcePackUrl(packet.url()) == null) {
                this.connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.INVALID_URL));
                ci.cancel();
            }
        }
    }

    @Redirect(method = "handleKeepAlive", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientCommonPacketListenerImpl;sendWhen(Lnet/minecraft/network/protocol/Packet;Ljava/util/function/BooleanSupplier;Ljava/time/Duration;)V"))
    private void forceSendKeepAlive(ClientCommonPacketListenerImpl instance, Packet<? extends ServerboundPacketListener> packet, BooleanSupplier condition, Duration expireAfterDuration) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_3)) {
            send(packet);
        } else {
            sendWhen(packet, condition, expireAfterDuration);
        }
    }

    @Inject(method = "handlePing", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER), cancellable = true)
    private void addMissingConditions(ClientboundPingPacket packet, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_4)) {
            final short inventoryId = (short) ((packet.getId() >> 16) & 0xFF);
            if (inventoryId != 0 && inventoryId != minecraft.player.containerMenu.containerId) {
                ci.cancel();
            }
        }
    }

}
