package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.EntityRenderStateAccessor;
import com.github.epsilon.modules.impl.render.Chams;
import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void submitChamsPlayer(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera, Operation<Void> original) {
        Chams chams = Chams.INSTANCE;
        boolean previous = chams.isSubmittingPlayer();
        chams.setSubmittingPlayer(chams.isEnabled() && state instanceof EntityRenderStateAccessor accessor && chams.isValidEntity(accessor.epsilon$getEntity()));
        try {
            original.call(renderer, state, poseStack, submitNodeCollector, camera);
        } finally {
            chams.setSubmittingPlayer(previous);
        }
    }

    @ModifyReturnValue(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> EntityRenderState onExtractEntity(EntityRenderState state, E entity, float partialTicks) {
        if (state instanceof EntityRenderStateAccessor entityRenderState) {
            entityRenderState.epsilon$setEntity(entity);
        }
        return state;
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void epsilon$hideEntity(Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.INSTANCE.noEntity(entity)) cir.setReturnValue(false);
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void epsilon$hideDeadEntity(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector collector, CallbackInfo ci) {
        if (NoRender.INSTANCE.noDeadEntities() && state instanceof EntityRenderStateAccessor accessor
                && accessor.epsilon$getEntity() instanceof LivingEntity entity && entity.isDeadOrDying()) ci.cancel();
    }

}
