package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.EntityRenderStateAccessor;
import com.github.epsilon.modules.impl.render.Chams;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.github.epsilon.Constants.mc;

@Mixin(CapeLayer.class)
public class MixinCapeLayer {

    @WrapOperation(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;entitySolid(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType redirectCapeRenderType(Identifier texture, Operation<RenderType> original, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        Chams chamsModule = Chams.INSTANCE;
        if (chamsModule.isEnabled() && chamsModule.noDepth.getValue() && ((EntityRenderStateAccessor) state).epsilon$getEntity() instanceof Player player && player != mc.player) {
            return chamsModule.getRenderType(texture);
        }
        return original.call(texture);
    }

}
