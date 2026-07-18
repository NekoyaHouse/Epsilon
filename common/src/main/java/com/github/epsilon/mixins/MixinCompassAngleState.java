package com.github.epsilon.mixins;

import com.github.epsilon.modules.impl.render.FreeCamera;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.github.epsilon.Constants.mc;

@Mixin(CompassAngleState.class)
public class MixinCompassAngleState {

    @ModifyExpressionValue(method = "getWrappedVisualRotationY", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ItemOwner;getVisualRotationYInDegrees()F"))
    private static float hookGetWrappedVisualRotationY(float original) {
        if (FreeCamera.INSTANCE.isEnabled()) {
            return mc.gameRenderer.getMainCamera().yRot();
        }
        return original;
    }

    @ModifyReturnValue(method = "getAngleFromEntityToPos(Lnet/minecraft/world/entity/ItemOwner;Lnet/minecraft/core/BlockPos;)D", at = @At("RETURN"))
    private static double modifyGetAngleTo(double original, ItemOwner owner, BlockPos position) {
        if (FreeCamera.INSTANCE.isEnabled()) {
            Vec3 vec3d = Vec3.atCenterOf(position);
            Camera camera = mc.gameRenderer.getMainCamera();
            return Math.atan2(vec3d.z() - camera.position().z, vec3d.x() - camera.position().x) / (float) (Math.PI * 2);
        }
        return original;
    }

}
