package me.sofurry.via.injection.mixin.features.movement.liquid;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityFluidInteraction.Tracker.class)
public abstract class MixinEntityFluidInteraction_Tracker {

    @Redirect(method = "applyCurrentTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D"))
    private double useLengthInstead(Vec3 instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            return instance.length();
        } else {
            return instance.lengthSqr();
        }
    }

    @ModifyConstant(method = "applyCurrentTo", constant = @Constant(doubleValue = (double) 1.0E-5F))
    private double changeThreshold(double constant) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            return 0;
        } else {
            return constant;
        }
    }

    @Redirect(method = "applyCurrentTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;", ordinal = 0))
    private Vec3 normalizeInsteadScale(Vec3 instance, double scale) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return instance.normalize();
        } else {
            return instance.scale(scale);
        }
    }

    @Redirect(method = "applyCurrentTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;length()D"))
    private double dontScaleSmallValues(Vec3 instance) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return Double.MAX_VALUE;
        } else {
            return instance.length();
        }
    }

}
