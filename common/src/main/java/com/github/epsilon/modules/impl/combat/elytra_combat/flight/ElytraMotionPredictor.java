package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 26.2 滑翔运动方程工具。
 *
 * <p>基础 A* 只负责输出方块路径；这里保留 input 模式反解旋转和
 * {@link ElytraDirectionSolver} 安全预演所需的下一 tick 滑翔速度计算。</p>
 */
public final class ElytraMotionPredictor {

    private ElytraMotionPredictor() {
    }

    public record PlayerCollisionProfile(double width, double height) {
    }

    public static Vec3 nextFallFlyingMovement(Vec3 movement, float yaw, float pitch, double gravity) {
        // 严格按 LivingEntity.updateFallFlyingMovement 的顺序复算：重力、转向修正、阻力。
        Vec3 lookAngle = calculateViewVector(pitch, yaw);
        double leanAngle = pitch * (Math.PI / 180.0);
        double lookHorizontalLength = Math.sqrt(lookAngle.x * lookAngle.x + lookAngle.z * lookAngle.z);
        double movementHorizontalLength = movement.horizontalDistance();
        double liftForce = Math.cos(leanAngle) * Math.cos(leanAngle);

        movement = movement.add(0.0, gravity * (-1.0 + liftForce * 0.75), 0.0);
        if (movement.y < 0.0 && lookHorizontalLength > 0.0) {
            double convert = movement.y * -0.1 * liftForce;
            movement = movement.add(
                    lookAngle.x * convert / lookHorizontalLength,
                    convert,
                    lookAngle.z * convert / lookHorizontalLength
            );
        }

        if (leanAngle < 0.0 && lookHorizontalLength > 0.0) {
            double convert = movementHorizontalLength * -Math.sin(leanAngle) * 0.04;
            movement = movement.add(
                    -lookAngle.x * convert / lookHorizontalLength,
                    convert * 3.2,
                    -lookAngle.z * convert / lookHorizontalLength
            );
        }

        if (lookHorizontalLength > 0.0) {
            movement = movement.add(
                    (lookAngle.x / lookHorizontalLength * movementHorizontalLength - movement.x) * 0.1,
                    0.0,
                    (lookAngle.z / lookHorizontalLength * movementHorizontalLength - movement.z) * 0.1
            );
        }

        return movement.multiply(0.99, 0.98, 0.99);
    }

    private static Vec3 calculateViewVector(float pitch, float yaw) {
        float realPitch = pitch * (float) (Math.PI / 180.0);
        float realYaw = -yaw * (float) (Math.PI / 180.0);
        float yawCos = Mth.cos(realYaw);
        float yawSin = Mth.sin(realYaw);
        float pitchCos = Mth.cos(realPitch);
        float pitchSin = Mth.sin(realPitch);
        return new Vec3(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
    }
}
