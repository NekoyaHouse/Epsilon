package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

object AutoMap : Module("Auto Map", Category.MOVEMENT) {

    private const val STATE_VERSION = 1
    private val TWO_PI = Math.PI * 2.0

    private val autoLaunch by boolSetting("Auto Launch", true)
    private val spiralSpacing by doubleSetting("Spiral Spacing", 192.0, 16.0, 1024.0, 1.0)
    private val pointsPerLap by intSetting("Points Per Lap", 16, 4, 96, 1)
    private val arrivalDistance by doubleSetting("Arrival Distance", 8.0, 2.0, 64.0, 0.5)
    private val rotationSpeed by intSetting("Rotation Speed", 180, 10, 180, 10)
    private val takeoffHeight by doubleSetting("Takeoff Height", 192.0, 16.0, 1024.0, 1.0)
    private val takeoffFirework by boolSetting("Takeoff Firework", true, dependency = { autoLaunch })

    private val saveInterval by intSetting("Save Interval", 100, 20, 1200, 20)
    private val writeRecordButton by buttonSetting("Write Record", Runnable { writeRecord(true) })
    private val resetRecordButton by buttonSetting("Reset Record", Runnable { resetRecord(true) })

    private var initializedInWorld = false
    private var routeLoaded = false
    private var routeDirty = false
    private var saveTicks = 0

    private var centerX = 0.0
    private var centerZ = 0.0
    private var spiralAngle = 0.0
    private var spiralRadius = 0.0
    private var dimensionId = ""

    private var restorePitch40State: ElytraFly.Pitch40ControlState? = null
    private var activeTakeoffTargetHeight = Double.NaN
    private var takeoffBaseY = Double.NaN

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            if (!initializedInWorld) {
                initializeInWorld()
            }

