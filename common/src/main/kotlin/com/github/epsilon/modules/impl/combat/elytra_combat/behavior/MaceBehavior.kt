package com.github.epsilon.modules.impl.combat.elytra_combat.behavior

import com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatHitTracker
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDebug
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.LocalFlightAvoidance
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * 重锤空袭状态机。
 *
 * 状态流转为 NONE -> PULL_UP -> FOLLOW。攻击本身交给 KillAura，本状态机只负责占位与高度：
 * 拉升到目标上方配置高度后在目标上方跟随，等待 KillAura 出手。
 */
class MaceBehavior : ElytraCombatBehavior {

    private enum class State {
        /** 初始状态：根据当前高度差决定直接跟随还是先拉升。 */
        NONE,

        /** 持续拉升到目标上方安全高度。 */
        PULL_UP,

        /** 空中跟随或搜索地面目标的落点。 */
        FOLLOW,
    }

    private var state: State = State.NONE
    private var pullUpStartTick: Int = 0

    /**
     * 头顶受阻的连续 tick 数，用于抑制状态在边界上逐 tick 互抢。
     */
    private var headBlockedTicks: Int = 0

    /**
     * 已选中的地面落点；有效期间保持不变。
     */
    private var lastGroundCandidate: BlockPos? = null

    /**
     * 俯冲攻击后的改出剩余 tick 数。
     */
    private var recoveryTicks: Int = 0

    /**
     * 本次俯冲进入攻击距离后还剩几个下压 tick；归零说明这一趟打空了。
     */
    private var strikeTicks: Int = 0

    override fun reset() {
        this.state = State.NONE
        this.pullUpStartTick = 0
        this.headBlockedTicks = 0
        this.lastGroundCandidate = null
        this.recoveryTicks = 0
        this.strikeTicks = 0
    }

    override fun tick(
        bot: ElytraCombat,
        target: TargetSnapshot?,
        planner: FlightIntentPlanner,
        planConfig: FlightPlanConfig,
    ): FlightIntent {
        if (target == null) {
            reset()
            return FlightIntent.idle(bot.playerLook())
        }

        val tick = bot.player().tickCount
        val targetPos = if (bot.maceUsePredictor.value) target.predictedPosition else target.position
        val desired: Vec3

        when (this.state) {
            State.NONE -> {
                // 已经处于俯冲高度时无需再拉升，直接进入跟随段。
                if (bot.player().fallDistance > 4.0 &&
                    bot.player().y > target.entity.y + 4.0
                ) {
                    this.state = State.FOLLOW
                } else {
                    enterPullUp(tick)
                }
                desired = pullUp(bot, targetPos, target)
            }

            State.PULL_UP -> {
                if (this.pullUpStartTick <= 0) {
                    this.pullUpStartTick = tick
                    this.recoveryTicks = RECOVERY_TICKS
                }
                if (headBlocked(bot)) {
                    this.headBlockedTicks++
                } else {
                    this.headBlockedTicks = 0
                }
                if (this.headBlockedTicks >= PROBE_LATCH_TICKS) {
                    this.state = State.FOLLOW
                    desired = follow(bot, targetPos, target)
                } else {
                    // 到达高度与拉升目标使用同一配置值。
                    val followHeight = if (target.supported) {
                        bot.maceGroundHeight.value
                    } else {
                        bot.maceHeight.value
                    }
                    val aimY = target.entity.y + followHeight
                    val arrived = bot.player().y > target.entity.y &&
                            aimY - bot.player().y <= PULL_UP_ARRIVE_TOLERANCE
                    val mayFollow = arrived ||
                            (bot.player().y > target.entity.y &&
                                    tick - this.pullUpStartTick > bot.macePullUpTicks.value)
                    desired = if (mayFollow) {
                        this.state = State.FOLLOW
                        if (target.supported && !inAttackRange(bot, target)) {
                            groundApproach(bot, target)
                        } else {
                            followOrHold(bot, targetPos, target)
                        }
                    } else {
                        if (this.recoveryTicks > 0) {
                            this.recoveryTicks--
                            recover(bot, targetPos, target)
                        } else {
                            pullUp(bot, targetPos, target)
                        }
                    }
                }
            }

            State.FOLLOW -> {
                // 地面目标需要先找可攻击落点；空中目标直接追预测位置。
                desired = if (target.supported) {
                    strikeOrReapproach(bot, targetPos, target, tick)
                } else if (bot.player().fallDistance < 1.0E-6 && bot.lastFallDistance > 1.0E-6) {
                    enterPullUp(tick)
                    pullUp(bot, targetPos, target)
                } else {
                    follow(bot, targetPos, target)
                }
            }
        }

        bot.lastFallDistance = bot.player().fallDistance
        ElytraDebug.log(
            ElytraDebug.SLOT_MACE_STATE,
            "mace.state",
            this.state.name +
                    " head=" + this.headBlockedTicks +
                    " y=" + ElytraDebug.fmt(bot.player().y) +
                    " ty=" + ElytraDebug.fmt(targetPos.y) +
                    " fall=" + ElytraDebug.fmt(bot.player().fallDistance),
        )
        if (desired.lengthSqr() < 1.0E-8) {
            return FlightIntent.idle(bot.playerLook())
        }
        val raw = FlightIntent(
            desired,
            desired.normalize(),
            bot.controlMode.`is`(ElytraCombat.ControlMode.DirectVelocity),
            true,
        )
        return planner.plan(bot.player(), raw, target.predictedPosition, planConfig)
    }

