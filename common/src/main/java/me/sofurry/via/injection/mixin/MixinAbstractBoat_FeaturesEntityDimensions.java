package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.features.entity.dimensions.EntityRidingOffsetsPre1_20_2;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBoat.class)
public abstract class MixinAbstractBoat_FeaturesEntityDimensions extends VehicleEntity {

    public MixinAbstractBoat_FeaturesEntityDimensions(EntityType<?> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "positionRider", at = @At(value = "HEAD"), cancellable = true)
    private void updatePassengerPosition1_8(Entity passenger, Entity.MoveFunction moveFunction, CallbackInfo ci) {
        if (ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            final Vec3 newPosition = EntityRidingOffsetsPre1_20_2.getMountedHeightOffset(this, passenger).add(this.position());
            moveFunction.accept(passenger, newPosition.x, newPosition.y + EntityRidingOffsetsPre1_20_2.getHeightOffset(passenger), newPosition.z);
            ci.cancel();
        }
    }

}
