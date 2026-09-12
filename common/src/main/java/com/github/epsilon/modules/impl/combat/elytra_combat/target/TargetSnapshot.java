package com.github.epsilon.modules.impl.combat.elytra_combat.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 每 tick 生成的不可变目标快照。
 *
 * <p>{@code predictedPosition} 由 {@link TargetMotionTracker} 计算；行为层只读快照，
 * 不再直接访问轨迹历史，避免网络线程与客户端 tick 状态交叉。</p>
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
