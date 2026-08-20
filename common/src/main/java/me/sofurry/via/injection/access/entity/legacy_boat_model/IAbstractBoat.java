package me.sofurry.via.injection.access.entity.legacy_boat_model;

import net.minecraft.world.phys.Vec3;

public interface IAbstractBoat {

    void viaVersion$setBoatInterpolationSteps(final int steps);

    Vec3 viaVersion$getBoatVelocity();

    void viaVersion$setBoatVelocity(final Vec3 velocity);

}
