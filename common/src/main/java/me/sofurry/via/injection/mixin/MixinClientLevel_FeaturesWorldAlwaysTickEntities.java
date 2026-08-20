package me.sofurry.via.injection.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.sofurry.via.injection.access.world.always_tick_entities.IEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel_FeaturesWorldAlwaysTickEntities extends Level {

    @Shadow
    @Final
    private EntityTickList tickingEntities;

    protected MixinClientLevel_FeaturesWorldAlwaysTickEntities(WritableLevelData properties, ResourceKey<Level> registryRef, RegistryAccess registryManager, Holder<DimensionType> dimensionEntry, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryManager, dimensionEntry, isClient, debugWorld, seed, maxChainedNeighborUpdates);
    }

    @Shadow
    protected abstract void tickPassenger(final Entity vehicle, final Entity entity);

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void alwaysTickEntities(Entity entity, CallbackInfo ci) {
        final IEntity mixinEntity = (IEntity) entity;
        if (!mixinEntity.viaVersion$isInLoadedChunkAndShouldTick() && !entity.isSpectator()) {
            entity.setOldPosAndRot();
            this.viaVersion$checkChunk(entity);
            if (mixinEntity.viaVersion$isInLoadedChunkAndShouldTick()) {
                for (Entity entity2 : entity.getPassengers()) {
                    this.tickPassenger(entity, entity2);
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void alwaysTickEntities(Entity vehicle, Entity entity, CallbackInfo ci) {
        final IEntity mixinPassenger = (IEntity) entity;
        if (!mixinPassenger.viaVersion$isInLoadedChunkAndShouldTick()) {
            if (entity.isRemoved() || entity.getVehicle() != vehicle) {
                entity.stopRiding();
            } else if (entity instanceof Player || this.tickingEntities.contains(entity)) {
                entity.setOldPosAndRot();
                this.viaVersion$checkChunk(entity);
                if (mixinPassenger.viaVersion$isInLoadedChunkAndShouldTick()) {
                    for (Entity entity2 : entity.getPassengers()) {
                        this.tickPassenger(entity, entity2);
                    }
                }
            }
            ci.cancel();
        }
    }

    @WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getPassengers()Ljava/util/List;"))
    private List<Entity> alwaysTickEntities(Entity instance, Operation<List<Entity>> original) {
        this.viaVersion$checkChunk(instance);
        final IEntity mixinEntity = (IEntity) instance;
        if (mixinEntity.viaVersion$isInLoadedChunkAndShouldTick()) {
            return original.call(instance);
        } else {
            return List.of();
        }
    }

    @Unique
    private void viaVersion$checkChunk(Entity entity) {
        final IEntity mixinEntity = (IEntity) entity;
        final int chunkX = Mth.floor(entity.getX() / 16.0D);
        final int chunkZ = Mth.floor(entity.getZ() / 16.0D);
        if (!mixinEntity.viaVersion$isInLoadedChunkAndShouldTick() || entity.chunkPosition().x() != chunkX || entity.chunkPosition().z() != chunkZ) {
            if (!(this.getChunk(chunkX, chunkZ).isEmpty())) {
                mixinEntity.viaVersion$setInLoadedChunkAndShouldTick(true);
            }
        }
    }

}