            syncFlightControl()
            updateNavigation()
            autoSaveRecord()
        }

        nonNullListener<KeyboardInputEvent> { event ->
            if (!initializedInWorld) return@nonNullListener

            event.forward = 1.0f
            event.strafe = 0.0f
            if (autoLaunch && !player.isFallFlying && !player.isInWater) {
                event.isJump = true
            }
        }
    }

    override fun onEnable() {
        saveTicks = 0
        runSafe {
            initializeInWorld()
        }
    }

    override fun onDisable() {
        initializedInWorld = false
        restoreFlightControl()
    }

    override fun getInfo(): String? {
        return if (routeLoaded) "R" + Math.round(spiralRadius) else null
    }

    override fun resetCustomState() {
        initializedInWorld = false
        routeLoaded = false
        routeDirty = false
        saveTicks = 0
        centerX = 0.0
        centerZ = 0.0
        spiralAngle = 0.0
        spiralRadius = 0.0
        dimensionId = ""
    }

    override fun saveCustomState(): JsonObject? {
        if (!routeLoaded) return null

        val state = JsonObject()
        state.addProperty("version", STATE_VERSION)
        state.addProperty("dimension", dimensionId)
        state.addProperty("centerX", centerX)
        state.addProperty("centerZ", centerZ)
        state.addProperty("spiralAngle", spiralAngle)
        state.addProperty("spiralRadius", spiralRadius)
        return state
    }

    override fun loadCustomState(state: JsonObject) {
        val loadedDimension = readString(state, "dimension", "")
        val loadedCenterX = readDouble(state, "centerX", Double.NaN)
        val loadedCenterZ = readDouble(state, "centerZ", Double.NaN)
        val loadedAngle = readDouble(state, "spiralAngle", Double.NaN)
        val loadedRadius = readDouble(state, "spiralRadius", Double.NaN)

        if (!isFinite(loadedCenterX) || !isFinite(loadedCenterZ) || !isFinite(loadedAngle) ||
            !isFinite(loadedRadius) || loadedRadius < 0.0
        ) {
            resetCustomState()
            return
        }

        dimensionId = loadedDimension
        centerX = loadedCenterX
        centerZ = loadedCenterZ
        spiralAngle = normalizeAngle(loadedAngle)
        spiralRadius = loadedRadius
        routeLoaded = true
        routeDirty = false
        saveTicks = 0
    }

    private fun initializeInWorld() {
        initializedInWorld = true

        if (!routeLoaded || dimensionId != currentDimension()) {
            resetRouteToPlayer(false)
        }

        updateTakeoffTarget(true)
        syncFlightControl()
    }

    private fun syncFlightControl() {
        captureFlightState()
        updateTakeoffTarget(false)
        ElytraFly.applyPitch40Control(
            autoLaunch,
            autoLaunch && takeoffFirework,
            -128.0,
            activeTakeoffTargetHeight,
            yawTo(currentTarget())
        )
    }

    private fun captureFlightState() {
        if (restorePitch40State == null) {
            restorePitch40State = ElytraFly.capturePitch40ControlState()
        }
    }

    private fun restoreFlightControl() {
        val state = restorePitch40State
        if (state != null) {
            ElytraFly.restorePitch40Control(state)
            restorePitch40State = null
        }
        activeTakeoffTargetHeight = Double.NaN
        takeoffBaseY = Double.NaN
    }

    private fun updateNavigation() {
        runSafe {
            if (!routeLoaded) return@runSafe

            var target = currentTarget()
            var advances = 0
            while (horizontalDistanceSqr(target) <= Mth.square(arrivalDistance) && advances++ < 8) {
                advanceTarget()
                target = currentTarget()
            }

            val yaw = yawTo(target)
            val pitch = if (RotationManager.INSTANCE.isActive()) {
                RotationManager.INSTANCE.getPitch()
            } else {
                player.xRot
            }
            RotationManager.INSTANCE.setRotations(Rot2f(yaw, pitch), rotationSpeed.toDouble(), Priority.Highest)
        }
    }

    private fun currentTarget(): Vec3 = runSafeOrElse(Vec3.ZERO) {
        val x = centerX + Math.cos(spiralAngle) * spiralRadius
        val z = centerZ + Math.sin(spiralAngle) * spiralRadius
        Vec3(x, player.y, z)
    }

    private fun advanceTarget() {
        spiralAngle = normalizeAngle(spiralAngle + angleStep())
        spiralRadius += radiusStep()
        routeDirty = true
    }

    private fun angleStep(): Double {
        return TWO_PI / Math.max(1, pointsPerLap)
    }

    private fun radiusStep(): Double {
        return spiralSpacing / Math.max(1, pointsPerLap)
    }

    private fun horizontalDistanceSqr(target: Vec3): Double = runSafeOrElse(Double.MAX_VALUE) {
        val dx = target.x - player.x
        val dz = target.z - player.z
        dx * dx + dz * dz
    }

    private fun yawTo(target: Vec3): Float = runSafeOrElse(0.0f) {
        val dx = target.x - player.x
        val dz = target.z - player.z
        if (dx * dx + dz * dz < 1.0E-6) {
            return@runSafeOrElse player.yRot
        }
        Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)).toFloat() - 90.0f)
    }

    private fun resetRecord(notify: Boolean) {
        if (NonNullContext.current == null) {
            if (notify) sendMessage("当前没有可用的玩家位置")
            return
        }

        resetRouteToPlayer(true)
        writeRecord(false)
        if (notify) sendMessage("已重置记录")
    }

    private fun resetRouteToPlayer(dirty: Boolean) {
        runSafe {
            centerX = player.x
            centerZ = player.z
            dimensionId = currentDimension()
            spiralAngle = normalizeAngle(angleStep())
            spiralRadius = radiusStep()
            routeLoaded = true
            routeDirty = dirty
            saveTicks = 0
        }
    }

    private fun updateTakeoffTarget(forceReset: Boolean) {
        runSafe {
            if (!forceReset && !needsTakeoffRetarget()) {
                return@runSafe
            }

            takeoffBaseY = player.y
            activeTakeoffTargetHeight = takeoffBaseY + takeoffHeight
        }
    }

    private fun needsTakeoffRetarget(): Boolean = runSafeOrFalse {
        if (activeTakeoffTargetHeight.isNaN() || takeoffBaseY.isNaN()) {
            true
        } else {
            player.onGround() && !player.isFallFlying && Math.abs(player.y - takeoffBaseY) > 0.5
        }
    }

    private fun writeRecord(notify: Boolean) {
        if (!routeLoaded) {
            if (NonNullContext.current == null) {
                if (notify) sendMessage("暂无可写入的记录")
                return
            }
            resetRouteToPlayer(true)
        }

        routeDirty = false
        saveTicks = 0
        if (notify) sendMessage("已写入记录")
    }

    private fun autoSaveRecord() {
        if (!routeDirty) return

        saveTicks++
        if (saveTicks >= saveInterval) {
            writeRecord(false)
        }
    }

    private fun currentDimension(): String {
        val level = mc.level ?: return ""
        return level.dimension().identifier().toString()
    }

    private fun sendMessage(message: String) {
        runSafe {
            player.sendSystemMessage(Component.literal("[自动扫图] " + message))
        }
    }

    private fun normalizeAngle(angle: Double): Double {
        val normalized = angle % TWO_PI
        return if (normalized < 0.0) normalized + TWO_PI else normalized
    }

    private fun isFinite(value: Double): Boolean {
        return !value.isNaN() && !value.isInfinite()
    }

    private fun readString(obj: JsonObject, key: String, fallback: String): String {
        return try {
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asString else fallback
        } catch (ignored: Exception) {
            fallback
        }
    }

    private fun readDouble(obj: JsonObject, key: String, fallback: Double): Double {
        return try {
            if (obj.has(key) && obj.get(key).isJsonPrimitive) obj.get(key).asDouble else fallback
        } catch (ignored: Exception) {
            fallback
        }
    }

}
