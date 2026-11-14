package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(MapRenderer.class)
public abstract class MixinMapRenderer {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void epsilon$hideMap(CallbackInfo ci) {
        if (NoRender.INSTANCE.noMapContents()) ci.cancel();
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/MapRenderState;decorations:Ljava/util/List;"))
    private List<MapRenderState.MapDecorationRenderState> epsilon$hideMapMarkers(List<MapRenderState.MapDecorationRenderState> original) {
        return NoRender.INSTANCE.noMapMarkers() ? List.of() : original;
    }

}
