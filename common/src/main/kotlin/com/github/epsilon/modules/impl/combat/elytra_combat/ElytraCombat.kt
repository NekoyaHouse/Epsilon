package com.github.epsilon.modules.impl.combat.elytra_combat

import com.github.epsilon.Constants
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.*
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.elytra_combat.behavior.ElytraCombatBehavior
import com.github.epsilon.modules.impl.combat.elytra_combat.behavior.FollowBehavior
import com.github.epsilon.modules.impl.combat.elytra_combat.behavior.MaceBehavior
import com.github.epsilon.modules.impl.combat.elytra_combat.behavior.SpearBehavior
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatHitTracker
import com.github.epsilon.modules.impl.combat.elytra_combat.combat.CombatWeaponController
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.*
import com.github.epsilon.modules.impl.combat.elytra_combat.target.PredictorMode
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetMotionTracker
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFlightModes
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.group
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.math.toDegrees
import com.github.epsilon.utils.rotation.Rot2f
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.*
import java.util.function.Predicate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * 追人、重锤和长矛合并后的 ElytraCombat 模块。
 *
 * 模块层只负责目标生命周期、模式调度和事件边界；具体行为由 behavior 状态机生成，
 * 全部飞行结果统一收敛为 [FlightIntent]。
 */
object ElytraCombat : Module("Elytra Combat", Category.COMBAT) {

    enum class ControlMode {
        /** 通过 yaw/pitch 和 WASD 输入控制，兼容原版滑翔物理。 */
        Input,

        /** 在 FallFlyingMovementEvent 中直接覆盖当 tick 速度，作为低反模式。 */
        DirectVelocity,
    }

    private val sgGeneral: SettingGroup = settingGroup("General")
    private val sgTarget: SettingGroup = settingGroup("Target")
    private val sgFollow: SettingGroup = settingGroup("Follow")
    private val sgMace: SettingGroup = settingGroup("Mace")
    private val sgSpear: SettingGroup = settingGroup("Spear")
    private val sgFlight: SettingGroup = settingGroup("Flight")
    private val sgRender: SettingGroup = settingGroup("Render")

    var mode by enumSetting("Mode", ElytraCombatMode.Follow, onChanged = { newMode ->
        onModeChanged(newMode)
    }).group(sgGeneral)

    private var switchModeKey by keybindSetting("Switch Mode Key", -1).group(sgGeneral)

    /** 战斗决策调试输出开关。 */
    var debug by boolSetting("Debug", false, onChanged = { value ->
        ElytraDebug.enabled = value
        ElytraDebug.reset()
    }).group(sgGeneral)

    var targetRange by intSetting("Target Range", 80, 8..256, 1).group(sgGeneral)
    var dynamicTarget by boolSetting("Dynamic Target", false).group(sgGeneral)
    private var onlyWhenNoWASD by boolSetting("Only When No WASD", false).group(sgGeneral)
    private var autoControlElytra by boolSetting("Auto Control Elytra", true).group(sgGeneral)

    private var players by boolSetting("Players", true).group(sgTarget)
    private var mobs by boolSetting("Mobs", false).group(sgTarget)
    private var animals by boolSetting("Animals", false).group(sgTarget)
    private var villagers by boolSetting("Villagers", false).group(sgTarget)
    private var ambient by boolSetting("Ambient", false).group(sgTarget)
    private var water by boolSetting("Water", false).group(sgTarget)
    private var others by boolSetting("Others", false).group(sgTarget)
    private var invisible by boolSetting("Invisible", false).group(sgTarget)
    private var followFriend by boolSetting("Follow Friend", false).group(sgTarget)
    var predictorMode by enumSetting("Predictor", PredictorMode.NV).group(sgTarget)
    var predictTicks by intSetting("Predict Ticks", 2, 0..20, 1).group(sgTarget)
    var predictionHistory by intSetting("Prediction History", 5, 2..20, 1).group(sgTarget)

