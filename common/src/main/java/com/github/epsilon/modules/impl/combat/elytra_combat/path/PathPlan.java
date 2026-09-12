package com.github.epsilon.modules.impl.combat.elytra_combat.path;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 后台寻路结果；points 始终由不可变列表持有，可安全跨线程读取。
 *
 * <p>nextPoint 是消费端本 tick 应追踪的节点，points 保留完整原始 A* 路径。</p>
 */
public record PathPlan(Vec3 nextPoint, List<Vec3> points) {
}
