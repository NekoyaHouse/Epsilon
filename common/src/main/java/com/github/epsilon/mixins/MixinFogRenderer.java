package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.WorldTweaks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.awt.*;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
    private FogData modifyFog(FogData fog) {
        WorldTweaks worldTweaks = WorldTweaks.INSTANCE;
        if (worldTweaks.isEnabled() && worldTweaks.getFogModify()) {
            fog.environmentalStart = worldTweaks.getFogStart();
            fog.environmentalEnd = worldTweaks.getFogEnd();
            Color color = worldTweaks.getFogColor();
            fog.color.set(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, 1.0F);
            return fog;
        }
        return fog;
    }

}
