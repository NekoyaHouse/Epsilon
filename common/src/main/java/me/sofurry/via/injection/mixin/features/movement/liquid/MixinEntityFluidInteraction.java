package me.sofurry.via.injection.mixin.features.movement.liquid;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityFluidInteraction.class)
public abstract class MixinEntityFluidInteraction {

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeY()D"))
    private double subtractMagicOffset(Entity instance) {
        if (ProtocolTranslator.getTargetVersion().betweenInclusive(ProtocolVersion.v1_16, ProtocolVersion.v1_20_3)) {
            return instance.getEyeY() - 0.11111111F;
        } else {
            return instance.getEyeY();
        }
    }

    @Definition(id = "eyeY", local = @Local(type = double.class, name = "eyeY"))
    @Definition(id = "fluidTop", local = @Local(type = double.class, name = "fluidTop"))
    @Expression("eyeY <= fluidTop")
    @ModifyExpressionValue(method = "update", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean addMagicOffset(boolean original, @Local(name = "eyeY") double eyeY, @Local(name = "fluidBottom") double fluidBottom, @Local(name = "level") BlockGetter level, @Local(name = "fluidState") FluidState fluidState, @Local(name = "mutablePos") BlockPos.MutableBlockPos mutablePos) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            return eyeY <= (fluidBottom + (fluidState.getHeight(level, mutablePos) + (0.11111111F * 2F)));
        } else {
            return original;
        }
    }

    @Definition(id = "fluidTop", local = @Local(type = double.class, name = "fluidTop"))
    @Definition(id = "box", local = @Local(type = AABB.class, name = "box"))
    @Definition(id = "minY", field = "Lnet/minecraft/world/phys/AABB;minY:D")
    @Expression("fluidTop < box.minY")
    @ModifyExpressionValue(method = "update", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean removeConditional(boolean original) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return false; // Equates to true due to the negation in the original code
        } else {
            return original;
        }
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
    private double adjustHeightCalculation(double a, double b) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return Math.max(a + 0.4, b);
        } else {
            return Math.max(a, b);
        }
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 dontScaleCurrent(Vec3 instance, double scale) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return instance;
        } else {
            return instance.scale(scale);
        }
    }

}