    var stopDistance by doubleSetting("Stop Distance", 6.0, 1.0..32.0, 0.5).group(sgFollow)
    var followGroundHeight by doubleSetting("Follow Ground Height", 2.0, 0.0..12.0, 0.5).group(sgFollow)
    private var pathfinding by boolSetting("Pathfinding", true).group(sgFollow)
    private var searchRadius by intSetting("Search Radius", 24, 6..64, 1, { pathfinding }).group(sgFollow)
    private var maxNodes by intSetting("Max Nodes", 1200, 100..6000, 100, { pathfinding }).group(sgFollow)
    private var dataSize by intSetting(
        "Data Size", 50, 25..100, 5, { pathfinding },
        onChanged = { size -> setPathDataSize(size) }).apply { applyWhenRelease() }.group(sgFollow)

    var maceEngageRange by doubleSetting("Mace Range", 10.0, 2.0..16.0, 0.5).group(sgMace)
    var maceHeight by doubleSetting("Mace Height", 10.0, 2.0..32.0, 0.5).group(sgMace)
    var maceGroundHeight by doubleSetting("Mace Ground Height", 10.0, 2.0..32.0, 0.5).group(sgMace)
    var maceFollowMinHeight by doubleSetting("Mace Min Follow Height", 1.5, 0.0..12.0, 0.5).group(sgMace)
    var maceYBias by doubleSetting("Mace Y Bias", 3.0, -10.0..10.0, 0.5).group(sgMace)
    var macePullUpTicks by intSetting("Mace Pull Up Max Ticks", 20, 1..100, 1).group(sgMace)
    var maceUsePredictor by boolSetting("Mace Use Predictor", true).group(sgMace)
    var maceAttackThreshold by doubleSetting("Mace Attack Threshold", 0.75, 0.1..1.0, 0.05).group(sgMace)
    var maceSwingHand by boolSetting("Mace Swing Hand", true).group(sgMace)
    var maceAntiShield by boolSetting("Mace Anti Shield", true).group(sgMace)
    var maceSmoothFlight by boolSetting("Mace Smooth Flight", true).group(sgMace)
    var maceAngleOptimize by boolSetting("Mace Angle Optimize", false).group(sgMace)
    var maceDownAngle by doubleSetting(
        "Mace Down Angle",
        30.5,
        5.0..80.0,
        0.5, { maceAngleOptimize }).group(sgMace)

    var spearEngageRange by doubleSetting("Spear Range", 8.0, 2.0..24.0, 0.5).group(sgSpear)
    var spearMinRange by doubleSetting("Spear Min Range", 1.0, 0.0..8.0, 0.5).group(sgSpear)
    var spearLungeStrength by doubleSetting("Spear Lunge Strength", 3.7, 0.5..15.0, 0.1).group(sgSpear)
    var spearPullOverTicks by intSetting("Spear Pull Over Ticks", 6, 1..40, 1).group(sgSpear)
    var spearUsePredictor by boolSetting("Spear Use Predictor", true).group(sgSpear)
    var spearAntiSpear by boolSetting("Spear Anti Spear", true).group(sgSpear)
    var spearAntiSpearExtra by doubleSetting(
        "Spear Anti Extra Distance",
        1.0,
        0.0..8.0,
        0.5, { spearAntiSpear }).group(sgSpear)
    private var logSpearHit by boolSetting("Log Spear Hit", false).group(sgSpear)

    var controlMode by enumSetting("Control Mode", ControlMode.Input).group(sgFlight)
    var maxFlightSpeed by doubleSetting("Max Flight Speed", 2.0, 0.5..10.0, 0.1).group(sgFlight)

    /** 每 tick 最大转向角度（度）；360 表示不限速。 */
    var maxTurnSpeed by doubleSetting("Max Turn Speed", 20.0, 5.0..360.0, 5.0).group(sgFlight)
    private var allowFirework by boolSetting("Allow Firework", true).group(sgFlight)

    private var render by boolSetting("Render Vector", true).group(sgRender)
    private var renderColor by colorSetting(
        "Render Color",
        Color(255, 133, 161, 210), true, { render }).group(sgRender)
    private var renderWidth by doubleSetting("Render Width", 2.0, 0.5..8.0, 0.5, { render }).group(sgRender)

