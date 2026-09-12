package com.github.epsilon.modules.impl.combat.elytra_combat;

import net.minecraft.world.phys.Vec3;

/**
 * ControlElytraFlightMode 消费的统一控制输入。
 */
public record ElytraCombatInput(
        boolean forward,
        boolean back,
        boolean left,
        boolean right,
        boolean jump,
        boolean sneak,
        float yaw,
        float pitch,
        Vec3 directVelocity
) {

    public float forwardImpulse() {
        if (forward == back) return 0.0f;
        return forward ? 1.0f : -1.0f;
    }

    public float strafeImpulse() {
        if (left == right) return 0.0f;
        return left ? 1.0f : -1.0f;
    }

    public boolean hasMoveInput() {
        return forward || back || left || right || jump || sneak;
    }

    public boolean hasDirectVelocity() {
        return directVelocity != null;
    }
}
