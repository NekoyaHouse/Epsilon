package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

import net.minecraft.world.phys.Vec3;

/**
 * 行为层与飞控层之间的纯数据接口。
 *
 * <p>desiredVelocity 是归一化前的期望速度；lookDirection 用于输入模式的俯仰/偏航反解；
 * directVelocity 为 true 时由 FallFlyingMovementEvent 覆盖最终速度。</p>
 */
public record FlightIntent(
        Vec3 desiredVelocity,
        Vec3 lookDirection,
        boolean directVelocity,
        boolean useFirework
) {

    public static FlightIntent idle(Vec3 lookDirection) {
        return new FlightIntent(Vec3.ZERO, lookDirection, false, false);
    }
}
