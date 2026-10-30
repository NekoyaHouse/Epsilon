package com.github.epsilon.modules.impl.combat.elytra_combat

import com.github.epsilon.Constants
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.AttackEntityEvent
import com.github.epsilon.events.impl.FallFlyingMovementEvent
import com.github.epsilon.events.impl.KeyPressEvent
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.MousePressEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
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
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDebug
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDirectionSolver
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraMotionPredictor
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntent
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightIntentPlanner
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.FlightPlanConfig
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.LocalFlightAvoidance
import com.github.epsilon.modules.impl.combat.elytra_combat.target.PredictorMode
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetMotionTracker
import com.github.epsilon.modules.impl.combat.elytra_combat.target.TargetSnapshot
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFlightModes
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.settings.impl.KeybindSetting
import com.github.epsilon.utils.client.KeybindUtils
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
import java.util.EnumMap
import java.util.function.Predicate

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

    val mode: EnumSetting<ElytraCombatMode> by enumSetting("Mode", ElytraCombatMode.Follow) { newMode ->
        onModeChanged(newMode)
    }.group(sgGeneral)

    private val switchModeKey by keybindSetting("Switch Mode Key", -1).group(sgGeneral)

    /** 战斗决策调试输出开关。 */
    val debug by boolSetting("Debug", false) { value ->
        ElytraDebug.enabled = value
        ElytraDebug.reset()
    }.group(sgGeneral)

    val targetRange by intSetting("Target Range", 80, 8, 256, 1).group(sgGeneral)
    val dynamicTarget by boolSetting("Dynamic Target", false).group(sgGeneral)
    private val onlyWhenNoWASD by boolSetting("Only When No WASD", false).group(sgGeneral)
    private val autoControlElytra by boolSetting("Auto Control Elytra", true).group(sgGeneral)

    private val players by boolSetting("Players", true).group(sgTarget)
    private val mobs by boolSetting("Mobs", false).group(sgTarget)
    private val animals by boolSetting("Animals", false).group(sgTarget)
    private val villagers by boolSetting("Villagers", false).group(sgTarget)
    private val ambient by boolSetting("Ambient", false).group(sgTarget)
    private val water by boolSetting("Water", false).group(sgTarget)
    private val others by boolSetting("Others", false).group(sgTarget)
    private val invisible by boolSetting("Invisible", false).group(sgTarget)
    private val followFriend by boolSetting("Follow Friend", false).group(sgTarget)
    val predictorMode: EnumSetting<PredictorMode> by enumSetting("Predictor", PredictorMode.NV).group(sgTarget)
    val predictTicks by intSetting("Predict Ticks", 2, 0, 20, 1).group(sgTarget)
    val predictionHistory by intSetting("Prediction History", 5, 2, 20, 1).group(sgTarget)

    val stopDistance by doubleSetting("Stop Distance", 6.0, 1.0, 32.0, 0.5).group(sgFollow)
    val followGroundHeight by doubleSetting("Follow Ground Height", 2.0, 0.0, 12.0, 0.5).group(sgFollow)
    private val pathfinding by boolSetting("Pathfinding", true).group(sgFollow)
    private val searchRadius by intSetting("Search Radius", 24, 6, 64, 1, Setting.Dependency { pathfinding }).group(sgFollow)
    private val maxNodes by intSetting("Max Nodes", 1200, 100, 6000, 100, Setting.Dependency { pathfinding }).group(sgFollow)
    private val dataSize by intSetting(
        "Data Size", 50, 25, 100, 5,
        Setting.Dependency { pathfinding },
    ) { size -> setPathDataSize(size) }.applyWhenRelease<IntSetting>().group<IntSetting>(sgFollow)

    val maceEngageRange by doubleSetting("Mace Range", 10.0, 2.0, 16.0, 0.5).group(sgMace)
    val maceHeight by doubleSetting("Mace Height", 10.0, 2.0, 32.0, 0.5).group(sgMace)
    val maceGroundHeight by doubleSetting("Mace Ground Height", 10.0, 2.0, 32.0, 0.5).group(sgMace)
    val maceFollowMinHeight by doubleSetting("Mace Min Follow Height", 1.5, 0.0, 12.0, 0.5).group(sgMace)
    val maceYBias by doubleSetting("Mace Y Bias", 3.0, -10.0, 10.0, 0.5).group(sgMace)
    val macePullUpTicks by intSetting("Mace Pull Up Max Ticks", 20, 1, 100, 1).group(sgMace)
    val maceUsePredictor by boolSetting("Mace Use Predictor", true).group(sgMace)
    val maceAttackThreshold by doubleSetting("Mace Attack Threshold", 0.75, 0.1, 1.0, 0.05).group(sgMace)
    val maceSwingHand by boolSetting("Mace Swing Hand", true).group(sgMace)
    val maceAntiShield by boolSetting("Mace Anti Shield", true).group(sgMace)
    val maceSmoothFlight by boolSetting("Mace Smooth Flight", true).group(sgMace)
    val maceAngleOptimize by boolSetting("Mace Angle Optimize", false).group(sgMace)
    val maceDownAngle by doubleSetting("Mace Down Angle", 30.5, 5.0, 80.0, 0.5, Setting.Dependency { maceAngleOptimize }).group(sgMace)

    val spearEngageRange by doubleSetting("Spear Range", 8.0, 2.0, 24.0, 0.5).group(sgSpear)
    val spearMinRange by doubleSetting("Spear Min Range", 1.0, 0.0, 8.0, 0.5).group(sgSpear)
    val spearLungeStrength by doubleSetting("Spear Lunge Strength", 3.7, 0.5, 15.0, 0.1).group(sgSpear)
    val spearPullOverTicks by intSetting("Spear Pull Over Ticks", 6, 1, 40, 1).group(sgSpear)
    val spearUsePredictor by boolSetting("Spear Use Predictor", true).group(sgSpear)
    val spearAntiSpear by boolSetting("Spear Anti Spear", true).group(sgSpear)
    val spearAntiSpearExtra by doubleSetting("Spear Anti Extra Distance", 1.0, 0.0, 8.0, 0.5, Setting.Dependency { spearAntiSpear }).group(sgSpear)
    private val logSpearHit by boolSetting("Log Spear Hit", false).group(sgSpear)

    val controlMode: EnumSetting<ControlMode> by enumSetting("Control Mode", ControlMode.Input).group(sgFlight)
    val maxFlightSpeed by doubleSetting("Max Flight Speed", 2.0, 0.5, 10.0, 0.1).group(sgFlight)

    /** 每 tick 最大转向角度（度）；360 表示不限速。 */
    val maxTurnSpeed by doubleSetting("Max Turn Speed", 20.0, 5.0, 360.0, 5.0).group(sgFlight)
    private val allowFirework by boolSetting("Allow Firework", true).group(sgFlight)

    private val render by boolSetting("Render Vector", true).group(sgRender)
    private val renderColor by colorSetting("Render Color", Color(255, 133, 161, 210), Setting.Dependency { render }).group(sgRender)
    private val renderWidth by doubleSetting("Render Width", 2.0, 0.5, 8.0, 0.5, Setting.Dependency { render }).group(sgRender)

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
        nonNullListener<PlayerTickEvent.Pre>(EventPriority.HIGH) { onPlayerTick() }

        // 行为规划出的输入覆盖真实键盘，DirectVelocity 模式仍保留同一套 WASD 反馈。
        handler<KeyboardInputEvent>(EventPriority.HIGH) { onKeyboardInput(it) }

        // 事件发生在速度写入前，按托管旋转复算滑翔速度，使本地与服务端朝向一致。
        nonNullListener<FallFlyingMovementEvent>(EventPriority.HIGHEST) { onFallFlyingMovement(it) }

        // 只记录本地玩家对当前目标发起的攻击，行为状态机据此进入等待/脱战阶段。
        nonNullListener<AttackEntityEvent> { onAttackEntity(it) }

        // 伤害包用于重锤确认，实体事件用于 kinetic 命中；传送包只清除旧轨迹。
        handler<PacketEvent.Receive> { onPacketReceive(it) }

        handler<KeyPressEvent> { onKeyPress(it) }
        handler<MousePressEvent> { onMousePress(it) }

        // 渲染 4 格期望方向，便于区分直飞、A* 航点和避障意图。
        handler<Render3DEvent> { onRender3D() }
    }

    override fun onEnable() {
        // 自动接管时先保存原 ElytraFly 状态，避免关闭模块后把用户设置覆盖掉。
        this.currentBehavior = this.behaviors.getValue(this.mode.value)
        this.capturedElytraEnabled = ElytraFly.isEnabled
        this.capturedElytraMode = ElytraFly.mode
        this.changedElytraControl = false
        if (this.autoControlElytra.value) {
            if (!ElytraFly.isEnabled) {
                ElytraFly.isEnabled = true
                this.changedElytraControl = true
            }
            if (ElytraFly.mode != ElytraFlightModes.Control) {
                ElytraFly.mode = ElytraFlightModes.Control
                this.changedElytraControl = true
            }
        }
        this.flightPlanner.setDataSize(this.dataSize.value)
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
        return this.allowFirework.value && this.latestIntent.useFirework
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
            this.mode.value.toString() + " " + state
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
        if (this.onlyWhenNoWASD.value && hasMovementControl()) {
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
            this.predictorMode.value,
            this.predictTicks.value,
            this.predictionHistory.value,
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
            this.stopDistance.value,
            this.searchRadius.value,
            this.maxNodes.value,
            this.pathfinding.value,
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
        event.setForward(input.forwardImpulse())
        event.setStrafe(input.strafeImpulse())
        event.setJump(input.jump)
        event.setSneak(input.sneak)
        event.setSprint(false)
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
            event.setMovement(directVelocity)
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
                    " vanillaVy=" + ElytraDebug.fmt(event.getMovement().y) +
                    " fixedVy=" + ElytraDebug.fmt(corrected.y),
        )
        event.setMovement(corrected)
    }

    private fun onAttackEntity(event: AttackEntityEvent) {
        if (event.getPlayer() !== this.mc.player) {
            return
        }
        val target = this.target ?: return
        if (event.getEntity() === target) {
            this.hitTracker.markAttack(target, this.player().tickCount)
            this.currentBehavior.onAttack(target)
        }
    }

    private fun onPacketReceive(event: PacketEvent.Receive) {
        when (val packet = event.getPacket()) {
            is ClientboundDamageEventPacket -> this.hitTracker.onDamagePacket(packet)
            is ClientboundEntityEventPacket -> this.hitTracker.onEntityStatusPacket(packet)
            is ClientboundTeleportEntityPacket -> this.motionTracker.noteTeleport(packet.id())
            is ClientboundEntityPositionSyncPacket -> this.motionTracker.noteTeleport(packet.id())
        }
    }

    private fun onKeyPress(event: KeyPressEvent) {
        if (event.getAction() != InputConstants.PRESS) return
        if (this.switchModeKey.value == event.getKey()) {
            cycleMode()
        }
    }

    private fun onMousePress(event: MousePressEvent) {
        if (event.getAction() != InputConstants.PRESS) return
        if (this.switchModeKey.value == KeybindUtils.encodeMouseButton(event.getButton())) {
            cycleMode()
        }
    }

    private fun onRender3D() {
        if (!this.render.value || this.controlInput == null) return
        val player = this.mc.player ?: return
        val velocity = this.latestIntent.desiredVelocity
        if (velocity.lengthSqr() < 1.0E-8) return

        val start = player.position().add(0.0, player.bbHeight * 0.5, 0.0)
        Render3DScheduler.addLine(
            start,
            start.add(velocity.normalize().scale(4.0)),
            this.renderColor.value,
            this.renderWidth.value.toFloat(),
        )
    }

    private fun refreshTarget() {
        // 目标死亡、换维度或超出追击范围时立即失效；动态目标模式每 tick 重新选择。
        val player = this.mc.player ?: return
        val target = this.target
        if (target != null &&
            (!target.isAlive ||
                    target.level() !== this.mc.level ||
                    player.distanceTo(target) > this.targetRange.value)
        ) {
            this.target = null
        }
        if (this.target == null || this.dynamicTarget.value) {
            this.target = TargetManager.INSTANCE.acquirePrimary(
                TargetRequest.of(
                    this.targetRange.value.toDouble(),
                    360.0f,
                    this.players.value,
                    this.mobs.value,
                    this.animals.value,
                    this.villagers.value,
                    this.ambient.value,
                    this.water.value,
                    this.others.value,
                    this.invisible.value,
                    this.followFriend.value,
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
        val clamped = if (length > this.maxFlightSpeed.value) {
            velocity.scale(this.maxFlightSpeed.value / length)
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
        val limit = this.maxTurnSpeed.value
        val unlimited = limit >= 360.0 || this.mode.`is`(ElytraCombatMode.Spear)

        val previousDirection = previous.normalize()
        val nextDirection = desired.normalize()
        val previousYaw = Math.toDegrees(Math.atan2(-previousDirection.x, previousDirection.z)).toFloat()
        val previousPitch = -Math.toDegrees(
            Math.atan2(
                previousDirection.y,
                Math.max(1.0E-4, previousDirection.horizontalDistance()),
            )
        ).toFloat()

        // 目标方向接近垂直时 yaw 没有意义，保持上一 tick 的 yaw，只允许俯仰变化。
        val yaw: Float
        if (nextDirection.horizontalDistance() < 1.0E-3) {
            yaw = previousYaw
        } else {
            val nextYaw = Math.toDegrees(Math.atan2(-nextDirection.x, nextDirection.z)).toFloat()
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

        val nextPitch = -Math.toDegrees(
            Math.atan2(
                nextDirection.y,
                Math.max(1.0E-4, nextDirection.horizontalDistance()),
            )
        ).toFloat()
        val pitchDelta = nextPitch - previousPitch
        if (Math.abs(Mth.wrapDegrees(yaw - previousYaw).toDouble()) < TURN_DEAD_ZONE_DEGREES &&
            Math.abs(pitchDelta.toDouble()) < TURN_DEAD_ZONE_DEGREES
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

        val pitchTolerance = if (this.mode.`is`(ElytraCombatMode.Spear)) {
            SPEAR_PITCH_DEVIATION_LIMIT
        } else {
            ElytraDirectionSolver.DEFAULT_PITCH_DEVIATION_LIMIT
        }
        val rotations: Rot2f = ElytraDirectionSolver.solveSafe(player, velocity, pitchTolerance)
        ElytraDirectionSolver.rememberPitch(rotations.pitch)
        val yawDelta = Mth.wrapDegrees(rotations.yaw - player.yRot)
        val direction = directionInput(yawDelta)
        val direct = if (this.controlMode.`is`(ControlMode.DirectVelocity)) velocity else null
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
        val sector = Math.floorMod(Math.round(yawDelta / 45.0f), 8)
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
            if (hit == CombatHitTracker.HitType.SPEAR && this.logSpearHit.value) {
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
        val modes = this.mode.modes
        val next = Math.floorMod(this.mode.modeIndex + 1, modes.size)
        this.mode.setMode(modes[next])
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
