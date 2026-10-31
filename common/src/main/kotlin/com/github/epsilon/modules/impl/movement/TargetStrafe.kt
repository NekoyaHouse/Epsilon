package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.utils.math.toDegrees
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rotation
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.*

object TargetStrafe : Module("Target Strafe", Category.MOVEMENT) {

    private val radius by doubleSetting("Radius", 1.0, 0.0..6.0, 0.1)
    private val points by intSetting("Points", 6, 3..24, 1)
    private val requireJump by boolSetting("Require Jump", true)
    private val speedOnly by boolSetting("Speed Only", true)
    private val drawRadius by boolSetting("Draw Radius", true)

    private var target: LivingEntity? = null
    private var targetYaw = Float.NaN
    private var direction = 1

    init {
        safeEventHandler<PlayerTickEvent.Pre>(EventPriority.HIGHEST) {
            val left = mc.options.keyLeft.isDown
            val right = mc.options.keyRight.isDown
            if (left != right) {
                direction = if (left) 1 else -1
            }

            if (speedOnly && !Speed.isEnabled && !Flight.isEnabled) {
                resetTarget()
                return@safeEventHandler
            }

            if (requireJump && (mc.gui.screen() != null || !mc.options.keyJump.isDown)) {
                resetTarget()
                return@safeEventHandler
            }

            if (KillAura.isEnabled && !Scaffold.isEnabled && !player.isUsingItem && !player.isBlocking) {
                target = KillAura.target
            } else {
                resetTarget()
            }

            if (target == null) {
                targetYaw = Float.NaN
                return@safeEventHandler
            }

            val offsets = createOrbitOffsets()
            var closestIndex = findClosestIndex(offsets)
            if (closestIndex < 0) {
                resetTarget()
                return@safeEventHandler
            }

            if (player.horizontalCollision) {
                direction *= -1
            }

            var nextIndex = wrapIndex(closestIndex + direction, offsets.size)
            var nextPosition = getOrbitPosition(offsets[nextIndex])
            if (isOverVoid(nextPosition.x, nextPosition.z)) {
                direction *= -1
                nextIndex = wrapIndex(closestIndex + direction, offsets.size)
                nextPosition = getOrbitPosition(offsets[nextIndex])
            }

            val deltaX = nextPosition.x - player.x
            val deltaZ = nextPosition.z - player.z

            targetYaw = Mth.wrapDegrees((atan2(deltaZ, deltaX)).toDegrees().toFloat() - 90.0f)

            RotationManager.setRotations(
                Rotation(targetYaw, RotationManager.getPitch()),
                180.0,
                Priority.Low
            )
        }

        handler<KeyboardInputEvent> { event ->
            if (targetYaw.isNaN() || (event.forward == 0.0f && event.strafe == 0.0f)) return@handler
            event.strafe = 0.0f
            event.forward = 1.0f
        }

        handler<Render3DEvent> {
            if (!drawRadius || target == null) return@handler

            val color = getTargetColor(target!!)
            drawCircle(target!!, darken(color, 0.2f), 3.0f)
            drawCircle(target!!, color, 1.5f)
        }
    }

    override fun onDisable() {
        resetTarget()
    }

    private fun createOrbitOffsets(): MutableList<OrbitOffset> {
        val pointCount = points
        val orbitRadius = radius
        val offsets = ArrayList<OrbitOffset>(pointCount)
        for (i in 0 until pointCount) {
            val angle = i * (PI * 2.0 / pointCount)
            offsets.add(OrbitOffset(orbitRadius * cos(angle), orbitRadius * sin(angle)))
        }
        return offsets
    }

    context(nn: NonNullContext)
    private fun findClosestIndex(offsets: List<OrbitOffset>): Int {
        var closestDistance = Double.MAX_VALUE
        var closestIndex = -1
        for (i in offsets.indices) {
            val position = getOrbitPosition(offsets[i])
            val distance = nn.player.distanceToSqr(position.x, nn.player.y, position.z)
            if (distance < closestDistance) {
                closestDistance = distance
                closestIndex = i
            }
        }
        return closestIndex
    }

    context(nn: NonNullContext)
    private fun getOrbitPosition(offset: OrbitOffset): Vec3 {
        return Vec3(target!!.x + offset.x, nn.player.y, target!!.z + offset.z)
    }

    context(nn: NonNullContext)
    private fun isOverVoid(x: Double, z: Double): Boolean {
        if (nn.player.isInWater || nn.player.isInLava) return false

        val box = AABB(
            x - 0.015,
            nn.player.y,
            z - 0.015,
            x + 0.015,
            nn.player.y + nn.player.bbHeight,
            z + 0.015
        )
        val minY = Mth.floor(box.minY)
        if (minY < nn.level.minY) return true

        val minX = Mth.floor(box.minX)
        val maxX = Mth.floor(box.maxX + 1.0)
        val minZ = Mth.floor(box.minZ)
        val maxZ = Mth.floor(box.maxZ + 1.0)
        val pos = BlockPos.MutableBlockPos()

        for (blockX in minX until maxX) {
            for (blockZ in minZ until maxZ) {
                var blockY = minY
                while (blockY >= nn.level.minY) {
                    val state: BlockState = nn.level.getBlockState(pos.set(blockX, blockY, blockZ))
                    if (!state.canBeReplaced()) {
                        return false
                    }
                    blockY--
                }
            }
        }
        return true
    }

    private fun wrapIndex(index: Int, size: Int): Int {
        if (index < 0) return size - 1
        return if (index >= size) 0 else index
    }

    private fun getTargetColor(entity: LivingEntity): Color {
        if (entity is Player) {
            if (FriendManager.isFriend(entity)) {
                return Color(85, 255, 85)
            } else {
                Color(entity.teamColor)
            }
        }
        return Color.WHITE
    }

    private fun darken(color: Color, amount: Float): Color {
        val factor = 1.0f - amount.coerceIn(0.0f, 1.0f)
        return Color(
            (color.red * factor).roundToInt(),
            (color.green * factor).roundToInt(),
            (color.blue * factor).roundToInt(),
            color.alpha
        )
    }

    private fun drawCircle(entity: LivingEntity, color: Color, width: Float) {
        val tickDelta = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val x = Mth.lerp(tickDelta.toDouble(), entity.xOld, entity.x)
        val y = Mth.lerp(tickDelta.toDouble(), entity.yOld, entity.y)
        val z = Mth.lerp(tickDelta.toDouble(), entity.zOld, entity.z)
        val pointCount = points
        val orbitRadius = radius

        for (i in 0 until pointCount) {
            val angle = i * (PI * 2.0 / pointCount)
            val nextAngle = (i + 1) * (PI * 2.0 / pointCount)
            val from = Vec3(x + cos(angle) * orbitRadius, y, z + sin(angle) * orbitRadius)
            val to = Vec3(x + cos(nextAngle) * orbitRadius, y, z + sin(nextAngle) * orbitRadius)
            Render3DScheduler.addLine(from, to, color, width)
        }
    }

    private fun resetTarget() {
        target = null
        targetYaw = Float.NaN
    }

    private data class OrbitOffset(val x: Double, val z: Double)

}
