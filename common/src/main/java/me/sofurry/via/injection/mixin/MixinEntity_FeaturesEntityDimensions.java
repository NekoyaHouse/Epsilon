package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.features.entity.dimensions.EntityRidingOffsetsPre1_20_2;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class MixinEntity_FeaturesEntityDimensions {

    @Shadow
    protected abstract Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scaleFactor);

    @Redirect(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getVehicleAttachmentPoint(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 use1_20_1RidingOffset(Entity instance, Entity vehicle) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20)) {
            return new Vec3(0, -EntityRidingOffsetsPre1_20_2.getHeightOffset(instance), 0);
        } else {
            return instance.getVehicleAttachmentPoint(vehicle);
        }
    }

    @Redirect(method = "getPassengerRidingPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getPassengerAttachmentPoint(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/EntityDimensions;F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 getPassengerRidingPos1_20_1(Entity instance, Entity passenger, EntityDimensions dimensions, float scale) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20)) {
            return EntityRidingOffsetsPre1_20_2.getMountedHeightOffset(instance, passenger).yRot(-instance.getYRot() * (float) (Math.PI / 180));
        } else {
            return getPassengerAttachmentPoint(passenger, dimensions, scale);
        }
    }

}
