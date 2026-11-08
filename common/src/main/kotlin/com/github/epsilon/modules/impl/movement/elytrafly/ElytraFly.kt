package com.github.epsilon.modules.impl.movement.elytrafly

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.*
import com.github.epsilon.kernel.jvm.*
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraDirectionSolver
import com.github.epsilon.modules.impl.combat.elytra_combat.flight.ElytraMotionPredictor
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.utils.math.toRadians
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.math.cos

object ElytraFly : Module("Elytra Fly", Category.MOVEMENT) {

    /**
     * 启用后压制 AutoSprint 的 tick 数。
     */
    private const val SPRINT_SUPPRESS_TICKS = 3
    private const val ROCKET_SEARCH_RADIUS = 6.0
    private const val ROCKET_OWNERLESS_RADIUS = 2.0
    /**
     * 单 tick 位移上限；超过说明发生了传送或服务端复位，此时位移不代表客户端速度。
     */
    private const val MAX_TICK_DISPLACEMENT = 4.0

    /**
     * 烟花盒上限的保守系数。
     *
     * 实测飞行中 Simulation 会稳定报出 0.2~0.3 的 offset：按 Grim 硬上限 1.7 算出的盒边界
     * 比服务端实际接受的宽约 0.2 格/tick，持续超出会累积成复位。这里按比例收窄，
     * 使每轴可用量落到 1.7 × 0.88 ≈ 1.5，正好对齐参考实现使用的默认值。
     */
    private const val BOX_IMPACT_SCALE = 0.88

    enum class SwapMode {
        Silent,
        InvSwitch,
    }

    data class Pitch40ControlState(
        val enabled: Boolean,
        val mode: ElytraFlightModes,
        val lowerBounds: Double,
        val autoTakeoff: Boolean,
        val takeoffTargetHeight: Double,
        val autoFirework: Boolean,
        val yawOverride: Float?,
    )

    /**
     * Grim 允许玩家本 tick 到达的绝对速度区间，逐轴独立。
     *
     * 区间由滑翔方程预测的速度与烟花位移盒合并而来：只要写入的速度落在区间内，
     * 服务端 Simulation 就不会累计 offset。
     */
    private data class VelocityBox(
        val minX: Double,
        val minY: Double,
        val minZ: Double,
        val maxX: Double,
        val maxY: Double,
        val maxZ: Double,
    ) {

        /**
         * 单轴需要缩小多少倍才能贴到区间边界：小于 1 表示还有余量，大于 1 表示已越界。
         */
        private fun axisRatio(value: Double, min: Double, max: Double): Double = when {
            value > 0.0 -> if (max > 1.0E-6) value / max else Double.MAX_VALUE
            value < 0.0 -> if (min < -1.0E-6) value / min else Double.MAX_VALUE
            else -> 0.0
        }

        /**
         * 取三轴中最大的比值，按它等比缩放后三轴都不会越界。
         */
        fun ratioOf(velocity: Vec3): Double = maxOf(
            axisRatio(velocity.x, minX, maxX),
            maxOf(axisRatio(velocity.y, minY, maxY), axisRatio(velocity.z, minZ, maxZ)),
        )

        fun clamp(velocity: Vec3): Vec3 = Vec3(
            velocity.x.coerceIn(minX, maxX),
            velocity.y.coerceIn(minY, maxY),
            velocity.z.coerceIn(minZ, maxZ),
        )

        fun withMaxY(value: Double): VelocityBox = copy(maxY = value)

    }

    private val modes: MutableMap<ElytraFlightModes, ElytraFlightMode> = EnumMap(ElytraFlightModes::class.java)

    var mode by enumSetting("Mode", ElytraFlightModes.Control, onChanged = ::onModeChanged)
    val swapMode by enumSetting("Swap Mode", SwapMode.InvSwitch)

    val armored by boolSetting("Armored", false)
    val noEat by boolSetting("No Eat", false)
    val unbreaking by boolSetting("Unbreaking", true)
    val unbreakingDelay by intSetting("Unbreaking Delay", 800, 100..2000, 50, { unbreaking })
    val noSprint by boolSetting(
        "No Sprint", true,
        { mode == ElytraFlightModes.Control && armored }
    )
    val useFireworks by boolSetting("Use Fireworks", true, { mode == ElytraFlightModes.Control })
    val fireworkSeamless by boolSetting(
        "Firework Seamless", false,
        { mode == ElytraFlightModes.Control && useFireworks }
    )
    val boostDelay by intSetting(
        "Boost Delay", 20, 2..50, 1,
        { mode == ElytraFlightModes.Control && useFireworks && !fireworkSeamless }
    )
    val fireworkSeamlessGap by intSetting(
        "Firework Seamless Gap", 2, 0..10, 1,
        { mode == ElytraFlightModes.Control && useFireworks && fireworkSeamless }
    )
    val fireworkSeamlessLife by intSetting(
        "Firework Seamless Life", 20, 5..60, 1,
        { mode == ElytraFlightModes.Control && useFireworks && fireworkSeamless }
    )

