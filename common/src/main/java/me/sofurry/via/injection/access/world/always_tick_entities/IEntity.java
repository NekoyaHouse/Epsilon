package me.sofurry.via.injection.access.world.always_tick_entities;

public interface IEntity {

    boolean viaVersion$isInLoadedChunkAndShouldTick();

    void viaVersion$setInLoadedChunkAndShouldTick(final boolean inLoadedChunkAndShouldTick);

}
