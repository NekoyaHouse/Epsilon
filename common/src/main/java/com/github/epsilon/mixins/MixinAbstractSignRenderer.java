package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractSignRenderer.class)
public class MixinAbstractSignRenderer {

    @ModifyExpressionValue(method = "submitSignText", at = @At(value = "CONSTANT", args = {"intValue=4", "ordinal=1"}))
    private int loopTextLengthProxy(int i) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.signText.getValue()) return 0;
        return i;
    }

}
