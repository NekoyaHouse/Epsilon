package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(MapRenderer.class)
public abstract class MixinMapRenderer {

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/MapRenderState;decorations:Ljava/util/List;", opcode = Opcodes.GETFIELD))
    private List<MapDecoration> getDecorationsProxy(List<MapDecoration> original) {
        return (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mapMarkers.getValue()) ? List.of() : original;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(MapRenderState mapRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, boolean showOnlyFrame, int lightCoords, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.mapContents.getValue()) ci.cancel();
    }

}
