package com.github.epsilon.modules.impl.combat.elytra_combat.path;

/**
 * 后台 A* 的单次搜索参数。
 *
 * <p>searchRadius 最终还会被体素窗口大小限制；maxNodes 是防止绕大障碍时
 * 无界扩展的硬上限。</p>
 */
public record PathConfig(
        double stopDistance,
        int searchRadius,
        int maxNodes
) {
}
