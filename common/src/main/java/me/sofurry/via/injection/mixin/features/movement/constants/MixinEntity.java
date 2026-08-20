package me.sofurry.via.injection.mixin.features.movement.constants;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @ModifyConstant(method = "checkInsideBlocks(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/InsideBlockEffectApplier$StepBasedCollector;Lit/unimi/dsi/fastutil/longs/LongSet;I)I", constant = @Constant(doubleValue = 9.999999747378752E-6))
    private double fixBlockCollisionMargin(double constant) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_1)) {
            return 1E-3;
        } else if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            return 1E-7;
        } else {
            return constant;
        }
    }

    @Inject(method = "getInputVector", at = @At("HEAD"), cancellable = true)
    private static void getInputVector1_13_2(Vec3 input, float speed, float yRot, CallbackInfoReturnable<Vec3> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            float x = (float) input.x;
            float z = (float) input.z;

            float length = x * x + z * z;
            if (length < 1.0E-4F) {
                cir.setReturnValue(Vec3.ZERO);
            }

            length = Math.max(Mth.sqrt(length), 1.0F);

            final float scale = speed / length;

            x *= scale;
            z *= scale;

            final float sin = Mth.sin(yRot * (float) (Math.PI / 180.0));
            final float cos = Mth.cos(yRot * (float) (Math.PI / 180.0));

            cir.setReturnValue(new Vec3(x * cos - z * sin, input.y, z * cos + x * sin));
        }
    }

    @Inject(method = "getPickRadius", at = @At("HEAD"), cancellable = true)
    private void expandHitBox(CallbackInfoReturnable<Float> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            cir.setReturnValue(0.1F);
        }
    }

}