    /** 模式到状态机的固定映射；切换模式只替换引用，不重建行为对象。 */
    private val behaviors: EnumMap<ElytraCombatMode, ElytraCombatBehavior> =
        EnumMap<ElytraCombatMode, ElytraCombatBehavior>(ElytraCombatMode::class.java).apply {
            put(ElytraCombatMode.Follow, FollowBehavior())
            put(ElytraCombatMode.Mace, MaceBehavior())
            put(ElytraCombatMode.Spear, SpearBehavior())
        }

    /** 目标轨迹预测、命中包解析和飞行规划三个纯数据组件。 */
    private val motionTracker = TargetMotionTracker()
    private val hitTracker = CombatHitTracker()
    private val flightPlanner = FlightIntentPlanner()

    private var currentBehavior: ElytraCombatBehavior = behaviors.getValue(ElytraCombatMode.Follow)
    private var target: LivingEntity? = null
    private var controlInput: ElytraCombatInput? = null
    private var latestIntent: FlightIntent = FlightIntent.idle(Vec3.ZERO)

    /** 记录模块启用前的 ElytraFly 状态，关闭时只恢复被本模块改动过的部分。 */
    private var capturedElytraEnabled: Boolean = false
    private var capturedElytraMode: ElytraFlightModes? = null
    private var changedElytraControl: Boolean = false

    var lastFallDistance: Double = 0.0

    init {
        // tick 顺序：刷新目标 → 更新预测快照 → 消费命中 → 行为状态机 → 限速 → 转控制输入。
        nonNullHandler<PlayerTickEvent.Pre>(EventPriority.HIGH) { onPlayerTick() }

        // 行为规划出的输入覆盖真实键盘，DirectVelocity 模式仍保留同一套 WASD 反馈。
        handler<KeyboardInputEvent>(EventPriority.HIGH) { onKeyboardInput(it) }

        // 事件发生在速度写入前，按托管旋转复算滑翔速度，使本地与服务端朝向一致。
        nonNullHandler<FallFlyingMovementEvent>(EventPriority.HIGHEST) { onFallFlyingMovement(it) }

        // 只记录本地玩家对当前目标发起的攻击，行为状态机据此进入等待/脱战阶段。
        nonNullHandler<AttackEntityEvent> { onAttackEntity(it) }

        // 伤害包用于重锤确认，实体事件用于 kinetic 命中；传送包只清除旧轨迹。
        handler<PacketEvent.Receive> { onPacketReceive(it) }

        handler<KeyPressEvent> { onKeyPress(it) }
        handler<MousePressEvent> { onMousePress(it) }

        // 渲染 4 格期望方向，便于区分直飞、A* 航点和避障意图。
        handler<Render3DEvent> { onRender3D() }
    }

    override fun onEnable() {
        // 自动接管时先保存原 ElytraFly 状态，避免关闭模块后把用户设置覆盖掉。
        this.currentBehavior = this.behaviors.getValue(this.mode)
        this.capturedElytraEnabled = ElytraFly.isEnabled
        this.capturedElytraMode = ElytraFly.mode
        this.changedElytraControl = false
        if (this.autoControlElytra) {
            if (!ElytraFly.isEnabled) {
                ElytraFly.isEnabled = true
                this.changedElytraControl = true
            }
            if (ElytraFly.mode != ElytraFlightModes.Control) {
                ElytraFly.mode = ElytraFlightModes.Control
                this.changedElytraControl = true
            }
        }
        this.flightPlanner.setDataSize(this.dataSize)
        resetState()
    }

    override fun onDisable() {
        // 释放长矛蓄力、停止后台寻路线程，并恢复接管前的 ElytraFly 状态。
        CombatWeaponController.stopSpearUse()
        this.flightPlanner.stop()
        this.controlInput = null
        this.target = null
        this.latestIntent = FlightIntent.idle(Vec3.ZERO)
        if (this.changedElytraControl) {
            val capturedMode = this.capturedElytraMode
            if (capturedMode != null && ElytraFly.mode != capturedMode) {
                ElytraFly.mode = capturedMode
            }
            if (!this.capturedElytraEnabled && ElytraFly.isEnabled) {
                ElytraFly.isEnabled = false
            }
        }
    }

