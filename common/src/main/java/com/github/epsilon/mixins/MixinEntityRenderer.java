package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.CrystalChams;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer<T extends Entity> {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void hookShouldRender(T entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (CrystalChams.INSTANCE.isEnabled() && entity instanceof EndCrystal) {
            cir.setReturnValue(false);
        }
    }

}
