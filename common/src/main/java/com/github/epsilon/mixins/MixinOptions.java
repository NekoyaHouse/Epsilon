package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.FreeCamera;
import com.mojang.serialization.Codec;
import net.minecraft.client.CameraType;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class MixinOptions {

    @Mutable
    @Shadow
    @Final
    private OptionInstance<Integer> fov;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;load()V"))
    private void replaceFovOption(Options options) {
        this.fov = new OptionInstance<>("options.fov", OptionInstance.noTooltip(), (caption, value) -> switch (value) {
            case 70 -> Options.genericValueLabel(caption, Component.translatable("options.fov.min"));
            case 150 -> Options.genericValueLabel(caption, Component.translatable("options.fov.max"));
            default -> Options.genericValueLabel(caption, value);
        }, new OptionInstance.IntRange(30, 150), Codec.DOUBLE.xmap(value -> (int) (value * (double) 40.0F + (double) 70.0F), value -> ((double) value - (double) 70.0F) / (double) 40.0F), 110, OptionInstance.NO_ACTION);
        options.load();
    }

    @Inject(method = "setCameraType", at = @At("HEAD"), cancellable = true)
    private void setPerspective(CameraType cameraType, CallbackInfo ci) {
        if (FreeCamera.INSTANCE.isEnabled()) {
            ci.cancel();
        }
    }

}
