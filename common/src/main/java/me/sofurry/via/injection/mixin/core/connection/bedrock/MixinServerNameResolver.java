package me.sofurry.via.injection.mixin.core.connection.bedrock;

import dev.kastle.netty.channel.nethernet.config.NetherNetAddress;
import me.sofurry.via.injection.access.core.bedrock.IServerAddress;
import me.sofurry.via.util.bedrock.NetherNetInetSocketAddress;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.util.Optional;

@Mixin(ServerNameResolver.class)
public abstract class MixinServerNameResolver {

    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
    private void returnNetherNetAddressEarly(ServerAddress address, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        if ((Object) address instanceof IServerAddress mixinServerAddress && mixinServerAddress.viaVersion$getNetherNetAddress() != null) {
            final NetherNetAddress netherNetAddress = mixinServerAddress.viaVersion$getNetherNetAddress();
            cir.setReturnValue(Optional.of(new ResolvedServerAddress() {
                @Override
                public @NonNull String getHostName() {
                    return netherNetAddress.getNetworkId();
                }

                @Override
                public @NonNull String getHostIp() {
                    return netherNetAddress.getNetworkId();
                }

                @Override
                public int getPort() {
                    return 0;
                }

                @Override
                public @NonNull InetSocketAddress asInetSocketAddress() {
                    return new NetherNetInetSocketAddress(netherNetAddress);
                }
            }));
        }
    }

}