    fun getControlInput(): ElytraCombatInput? {
        return this.controlInput
    }

    /** 本 tick 未截断的意图抬升量，用于飞控的头顶碰撞探测。 */
    fun getCombatIntendedClimb(): Double {
        return this.latestIntent.desiredVelocity.y
    }

    fun isControllingCombat(): Boolean {
        return isEnabled && this.currentBehavior != null
    }

    /** 已选中目标并产出飞行输入时返回 true；待机时不接管 ElytraFly 的烟花决策。 */
    fun isDrivingFlight(): Boolean {
        return isEnabled && this.controlInput != null
    }

    /** 当前是否允许使用烟花；时机完全交给 ElytraFly 的烟花逻辑（Use Fireworks / Boost Delay）。 */
    fun shouldUseFirework(): Boolean {
        return this.allowFirework && this.latestIntent.useFirework
    }

    fun player(): LocalPlayer {
        return this.mc.player ?: throw IllegalStateException("ElytraCombat requires a local player")
    }

    fun playerLook(): Vec3 {
        return this.mc.player?.lookAngle ?: Vec3.ZERO
    }

    override fun getInfo(): String? {
        val state = this.currentBehavior.stateName()
        val target = this.target
        return if (target == null) {
            this.mode.toString() + " " + state
        } else {
            target.name.string + " " + state
        }
    }

    private fun onPlayerTick() {
        val player = this.mc.player ?: run {
            clearControl()
            return
        }
        if (!canControlFlight()) {
            clearControl()
            return
        }
        if (this.onlyWhenNoWASD && hasMovementControl()) {
            clearControl()
            return
        }

        refreshTarget()
        val target = this.target ?: run {
            clearControl()
            return
        }

        val snapshot = this.motionTracker.update(
            target,
            player.tickCount,
            this.predictorMode,
            this.predictTicks,
            this.predictionHistory,
        )
        ElytraDebug.log(
            ElytraDebug.SLOT_TARGET,
            "target",
            target.name.string +
                    " action=" + snapshot.action +
                    " support=" + snapshot.supported +
                    " spear=" + snapshot.usingSpear +
                    " d=" + ElytraDebug.fmt(player.distanceTo(target).toDouble()),
        )
        this.hitTracker.setContext(player, target)
        processHits()

        val planConfig = FlightPlanConfig(
            this.stopDistance,
            this.searchRadius,
            this.maxNodes,
            this.pathfinding,
        )
        val rawIntent = this.currentBehavior.tick(this, snapshot, this.flightPlanner, planConfig)
        this.latestIntent = stabilizeIntent(clampIntent(rawIntent))
        this.controlInput = toInput(player, this.latestIntent)
        this.lastFallDistance = player.fallDistance

        val controlInput = this.controlInput
        ElytraDebug.log(
            ElytraDebug.SLOT_INTENT,
            "intent",
            "raw=" + fmt(rawIntent.desiredVelocity) +
                    " out=" + fmt(this.latestIntent.desiredVelocity) +
                    " move=" + (controlInput != null && controlInput.hasMoveInput()),
        )
    }

    private fun onKeyboardInput(event: KeyboardInputEvent) {
        val input = this.controlInput ?: return
        if (!canControlFlight()) return
        event.forward = (input.forwardImpulse())
        event.strafe = (input.strafeImpulse())
        event.isJump = (input.jump)
        event.isSneak = (input.sneak)
        event.isSprint = (false)
    }

