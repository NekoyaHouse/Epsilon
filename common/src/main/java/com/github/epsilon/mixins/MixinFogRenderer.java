package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.github.epsilon.modules.impl.render.WorldTweaks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.awt.*;

@Mixin(FogRenderer.class)
public abstract class MixinFogRenderer {

    @ModifyExpressionValue(method = "getBuffer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;fogEnabled:Z", opcode = Opcodes.GETSTATIC))
    private boolean modifyFogEnabled(boolean original) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.fog.getValue()) return false;
        return original;
    }

    @ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
    private FogData modifyFog(FogData fog) {
        WorldTweaks worldTweaks = WorldTweaks.INSTANCE;
        if (worldTweaks.isEnabled() && worldTweaks.fogModify.getValue()) {
            fog.environmentalStart = worldTweaks.fogStart.getValue();
            fog.environmentalEnd = worldTweaks.fogEnd.getValue();
            Color color = worldTweaks.fogColor.getValue();
            fog.color.set(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, 1.0F);
            return fog;
        }
        return fog;
    }

}
