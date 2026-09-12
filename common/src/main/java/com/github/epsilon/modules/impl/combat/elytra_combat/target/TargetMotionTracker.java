package com.github.epsilon.modules.impl.combat.elytra_combat.target;

import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatWeaponController;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 维护单个目标的最近位置历史，并提供预测和动作分类。
 *
 * <p>采样只在客户端 tick 执行，避免从网络线程访问实体或 Level。历史保留 30 个样本，
 * 目标切换时立即清空，防止把旧目标速度带入新目标。</p>
 */
public final class TargetMotionTracker {

    private static final int MAX_HISTORY = 30;
    private static final int AFK_TICKS = 20;
    private static final double SLOW_DISTANCE = 0.75;
    private static final double STRAIGHT_ANGLE_DEGREES = 60.0;

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private LivingEntity trackedTarget;
    private volatile int trackedTargetId = -1;
    private volatile boolean teleportPending;

    public void reset() {
        this.trackedTarget = null;
        this.trackedTargetId = -1;
        this.teleportPending = false;
        this.samples.clear();
    }

    public TargetSnapshot update(LivingEntity target, int tick, PredictorMode mode, int ticksLater, int historyTicks) {
        if (target != this.trackedTarget || this.teleportPending) {
            this.trackedTarget = target;
            this.trackedTargetId = target.getId();
            this.teleportPending = false;
            this.samples.clear();
        }

        Vec3 position = target.position();
        Sample newest = this.samples.peekLast();
        if (newest == null || newest.tick() != tick) {
            this.samples.addLast(new Sample(position, tick));
        } else {
            this.samples.removeLast();
            this.samples.addLast(new Sample(position, tick));
        }
        while (this.samples.size() > MAX_HISTORY) {
            this.samples.removeFirst();
        }

        Vec3 predicted = predict(ticksLater, mode, historyTicks);
        return new TargetSnapshot(
                target,
                position,
                knownDeltaMovement(),
                predicted,
                classify(tick),
                target.onGround(),
                target.onGround() || !target.level().noBlockCollision(target, target.getBoundingBox().move(0.0, -0.04, 0.0)),
                CombatWeaponController.isUsingSpear(target)
        );
    }

    /**
     * 由网络线程标记目标传送；实际轨迹清理延迟到客户端 tick，避免跨线程访问 Entity。
     */
    public void noteTeleport(int entityId) {
        if (entityId == this.trackedTargetId) {
            this.teleportPending = true;
        }
    }

    public Vec3 knownDeltaMovement() {
        if (this.samples.size() < 2) {
            return Vec3.ZERO;
        }

        Sample newest = this.samples.peekLast();
        Sample second = null;
        Sample previous = null;
        for (Sample sample : this.samples) {
            if (sample == newest) {
                second = previous;
                break;
            }
            previous = sample;
        }
        if (second == null || newest.tick() <= second.tick()) {
            return Vec3.ZERO;
        }
        return newest.position().subtract(second.position()).scale(1.0 / (newest.tick() - second.tick()));
    }

    public Vec3 predict(int ticksLater, PredictorMode mode, int historyTicks) {
        if (this.samples.isEmpty()) {
            return Vec3.ZERO;
        }

        List<Sample> window = recentSamples(historyTicks);
        if (window.size() < 2 || ticksLater <= 0) {
            return window.getLast().position();
        }

        return switch (mode) {
            case Linear -> linearPrediction(window, ticksLater);
            case Quadratic -> quadraticPrediction(window, ticksLater);
            case NV -> nvPrediction(window, ticksLater);
        };
    }

    public TargetAction classify(int currentTick) {
        if (this.samples.size() < 2) {
            return TargetAction.CIRCLING;
        }

        List<Sample> window = recentSamples(3);
        Sample oldest = window.getFirst();
        if (currentTick - oldest.tick() > AFK_TICKS) {
            return TargetAction.AFK;
        }
        if (window.size() < 3) {
            return TargetAction.TOWARDS;
        }

        Vec3 pos0 = window.get(0).position();
        Vec3 pos1 = window.get(1).position();
        Vec3 pos2 = window.get(2).position();
        double dist01 = pos0.distanceTo(pos1);
        double dist12 = pos1.distanceTo(pos2);
        if (dist01 < 1.0E-6 && dist12 < 1.0E-6) {
            return TargetAction.AFK;
        }
        if (dist01 < SLOW_DISTANCE && dist12 < SLOW_DISTANCE) {
            return TargetAction.SLOW_SPEED;
        }

        Vec3 ab = pos1.subtract(pos0);
        Vec3 bc = pos2.subtract(pos1);
        double denominator = ab.length() * bc.length();
        if (denominator < 1.0E-6) {
            return TargetAction.SLOW_SPEED;
        }

        double dot = ab.dot(bc) / denominator;
        double angle = Math.toDegrees(Math.acos(Math.clamp(dot, -1.0, 1.0)));
        if (angle >= STRAIGHT_ANGLE_DEGREES) {
            return TargetAction.CIRCLING;
        }

        Vec3 toPlayer = net.minecraft.client.Minecraft.getInstance().player.position().subtract(pos2);
        double moveDot = bc.normalize().dot(toPlayer.normalize());
        return moveDot > 0.0 ? TargetAction.TOWARDS : TargetAction.ESCAPING;
    }