    val fireworkBoost by boolSetting(
        "Firework Boost", false,
        { mode == ElytraFlightModes.Control && useFireworks }
    )
    val fireworkBoostSpeed by doubleSetting(
        "Firework Boost Speed", 2.35, 0.5..6.0, 0.05,
        { mode == ElytraFlightModes.Control && fireworkBoost }
    )
    val fireworkBoostTurnLimit by doubleSetting(
        "Firework Boost Turn Limit", 20.0, 5.0..90.0, 1.0,
        { mode == ElytraFlightModes.Control && fireworkBoost }
    )
    val fireworkBoostAccel by doubleSetting(
        "Firework Boost Accel", 0.35, 0.05..2.0, 0.05,
        { mode == ElytraFlightModes.Control && fireworkBoost }
    )
    val fireworkBoostRescale by boolSetting(
        "Firework Boost Rescale", true,
        { mode == ElytraFlightModes.Control && fireworkBoost }
    )
    val fireworkBoostAxisLimit by doubleSetting(
        "Firework Boost Axis Limit", 0.05, 0.0..0.3, 0.01,
        { mode == ElytraFlightModes.Control && fireworkBoost && fireworkBoostRescale }
    )
    val fireworkBoostMaxImpact by doubleSetting(
        "Firework Boost Max Impact", 1.7, 0.3..1.7, 0.05,
        { mode == ElytraFlightModes.Control && fireworkBoost && fireworkBoostRescale }
    )
    val fireworkBoostCompensation by doubleSetting(
        "Firework Boost Compensation", 0.01, 0.0..0.05, 0.01,
        { mode == ElytraFlightModes.Control && fireworkBoost && fireworkBoostRescale }
    )
    val fireworkBoostExtraTicks by intSetting(
        "Firework Boost Extra Ticks", 0, 0..10, 1,
        { mode == ElytraFlightModes.Control && fireworkBoost }
    )

    var pitch40lowerBounds by doubleSetting(
        "Pitch40 Lower Bounds", 180.0, -128.0..1024.0, 1.0,
        { mode == ElytraFlightModes.Pitch40 }
    )
    val pitch40rotationSpeedUp by doubleSetting(
        "Pitch40 Rotate Speed Up", 5.45, 1.0..20.0, 0.05,
        { mode == ElytraFlightModes.Pitch40 }
    )
    val pitch40rotationSpeedDown by doubleSetting(
        "Pitch40 Rotate Speed Down", 0.90, 0.5..2.0, 0.05,
        { mode == ElytraFlightModes.Pitch40 }
    )
    val pitch40PacketDelay by intSetting(
        "Pitch40 Packet Delay", 3, 1..20, 1,
        { mode == ElytraFlightModes.Pitch40 && armored }
    )
    var pitch40AutoTakeoff by boolSetting(
        "Pitch40 Auto Takeoff", true,
        { mode == ElytraFlightModes.Pitch40 }
    )
    var pitch40TakeoffTargetHeight by doubleSetting(
        "Pitch40 Takeoff Target Height", 300.0, -128.0..1024.0, 1.0,
        { mode == ElytraFlightModes.Pitch40 && pitch40AutoTakeoff }
    )
    var pitch40AutoFirework by boolSetting(
        "Pitch40 Auto Firework", true,
        { mode == ElytraFlightModes.Pitch40 && pitch40AutoTakeoff }
    )
    val pitch40FireworkCooldown by intSetting(
        "Pitch40 Firework Cooldown", 10, 0..100, 1,
        {
            mode == ElytraFlightModes.Pitch40 && pitch40AutoTakeoff && pitch40AutoFirework
        }
    )

