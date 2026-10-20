package com.github.epsilon.mixins;
import com.github.epsilon.features.DisconnectGuard;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class MixinPauseScreenLogout {
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void epsilon$intentionalQuit(CallbackInfo ci) { DisconnectGuard.suppress(); }
}
