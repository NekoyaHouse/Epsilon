package com.github.epsilon.modules.impl.combat.elytra_combat.flight

import net.minecraft.world.phys.Vec3

/**
 * 行为层与飞控层之间的纯数据接口。
 *
 * desiredVelocity 是归一化前的期望速度；lookDirection 用于输入模式的俯仰/偏航反解；
 * directVelocity 为 true 时由 FallFlyingMovementEvent 覆盖最终速度。
 */
data class FlightIntent(
    val desiredVelocity: Vec3,
    val lookDirection: Vec3,
    val directVelocity: Boolean,
    val useFirework: Boolean,
) {

    companion object {

        /** 无飞行需求时的空意图；lookDirection 仅用于保持视角自然。 */
        fun idle(lookDirection: Vec3): FlightIntent {
            return FlightIntent(Vec3.ZERO, lookDirection, false, false)
        }

    }

}
