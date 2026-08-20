package me.sofurry.via.injection.mixin.core;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "close", at = @At("RETURN"))
    private void forceShutdown(CallbackInfo ci) {
        System.exit(0); // Workaround for GH-1218; Before 26.2 this was done by Mojang instead
    }

}
