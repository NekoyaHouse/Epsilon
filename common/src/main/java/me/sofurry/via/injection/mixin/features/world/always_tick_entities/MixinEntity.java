package me.sofurry.via.injection.mixin.features.world.always_tick_entities;

import com.github.epsilon.modules.impl.ClientSetting;
import me.sofurry.via.injection.access.world.always_tick_entities.IEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntity {

    @Unique
    private boolean viaVersion$isInLoadedChunkAndShouldTick;

    @Override
    public boolean viaVersion$isInLoadedChunkAndShouldTick() {
        return this.viaVersion$isInLoadedChunkAndShouldTick || ClientSetting.INSTANCE.alwaysTickClientPlayer.isEnabled();
    }

    @Override
    public void viaVersion$setInLoadedChunkAndShouldTick(final boolean inLoadedChunkAndShouldTick) {
        this.viaVersion$isInLoadedChunkAndShouldTick = inLoadedChunkAndShouldTick;
    }

}
