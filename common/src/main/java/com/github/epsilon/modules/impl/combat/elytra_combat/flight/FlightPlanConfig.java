package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

public record FlightPlanConfig(
        double stopDistance,
        int searchRadius,
        int maxNodes,
        boolean pathfinding
) {
}