    /**
     * 监听本地攻击事件进入拉升；滑翔时不能依赖猛击伤害包或 fallDistance 归零。
     */
    override fun onAttack(target: LivingEntity) {
        beginRecoveryPullUp("attack")
    }

    override fun onHit(hitType: CombatHitTracker.HitType) {
        if (hitType == CombatHitTracker.HitType.MACE) {
            beginRecoveryPullUp("mace hit")
        }
    }

    /**
     * 进入带改出阶段的拉升：`pullUpStartTick` 置 -1，下一 tick 由 [tick] 初始化，
     * 先沿当前航向爬升 [RECOVERY_TICKS] 个 tick 再回头瞄准目标上方高度。
     */
    private fun beginRecoveryPullUp(reason: String) {
        this.state = State.PULL_UP
        this.pullUpStartTick = -1
        ElytraDebug.log(ElytraDebug.SLOT_MACE_STATE, "mace.trigger", "$reason -> PULL_UP")
    }

    override fun stateName(): String {
        return this.state.name
    }

    private fun enterPullUp(tick: Int) {
        this.state = State.PULL_UP
        this.pullUpStartTick = tick
    }

    private fun pullUpDirection(bot: ElytraCombat, targetPos: Vec3, target: TargetSnapshot): Vec3 {
        val height = if (target.supported) bot.maceGroundHeight.value else bot.maceHeight.value
        val movement = Vec3(targetPos.x, targetPos.y + height, targetPos.z)
            .subtract(bot.player().position())
        return ensureMinimumLength(movement, 5.0)
    }

    private fun followDirection(bot: ElytraCombat, targetPos: Vec3, target: TargetSnapshot): Vec3 {
        var movement = targetPos.subtract(bot.player().position())
        if (bot.maceYBias.value != 0.0 && !target.supported) {
            movement = movement.add(0.0, bot.maceYBias.value, 0.0)
        }
        if (bot.maceSmoothFlight.value &&
            movement.y < 0.0 &&
            bot.player().y > target.entity.y + bot.maceFollowMinHeight.value
        ) {
            val horizontal = Math.max(0.001, movement.horizontalDistance())
            val downAngle = if (bot.maceAngleOptimize.value) bot.maceDownAngle.value else 30.5
            movement = Vec3(
                movement.x,
                -horizontal * Math.tan(Math.toRadians(downAngle)),
                movement.z,
            )
        }
        return ensureMinimumLength(movement, 5.0)
    }

