package com.github.epsilon.modules.impl.movement.follower;

import net.minecraft.world.phys.Vec3;

import java.util.List;

public record FollowerPath(Vec3 nextPoint, List<Vec3> points) {
}
