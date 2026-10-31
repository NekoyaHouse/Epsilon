package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.ACSpoof;
import com.github.epsilon.modules.impl.render.Chams;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SubmitNodeCollection.class)
public abstract class MixinSubmitNodeCollection {

    @Shadow
    @Final
    public SimpleFeatureRenderPhase alwaysOnTopGizmos;

    @Shadow
    @Final
    public SimpleFeatureRenderPhase solid;

    @Shadow
    @Final
    public SimpleFeatureRenderPhase translucentCustomGeometry;

    @Shadow
    @Final
    public FeatureRenderPhase<? super TranslucentSubmit> translucentBlocksAndItems;

    @Shadow
    @Final
    public FeatureRenderPhase<? super TranslucentSubmit> translucentModels;

    @ModifyVariable(method = "submitNameTag", at = @At("HEAD"), argsOnly = true)
    private Component spoofNameTag(Component name) {
        return ACSpoof.INSTANCE.text(name, -1);
    }

    @WrapOperation(method = {"submitModel", "submitItem", "submitCustomGeometry"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V"))
    private void submitChamsGeometry(SimpleFeatureRenderPhase phase, SubmitNode submit, Operation<Void> original) {
        Chams chams = Chams.INSTANCE;
        if ((phase == this.solid || phase == this.translucentCustomGeometry) && (chams.isSubmittingPlayer() || submit instanceof ModelFeatureRenderer.Submit<?> model && chams.isChamsRenderType(model.renderType()))) {
            chams.markAlwaysOnTopSubmit();
            this.alwaysOnTopGizmos.submit(submit);
        } else {
            original.call(phase, submit);
        }
    }

    @WrapOperation(method = {"submitModel", "submitItem"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/phase/FeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V"))
    private void submitChamsTranslucentGeometry(FeatureRenderPhase<SubmitNode> phase, SubmitNode submit, Operation<Void> original) {
        Chams chams = Chams.INSTANCE;
        boolean translucentPhase = phase == this.translucentModels || phase == this.translucentBlocksAndItems;
        if (translucentPhase && (chams.isSubmittingPlayer() || submit instanceof ModelFeatureRenderer.Submit<?> model && chams.isChamsRenderType(model.renderType()))) {
            chams.markAlwaysOnTopSubmit();
            this.alwaysOnTopGizmos.submit(submit);
        } else {
            original.call(phase, submit);
        }
    }

}
