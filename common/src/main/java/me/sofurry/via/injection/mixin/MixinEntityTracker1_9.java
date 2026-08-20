package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.protocols.v1_8to1_9.storage.EntityTracker1_9;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = EntityTracker1_9.class, remap = false)
public abstract class MixinEntityTracker1_9 {

    @Redirect(method = "handleEntityData", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F"), slice = @Slice(from = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/configuration/ViaVersionConfig;isBossbarAntiflicker()Z")))
    private float removeMin(float a, float b) {
        return a;
    }

    @Redirect(method = "handleEntityData", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"), slice = @Slice(from = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/configuration/ViaVersionConfig;isBossbarAntiflicker()Z")))
    private float removeMax(float a, float b) {
        return b;
    }

    @Redirect(method = "handleEntityData", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/minecraft/entitydata/EntityData;getValue()Ljava/lang/Object;"), slice = @Slice(from = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/configuration/ViaVersionConfig;isBossbarAntiflicker()Z")))
    private Object remapNaNToZero(EntityData instance) {
        if (instance.getValue() instanceof Float && ((Float) instance.getValue()).isNaN()) {
            return 0F;
        } else {
            return instance.getValue();
        }
    }

}