    private fun onFallFlyingMovement(event: FallFlyingMovementEvent) {
        val input = this.controlInput ?: return
        if (!isEnabled) {
            return
        }

        if (input.hasDirectVelocity()) {
            val directVelocity = input.directVelocity ?: return
            val player = this.mc.player ?: return
            if (!LocalFlightAvoidance.isSegmentClear(
                    player,
                    player.position(),
                    player.position().add(directVelocity),
                )
            ) {
                // 直接速度不安全时保留原版滑翔结果，由 solveSafe 选择的旋转接管本 tick。
                return
            }
            event.movement = (directVelocity)
            return
        }

        val player = this.mc.player ?: return
        val movement = player.deltaMovement
        val corrected = ElytraMotionPredictor.nextFallFlyingMovement(
            movement,
            input.yaw,
            input.pitch,
            ElytraDirectionSolver.effectiveGravity(player),
        )
        ElytraDebug.log(
            ElytraDebug.SLOT_ROTATION,
            "rotation.physics",
            "real=(" + ElytraDebug.fmt(player.yRot.toDouble()) + "," + ElytraDebug.fmt(player.xRot.toDouble()) + ")" +
                    " req=(" + ElytraDebug.fmt(input.yaw.toDouble()) + "," + ElytraDebug.fmt(input.pitch.toDouble()) + ")" +
                    " vanillaVy=" + ElytraDebug.fmt(event.movement.y) +
                    " fixedVy=" + ElytraDebug.fmt(corrected.y),
        )
        event.movement = (corrected)
    }

    private fun onAttackEntity(event: AttackEntityEvent) {
        if (event.player !== this.mc.player) {
            return
        }
        val target = this.target ?: return
        if (event.entity === target) {
            this.hitTracker.markAttack(target, this.player().tickCount)
            this.currentBehavior.onAttack(target)
        }
    }

    private fun onPacketReceive(event: PacketEvent.Receive) {
        when (val packet = event.packet) {
            is ClientboundDamageEventPacket -> this.hitTracker.onDamagePacket(packet)
            is ClientboundEntityEventPacket -> this.hitTracker.onEntityStatusPacket(packet)
            is ClientboundTeleportEntityPacket -> this.motionTracker.noteTeleport(packet.id())
            is ClientboundEntityPositionSyncPacket -> this.motionTracker.noteTeleport(packet.id())
        }
    }

    private fun onKeyPress(event: KeyPressEvent) {
        if (event.action != InputConstants.PRESS) return
        if (this.switchModeKey == event.getKey()) {
            cycleMode()
        }
    }

    private fun onMousePress(event: MousePressEvent) {
        if (event.action != InputConstants.PRESS) return
        if (this.switchModeKey == KeybindUtils.encodeMouseButton(event.button)) {
            cycleMode()
        }
    }

    private fun onRender3D() {
        if (!this.render || this.controlInput == null) return
        val player = this.mc.player ?: return
        val velocity = this.latestIntent.desiredVelocity
        if (velocity.lengthSqr() < 1.0E-8) return

        val start = player.position().add(0.0, player.bbHeight * 0.5, 0.0)
        Render3DScheduler.addLine(
            start,
            start.add(velocity.normalize().scale(4.0)),
            this.renderColor,
            this.renderWidth.toFloat(),
        )
    }

    private fun refreshTarget() {
        // 目标死亡、换维度或超出追击范围时立即失效；动态目标模式每 tick 重新选择。
        val player = this.mc.player ?: return
        val target = this.target
        if (target != null &&
            (!target.isAlive ||
                    target.level() !== this.mc.level ||
                    player.distanceTo(target) > this.targetRange)
        ) {
            this.target = null
        }
        if (this.target == null || this.dynamicTarget) {
            this.target = TargetManager.acquirePrimary(
                TargetRequest.of(
                    this.targetRange.toDouble(),
                    360.0f,
                    this.players,
                    this.mobs,
                    this.animals,
                    this.villagers,
                    this.ambient,
                    this.water,
                    this.others,
                    this.invisible,
                    this.followFriend,
                    Predicate { living -> living !== this.mc.player },
                    1,
                )
            )
        }
    }

    private fun clampIntent(intent: FlightIntent): FlightIntent {
        // 行为层可以返回任意长度向量，统一限制到 Max Flight Speed 后再交给飞控。
        val velocity = intent.desiredVelocity
        val length = velocity.length()
        if (length < 1.0E-8) {
            return FlightIntent(Vec3.ZERO, intent.lookDirection, intent.directVelocity, false)
        }
        val clamped = if (length > this.maxFlightSpeed) {
            velocity.scale(this.maxFlightSpeed / length)
        } else {
            velocity
        }
        return FlightIntent(clamped, clamped.normalize(), intent.directVelocity, intent.useFirework)
    }

