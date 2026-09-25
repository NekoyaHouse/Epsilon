package com.github.epsilon.modules.impl.combat.zealot_crystal_plus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ZealotCrystalPlus 的纯数学与几何工具。
 * <p>
 * 只包含无副作用的静态计算，供 Render、Decision、Commit 等 Part 复用；不读取 Minecraft 状态。
 */
final class ZealotMath {

    private static final float DOUBLE_SIZE = 12.0f;

    /**
     * 原版爆炸伤害的固定系数，对应 {@code Explosion} 的 damage 计算。
     */
    static final float DAMAGE_FACTOR = 42.0f;

    private ZealotMath() {
    }

    /**
     * 球形爆炸的作用半径。
     */
    static float doubleSize() {
        return DOUBLE_SIZE;
    }

    static float easeOutQuart(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u * u;
    }

    static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    static float easeInCubic(float t) {
        return t * t * t;
    }

    /**
     * 把经过的毫秒数归一化为 {@code 0..1} 的动画进度。
     */
    static float toDelta(long startTime, int lengthMs) {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.clamp((float) elapsed / Math.max(1, lengthMs), 0.0f, 1.0f);
    }

    /**
     * 体素遍历中从起点到下一个整数边界所需的参数 t；平行轴返回正无穷。
     */
    static double intBound(double s, double ds) {
        if (ds > 0.0) {
            return (Math.floor(s + 1.0) - s) / ds;
        }
        if (ds < 0.0) {
            return (s - Math.floor(s)) / -ds;
        }
        return Double.POSITIVE_INFINITY;
    }

    /**
     * 按当前难度缩放玩家承受的爆炸伤害。
     */
    static float applyDifficultyDamage(net.minecraft.world.Difficulty difficulty, float damage) {
        return switch (difficulty) {
            case PEACEFUL -> 0.0f;
            case EASY -> Math.min(damage * 0.5f + 1.0f, damage);
            case HARD -> damage * 1.5f;
            default -> damage;
        };
    }

    // ------------------------------------------------------------------
    // 水晶几何
    // ------------------------------------------------------------------

    /**
     * 支撑方块上方水晶实体的中心点。
     */
    static Vec3 crystalPos(BlockPos supportPos) {
        return new Vec3(supportPos.getX() + 0.5, supportPos.getY() + 1.0, supportPos.getZ() + 0.5);
    }

    /**
     * 放置水晶后占据的方块区域（支撑方块上方两格）。
     */
    static AABB crystalPlaceBox(BlockPos supportPos) {
        return new AABB(
                supportPos.getX(), supportPos.getY() + 1.0, supportPos.getZ(),
                supportPos.getX() + 1.0, supportPos.getY() + 3.0, supportPos.getZ() + 1.0
        );
    }

    /**
     * 水晶实体的判定盒；比实体本身略大，用于判断水晶是否落在放置区域内。
     */
    static AABB crystalBoundingBox(Vec3 crystalPos) {
        return new AABB(
                crystalPos.x - 1.0, crystalPos.y, crystalPos.z - 1.0,
                crystalPos.x + 1.0, crystalPos.y + 2.0, crystalPos.z + 1.0
        );
    }

    static boolean crystalPlaceBoxIntersects(BlockPos supportPos, AABB crystalBox) {
        return crystalPlaceBox(supportPos).intersects(crystalBox);
    }

    static long toLong(double x, double y, double z) {
        return BlockPos.containing(x, y, z).asLong();
    }

    /**
     * 选取最接近“眼睛 → 命中点”连线的方块面，用于 Closest 放置绕行。
     */
    static Direction calcDirection(Vec3 eyePos, Vec3 hitVec) {
        double x = eyePos.x - hitVec.x;
        double y = eyePos.y - hitVec.y;
        double z = eyePos.z - hitVec.z;

        Direction best = Direction.NORTH;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            Vec3 vec = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            double dot = x * vec.x + y * vec.y + z * vec.z;
            if (dot > bestDot) {
                bestDot = dot;
                best = direction;
            }
        }
        return best;
    }
}
