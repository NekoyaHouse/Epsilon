package me.sofurry.via.injection.mixin;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerRedirectHandler;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.naming.directory.DirContext;
import java.util.Optional;

@Mixin(ServerRedirectHandler.class)
public interface MixinServerRedirectHandler {

    @Inject(method = "lambda$createDnsSrvRedirectHandler$0", at = @At("HEAD"), cancellable = true)
    private static void disableSrvForPre1_3(DirContext context, ServerAddress originalAddress, CallbackInfoReturnable<Optional<ServerAddress>> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThan(LegacyProtocolVersion.r1_3_1tor1_3_2)) {
            cir.setReturnValue(Optional.empty());
        }
    }

}
