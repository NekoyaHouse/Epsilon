package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.LivingEntity;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_FeaturesMovementJump {

    @Shadow
    protected boolean jumping;

    @Shadow
    private int noJumpDelay;

    @Redirect(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
    private double dontLimitJumpVelocity(double a, double b) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            return a;
        } else {
            return Math.max(a, b);
        }
    }

    @Redirect(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;jumping:Z", opcode = Opcodes.GETFIELD))
    private boolean disableJumpOnLadder(LivingEntity self) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_13_2) && jumping;
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void removeJumpDelay(CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThan(LegacyProtocolVersion.r1_0_0tor1_0_1)) {
            this.noJumpDelay = 0;
        }
    }

}
