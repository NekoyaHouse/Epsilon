package com.github.epsilon.modules.impl.combat.elytra_combat.path

/**
 * 后台 A* 的单次搜索参数。
 *
 * searchRadius 最终还会被体素窗口大小限制；maxNodes 是防止绕大障碍时无界扩展的硬上限。
 */
data class PathConfig(
    val stopDistance: Double,
    val searchRadius: Int,
    val maxNodes: Int,
)
