package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.EntityRenderStateAccessor;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @ModifyReturnValue(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> EntityRenderState onExtractEntity(EntityRenderState state, E entity, float partialTicks) {
        if (state instanceof EntityRenderStateAccessor entityRenderState) {
            entityRenderState.epsilon$setEntity(entity);
        }
        return state;
    }

}
