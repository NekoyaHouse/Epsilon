package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public abstract class MixinFogRenderer {

    @ModifyExpressionValue(method = "getBuffer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;fogEnabled:Z", opcode = Opcodes.GETSTATIC))
    private boolean modifyFogEnabled(boolean original) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.fog.getValue()) return false;
        return original;
    }

}
