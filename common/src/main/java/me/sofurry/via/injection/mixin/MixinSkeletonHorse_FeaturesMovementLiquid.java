package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkeletonHorse.class)
public abstract class MixinSkeletonHorse_FeaturesMovementLiquid extends AbstractHorse {

    protected MixinSkeletonHorse_FeaturesMovementLiquid(EntityType<? extends AbstractHorse> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "getWaterSlowDown", at = @At("HEAD"), cancellable = true)
    private void modifyBaseWaterMovementSpeedMultiplier(CallbackInfoReturnable<Float> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(super.getWaterSlowDown());
        }
    }

}
