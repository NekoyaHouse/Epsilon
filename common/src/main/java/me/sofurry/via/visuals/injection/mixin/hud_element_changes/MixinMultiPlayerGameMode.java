package me.sofurry.via.visuals.injection.mixin.hud_element_changes;

import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Inject(method = "hasExperience", at = @At("HEAD"), cancellable = true)
    private void removeExperienceBar(CallbackInfoReturnable<Boolean> cir) {
        if (ClientSetting.INSTANCE.hideModernHUDElements.isEnabled()) {
            cir.setReturnValue(false);
        }
    }

}
