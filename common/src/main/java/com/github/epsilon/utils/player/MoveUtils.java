package com.github.epsilon.utils.player;

import com.github.epsilon.managers.rotation.RotationManager;
import net.minecraft.world.phys.Vec2;

import static com.github.epsilon.Constants.mc;

public class MoveUtils {

    /**
     * 根据当前旋转计算忽略横移输入的水平速度向量。
     *
     * @param speed 移动速度或最大旋转速度
     * @return 操作结果
     */
    public static double[] forwardWithoutStrafe(double speed) {
        float yaw = RotationManager.INSTANCE.getYaw();

        double rad = Math.toRadians(yaw + 90.0f);

        double d4 = speed * Math.cos(rad);
        double d5 = speed * Math.sin(rad);

        return new double[]{d4, d5};
    }

    /**
     * 根据当前旋转和移动输入计算水平速度向量。
     *
     * @param speed 移动速度或最大旋转速度
     * @return 操作结果
     */
    public static double[] forward(double speed) {
        float yaw = RotationManager.INSTANCE.getYaw();
        Vec2 moveVector = mc.player.input.getMoveVector();
        float forward = moveVector.y;
        float left = moveVector.x;

        if (forward != 0.0f) {
            if (left > 0.0f) {
                yaw += ((forward > 0.0f) ? -45 : 45);
            } else if (left < 0.0f) {
                yaw += ((forward > 0.0f) ? 45 : -45);
            }
            left = 0.0f;
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }

        double rad = Math.toRadians(yaw + 90.0f);

        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        double d4 = forward * speed * cos + left * speed * sin;
        double d5 = forward * speed * sin - left * speed * cos;

        return new double[]{d4, d5};
    }

}