    private fun headBlocked(bot: ElytraCombat): Boolean {
        val position = bot.player().position()
        val blocked = !LocalFlightAvoidance.isSegmentClear(bot.player(), position, position.add(0.0, 0.1, 0.0))
        if (ElytraDebug.enabled) {
            ElytraDebug.log(
                ElytraDebug.SLOT_PROBE,
                "probe.head",
                blocked.toString() + " run=" + (if (blocked) this.headBlockedTicks + 1 else 0),
            )
        }
        return blocked
    }

    /**
     * 进入攻击距离后保持配置高度，攻击由 KillAura 负责。
     */
    private fun followOrHold(bot: ElytraCombat, targetPos: Vec3, target: TargetSnapshot): Vec3 {
        return pullUp(bot, targetPos, target)
    }

    /**
     * 保持下压窗口等待 KillAura 出手；窗口结束仍未攻击则改出并重新拉升。
     */
    private fun strikeOrReapproach(bot: ElytraCombat, targetPos: Vec3, target: TargetSnapshot, tick: Int): Vec3 {
        if (inAttackRange(bot, target)) {
            if (this.strikeTicks <= 0) {
                this.strikeTicks = STRIKE_TICKS
                return groundApproach(bot, target)
            }
            if (--this.strikeTicks > 0) {
                return groundApproach(bot, target)
            }
            this.strikeTicks = 0
            beginRecoveryPullUp(missReason(bot, target))
            return recover(bot, targetPos, target)
        }

        if (Math.abs(bot.player().y - target.entity.y) < MISS_ALTITUDE_BAND) {
            this.strikeTicks = 0
            beginRecoveryPullUp(missReason(bot, target))
            return recover(bot, targetPos, target)
        }

        this.strikeTicks = 0
        return groundApproach(bot, target)
    }

    private fun inAttackRange(bot: ElytraCombat, target: TargetSnapshot): Boolean {
        return bot.player().isWithinEntityInteractionRange(target.entity.boundingBox, 0.5)
    }

    /**
     * 俯冲攻击后先沿当前水平航向爬升，再转向目标上方。
     */
    private fun recover(bot: ElytraCombat, targetPos: Vec3, target: TargetSnapshot): Vec3 {
        val player: LocalPlayer = bot.player()
        val velocity = player.deltaMovement
        val horizontal = Vec3(velocity.x, 0.0, velocity.z)
        if (horizontal.lengthSqr() < 1.0E-4) {
            return pullUp(bot, targetPos, target)
        }
        val height = if (target.supported) bot.maceGroundHeight.value else bot.maceHeight.value
        val climb = targetPos.y + height - player.y
        return horizontal.normalize().scale(RECOVERY_HORIZONTAL_SPEED)
            .add(0.0, Math.max(RECOVERY_CLIMB, climb), 0.0)
    }

    /**
     * 地面目标被遮挡时，在射线命中点周围搜索满足攻击距离、视线和碰撞空间的候选落点。
     */
    private fun groundApproach(bot: ElytraCombat, target: TargetSnapshot): Vec3 {
        val playerPos = bot.player().position()
        val targetEye = target.entity.eyePosition
        val hit = bot.player().level().clip(
            ClipContext(
                bot.player().eyePosition,
                targetEye,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                bot.player(),
            )
        )
        if (hit.type == HitResult.Type.MISS) {
            return ensureMinimumLength(targetEye.subtract(playerPos), 5.0)
        }

        val radius = Math.min(4, Math.max(1, Math.ceil(bot.maceEngageRange.value).toInt()))
        val hitPos = hit.blockPos
        val candidates = ArrayList<BlockPos>((radius * 2 + 1) * (radius * 2 + 1) * (radius * 2 + 1))
        for (x in hitPos.x - radius..hitPos.x + radius) {
            for (y in hitPos.y - radius..hitPos.y + radius) {
                for (z in hitPos.z - radius..hitPos.z + radius) {
                    candidates.add(BlockPos(x, y, z))
                }
            }
        }
        candidates.sortWith(Comparator.comparingDouble<BlockPos> { pos -> Vec3.atCenterOf(pos).distanceToSqr(targetEye) })

        // 有效落点保持不变，避免在相邻候选间频繁切换。
        val lastCandidate = this.lastGroundCandidate
        if (lastCandidate != null) {
            val center = Vec3.atCenterOf(lastCandidate)
            if (playerPos.distanceToSqr(center) < 2.25 || !isGroundCandidateUsable(bot, target, center)) {
                this.lastGroundCandidate = null
            } else {
                return ensureMinimumLength(center.subtract(playerPos), 5.0)
            }
        }

        for (candidatePos in candidates) {
            val center = Vec3.atCenterOf(candidatePos)
            if (!isGroundCandidateUsable(bot, target, center)) {
                continue
            }
            this.lastGroundCandidate = candidatePos
            return ensureMinimumLength(center.subtract(playerPos), 5.0)
        }
        this.lastGroundCandidate = null
        return ensureMinimumLength(targetEye.subtract(playerPos), 5.0)
    }

