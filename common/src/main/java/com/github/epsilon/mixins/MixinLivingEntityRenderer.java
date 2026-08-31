package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.RotationAnimationEvent;
import com.github.epsilon.interfaces.EntityRenderStateAccessor;
import com.github.epsilon.modules.impl.render.Chams;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import static com.github.epsilon.Constants.mc;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity, S extends LivingEntityRenderState> {

    @WrapMethod(method = "submit")
    private void wrapChamsPlayerSubmit(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, Operation<Void> original) {
        Chams chams = Chams.INSTANCE;
        boolean renderThroughWalls = ((EntityRenderStateAccessor) state).epsilon$getEntity() instanceof Player player && chams.shouldRenderPlayer(player);
        if (renderThroughWalls) chams.beginPlayerSubmit();
        try {
            original.call(state, poseStack, submitNodeCollector, camera);
        } finally {
            if (renderThroughWalls) chams.endPlayerSubmit();
        }
    }

    @ModifyExpressionValue(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getCameraEntity()Lnet/minecraft/world/entity/Entity;"))
    private Entity hookShouldShowName(Entity cameraEntity) {
        return FreeCamera.INSTANCE.isEnabled() ? null : cameraEntity;
    }

    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;solveBodyRot(Lnet/minecraft/world/entity/LivingEntity;FF)F"))
    private float modifyBodyYaw(float original, LivingEntity entity, S state, float partialTicks) {
        if (entity == mc.player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(entity.yBodyRot, entity.yBodyRotO, 0.0f, 0.0f));
            return Mth.rotLerp(partialTicks, event.getLastYaw(), event.getYaw());
        }
        return original;
    }

    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F"))
    private float modifyHeadYaw(float original, LivingEntity entity, S state, float partialTicks) {
        if (entity == mc.player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(entity.yHeadRot, entity.yHeadRotO, 0.0f, 0.0f));
            return Mth.rotLerp(partialTicks, event.getLastYaw(), event.getYaw());
        }
        return original;
    }

    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F"))
    private float modifyPitch(float original, LivingEntity entity, S state, float partialTicks) {
        if (entity == mc.player) {
            RotationAnimationEvent event = EventBus.INSTANCE.post(new RotationAnimationEvent(0.0f, 0.0f, entity.getXRot(), entity.getXRot(0.0f)));
            return Mth.rotLerp(partialTicks, event.getLastPitch(), event.getPitch());
        }
        return original;
    }

}
