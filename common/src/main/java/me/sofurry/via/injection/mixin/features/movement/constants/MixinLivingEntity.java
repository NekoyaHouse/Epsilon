package me.sofurry.via.injection.mixin.features.movement.constants;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = LivingEntity.class, priority = 999 /* Workaround for https://github.com/ViaVersion/ViaFabricPlus/issues/684 */)
public abstract class MixinLivingEntity {

    @ModifyExpressionValue(method = "tickEffects", at = @At(value = "CONSTANT", args = "intValue=4"))
    private int changeParticleDensity(int original) {
        if (ProtocolTranslator.getTargetVersion().olderThan(ProtocolVersion.v1_20_5)) {
            return 2;
        } else {
            return original;
        }
    }

    @ModifyConstant(method = "aiStep", constant = @Constant(doubleValue = 0.003D))
    private double modifyVelocityZero(double constant) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return 0.005D;
        } else {
            return constant;
        }
    }

}