    /**
     * 落点是否仍然可用：满足攻击距离（或视线可达）且玩家碰撞箱能放下。
     */
    private fun isGroundCandidateUsable(bot: ElytraCombat, target: TargetSnapshot, center: Vec3): Boolean {
        if (!bot.player().isWithinEntityInteractionRange(target.entity.boundingBox, 0.5) &&
            center.distanceToSqr(target.entity.eyePosition) > bot.maceEngageRange.value * bot.maceEngageRange.value
        ) {
            return false
        }
        if (bot.player().level().clip(
                ClipContext(
                    bot.player().position(),
                    center,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    bot.player(),
                )
            ).type != HitResult.Type.MISS
        ) {
            return false
        }
        val box = bot.player().getDimensions(bot.player().pose).makeBoundingBox(center)
        return bot.player().level().noBlockCollision(bot.player(), box)
    }

    private fun pullUp(bot: ElytraCombat, targetPos: Vec3, target: TargetSnapshot): Vec3 {
        val result = pullUpDirection(bot, targetPos, target)
        ElytraDebug.log(ElytraDebug.SLOT_MACE_MANEUVER, "maneuver.pullup", vec(result))
        return result
    }

    private fun follow(bot: ElytraCombat, targetPos: Vec3, target: TargetSnapshot): Vec3 {
        val result = followDirection(bot, targetPos, target)
        ElytraDebug.log(ElytraDebug.SLOT_MACE_MANEUVER, "maneuver.follow", vec(result))
        return result
    }

    companion object {

        /**
         * 探测条件必须连续成立这么多 tick 才允许切换状态，避免拉升与下压互相抢转向。
         */
        private const val PROBE_LATCH_TICKS = 3

        /**
         * 进入攻击距离后等待 KillAura 出手的下压窗口（tick）。
         */
        private const val STRIKE_TICKS = 4

        /**
         * 未进入攻击距离时，判定俯冲打空的高度带（格）。
         */
        private const val MISS_ALTITUDE_BAND = 3.0

        /**
         * 拉升目标高度的到达容差（格）。
         */
        private const val PULL_UP_ARRIVE_TOLERANCE = 2.0

        /**
         * 俯冲攻击后的改出阶段：持续 tick 数、水平速度与抬升分量。
         */
        private const val RECOVERY_TICKS = 5
        private const val RECOVERY_HORIZONTAL_SPEED = 6.0
        private const val RECOVERY_CLIMB = 8.0

        private fun missReason(bot: ElytraCombat, target: TargetSnapshot): String {
            return "miss d=" + ElytraDebug.fmt(bot.player().distanceTo(target.entity).toDouble()) +
                    " dy=" + ElytraDebug.fmt(bot.player().y - target.entity.y)
        }

        private fun ensureMinimumLength(vector: Vec3, minimum: Double): Vec3 {
            if (vector.lengthSqr() < 1.0E-8) {
                return Vec3.ZERO
            }
            return if (vector.length() < minimum) vector.normalize().scale(minimum) else vector
        }

        private fun vec(value: Vec3?): String {
            return if (value == null) {
                "null"
            } else {
                "(" + ElytraDebug.fmt(value.x) + "," + ElytraDebug.fmt(value.y) + "," + ElytraDebug.fmt(value.z) + ")"
            }
        }

    }

}
