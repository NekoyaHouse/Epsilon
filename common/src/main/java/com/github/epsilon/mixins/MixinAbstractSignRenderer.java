package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignRenderer.class)
public abstract class MixinAbstractSignRenderer {

    @Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true)
    private void epsilon$hideSignText(CallbackInfo ci) {
        if (NoRender.INSTANCE.noSignText()) ci.cancel();
    }

}
