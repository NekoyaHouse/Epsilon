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
public final class FlightIntentPlanner {

    private static final double LOCAL_PROBE_DISTANCE = 6.0;

    private final ElytraPathNavigator pathNavigator = new ElytraPathNavigator();
    /** 局部避障上一 tick 的方向，用于抑制左右两侧得分接近时来回切换。 */
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
            this.lastAvoidanceDirection = null;
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
            this.lastAvoidanceDirection = null;
            Vec3 waypointVelocity = waypoint.subtract(player.position());
            if (waypointVelocity.lengthSqr() >= 1.0E-8) {
                waypointVelocity = waypointVelocity.normalize().scale(desired.length());
                return new FlightIntent(waypointVelocity, waypointVelocity.normalize(), rawIntent.directVelocity(), false);
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
            return FlightIntent.idle(player.getLookAngle());
        }
        this.lastAvoidanceDirection = avoidance.normalize();
        return new FlightIntent(avoidance, this.lastAvoidanceDirection, rawIntent.directVelocity(), false);
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
