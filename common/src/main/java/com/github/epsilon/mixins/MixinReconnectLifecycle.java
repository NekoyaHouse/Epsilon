package com.github.epsilon.mixins;
import com.github.epsilon.features.DisconnectGuard;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class MixinReconnectLifecycle {
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void epsilon$networkBegin(CallbackInfo ci) { DisconnectGuard.networkBegin(); }
    @Inject(method = "onDisconnect", at = @At("RETURN"))
    private void epsilon$networkEnd(CallbackInfo ci) { DisconnectGuard.networkEnd(); }
}
