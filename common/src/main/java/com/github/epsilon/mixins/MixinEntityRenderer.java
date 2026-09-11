package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.CrystalChams;
import com.github.epsilon.modules.impl.render.NameTags;
import com.github.epsilon.modules.impl.render.NoRender;
import com.github.epsilon.modules.impl.render.Shaders;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("RETURN"))
    private void onExtractRenderStateReturn(T entity, S state, float partialTicks, CallbackInfo ci) {
        Shaders shaders = Shaders.INSTANCE;
        if (shaders.isEnabled() && shaders.shouldRender(entity)) {
            state.outlineColor = shaders.getOutlineColor(entity, shaders.entityShader);
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void hookShouldRender(T entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (CrystalChams.INSTANCE.isEnabled() && entity instanceof EndCrystal) {
            cir.setReturnValue(false);
            return;
        }

        NoRender noRender = NoRender.INSTANCE;
        if (noRender.isEnabled() && (noRender.noEntity(entity.getType()) || (noRender.fallingBlocks.getValue() && entity instanceof FallingBlockEntity))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V", at = @At("HEAD"), cancellable = true)
    private void hookExtractNameTags(T entity, S state, float partialTicks, double nameTagDistance, double belowNameDistance, CallbackInfo ci) {
        boolean hideGlobalNametags = NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.noNametags.getValue();
        boolean hidePlayerNametags = NameTags.INSTANCE.isEnabled() && entity instanceof Player;
        if (hideGlobalNametags || hidePlayerNametags) {
            ci.cancel();
        }
    }

    @Inject(method = "finalizeRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void onFinalizeRenderState(T entity, S state, CallbackInfo ci) {
        if (
                NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.noDeadEntities.getValue()
                        && state instanceof LivingEntityRenderState livingState
                        && livingState.deathTime > 0
        ) {
            ci.cancel();
        }
    }

}
