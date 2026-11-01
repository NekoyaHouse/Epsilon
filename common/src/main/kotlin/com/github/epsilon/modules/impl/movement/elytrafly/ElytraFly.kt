package com.github.epsilon.modules.impl.movement.elytrafly

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.*
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.DoubleSetting
import com.mojang.blaze3d.platform.InputConstants
import java.util.*

object ElytraFly : Module("Elytra Fly", Category.MOVEMENT) {

    /**
     * 启用后压制 AutoSprint 的 tick 数。
     */
    private const val SPRINT_SUPPRESS_TICKS = 3

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
    val boostDelay by intSetting(
        "Boost Delay", 20, 2..50, 1,
        { mode == ElytraFlightModes.Control && useFireworks }
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

    init {
        onEnable {
            activeModeType = mode
            getActiveMode().armUnbreakingTimer()
            getActiveMode().onEnable()
            stopSprintOnce()
        }

        onDisable {
            sprintSuppressTicks = 0
            getMode(activeModeType).onDisable()
        }

        modes[ElytraFlightModes.Control] = ControlElytraFlightMode(this)
        modes[ElytraFlightModes.Pitch40] = Pitch40ElytraFlightMode(this)
        modes[ElytraFlightModes.NCP] = NCPElytraFlightMode(this)

        safeEventHandler<PlayerTickEvent.Pre> {
            if (sprintSuppressTicks > 0) {
                sprintSuppressTicks--
            }
            getActiveMode().onPlayerTick()
            if (isEnabled) {
                getActiveMode().handleUnbreaking()
            }
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

}
