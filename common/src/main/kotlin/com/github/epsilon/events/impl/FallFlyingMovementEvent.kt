package com.github.epsilon.events.impl

import net.minecraft.world.phys.Vec3

/**
 * 在 26.3 滑翔运动方程计算完成后发布，允许 Direct Velocity 控制器覆盖最终速度向量。
 */
class FallFlyingMovementEvent(var movement: Vec3)
