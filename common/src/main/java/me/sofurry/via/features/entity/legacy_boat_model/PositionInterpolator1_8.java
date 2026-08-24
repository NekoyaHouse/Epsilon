package me.sofurry.via.features.entity.legacy_boat_model;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.injection.access.entity.legacy_boat_model.IAbstractBoat;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

public class PositionInterpolator1_8 extends InterpolationHandler {

    private final AbstractBoat boatEntity;

    public PositionInterpolator1_8(final AbstractBoat entity) {
        super(entity);
        this.boatEntity = entity;
    }

    @Override
    public void interpolateTo(final Vec3 pos, final float yaw, final float pitch) {
        final IAbstractBoat mixinBoatEntity = (IAbstractBoat) this.boatEntity;
        if (/*interpolate &&*/ boatEntity.isVehicle() && ProtocolTranslator.getTargetVersion().newerThan(ProtocolVersion.v1_7_6)) {
            boatEntity.xo = pos.x;
            boatEntity.yo = pos.y;
            boatEntity.zo = pos.z;
            mixinBoatEntity.viaVersion$setBoatInterpolationSteps(0);
            boatEntity.setPos(pos);
            boatEntity.setRot(yaw, pitch);
            boatEntity.setDeltaMovement(Vec3.ZERO);
            mixinBoatEntity.viaVersion$setBoatVelocity(Vec3.ZERO);
        } else {
            if (!boatEntity.isVehicle()) {
                mixinBoatEntity.viaVersion$setBoatInterpolationSteps(8);
            } else {
                if (boatEntity.distanceToSqr(pos.x, pos.y, pos.z) <= 1) {
                    return;
                }
                mixinBoatEntity.viaVersion$setBoatInterpolationSteps(3);
            }

            this.interpolationData.position = pos;
            this.interpolationData.yRot = yaw;
            this.interpolationData.xRot = pitch;
            boatEntity.setDeltaMovement(mixinBoatEntity.viaVersion$getBoatVelocity());
        }
    }

    @Override
    public void interpolate() {
    }

}
