package com.github.epsilon.events.impl;

import net.minecraft.world.phys.Vec3;

/**
 * 在 26.2 滑翔运动方程计算完成后发布，允许 Direct Velocity 控制器覆盖最终速度向量。
 */
public class FallFlyingMovementEvent {

    private Vec3 movement;

    public FallFlyingMovementEvent(Vec3 movement) {
        this.movement = movement;
    }

    public Vec3 getMovement() {
        return movement;
    }

    public void setMovement(Vec3 movement) {
        this.movement = movement;
    }
}
