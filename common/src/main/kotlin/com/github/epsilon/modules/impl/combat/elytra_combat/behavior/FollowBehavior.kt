package com.github.epsilon.modules.impl.combat.elytra_combat.behavior

import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot

/**
 * 直接追击目标预测位置。
 *
 * 目标落地或被方块支撑时把瞄准点抬高 `Follow Ground Height`，避免贴地飞行；
 * 行为只生成期望速度，实际直飞、绕障或 A* 由 [FlightIntentPlanner] 决定。
 */
class FollowBehavior : ElytraCombatBehavior {

    override fun reset() {
    }

    override fun tick(
        bot: ElytraCombat,
        target: TargetSnapshot?,
        planner: FlightIntentPlanner,
        planConfig: FlightPlanConfig,
    ): FlightIntent {
        if (target == null) {
            return FlightIntent.idle(bot.playerLook())
        }

        var targetPoint = target.predictedPosition
        if (target.onGround || target.supported) {
            // 地面目标保留高度差，防止贴地追击时撞到台阶或地形。
            targetPoint = targetPoint.add(0.0, bot.followGroundHeight, 0.0)
        }

        val desired = targetPoint.subtract(bot.player().position())
        if (desired.lengthSqr() < 1.0E-8) {
            return FlightIntent.idle(bot.playerLook())
        }
        val raw = FlightIntent(
            desired,
            desired.normalize(),
            bot.controlMode == (ElytraCombat.ControlMode.DirectVelocity),
            true,
        )
        // 交给规划器决定直飞、基础 A* 或无路径时的局部避障。
        return planner.plan(bot.player(), raw, target.predictedPosition, planConfig)
    }

}