    val ncpInstantFly by boolSetting("NCP Instant Fly", true, { mode == ElytraFlightModes.NCP })
    val ncpUpPitch by doubleSetting(
        "NCP Up Pitch", 0.0, 0.0..90.0, 1.0,
        { mode == ElytraFlightModes.NCP }
    )
    val ncpUpFactorSetting: DoubleSetting = doubleSetting(
        "NCP Up Factor", 1.0, 0.0..10.0, 0.05,
        { mode == ElytraFlightModes.NCP }
    )
    val ncpUpFactor by ncpUpFactorSetting
    val ncpDownFactor by doubleSetting(
        "NCP Down Factor", 1.0, 0.0..10.0, 0.05,
        { mode == ElytraFlightModes.NCP }
    )
    val ncpSpeed by doubleSetting("NCP Speed", 1.0, 0.1..10.0, 0.1, { mode == ElytraFlightModes.NCP })
    val ncpDownSpeed by doubleSetting(
        "NCP Down Speed", 1.0, 0.1..10.0, 0.1,
        { mode == ElytraFlightModes.NCP }
    )
    val ncpTimer by boolSetting("NCP Timer", true, { mode == ElytraFlightModes.NCP })
    val ncpSpeedLimit by boolSetting("NCP Speed Limit", true, { mode == ElytraFlightModes.NCP })
    val ncpMaxSpeed by doubleSetting(
        "NCP Max Speed", 2.5, 0.1..10.0, 0.1,
        { mode == ElytraFlightModes.NCP && ncpSpeedLimit }
    )
    val ncpNoDrag by boolSetting("NCP No Drag", false, { mode == ElytraFlightModes.NCP })
    val ncpTimeout by doubleSetting(
        "NCP Timeout", 0.5, 0.1..1.0, 0.05,
        { mode == ElytraFlightModes.NCP }
    )

    private var activeModeType: ElytraFlightModes? = null
    private var pitch40YawOverride: Float? = null
    private var sprintSuppressTicks = 0

    /**
     * 烟花加速的上 tick 快照：位移、位置、写入的速度与朝向。
     */
    private var boostDelta = Vec3.ZERO
    private var boostPosition = Vec3.ZERO
    private var boostLastLook: Vec3? = null
    private var boostLastSet: Vec3? = null
    private var boostRocketTick = -1

    init {
        onEnable {
            activeModeType = mode
            getActiveMode().armUnbreakingTimer()
            getActiveMode().onEnable()
            stopSprintOnce()
            resetBoostState()
        }

        onDisable {
            sprintSuppressTicks = 0
            getMode(activeModeType).onDisable()
        }

        modes[ElytraFlightModes.Control] = ControlElytraFlightMode(this)
        modes[ElytraFlightModes.Pitch40] = Pitch40ElytraFlightMode(this)
        modes[ElytraFlightModes.NCP] = NCPElytraFlightMode(this)

        onUpdate {
            // 记录上一 tick 位移：服务端的烟花盒以客户端实际收到的位移为基准。
            val position = player.position()
            val delta = position.subtract(boostPosition)
            boostDelta = if (delta.lengthSqr() > MAX_TICK_DISPLACEMENT * MAX_TICK_DISPLACEMENT) Vec3.ZERO else delta
            boostPosition = position

            if (sprintSuppressTicks > 0) {
                sprintSuppressTicks--
            }
            getActiveMode().onPlayerTick()
            if (isEnabled) {
                getActiveMode().handleUnbreaking()
            }
        }

        safeEventHandler<FallFlyingMovementEvent>(EventPriority.LOW) {
            onFallFlyingMovement(it)
        }

        safeEventHandler<TravelEvent> {
            getActiveMode().onTravel(it)
        }

        safeEventHandler<KeyboardInputEvent> {
            getActiveMode().onKeyboardInput(it)
        }

        safeEventHandler<FallFlyingEvent> {
            getActiveMode().onFallFlying(it)
        }

        safeEventHandler<FireworkRotationEvent> {
            getActiveMode().onFireworkUpdate(it)
        }

        handler<MousePressEvent> { event ->
            if (mc.gui.screen() != null) return@handler
            if (!noEat) return@handler
            if (event.button == InputConstants.MOUSE_BUTTON_RIGHT && event.action == InputConstants.PRESS &&
                getActiveMode().shouldCancelRightClick()
            ) {
                event.cancel()
            }
        }

        safeEventHandler<RightClickEvent> {
            getActiveMode().onRightClick()
        }
    }

    /**
     * 停止疾跑，并短暂压制 AutoSprint。
     */
    private fun stopSprintOnce() {
        runSafe {
            sprintSuppressTicks = SPRINT_SUPPRESS_TICKS
            player.isSprinting = false
            mc.options.keySprint.isDown = false
        }
    }

    /**
     * AutoSprint 是否应当暂停强制疾跑。
     */
    val isSprintSuppressed: Boolean
        get() = sprintSuppressTicks > 0

    override fun getInfo(): String {
        return mode.toString()
    }

