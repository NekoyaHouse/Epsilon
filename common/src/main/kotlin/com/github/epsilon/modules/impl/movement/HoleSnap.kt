package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.MoveEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.managers.TimerManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.group
import com.github.epsilon.utils.math.toDegrees
import com.github.epsilon.utils.math.toRadians
import com.github.epsilon.utils.world.hole.Hole
import com.github.epsilon.utils.world.hole.HoleType
import com.github.epsilon.utils.world.hole.HoleUtils
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object HoleSnap : Module("Hole Snap", Category.MOVEMENT) {

    private val sgGeneral: SettingGroup = settingGroup("General")
    private val sgSpeed: SettingGroup = settingGroup("Speed")
    private val sgHole: SettingGroup = settingGroup("Hole")
    private val sgRender: SettingGroup = settingGroup("Render")

    private val step by boolSetting("Use Step", true).group(sgGeneral)
    private val disableStep by boolSetting("Toggle Step", true).group(sgGeneral)
    private val jump by boolSetting("Jump", false).group(sgGeneral)
    private val jumpCooldown by intSetting(
        "Jump Cooldown",
        5,
        0..100,
        1, { jump }).group(sgGeneral)
    private val range by intSetting("Range", 3, 0..10, 1).group(sgGeneral)
    private val downRange by intSetting("Down Range", 3, 0..5, 1).group(sgGeneral)
    private val collisionLimit by intSetting("Collisions To Disable", 15, 0..100, 1).group(sgGeneral)
    private val rubberbandLimit by intSetting("Rubberbands To Disable", 1, 0..100, 1).group(sgGeneral)

    private val speed by doubleSetting("Speed", 0.2873, 0.0..1.0, 0.0001).group(sgSpeed)
    private val boost by boolSetting("Speed Boost", false).group(sgSpeed)
    private val boostedSpeed by doubleSetting(
        "Boosted Speed",
        0.5,
        0.0..1.0,
        0.01, { boost }).group(sgSpeed)
    private val boostTicks by intSetting("Boost Ticks", 3, 1..10, 1, { boost }).group(sgSpeed)
    private val timer by doubleSetting("Timer", 1.5, 0.1..100.0, 0.1).group(sgSpeed)

    private val singleTarget by boolSetting("Single Target", true).group(sgHole)
    private val depth by intSetting("Hole Depth", 3, 1..5, 1).group(sgHole)
    private val singleHoles by boolSetting("Single Holes", true).group(sgHole)
    private val doubleHoles by boolSetting("Double Holes", true).group(sgHole)
    private val quadHoles by boolSetting("Quad Holes", true).group(sgHole)

    private val render by boolSetting("Render", true).group(sgRender)
    private val lineWidth by doubleSetting("Line Width", 2.0, 1.0..4.0, 0.1, { render }).group(sgRender)
    private val color by colorSetting("Color", Color.WHITE, true, { render }).group(sgRender)

    private var singleHole: Hole? = null
    private var targetHole: Hole? = null
    private var collisions = 0
    private var rubberbands = 0
    private var jumpTicks = 0
    private var boostLeft = 0

    init {
        onEnable {
            singleHole = runSafe { findHole() }
            targetHole = singleHole
            collisions = 0
            rubberbands = 0
            jumpTicks = 0
            boostLeft = if (boost) boostTicks else 0
        }

        onDisable {
            if (disableStep && step && Step.isEnabled) {
                Step.isEnabled = false
            }

            TimerManager.reset()
            singleHole = null
            targetHole = null
        }

        handler<PacketEvent.Receive> { event ->
            if (rubberbandLimit <= 0) return@handler

            val packet = event.packet
            if (packet is ClientboundPlayerPositionPacket || packet is ClientboundPlayerRotationPacket) {
                rubberbands++
                if (rubberbands >= rubberbandLimit) {
                    isEnabled = false
                }
            }
        }

        safeEventHandler<MoveEvent>(EventPriority.HIGHEST) { event ->
            val hole = if (singleTarget) singleHole else findHole()
            targetHole = hole

            if (hole == null || singleBlocked()) {
                toggle()
                return@safeEventHandler
            }

            TimerManager.set(timer.toFloat())

            val yaw = cos(((getAngle(hole.middle) + 90.0f).toDouble()).toRadians())
            val pitch = sin(((getAngle(hole.middle) + 90.0f).toDouble()).toRadians())

            if (player.x == hole.middle.x && player.z == hole.middle.z) {
                if (player.y == hole.middle.y) {
                    event.x = 0.0
                    event.z = 0.0
                    event.cancel()
                    toggle()
                } else if (!level.noBlockCollision(player, player.boundingBox.move(0.0, -0.05, 0.0))) {
                    toggle()
                } else {
                    event.x = 0.0
                    event.z = 0.0
                    event.cancel()
                }
                return@safeEventHandler
            }

            if (step && !Step.isEnabled) {
                Step.isEnabled = true
            }

            val motionX = speedValue() * yaw
            val distanceX = hole.middle.x - player.x
            val motionZ = speedValue() * pitch
            val distanceZ = hole.middle.z - player.z

            if (!level.noBlockCollision(player, player.boundingBox.move(motionX, 0.0, motionZ))) {
                collisions++
                if (collisionLimit > 0 && collisions >= collisionLimit) {
                    toggle()
                    return@safeEventHandler
                }
            } else {
                collisions = 0
            }

            if (jumpTicks > 0) {
                jumpTicks--
            } else if (jump && !level.noBlockCollision(player, player.boundingBox.move(0.0, -0.05, 0.0))) {
                jumpTicks = jumpCooldown
                event.y = 0.42
            }

            boostLeft--
            event.x = if (abs(motionX) < abs(distanceX)) motionX else distanceX
            event.z = if (abs(motionZ) < abs(distanceZ)) motionZ else distanceZ
            event.cancel()
        }

        safeEventHandler<Render3DEvent> {
            if (!render || targetHole == null) return@safeEventHandler

            val hole = targetHole ?: return@safeEventHandler
            val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            val playerPos = player.getPosition(partialTick)
            val topEdge = getTopEdge(hole, playerPos)
            val width = lineWidth.toFloat()

            Render3DScheduler.addLine(playerPos, topEdge, color, width)
            Render3DScheduler.addLine(topEdge, hole.middle, color, width)
        }
    }

    private fun getTopEdge(hole: Hole, playerPos: Vec3): Vec3 {
        var minX = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY

        for (pos in hole.positions) {
            minX = minOf(minX, pos.x.toDouble())
            minZ = minOf(minZ, pos.z.toDouble())
            maxX = maxOf(maxX, pos.x + 1.0)
            maxZ = maxOf(maxZ, pos.z + 1.0)
        }

        val deltaX = playerPos.x - hole.middle.x
        val deltaZ = playerPos.z - hole.middle.z
        val xRatio = abs(deltaX) / ((maxX - minX) * 0.5)
        val zRatio = abs(deltaZ) / ((maxZ - minZ) * 0.5)
        val ratio = maxOf(xRatio, zRatio)
        val scale = if (ratio > 1.0) 1.0 / ratio else 1.0

        return Vec3(
            hole.middle.x + deltaX * scale,
            hole.middle.y + 1.0,
            hole.middle.z + deltaZ * scale
        )
    }

    context(nn: NonNullContext)
    private fun singleBlocked(): Boolean {
        if (!singleTarget) return false
        val hole = singleHole ?: return false

        for (pos in hole.positions) {
            if (HoleUtils.isBlock(pos)) return true
        }
        return false
    }

    context(nn: NonNullContext)
    private fun findHole(): Hole? {
        var closest: Hole? = null
        val playerPos = nn.player.position()
        val playerBlockPos = nn.player.blockPosition()

        for (x in -range..range) {
            for (y in -downRange..0) {
                for (z in -range..range) {
                    val pos = playerBlockPos.offset(x, y, z)
                    val hole = HoleUtils.getHole(pos, singleHoles, doubleHoles, quadHoles, depth, true)

                    if (hole.type == HoleType.NotHole) continue
                    if (y == 0 && inHole(hole)) return hole

                    if (closest == null || hole.middle.distanceTo(playerPos) < closest.middle.distanceTo(playerPos)) {
                        closest = hole
                    }
                }
            }
        }

        return closest
    }

    context(nn: NonNullContext)
    private fun inHole(hole: Hole): Boolean {
        val playerBlockPos = nn.player.blockPosition()
        for (pos in hole.positions) {
            if (playerBlockPos == pos) return true
        }
        return false
    }

    context(nn: NonNullContext)
    private fun getAngle(pos: Vec3): Float {
        val deltaX = pos.x - nn.player.x
        val deltaZ = pos.z - nn.player.z
        val targetYaw = (atan2(deltaZ, deltaX)).toDegrees().toFloat() - 90.0f
        return nn.player.yRot + Mth.wrapDegrees(targetYaw - nn.player.yRot)
    }

    private fun speedValue(): Double {
        return if (boostLeft > 0) boostedSpeed else speed
    }

}
