package com.github.epsilon.modules.impl.movement.follower;

public record FollowerInput(
        boolean forward,
        boolean back,
        boolean left,
        boolean right,
        boolean jump,
        boolean sneak,
        float yaw,
        float pitch
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

}
