package me.sofurry.via.injection.mixin.features.movement.elytra;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    public MixinLivingEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Redirect(method = "travelFallFlying", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;onClimbable()Z"))
    private boolean dontStopGlidingWhenClimbing(LivingEntity instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_21_4) && instance.onClimbable();
    }

    @Redirect(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Ljava/lang/Math;cos(D)D", remap = false))
    private double fixCosTable(double a) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_18)) {
            return Mth.cos((float) a);
        } else {
            return Math.cos(a);
        }
    }

    @Redirect(method = {"canGlide()Z", "canGlide(Z)Z"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean allowElytraWhenLevitating(LivingEntity instance, Holder<MobEffect> effect) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_15_2) && instance.hasEffect(effect);
    }

    @Redirect(method = {"canGlide()Z", "canGlide(Z)Z"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z"))
    private boolean allowElytraInVehicle(LivingEntity instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_14_4) && instance.isPassenger();
    }


}
