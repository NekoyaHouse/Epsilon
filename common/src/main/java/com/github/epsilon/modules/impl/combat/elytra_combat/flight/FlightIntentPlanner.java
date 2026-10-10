package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

import com.github.epsilon.modules.impl.combat.elytra_combat.path.ElytraPathNavigator;
import com.github.epsilon.modules.impl.combat.elytra_combat.path.PathConfig;
import com.github.epsilon.modules.impl.combat.elytra_combat.path.PathPlan;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 将行为层的期望速度转换为可执行飞行意图。
 *
 * <p>先检查短距离直飞；开启寻路时直接使用后台基础 A*，否则退回局部扇区避障。
 * 这样可以把 A* 路径和局部避障明确分开，避免两套方向在同一 tick 互相覆盖。</p>
 */
public class FlightIntentPlanner {

    private static final double LOCAL_PROBE_DISTANCE = 6.0;

    private final ElytraPathNavigator pathNavigator = new ElytraPathNavigator();
    /**
     * 局部避障上一 tick 的方向，用于抑制左右两侧得分接近时来回切换。
     */
    private Vec3 lastAvoidanceDirection;

    public FlightIntent plan(
            LocalPlayer player,
            FlightIntent rawIntent,
            Vec3 targetPoint,
            FlightPlanConfig config
    ) {
        if (rawIntent.desiredVelocity().lengthSqr() < 1.0E-8) {
            return rawIntent;
        }

        Vec3 desired = rawIntent.desiredVelocity();
        double probe = Math.clamp(desired.length() * 4.0, 4.0, LOCAL_PROBE_DISTANCE);
        Vec3 directEnd = player.position().add(desired.normalize().scale(probe));
        if (LocalFlightAvoidance.isSegmentClear(player, player.position(), directEnd)) {
            // 短距离直线已验证安全，直接保留行为层的期望速度。
            // 注意不要在这里清空 lastAvoidanceDirection：否则下次进入避障时又失去左右偏好，
            // 会在障碍边界上逐 tick 左右摇摆（转头抽风）。
            ElytraDebug.log(ElytraDebug.SLOT_PLANNER, "planner", "direct");
            return new FlightIntent(desired, desired.normalize(), rawIntent.directVelocity(), rawIntent.useFirework());
        }

        if (!config.pathfinding()) {
            return planAvoidance(player, desired, targetPoint, probe, rawIntent);
        }

        PathPlan path = this.pathNavigator.getPath(
                player,
                targetPoint,
                new PathConfig(config.stopDistance(), config.searchRadius(), config.maxNodes())
        );
        // 从原始 A* 路径选择当前仍能直线到达的最近航点。
        Vec3 waypoint = selectPathWaypoint(player, path);
        if (waypoint != null) {
            Vec3 waypointVelocity = waypoint.subtract(player.position());
            if (waypointVelocity.lengthSqr() >= 1.0E-8) {
                waypointVelocity = waypointVelocity.normalize().scale(desired.length());
                ElytraDebug.log(ElytraDebug.SLOT_PLANNER, "planner", "waypoint " + vec(waypointVelocity));
                // 不在这里剥夺烟花：绕障/寻路时正是最需要推进的时候（撞上障碍后滑翔速度归零，
                // 没有烟花就只能贴着障碍低速磨），是否放由 Allow Firework 与行为层意愿决定。
                return new FlightIntent(waypointVelocity, waypointVelocity.normalize(), rawIntent.directVelocity(), rawIntent.useFirework());
            }
        }

        return planAvoidance(player, desired, targetPoint, probe, rawIntent);
    }

    private FlightIntent planAvoidance(
            LocalPlayer player,
            Vec3 desired,
            Vec3 targetPoint,
            double probe,
            FlightIntent rawIntent
    ) {
        // 只有 A* 没有可用路径时才启用局部扇区搜索，并延续上一次方向。
        Vec3 avoidance = LocalFlightAvoidance.findAvoidance(
                player,
                desired,
                targetPoint,
                probe,
                this.lastAvoidanceDirection
        );
        if (avoidance == null) {
            // 局部避障的所有候选都被挡时不要交还全零意图：那会让 controlInput 变成 null、旋转退回
            // "无请求"分支，玩家在目标旁边彻底失去控制（日志里的 avoidance none + intent 全零）。
            // 这里保留行为层的期望方向，交给 ElytraDirectionSolver 的安全预演与逃逸搜索去挑一个能飞的解。
            ElytraDebug.log(ElytraDebug.SLOT_AVOIDANCE, "avoidance", "none -> keep desired");
            return new FlightIntent(desired, desired.normalize(), rawIntent.directVelocity(), rawIntent.useFirework());
        }
        this.lastAvoidanceDirection = avoidance.normalize();
        ElytraDebug.log(ElytraDebug.SLOT_AVOIDANCE, "avoidance", vec(avoidance));
        // 同上：避障阶段保留烟花意愿，否则撞上障碍后没有推进，只能低速贴着障碍飞。
        return new FlightIntent(avoidance, this.lastAvoidanceDirection, rawIntent.directVelocity(), rawIntent.useFirework());
    }

    /** 调试用的向量格式化。 */
    private static String vec(Vec3 value) {
        return value == null ? "null"
                : "(" + ElytraDebug.fmt(value.x) + "," + ElytraDebug.fmt(value.y) + "," + ElytraDebug.fmt(value.z) + ")";
    }

    /**
     * 从 A* 原始路径中选取当前仍能直线到达的航点；优先使用前视点，被阻挡时回退到更近的节点。
     */
    private static Vec3 selectPathWaypoint(LocalPlayer player, PathPlan path) {
        List<Vec3> points = path.points();
        if (points.size() < 2) {
            return null;
        }

        Vec3 playerPos = player.position();
        int index = points.indexOf(path.nextPoint());
        if (index < 1) {
            index = 1;
        }
        for (int i = index; i >= 1; i--) {
            Vec3 candidate = points.get(i);
            if (playerPos.distanceToSqr(candidate) < 1.0E-4) {
                continue;
            }
            if (LocalFlightAvoidance.isSegmentClear(player, playerPos, candidate)) {
                // 从最远前视点向回退，优先保留尽量远的可达节点。
                return candidate;
            }
        }
        return null;
    }

    public void reset() {
        this.lastAvoidanceDirection = null;
        this.pathNavigator.stop();
    }

    public void stop() {
        this.lastAvoidanceDirection = null;
        this.pathNavigator.stop();
    }

    public void setDataSize(int dataSize) {
        this.pathNavigator.setDataSize(dataSize);
    }
}
