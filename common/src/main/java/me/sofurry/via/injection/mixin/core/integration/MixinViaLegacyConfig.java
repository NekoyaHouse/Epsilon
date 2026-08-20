package me.sofurry.via.injection.mixin.core.integration;

import com.github.epsilon.modules.impl.ClientSetting;
import net.raphimc.vialegacy.ViaLegacyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ViaLegacyConfig.class, remap = false)
public abstract class MixinViaLegacyConfig {

    @Inject(method = {"isLegacySkullLoading", "isLegacySkinLoading"}, at = @At("HEAD"), cancellable = true)
    private void replaceWithViaVersionSetting(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ClientSetting.INSTANCE.loadSkinsAndSkullsInLegacyVersions.getValue());
    }

}
