package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.EntityRenderStateAccessor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class MixinEntityRenderState implements EntityRenderStateAccessor {

    @Unique
    private Entity epsilon$entity;

    @Override
    public Entity epsilon$getEntity() {
        return epsilon$entity;
    }

    @Override
    public void epsilon$setEntity(Entity entity) {
        this.epsilon$entity = entity;
    }

}
