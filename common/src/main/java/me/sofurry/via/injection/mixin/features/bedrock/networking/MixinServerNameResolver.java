package me.sofurry.via.injection.mixin.features.bedrock.networking;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddressResolver;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ServerNameResolver.class)
public abstract class MixinServerNameResolver {

    @Shadow
    @Final
    private ServerAddressResolver resolver;

    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
    private void oldResolveBehaviour(ServerAddress address, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            cir.setReturnValue(this.resolver.resolve(address));
        }
    }

}
