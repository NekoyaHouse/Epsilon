package com.github.epsilon.modules.impl.combat.elytra_combat

import net.minecraft.world.phys.Vec3

/**
 * ControlElytraFlightMode 消费的统一控制输入。
 *
 * Input 模式使用 yaw/pitch 与 WASD 位；DirectVelocity 模式额外携带 [directVelocity]，
 * 由 `FallFlyingMovementEvent` 覆盖当 tick 最终速度。
 */
data class ElytraCombatInput(
    val forward: Boolean,
    val back: Boolean,
    val left: Boolean,
    val right: Boolean,
    val jump: Boolean,
    val sneak: Boolean,
    val yaw: Float,
    val pitch: Float,
    val directVelocity: Vec3?,
) {

    fun forwardImpulse(): Float {
        if (forward == back) return 0.0f
        return if (forward) 1.0f else -1.0f
    }

    fun strafeImpulse(): Float {
        if (left == right) return 0.0f
        return if (left) 1.0f else -1.0f
    }

    fun hasMoveInput(): Boolean {
        return forward || back || left || right || jump || sneak
    }

    fun hasDirectVelocity(): Boolean {
        return directVelocity != null
    }

}
