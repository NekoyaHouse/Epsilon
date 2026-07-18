package com.github.epsilon.events.impl;

import com.github.epsilon.events.bus.Cancellable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class AttackEntityEvent extends Cancellable {

    private final Player player;
    private final Entity entity;

    public AttackEntityEvent(Player player, Entity entity) {
        this.player = player;
        this.entity = entity;
    }

    public Player getPlayer() {
        return player;
    }

    public Entity getEntity() {
        return entity;
    }

}
