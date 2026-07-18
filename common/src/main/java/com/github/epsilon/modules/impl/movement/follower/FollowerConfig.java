package com.github.epsilon.modules.impl.movement.follower;

public record FollowerConfig(
        double stopDistance,
        double verticalDeadzone,
        int searchRadius,
        int maxNodes
) {
}
