package me.sofurry.via.injection.mixin.features.bedrock.movement;

import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Shadow
    public abstract MobEffectInstance getEffect(final Holder<MobEffect> effect);

    @Redirect(method = "getFluidFallingAdjustedMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSprinting()Z"))
    private boolean changeFluidGravityCondition(LivingEntity instance) {
        return ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest) ? instance.isSwimming() : instance.isSprinting();
    }

    @Inject(method = "getFluidFallingAdjustedMovement", at = @At("HEAD"), cancellable = true)
    private void applyLevitationVelocity(double baseGravity, boolean isFalling, Vec3 movement, CallbackInfoReturnable<Vec3> ci) {
        final MobEffectInstance effect = this.getEffect(MobEffects.LEVITATION);
        if (ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest) && effect != null) {
            ci.setReturnValue(new Vec3(movement.x, movement.y + (((effect.getAmplifier() + 1) * 0.05) - movement.y) * 0.2, movement.z));
        }
    }

}
