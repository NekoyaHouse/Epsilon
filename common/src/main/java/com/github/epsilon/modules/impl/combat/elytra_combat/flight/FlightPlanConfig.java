package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

/**
 * 飞控规划器每 tick 使用的不可变配置快照。
 */
public record FlightPlanConfig(
        double stopDistance,
        int searchRadius,
        int maxNodes,
        boolean pathfinding
) {
}
