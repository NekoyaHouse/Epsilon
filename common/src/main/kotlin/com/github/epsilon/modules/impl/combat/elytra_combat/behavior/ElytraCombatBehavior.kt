package com.github.epsilon.modules.impl.combat.elytra_combat.behavior

import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatHitTracker
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot
import net.minecraft.world.entity.LivingEntity

/**
 * ElytraCombat 行为统一接口。行为只生成 FlightIntent，不直接修改玩家速度。
 */
interface ElytraCombatBehavior {

    /** 清理状态机的内部状态；切换模式、换目标或模块关闭时调用。 */
    fun reset()

    /** 生成当前 tick 的飞行意图。实现只能返回数据，不得直接修改玩家速度。 */
    fun tick(
        bot: ElytraCombat,
        target: TargetSnapshot?,
        planner: FlightIntentPlanner,
        planConfig: FlightPlanConfig,
    ): FlightIntent

    fun onAttack(target: LivingEntity) {
    }

    /** 消费命中反馈；hitType 已经由 CombatHitTracker 在客户端 tick 中过滤。 */
    fun onHit(hitType: CombatHitTracker.HitType) {
    }

    /** HUD 显示的简短状态名。 */
    fun stateName(): String {
        return "Idle"
    }

}