    /**
     * 当前是否处于穿甲飞行状态：滑翔靠「换上鞘翅触发一次再换回胸甲」维持，鞘翅并不在身上。
     * 渲染层据此隐藏滑翔姿态，AutoArmor 据此不再把鞘翅设为最高优先级。
     */
    val isArmorMode: Boolean
        get() = isEnabled && armored &&
                (mode == ElytraFlightModes.Control || mode == ElytraFlightModes.NCP)

    fun capturePitch40ControlState(): Pitch40ControlState {
        return Pitch40ControlState(
            isEnabled,
            mode,
            pitch40lowerBounds,
            pitch40AutoTakeoff,
            pitch40TakeoffTargetHeight,
            pitch40AutoFirework,
            pitch40YawOverride
        )
    }

    fun applyPitch40Control(
        autoTakeoff: Boolean,
        autoFirework: Boolean,
        lowerBounds: Double,
        takeoffTargetHeight: Double
    ) {
        applyPitch40Control(autoTakeoff, autoFirework, lowerBounds, takeoffTargetHeight, null)
    }

    fun applyPitch40Control(
        autoTakeoff: Boolean,
        autoFirework: Boolean,
        lowerBounds: Double,
        takeoffTargetHeight: Double,
        yawOverride: Float?,
    ) {
        pitch40AutoTakeoff = autoTakeoff
        pitch40AutoFirework = autoFirework
        pitch40lowerBounds = lowerBounds
        pitch40TakeoffTargetHeight = takeoffTargetHeight
        pitch40YawOverride = yawOverride

        if (mode != ElytraFlightModes.Pitch40) {
            mode = ElytraFlightModes.Pitch40
        }
        if (!isEnabled) {
            isEnabled = true
        }
    }

    fun restorePitch40Control(state: Pitch40ControlState?) {
        if (state == null) return

        pitch40lowerBounds = state.lowerBounds
        pitch40AutoTakeoff = state.autoTakeoff
        pitch40TakeoffTargetHeight = state.takeoffTargetHeight
        pitch40AutoFirework = state.autoFirework
        pitch40YawOverride = state.yawOverride
        mode = state.mode

        if (!state.enabled && isEnabled) {
            isEnabled = false
        }
    }

    fun getPitch40Yaw(fallback: Float): Float {
        return pitch40YawOverride ?: fallback
    }

    fun getActiveMode(): ElytraFlightMode {
        return getMode(mode)
    }

    private fun getMode(mode: ElytraFlightModes?): ElytraFlightMode {
        return modes[mode] ?: modes[ElytraFlightModes.Control]!!
    }

    private fun onModeChanged(newMode: ElytraFlightModes) {
        if (!isEnabled) {
            activeModeType = newMode
            return
        }

        getMode(activeModeType).onDisable()
        activeModeType = newMode
        getActiveMode().onEnable()
    }

    private fun resetBoostState() {
        runSafe {
            boostPosition = player.position()
        }
        boostDelta = Vec3.ZERO
        boostLastLook = null
        boostLastSet = null
        boostRocketTick = -1
    }

    /**
     * 烟花加速接管鞘翅速度的入口。
     */
    private fun NonNullContext.onFallFlyingMovement(event: FallFlyingMovementEvent) {
        if (!isBoostActive()) return
        val current = event.movement
        if (current.lengthSqr() < 1.0E-6) return

        // 服务端按客户端实际发出的旋转做预测，所以这里必须取托管旋转而不是玩家原始视角。
        val rotation = RotationManager.getRotation()
        val yaw = rotation.yaw
        val pitch = rotation.pitch
        val look = Entity.calculateViewVector(pitch, yaw).normalize()
        // 朝向与飞行方向差太多时不做接管，否则等于把玩家往回拽。
        if (current.normalize().dot(look) < cos(fireworkBoostTurnLimit.toRadians())) return

        solveBoostVelocity(look, yaw, pitch)?.let { event.movement = it }
        boostLastSet = event.movement
    }

    /**
     * 写入鞘翅速度计算
     */
    private fun NonNullContext.solveBoostVelocity(look: Vec3, yaw: Float, pitch: Float): Vec3? {
        val lastLook = boostLastLook ?: look
        boostLastLook = look

        val lastDelta = boostDelta
        val lastSet = boostLastSet
        val lastDeltaLength = lastDelta.length()
        // 刚起飞、换甲复位或贴墙卡住时，上 tick 位移不能代表客户端速度，交给原版自行恢复。
        if (lastSet == null || lastDeltaLength < 1.0E-4 || lastDeltaLength > MAX_TICK_DISPLACEMENT) return null
        if (lastDelta.distanceTo(lastSet) > 1.0) return null

        val target = look.scale(fireworkBoostSpeed)
        if (!fireworkBoostRescale) return target

        val step = target.subtract(lastDelta)
        val stepLength = step.length()
        val desired = if (stepLength > fireworkBoostAccel) {
            lastDelta.add(step.scale(fireworkBoostAccel / stepLength))
        } else {
            target
        }

        val box = resolveVelocityBox(desired, look, lastLook, lastDelta, yaw, pitch)
        val ratio = box.ratioOf(desired)
        // ratio > 2 说明盒过窄或期望方向无法缩入区间，这种情况下强推只会触发复位。
        if (ratio < 1.0E-6 || ratio > 2.0) return null

        return box.clamp(if (ratio > 1.0) desired.scale(1.0 / ratio) else desired)
    }

