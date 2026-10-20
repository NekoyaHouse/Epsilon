package com.github.epsilon.events.impl;
import net.minecraft.world.entity.Entity;
/** Server entity event, posted after vanilla handles it on the client thread. */
public record TotemPopEvent(Entity entity) {}
