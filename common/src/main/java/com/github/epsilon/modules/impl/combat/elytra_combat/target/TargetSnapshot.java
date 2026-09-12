package com.github.epsilon.modules.impl.combat.elytra_combat.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 每 tick 生成的不可变目标快照，行为层不得再次读取轨迹历史。
 */
public record TargetSnapshot(
        LivingEntity entity,
        Vec3 position,
        Vec3 velocity,
        Vec3 predictedPosition,
        TargetAction action,
        boolean onGround,
        boolean supported,
        boolean usingSpear
) {
}
