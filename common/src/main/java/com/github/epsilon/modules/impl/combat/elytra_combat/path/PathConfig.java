package com.github.epsilon.modules.impl.combat.elytra_combat.path;

/**
 * 后台 A* 的单次搜索参数。
 */
public record PathConfig(
        double stopDistance,
        int searchRadius,
        int maxNodes
) {
}
