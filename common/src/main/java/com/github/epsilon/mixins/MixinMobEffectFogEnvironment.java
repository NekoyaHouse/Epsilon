package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MobEffectFogEnvironment.class)
public class MixinMobEffectFogEnvironment {

    @ModifyReturnValue(method = "isApplicable", at = @At("RETURN"))
    private boolean onIsApplicable(boolean original, FogType fogType, Entity entity) {
        if (!original || !NoRender.INSTANCE.isEnabled() || !NoRender.INSTANCE.negativeEffects.getValue()) {
            return original;
        }
        if (entity instanceof LivingEntity livingEntity && (livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS))) {
            return false;
        }
        return original;
    }

}
