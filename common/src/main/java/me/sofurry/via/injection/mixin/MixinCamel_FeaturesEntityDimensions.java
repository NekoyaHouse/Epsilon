package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camel.class)
public abstract class MixinCamel_FeaturesEntityDimensions extends AbstractHorse {

    public MixinCamel_FeaturesEntityDimensions(EntityType<? extends AbstractHorse> entityType, Level world) {
        super(entityType, world);
    }

    @ModifyConstant(method = "getBodyAnchorAnimationYOffset", constant = @Constant(doubleValue = 0.375))
    private double scaleSitOffset(double constant, @Local(ordinal = 1, argsOnly = true) float scale) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return constant * scale;
        } else {
            return constant;
        }
    }

    @Redirect(method = "getBodyAnchorAnimationYOffset", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/camel/Camel;isBaby()Z"))
    private boolean removeBabySitOffset(Camel instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v26_1) && instance.isBaby();
    }

    @Redirect(method = "getDefaultDimensions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/camel/Camel;isBaby()Z"))
    private boolean dontChangeScale(Camel instance) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v26_1) && instance.isBaby();
    }

    @Inject(method = "getAgeScale", at = @At("HEAD"), cancellable = true)
    private void changeBabyScale(CallbackInfoReturnable<Float> cir) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11) && this.isBaby()) {
            cir.setReturnValue(0.45F);
        }
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20) && this.getControllingPassenger() != passenger) {
            this.viaVersion$clampPassengerYaw1_20_1(passenger);
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction positionUpdater) {
        super.positionRider(passenger, positionUpdater);

        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20)) {
            this.viaVersion$clampPassengerYaw1_20_1(passenger);
        }
    }

    @Unique
    private void viaVersion$clampPassengerYaw1_20_1(final Entity passenger) {
        passenger.setYBodyRot(this.getYRot());
        final float passengerYaw = passenger.getYRot();

        final float deltaDegrees = Mth.wrapDegrees(passengerYaw - this.getYRot());
        final float clampedDelta = Mth.clamp(deltaDegrees, -160.0F, 160.0F);
        passenger.yRotO += clampedDelta - deltaDegrees;

        final float newYaw = passengerYaw + clampedDelta - deltaDegrees;
        passenger.setYRot(newYaw);
        passenger.setYHeadRot(newYaw);
    }

}
