package me.sofurry.via.injection.mixin;

import dev.kastle.netty.channel.nethernet.config.NetherNetAddress;
import me.sofurry.via.injection.access.core.bedrock.IServerAddress;
import me.sofurry.via.util.bedrock.NetherNetResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ServerNameResolver.class)
public abstract class MixinServerNameResolver_CoreConnectionBedrock {

    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
    private void returnNetherNetAddressEarly(ServerAddress address, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        if ((Object) address instanceof IServerAddress mixinServerAddress && mixinServerAddress.viaVersion$getNetherNetAddress() != null) {
            final NetherNetAddress netherNetAddress = mixinServerAddress.viaVersion$getNetherNetAddress();
            cir.setReturnValue(Optional.of(new NetherNetResolvedServerAddress(netherNetAddress)));
        }
    }

}
