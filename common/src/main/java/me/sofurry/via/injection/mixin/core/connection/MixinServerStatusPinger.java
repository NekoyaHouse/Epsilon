package me.sofurry.via.injection.mixin.core.connection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import me.sofurry.via.injection.access.core.ILocalSampleLogger;
import me.sofurry.via.injection.access.core.IServerData;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.net.InetSocketAddress;

@Mixin(ServerStatusPinger.class)
public abstract class MixinServerStatusPinger {

    @WrapOperation(method = "pingServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connectToServer(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;"))
    private Connection setForcedVersion(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, LocalSampleLogger localSampleLogger, Operation<Connection> original, @Local(argsOnly = true) ServerData data) {
        final IServerData mixinServerInfo = (IServerData) data;

        if (mixinServerInfo.viaVersion$forcedVersion() != null && !mixinServerInfo.viaVersion$passedDirectConnectScreen()) {
            // We use the PerformanceLog field to store the forced version since it's always null when pinging a server
            // So we can create a dummy instance, store the forced version in it and later destroy the instance again
            // To avoid any side effects, we also support cases where a mod is also creating a PerformanceLog instance
            if (localSampleLogger == null) {
                localSampleLogger = new LocalSampleLogger(1);
            }

            // Attach the forced version to the PerformanceLog instance
            ((ILocalSampleLogger) localSampleLogger).viaVersion$setForcedVersion(mixinServerInfo.viaVersion$forcedVersion());
            mixinServerInfo.viaVersion$passDirectConnectScreen(false);
        }

        return original.call(address, eventLoopGroupHolder, localSampleLogger);
    }

}