    private List<Sample> recentSamples(int count) {
        int size = Math.min(Math.max(1, count), this.samples.size());
        ArrayList<Sample> result = new ArrayList<>(size);
        int skip = this.samples.size() - size;
        int index = 0;
        for (Sample sample : this.samples) {
            if (index++ >= skip) {
                result.add(sample);
            }
        }
        return result;
    }

    /**
     * 对所有可用样本做最小二乘线性回归，并以外推到未来 tick。
     */
    private static Vec3 linearPrediction(List<Sample> samples, int ticksLater) {
        Sample newest = samples.getLast();
        double meanTick = 0.0;
        Vec3 meanPos = Vec3.ZERO;
        for (Sample sample : samples) {
            meanTick += sample.tick();
            meanPos = meanPos.add(sample.position());
        }
        meanTick /= samples.size();
        meanPos = meanPos.scale(1.0 / samples.size());

        double variance = 0.0;
        Vec3 covariance = Vec3.ZERO;
        for (Sample sample : samples) {
            double dt = sample.tick() - meanTick;
            variance += dt * dt;
            covariance = covariance.add(sample.position().subtract(meanPos).scale(dt));
        }
        if (variance < 1.0E-6) {
            return newest.position();
        }

        Vec3 slope = covariance.scale(1.0 / variance);
        double futureTick = newest.tick() + ticksLater;
        return meanPos.add(slope.scale(futureTick - meanTick));
    }

    /**
     * 三次样本不足时退化为线性；足够时拟合 x=a*t^2+b*t+c。
     */
    private static Vec3 quadraticPrediction(List<Sample> samples, int ticksLater) {
        if (samples.size() < 3) {
            return linearPrediction(samples, ticksLater);
        }

        Sample newest = samples.getLast();
        double[] t = new double[samples.size()];
        double[] sx = new double[samples.size()];
        double[] sy = new double[samples.size()];
        double[] sz = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            t[i] = sample.tick() - newest.tick();
            sx[i] = sample.position().x;
            sy[i] = sample.position().y;
            sz[i] = sample.position().z;
        }

        double[] x = polynomial2(t, sx);
        double[] y = polynomial2(t, sy);
        double[] z = polynomial2(t, sz);
        double future = ticksLater;
        return new Vec3(
                x[0] * future * future + x[1] * future + x[2],
                y[0] * future * future + y[1] * future + y[2],
                z[0] * future * future + z[1] * future + z[2]
        );
    }

    /**
     * Slimefun 的 NV 模型：对相邻速度差做指数加权平均，再线性外推。
     */
    private static Vec3 nvPrediction(List<Sample> samples, int ticksLater) {
        Vec3 weighted = Vec3.ZERO;
        Vec3 previous = null;
        for (Sample sample : samples) {
            if (previous != null) {
                weighted = weighted.add(sample.position().subtract(previous)).scale(0.5);
            }
            previous = sample.position();
        }
        return samples.getLast().position().add(weighted.scale(ticksLater));
    }

    private static double[] polynomial2(double[] t, double[] values) {
        double st0 = t.length;
        double st1 = 0.0;
        double st2 = 0.0;
        double st3 = 0.0;
        double st4 = 0.0;
        double sv0 = 0.0;
        double sv1 = 0.0;
        double sv2 = 0.0;
        for (int i = 0; i < t.length; i++) {
            double ti = t[i];
            double ti2 = ti * ti;
            st1 += ti;
            st2 += ti2;
            st3 += ti2 * ti;
            st4 += ti2 * ti2;
            sv0 += values[i];
            sv1 += ti * values[i];
            sv2 += ti2 * values[i];
        }

        double[][] matrix = {
                {st4, st3, st2, sv2},
                {st3, st2, st1, sv1},
                {st2, st1, st0, sv0}
        };
        for (int column = 0; column < 3; column++) {
            int pivot = column;
            for (int row = column + 1; row < 3; row++) {
                if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) {
                    pivot = row;
                }
            }
            double[] swap = matrix[column];
            matrix[column] = matrix[pivot];
            matrix[pivot] = swap;
            double divisor = matrix[column][column];
            if (Math.abs(divisor) < 1.0E-9) {
                return new double[]{0.0, 0.0, values[values.length - 1]};
            }
            for (int i = column; i < 4; i++) {
                matrix[column][i] /= divisor;
            }
            for (int row = 0; row < 3; row++) {
                if (row == column) {
                    continue;
                }
                double factor = matrix[row][column];
                for (int i = column; i < 4; i++) {
                    matrix[row][i] -= factor * matrix[column][i];
                }
            }
        }
        return new double[]{matrix[0][3], matrix[1][3], matrix[2][3]};
    }

    private record Sample(Vec3 position, int tick) {
    }
}
