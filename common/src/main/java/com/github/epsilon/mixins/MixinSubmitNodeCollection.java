package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.Chams;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SubmitNodeCollection.class)
public abstract class MixinSubmitNodeCollection {

    @Shadow
    @Final
    public SimpleFeatureRenderPhase alwaysOnTop;

    @Shadow
    @Final
    public SimpleFeatureRenderPhase solid;

    @Shadow
    @Final
    public SimpleFeatureRenderPhase translucentCustomGeometry;

    @WrapOperation(method = {"submitModel", "submitItem", "submitCustomGeometry"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V"))
    private void submitChamsGeometry(SimpleFeatureRenderPhase phase, SubmitNode submit, Operation<Void> original) {
        Chams chams = Chams.INSTANCE;
        if ((phase == this.solid || phase == this.translucentCustomGeometry) && (chams.isSubmittingPlayer() || submit instanceof ModelFeatureRenderer.Submit<?> model && chams.isChamsRenderType(model.renderType()))) {
            this.alwaysOnTop.submit(submit);
        } else {
            original.call(phase, submit);
        }
    }

    @WrapOperation(method = {"submitModel", "submitItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/TranslucentFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/TranslucentSubmit;)V"))
    private void submitChamsTranslucentGeometry(TranslucentFeatureRenderPhase phase, TranslucentSubmit submit, Operation<Void> original) {
        Chams chams = Chams.INSTANCE;
        if (chams.isSubmittingPlayer() || submit instanceof ModelFeatureRenderer.Submit<?> model && chams.isChamsRenderType(model.renderType())) {
            this.alwaysOnTop.submit(submit);
        } else {
            original.call(phase, submit);
        }
    }

}
