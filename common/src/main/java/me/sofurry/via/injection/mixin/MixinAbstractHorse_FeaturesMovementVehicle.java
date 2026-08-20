package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractHorse.class)
public abstract class MixinAbstractHorse_FeaturesMovementVehicle {

    @WrapWithCondition(method = "addPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;absSnapRotationTo(FF)V"))
    private boolean dontSetPassengerRotation(Entity instance, float yRot, float xRot) {
        return ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_21_7);
    }

}
