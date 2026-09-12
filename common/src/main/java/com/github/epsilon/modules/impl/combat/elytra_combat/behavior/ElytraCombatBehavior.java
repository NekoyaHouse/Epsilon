package com.github.epsilon.modules.impl.combat.elytra_combat.behavior;

import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat;
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatHitTracker;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner;
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig;
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot;
import net.minecraft.world.entity.LivingEntity;

/**
 * ElytraCombat 行为统一接口。行为只生成 FlightIntent，不直接修改玩家速度。
 */
public interface ElytraCombatBehavior {

    void reset();

    FlightIntent tick(
            ElytraCombat bot,
            TargetSnapshot target,
            FlightIntentPlanner planner,
            FlightPlanConfig planConfig
    );

    default void onAttack(LivingEntity target) {
    }

    default void onHit(CombatHitTracker.HitType hitType) {
    }

    default String stateName() {
        return "Idle";
    }
}