    /**
     * Grim烟花位移盒计算
     */
    private fun NonNullContext.resolveVelocityBox(
        desired: Vec3,
        look: Vec3,
        lastLook: Vec3,
        lastDelta: Vec3,
        yaw: Float,
        pitch: Float,
    ): VelocityBox {
        val anti = fireworkBoostAxisLimit
        val cap = minOf(fireworkBoostMaxImpact, desired.length()) * BOX_IMPACT_SCALE

        val minX = axisClamp((minOf(-anti, look.x) + minOf(-anti, lastLook.x)) * cap, cap)
        val maxX = axisClamp((maxOf(anti, look.x) + maxOf(anti, lastLook.x)) * cap, cap)
        val minY = axisClamp((minOf(-anti, look.y) + minOf(-anti, lastLook.y)) * cap, cap)
        val maxY = axisClamp((maxOf(anti, look.y) + maxOf(anti, lastLook.y)) * cap, cap)
        val minZ = axisClamp((minOf(-anti, look.z) + minOf(-anti, lastLook.z)) * cap, cap)
        val maxZ = axisClamp((maxOf(anti, look.z) + maxOf(anti, lastLook.z)) * cap, cap)

        val gravity = ElytraDirectionSolver.effectiveGravity(player)
        val predicted = ElytraMotionPredictor.nextFallFlyingMovement(lastDelta, yaw, pitch, gravity)
        var box = VelocityBox(
            predicted.x + minOf(0.0, minX - lastDelta.x),
            predicted.y + minOf(0.0, minY - lastDelta.y),
            predicted.z + minOf(0.0, minZ - lastDelta.z),
            predicted.x + maxOf(0.0, maxX - lastDelta.x),
            predicted.y + maxOf(0.0, maxY - lastDelta.y),
            predicted.z + maxOf(0.0, maxZ - lastDelta.z),
        )

        // 垂直爬升时水平速度接近 0，滑翔方程的爬升补偿项退化，需按补偿量重新估算一次 y 上限。
        if (box.maxY > 1.0E-6 && desired.horizontalDistance() < 0.04 * desired.y) {
            val desiredLength = desired.length()
            val inflate = (desiredLength + fireworkBoostCompensation) / desiredLength
            val lifted = ElytraMotionPredictor.nextFallFlyingMovement(desired.scale(inflate), yaw, pitch, gravity).y
            box = box.withMaxY(maxOf(box.maxY, lifted))
        }
        return box
    }

    /**
     * 当前是否由烟花加速接管滑翔速度：控制模式、开启烟花推进，且自己的烟花仍在生效。
     */
    private fun NonNullContext.isBoostActive(): Boolean {
        if (!isEnabled || !useFireworks || !fireworkBoost) return false
        if (mode != ElytraFlightModes.Control || !player.isFallFlying) return false
        return hasOwnRocket() || (boostRocketTick >= 0 &&
                player.tickCount >= boostRocketTick &&
                player.tickCount - boostRocketTick <= fireworkBoostExtraTicks)
    }

    /**
     * 烟花存活判断
     */
    private fun NonNullContext.hasOwnRocket(): Boolean {
        val ownerlessRange = ROCKET_OWNERLESS_RADIUS * ROCKET_OWNERLESS_RADIUS
        val area: AABB = player.boundingBox.inflate(ROCKET_SEARCH_RADIUS)
        for (rocket in level.getEntitiesOfClass(FireworkRocketEntity::class.java, area)) {
            if (!rocket.isAlive) continue
            val owner: Entity? = rocket.owner
            val mine = owner === player || (owner == null && rocket.distanceToSqr(player) < ownerlessRange)
            if (mine) {
                boostRocketTick = player.tickCount
                return true
            }
        }
        return false
    }

    fun hasActiveFirework(): Boolean = runSafeOrFalse { hasOwnRocket() }

    private fun axisClamp(value: Double, cap: Double): Double = value.coerceIn(-cap, cap)

}
