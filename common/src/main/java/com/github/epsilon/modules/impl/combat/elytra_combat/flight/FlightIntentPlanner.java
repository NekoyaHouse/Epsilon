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
 * <p>规划顺序固定为：直飞、局部扇区避障、后台单层 A*、停止。这样可以避免每个 tick
 * 都在客户端线程运行完整搜索。</p>
 */
public final class FlightIntentPlanner {

    private static final double LOCAL_PROBE_DISTANCE = 6.0;

    private final ElytraPathNavigator pathNavigator = new ElytraPathNavigator();

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
            return new FlightIntent(desired, desired.normalize(), rawIntent.directVelocity(), rawIntent.useFirework());
        }

        Vec3 avoidance = LocalFlightAvoidance.findAvoidance(
                player,
                desired,
                targetPoint,
                probe
        );
        if (avoidance != null) {
            return new FlightIntent(avoidance, avoidance.normalize(), rawIntent.directVelocity(), false);
        }

        if (!config.pathfinding()) {
            return FlightIntent.idle(player.getLookAngle());
        }

        PathPlan path = this.pathNavigator.getPath(
                player,
                targetPoint,
                new PathConfig(config.stopDistance(), config.searchRadius(), config.maxNodes())
        );
        Vec3 waypoint = selectWaypoint(player, path.points());
        if (waypoint == null) {
            return FlightIntent.idle(player.getLookAngle());
        }

        Vec3 waypointVelocity = waypoint.subtract(player.position());
        if (waypointVelocity.lengthSqr() < 1.0E-8) {
            return FlightIntent.idle(player.getLookAngle());
        }
        waypointVelocity = waypointVelocity.normalize().scale(desired.length());
        return new FlightIntent(waypointVelocity, waypointVelocity.normalize(), rawIntent.directVelocity(), false);
    }

    /**
     * 路径结果可能来自几 tick 前的位置。这里只选取当前碰撞箱仍能直线到达的近处
     * 航点，避免复用旧起点时把“切角”方向指向天花板或墙体。
     */
    private static Vec3 selectWaypoint(LocalPlayer player, List<Vec3> points) {
        if (points.size() < 2) {
            return null;
        }

        Vec3 playerPos = player.position();
        double maxDistanceSqr = LOCAL_PROBE_DISTANCE * LOCAL_PROBE_DISTANCE;
        Vec3 selected = null;
        for (int i = 1; i < points.size(); i++) {
            Vec3 point = points.get(i);
            double distanceSqr = playerPos.distanceToSqr(point);
            if (distanceSqr < 1.0E-4 || distanceSqr > maxDistanceSqr) {
                continue;
            }
            if (LocalFlightAvoidance.isSegmentClear(player, playerPos, point)) {
                selected = point;
            }
        }
        return selected;
    }

    public void reset() {
        this.pathNavigator.stop();
    }

    public void stop() {
        this.pathNavigator.stop();
    }

    public void setDataSize(int dataSize) {
        this.pathNavigator.setDataSize(dataSize);
    }
}