    /**
     * 用死区和 `Max Turn Speed` 限制飞行意图的方向变化。
     */
    private fun stabilizeIntent(next: FlightIntent): FlightIntent {
        val previous = this.latestIntent.desiredVelocity
        val desired = next.desiredVelocity
        if (previous.lengthSqr() < 1.0E-8 || desired.lengthSqr() < 1.0E-8) {
            return next
        }

        // 长矛瞄准跳过转向限速以跟随目标，仍保留微小变化的死区。
        val limit = this.maxTurnSpeed
        val unlimited = limit >= 360.0 || this.mode == (ElytraCombatMode.Spear)

        val previousDirection = previous.normalize()
        val nextDirection = desired.normalize()
        val previousYaw = (atan2(-previousDirection.x, previousDirection.z)).toDegrees().toFloat()
        val previousPitch =
            -(atan2(previousDirection.y, maxOf(1.0E-4, previousDirection.horizontalDistance()))).toDegrees().toFloat()

        // 目标方向接近垂直时 yaw 没有意义，保持上一 tick 的 yaw，只允许俯仰变化。
        val yaw: Float
        if (nextDirection.horizontalDistance() < 1.0E-3) {
            yaw = previousYaw
        } else {
            val nextYaw = (atan2(-nextDirection.x, nextDirection.z)).toDegrees().toFloat()
            yaw = if (unlimited) {
                nextYaw
            } else {
                previousYaw + Mth.clamp(
                    Mth.wrapDegrees(nextYaw - previousYaw),
                    (-limit).toFloat(),
                    limit.toFloat(),
                )
            }
        }

        val nextPitch =
            -(atan2(nextDirection.y, maxOf(1.0E-4, nextDirection.horizontalDistance()))).toDegrees().toFloat()
        val pitchDelta = nextPitch - previousPitch
        if (abs(Mth.wrapDegrees(yaw - previousYaw).toDouble()) < TURN_DEAD_ZONE_DEGREES &&
            abs(pitchDelta.toDouble()) < TURN_DEAD_ZONE_DEGREES
        ) {
            return FlightIntent(
                previousDirection.scale(desired.length()),
                previousDirection,
                next.directVelocity,
                next.useFirework,
            )
        }

        val pitch = if (unlimited) {
            nextPitch
        } else {
            previousPitch + Mth.clamp(pitchDelta, (-limit).toFloat(), limit.toFloat())
        }
        val direction = Entity.calculateViewVector(pitch, yaw).normalize()
        return FlightIntent(
            direction.scale(desired.length()),
            direction,
            next.directVelocity,
            next.useFirework,
        )
    }

    private fun toInput(player: LocalPlayer, intent: FlightIntent): ElytraCombatInput? {
        // 输入模式先反解下一 tick 速度对齐的旋转，再按 yaw 偏差映射到 8 个 WASD 扇区。
        val velocity = intent.desiredVelocity
        if (velocity.lengthSqr() < 1.0E-8) {
            return null
        }

        val pitchTolerance = if (this.mode == (ElytraCombatMode.Spear)) {
            SPEAR_PITCH_DEVIATION_LIMIT
        } else {
            ElytraDirectionSolver.DEFAULT_PITCH_DEVIATION_LIMIT
        }
        val rotations: Rot2f = ElytraDirectionSolver.solveSafe(player, velocity, pitchTolerance)
        ElytraDirectionSolver.rememberPitch(rotations.pitch)
        val yawDelta = Mth.wrapDegrees(rotations.yaw - player.yRot)
        val direction = directionInput(yawDelta)
        val direct = if (this.controlMode == (ControlMode.DirectVelocity)) velocity else null
        ElytraDebug.log(
            ElytraDebug.SLOT_INTENT,
            "input",
            describe(direction) +
                    " dYaw=" + ElytraDebug.fmt(yawDelta.toDouble()) +
                    " jump=" + (velocity.y > 0.0) +
                    " sneak=" + (velocity.y < 0.0),
        )
        return ElytraCombatInput(
            direction.forward,
            direction.back,
            direction.left,
            direction.right,
            velocity.y > 0.0,
            velocity.y < 0.0,
            rotations.yaw,
            rotations.pitch,
            direct,
        )
    }

