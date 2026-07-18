package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.FreeCamera;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkBorderRenderer.class)
public class MixinChunkBorderRenderer {

    @Final
    @Shadow
    private Minecraft minecraft;

    @ModifyExpressionValue(method = "emitGizmos", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/SectionPos;"))
    private SectionPos emitGizmos$getChunkPos(SectionPos original) {
        FreeCamera freeCamera = FreeCamera.INSTANCE;
        if (freeCamera.isEnabled()) {
            float tickDelta = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            return SectionPos.of(
                    SectionPos.posToSectionCoord(Mth.floor(freeCamera.getX(tickDelta))),
                    SectionPos.posToSectionCoord(Mth.floor(freeCamera.getY(tickDelta))),
                    SectionPos.posToSectionCoord(Mth.floor(freeCamera.getZ(tickDelta)))
            );
        }
        return original;
    }

}
