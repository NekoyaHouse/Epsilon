package com.github.epsilon.modules.impl.combat.elytra_combat.behavior;

import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot;
import net.minecraft.world.phys.Vec3;

/**
 * 直接追击目标预测位置。
 *
 * <p>目标落地或被方块支撑时把瞄准点抬高 {@code Follow Ground Height}，避免贴地飞行；
 * 行为只生成期望速度，实际直飞、绕障或 A* 由 {@link FlightIntentPlanner} 决定。</p>
 */
public final class FollowBehavior implements ElytraCombatBehavior {

    @Override
    public void reset() {
    }

    @Override
    public FlightIntent tick(
            ElytraCombat bot,
            TargetSnapshot target,
            FlightIntentPlanner planner,
            FlightPlanConfig planConfig
    ) {
        if (target == null) {
            return FlightIntent.idle(bot.playerLook());
        }

        Vec3 targetPoint = target.predictedPosition();
        if (target.onGround() || target.supported()) {
            // 地面目标保留高度差，防止贴地追击时撞到台阶或地形。
            targetPoint = targetPoint.add(0.0, bot.followGroundHeight.getValue(), 0.0);
        }

        Vec3 desired = targetPoint.subtract(bot.player().position());
        if (desired.lengthSqr() < 1.0E-8) {
            return FlightIntent.idle(bot.playerLook());
        }
        FlightIntent raw = new FlightIntent(
                desired,
                desired.normalize(),
                bot.controlMode.is(ElytraCombat.ControlMode.DirectVelocity),
                true
        );
        // 交给规划器决定直飞、基础 A* 或无路径时的局部避障。
        return planner.plan(bot.player(), raw, target.predictedPosition(), planConfig);
    }
}
