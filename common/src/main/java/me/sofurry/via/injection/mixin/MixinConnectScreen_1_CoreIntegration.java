package me.sofurry.via.injection.mixin;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.ChannelFuture;
import me.sofurry.via.injection.access.core.IServerData;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.protocoltranslator.util.ProtocolVersionDetector;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.Optional;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public abstract class MixinConnectScreen_1_CoreIntegration {

    @Shadow
    @Final
    ServerData val$server;

    @Final
    @Shadow
    ServerAddress val$hostAndPort;

    @Shadow
    @Final
    ConnectScreen this$0;

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Ljava/util/Optional;get()Ljava/lang/Object;", remap = false))
    private Object setServerInfoAndProtocolVersion(Optional<InetSocketAddress> instance, Operation<Object> original) throws Exception {
        final InetSocketAddress address = (InetSocketAddress) original.call(instance);
        final IServerData mixinServerInfo = (IServerData) this.val$server;

        ProtocolVersion targetVersion = ProtocolTranslator.getTargetVersion();
        if (mixinServerInfo.viaVersion$forcedVersion() != null && !mixinServerInfo.viaVersion$passedDirectConnectScreen()) {
            targetVersion = mixinServerInfo.viaVersion$forcedVersion();
            mixinServerInfo.viaVersion$passDirectConnectScreen(false); // reset state
        }
        if (targetVersion == ProtocolTranslator.AUTO_DETECT_PROTOCOL) {
            // If the server got already pinged, try to use that version if it's valid. Otherwise, perform auto-detect
            final boolean serverPinged = this.val$server.state() == ServerData.State.SUCCESSFUL || this.val$server.state() == ServerData.State.INCOMPATIBLE;
            if (serverPinged) {
                targetVersion = ProtocolVersion.getProtocol(this.val$server.protocol);
            }
            if (!serverPinged || !targetVersion.isKnown()) {
                this.this$0.updateStatus(EpsilonTranslations.component(EpsilonTranslations.Via.BASE_DETECTING_SERVER_VERSION));
                try {
                    targetVersion = ProtocolVersionDetector.get(this.val$hostAndPort, address, ProtocolTranslator.NATIVE_VERSION);
                } catch (final ConnectException ignored) {
                    // Don't let this one through as not relevant
                }
            }
        }
        ProtocolTranslator.setTargetVersion(targetVersion, true);
        return address;
    }

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
    private ChannelFuture resetProtocolVersionAfterDisconnect(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, Connection connection, Operation<ChannelFuture> original) {
        final ChannelFuture future = original.call(address, eventLoopGroupHolder, connection);
        ProtocolTranslator.injectPreviousVersionReset(future.channel());
        return future;
    }

}
