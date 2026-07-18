package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Shaders;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinBaseEntityRenderer<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("RETURN"))
    private void onExtractRenderStateReturn(T entity, S state, float partialTicks, CallbackInfo ci) {
        Shaders shaders = Shaders.INSTANCE;
        if (shaders.isEnabled() && shaders.shouldRender(entity)) {
            state.outlineColor = shaders.outlineColor.getValue().getRGB();
        }
    }

}