    private fun directionInput(yawDelta: Float): DirectionInput {
        // 每个扇区覆盖 45 度，命中边界时向相邻方向同时按键以平滑转向。
        val sector = ((yawDelta / 45.0f).roundToInt()).mod(8)
        return when (sector) {
            0 -> DirectionInput(true, false, false, false)
            1 -> DirectionInput(true, false, false, true)
            2 -> DirectionInput(false, false, false, true)
            3 -> DirectionInput(false, true, false, true)
            4 -> DirectionInput(false, true, false, false)
            5 -> DirectionInput(false, true, true, false)
            6 -> DirectionInput(false, false, true, false)
            else -> DirectionInput(true, false, true, false)
        }
    }

    private fun processHits() {
        // 网络线程只入队，命中反馈在客户端 tick 中按顺序消费。
        var hit = this.hitTracker.poll()
        while (hit != null) {
            this.currentBehavior.onHit(hit)
            if (hit == CombatHitTracker.HitType.SPEAR && this.logSpearHit) {
                Constants.LOGGER.info("ElytraCombat spear kinetic hit registered")
            }
            hit = this.hitTracker.poll()
        }
    }

    private fun canControlFlight(): Boolean {
        // ElytraCombat 只接管 Control 模式；其他 ElytraFly 模式保持用户手动控制。
        return ElytraFly.isEnabled && ElytraFly.mode == ElytraFlightModes.Control
    }

    private fun hasMovementControl(): Boolean {
        return this.mc.options.keyUp.isDown ||
                this.mc.options.keyDown.isDown ||
                this.mc.options.keyLeft.isDown ||
                this.mc.options.keyRight.isDown ||
                this.mc.options.keyJump.isDown ||
                this.mc.options.keyShift.isDown
    }

    private fun cycleMode() {
        // 切换模式时立即重置行为状态和路径请求，避免沿用上个模式的速度/状态。
        val modes = ElytraCombatMode.entries
        val next = (this.mode.ordinal + 1).mod(modes.size)
        this.mode = modes[next]
        resetState()
    }

    private fun onModeChanged(newMode: ElytraCombatMode) {
        this.currentBehavior = this.behaviors.getValue(newMode)
        resetState()
    }

    private fun resetState() {
        // 模式切换或状态重置时同时清空轨迹、命中队列、寻路结果和当前输入。
        this.motionTracker.reset()
        this.hitTracker.clear()
        this.flightPlanner.reset()
        ElytraDirectionSolver.resetEscape()
        this.controlInput = null
        this.latestIntent = FlightIntent.idle(Vec3.ZERO)
        this.currentBehavior.reset()
    }

    private fun clearControl() {
        // 失去目标或无法接管飞行时回到无输入状态，不保留过期路径。
        this.controlInput = null
        this.latestIntent = FlightIntent.idle(Vec3.ZERO)
        this.target = null
        this.flightPlanner.reset()
        ElytraDirectionSolver.resetEscape()
        this.currentBehavior.reset()
    }

    private fun setPathDataSize(size: Int) {
        // Data Size 修改后由导航器重建体素窗口；需要 applyWhenRelease 防止拖动时反复重建。
        this.flightPlanner.setDataSize(size)
    }

    private data class DirectionInput(val forward: Boolean, val back: Boolean, val left: Boolean, val right: Boolean)

    private const val TURN_DEAD_ZONE_DEGREES = 3.0f
    private const val SPEAR_PITCH_DEVIATION_LIMIT = 3.0f

    private fun fmt(value: Vec3?): String {
        return if (value == null) {
            "null"
        } else {
            "(" + ElytraDebug.fmt(value.x) + "," + ElytraDebug.fmt(value.y) + "," + ElytraDebug.fmt(value.z) + ")"
        }
    }

    private fun describe(input: DirectionInput): String {
        val builder = StringBuilder()
        if (input.forward) builder.append('W')
        if (input.back) builder.append('S')
        if (input.left) builder.append('A')
        if (input.right) builder.append('D')
        return if (builder.isEmpty()) "-" else builder.toString()
    }

}
