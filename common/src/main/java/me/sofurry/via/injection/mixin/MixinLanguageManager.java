package me.sofurry.via.injection.mixin;

import me.sofurry.via.ViaVersion;
import me.sofurry.via.visuals.features.force_unicode_font.UnicodeFontFix1_12_2;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LanguageManager.class)
public abstract class MixinLanguageManager {

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void updateUnicodeFontOverride(ResourceManager resourceManager, CallbackInfo ci) {
        UnicodeFontFix1_12_2.updateUnicodeFontOverride(ViaVersion.getImpl().getTargetVersion());
    }

}
