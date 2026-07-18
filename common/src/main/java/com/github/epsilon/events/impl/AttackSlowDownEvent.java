package com.github.epsilon.events.impl;

import com.github.epsilon.events.bus.Cancellable;
import net.minecraft.world.entity.Entity;

public class AttackSlowDownEvent extends Cancellable {

    private final Entity entity;
    private final float knockbackAmount;

    public AttackSlowDownEvent(Entity entity, float knockbackAmount) {
        this.entity = entity;
        this.knockbackAmount = knockbackAmount;
    }

    public Entity getEntity() {
        return entity;
    }

    public float getKnockbackAmount() {
        return knockbackAmount;
    }

}
