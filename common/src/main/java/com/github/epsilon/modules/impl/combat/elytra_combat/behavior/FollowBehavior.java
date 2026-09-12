package com.github.epsilon.modules.impl.combat.elytra_combat.behavior;

import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot;
import net.minecraft.world.phys.Vec3;

/**
 * 直接追击预测位置，目标落地时增加额外高度偏置。
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
        return planner.plan(bot.player(), raw, target.predictedPosition(), planConfig);
    }
}
