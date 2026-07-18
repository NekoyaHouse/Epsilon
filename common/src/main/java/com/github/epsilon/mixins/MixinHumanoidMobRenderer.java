package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.github.epsilon.Constants.mc;

@Mixin(HumanoidMobRenderer.class)
public class MixinHumanoidMobRenderer {

    @ModifyExpressionValue(method = "extractHumanoidRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z"))
    private static boolean spoofFallFlyingVisualState(boolean original, LivingEntity entity, HumanoidRenderState reusedState, float partialTick, ItemModelResolver itemModelResolver) {
        if (ElytraFly.INSTANCE.isArmorMode() && entity == mc.player) {
            return false;
        }
        return original;
    }

}