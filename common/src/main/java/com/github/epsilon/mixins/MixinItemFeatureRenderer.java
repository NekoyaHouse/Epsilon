package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemFeatureRenderer.class)
public abstract class MixinItemFeatureRenderer {

    @ModifyVariable(method = "renderItem", at = @At("STORE"), name = "foilType")
    private ItemStackRenderState.FoilType modifyEnchant(ItemStackRenderState.FoilType foilType) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.enchantGlint.getValue()) {
            return ItemStackRenderState.FoilType.NONE;
        }
        return foilType;
    }

}
