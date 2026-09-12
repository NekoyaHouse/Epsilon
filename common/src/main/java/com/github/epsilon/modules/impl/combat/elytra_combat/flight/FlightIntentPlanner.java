package com.github.epsilon.modules.impl.combat.elytra_combat.flight;

import com.github.epsilon.modules.impl.combat.elytra_combat.path.ElytraPathNavigator;
import com.github.epsilon.modules.impl.combat.elytra_combat.path.PathConfig;
import com.github.epsilon.modules.impl.combat.elytra_combat.path.PathPlan;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

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
        if (path.points().size() < 2 || player.position().distanceToSqr(path.nextPoint()) < 1.0E-4) {
            return FlightIntent.idle(player.getLookAngle());
        }

        Vec3 waypointVelocity = path.nextPoint().subtract(player.position());
        if (waypointVelocity.lengthSqr() < 1.0E-8) {
            return FlightIntent.idle(player.getLookAngle());
        }
        waypointVelocity = waypointVelocity.normalize().scale(desired.length());
        return new FlightIntent(waypointVelocity, waypointVelocity.normalize(), rawIntent.directVelocity(), false);
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
