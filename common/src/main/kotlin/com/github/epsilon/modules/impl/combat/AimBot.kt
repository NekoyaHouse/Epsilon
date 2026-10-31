package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.math.toDegrees
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.player
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BowItem
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object AimBot : Module("Aim Bot", Category.COMBAT) {

    private enum class Mode {
        AimAssist,
        BowAim,
    }

    private enum class Rotation {
        Client,
        Silent,
    }

    private val mode by enumSetting("Mode", Mode.AimAssist)
    private val rotation by enumSetting("Rotation", Rotation.Silent, { mode != Mode.AimAssist })
    private val aimStrength by intSetting("Aim Strength", 30, 1..100, 1, { mode == Mode.AimAssist })
    private val aimSmooth by intSetting("Aim Smooth", 45, 1..180, 1, { mode == Mode.AimAssist })
    private val aimTime by intSetting("Aim Time", 2, 1..10, 1, { mode == Mode.AimAssist })
    private val onlyWeapon by boolSetting("Only Weapon", false, { mode == Mode.AimAssist })
    private val lmbActivation by boolSetting("LMB Activation", false, { mode == Mode.AimAssist })
    private val ignoreWalls by boolSetting("Ignore Walls", true)
    private val reactionTime by intSetting(
        "Reaction Time", 80, 1..500, 1, { mode == Mode.AimAssist && !ignoreWalls })
    private val ignoreInvisible by boolSetting("Ignore Invis", false, { mode == Mode.AimAssist })

    private val predictTicks by intSetting("Predict Ticks", 2, 0..20, 1, { mode == Mode.BowAim })
    private val bowFov by intSetting("Bow FOV", 90, 10..360, 1, { mode == Mode.BowAim })
    private val bowRange by intSetting("Bow Range", 64, 8..128, 1, { mode == Mode.BowAim })
    private val bowSmooth by intSetting("Bow Smooth", 30, 5..100, 1, { mode == Mode.BowAim })

    private var target: Entity? = null
    private var rotationYaw = 0.0f
    private var assistAcceleration = 0.0f
    private var bowTargetYaw = Float.NaN
    private var bowTargetPitch = Float.NaN
    private var aimTicks = 0
    private val visibleTime = TimerUtils()

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            when (mode) {
                Mode.AimAssist -> updateAimAssist()
                Mode.BowAim -> updateBowAim()
            }
        }

        nonNullHandler<Render3DEvent> {
            if (mode == Mode.AimAssist) {
                if (!rotationYaw.isNaN()) {
                    player.yRot = Mth.lerp(assistAcceleration, player.yRot, rotationYaw)
                }
                return@nonNullHandler
            }

            if (mode == Mode.BowAim && rotation == Rotation.Client && !bowTargetYaw.isNaN()) {
                val tickDelta = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
                val factor = (bowSmooth / 100.0f * tickDelta).coerceIn(0.0f, 1.0f)
                val currentYaw = player.yRot
                player.yRot = currentYaw + Mth.wrapDegrees(bowTargetYaw - currentYaw) * factor
                player.xRot = Mth.lerp(factor, player.xRot, bowTargetPitch)
            }
        }
    }

    override fun onEnable() {
        if (mc.player == null) return
        target = null
        assistAcceleration = 0.0f
        aimTicks = 0
        bowTargetYaw = Float.NaN
        bowTargetPitch = Float.NaN
        visibleTime.reset()
    }

    context(nn: NonNullContext)
    private fun updateBowAim() {
        if (!isUsingBow()) {
            cancelBowAim()
            return
        }

        val bestTarget = getBestBowTarget()
        target = bestTarget
        if (bestTarget == null) {
            cancelBowAim()
            return
        }

        val charge = BowItem.getPowerForTime(player.ticksUsingItem)
        val velocity = charge * 3.0

        val predicted = predictPosition(bestTarget, predictTicks)

        val dx = predicted.x - player.x
        val dz = predicted.z - player.z
        val dy = (predicted.y + bestTarget.getEyeHeight(bestTarget.pose)) -
                (player.y + player.getEyeHeight(player.pose))

        val targetPitch = computeProjectilePitch(dx, dz, dy, velocity)
        if (targetPitch.isNaN()) {
            cancelBowAim()
            return
        }

        val targetYaw = Mth.wrapDegrees((atan2(dz, dx)).toDegrees().toFloat() - 90.0f)

        bowTargetYaw = targetYaw
        bowTargetPitch = targetPitch

        RotationManager.INSTANCE.setRotations(
            Rot2f(targetYaw, targetPitch),
            bowSmooth.toDouble(),
            Priority.Medium
        )
    }

    private fun cancelBowAim() {
        bowTargetYaw = Float.NaN
        bowTargetPitch = Float.NaN
    }

    context(nn: NonNullContext)
    private fun updateAimAssist() {
        if (lmbActivation && !mc.options.keyAttack.isDown) {
            resetAimAssist()
            return
        }

        if (onlyWeapon && !player.mainHandItem.has(DataComponents.WEAPON)) {
            resetAimAssist()
            return
        }

        val hitResult = mc.hitResult
        if (hitResult != null && hitResult.type == HitResult.Type.ENTITY) {
            aimTicks++
        } else {
            aimTicks = 0
        }

        if (aimTicks >= aimTime) {
            assistAcceleration = 0.0f
            return
        }

        val nearestTarget = getNearestTarget(5.0f)
        assistAcceleration = (assistAcceleration + aimStrength / 10000.0f).coerceIn(0.0f, 1.0f)

        if (nearestTarget != null) {
            if (!player.hasLineOfSight(nearestTarget) && !ignoreWalls) {
                visibleTime.reset()
            }

            if (!visibleTime.passedMillise(reactionTime.toLong())) {
                rotationYaw = Float.NaN
                return
            }

            if (rotationYaw.isNaN()) {
                rotationYaw = player.yRot
            }

            val deltaYaw = Mth.wrapDegrees(
                (atan2(nearestTarget.eyePosition.z - player.z, nearestTarget.eyePosition.x - player.x)).toDegrees()
                    .toFloat() - 90.0f - rotationYaw
            )
            var fixedDeltaYaw = deltaYaw
            if (fixedDeltaYaw > 180.0f) {
                fixedDeltaYaw -= 180.0f
            }
            val yawStep = Mth.abs(fixedDeltaYaw).coerceIn(-aimSmooth.toFloat(), aimSmooth.toFloat())
            val newYaw = rotationYaw + (if (fixedDeltaYaw > 0.0f) yawStep else -yawStep)
            val gcdFix = (mc.options.sensitivity().get() * 0.6 + 0.2).pow(3.0) * 1.2
            rotationYaw = (newYaw - (newYaw - rotationYaw) % gcdFix).toFloat()
        } else {
            resetAimAssist()
        }
    }

    private fun resetAimAssist() {
        rotationYaw = Float.NaN
        assistAcceleration = 0.0f
        aimTicks = 0
    }

    private fun computeProjectilePitch(dx: Double, dz: Double, dy: Double, velocity: Double): Float {
        val horizontalDist = sqrt(dx * dx + dz * dz)
        val v2 = velocity * velocity
        val v4 = v2 * v2
        val g = 0.05

        val discriminant = v4 - g * (g * horizontalDist * horizontalDist + 2.0 * dy * v2)
        if (discriminant < 0.0) return Float.NaN

        val tanTheta = (v2 - sqrt(discriminant)) / (g * horizontalDist)
        return -(atan(tanTheta)).toDegrees().toFloat()
    }

    context(nn: NonNullContext)
    private fun getBestBowTarget(): Player? {
        var best: Player? = null
        var bestScore = Double.MAX_VALUE
        val halfFov = bowFov / 2.0f
        val rangeSq = bowRange.toDouble() * bowRange.toDouble()

        for (entity in level.entitiesForRendering()) {
            val candidate = entity as? Player ?: continue
            if (shouldSkipPlayer(candidate)) continue

            val distSq = player.distanceToSqr(candidate)
            if (distSq > rangeSq) continue

            val yawDiff = abs(
                Mth.wrapDegrees(
                    getYawBetween(player.yRot, player.x, player.z, candidate.x, candidate.z) - player.yRot
                )
            )
            if (yawDiff > halfFov) continue

            if (!ignoreWalls && !player.hasLineOfSight(candidate)) continue

            val score = yawDiff * sqrt(distSq)
            if (score < bestScore) {
                best = candidate
                bestScore = score
            }
        }
        return best
    }

    context(nn: NonNullContext)
    private fun getNearestTarget(range: Float): Player? {
        var nearest: Player? = null
        var bestDistance = (range * range).toDouble()
        for (entity in level.entitiesForRendering()) {
            val candidate = entity as? Player ?: continue
            if (shouldSkipPlayer(candidate)) continue
            if (candidate.isInvisible && ignoreInvisible) continue
            if (!ignoreWalls && !player.hasLineOfSight(candidate)) continue
            val distance = player.distanceToSqr(candidate)
            if (distance < bestDistance) {
                nearest = candidate
                bestDistance = distance
            }
        }
        return nearest
    }

    context(nn: NonNullContext)
    private fun shouldSkipPlayer(candidate: Player): Boolean {
        if (candidate === player || !candidate.isAlive || candidate.isDeadOrDying) return true
        if (AntiBot.isBot(candidate)) return true
        if (FriendManager.isFriend(candidate)) return true
        return false
    }

    private fun getYawBetween(yaw: Float, srcX: Double, srcZ: Double, destX: Double, destZ: Double): Float {
        val xDist = destX - srcX
        val zDist = destZ - srcZ
        val yaw1 = (StrictMath.atan2(zDist, xDist) * 180.0 / PI).toFloat() - 90.0f
        return yaw + Mth.wrapDegrees(yaw1 - yaw)
    }

    private fun predictPosition(entity: Entity, ticks: Int): Vec3 {
        val motionX = entity.x - entity.xo
        val motionY = entity.y - entity.yo
        val motionZ = entity.z - entity.zo
        return entity.position().add(motionX * ticks, motionY * ticks, motionZ * ticks)
    }

    context(nn: NonNullContext)
    private fun isUsingBow(): Boolean {
        return player.useItem.item is BowItem
    }

}
