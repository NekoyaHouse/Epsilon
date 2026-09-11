package com.github.epsilon.fabric.mixins.iris;

import com.github.epsilon.managers.ShaderManager;
import com.github.epsilon.modules.impl.render.Shaders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class MixinIrisHandRenderer {

    @WrapOperation(method = {"renderSolid", "renderTranslucent"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"), remap = true)
    private void epsilon$renderFeaturesWithOutline(FeatureRenderDispatcher dispatcher, SubmitNodeStorage submitNodeStorage, Operation<Void> original) {
        if (Shaders.INSTANCE.isEnabled() && Shaders.INSTANCE.hands.getValue()) {
            try (FeatureRenderDispatcher.PreparedFrame frame = dispatcher.prepareFrame(submitNodeStorage)) {
                frame.executeSolid();
                frame.executeTranslucent();
                frame.executeOutline();
                frame.executeTranslucentAfterTerrain();
                frame.executeAlwaysOnTop();
            } finally {
                ShaderManager.INSTANCE.endHandOutlineCapture();
            }
        } else {
            original.call(dispatcher, submitNodeStorage);
        }
    }

}
