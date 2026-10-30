package com.github.epsilon.modules.impl.player

import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.KillAura
import com.github.epsilon.modules.impl.movement.Scaffold
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.NonNullContext
import com.github.epsilon.utils.interaction
import com.github.epsilon.utils.player
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RaytraceUtils
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.world
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color

object BedNuker : Module("Bed Nuker", Category.PLAYER) {

    private val range by doubleSetting("Range", 4.5, 1.0, 6.0, 0.1)
    private val throughWalls by boolSetting("Through Walls", false)
    private val rotate by boolSetting("Rotate", true)
    private val rotationSpeed by intSetting("Rotation Speed", 180, 10, 180, 10, dependency = { rotate })
    private val swingHand by boolSetting("Swing Hand", true)
    private val esp by boolSetting("ESP", true)

    private var currentTarget: BlockPos? = null
    private var currentFace: Direction? = null
    private var startedBreaking = false
    private var trackedBedPos: BlockPos? = null
    private var trackedBedFace: Direction? = null

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            if (shouldPauseBreaking()) {
                clearBreakingState()
                return@nonNullListener
            }

            val maxRange = range
            val eyes = player.getEyePosition()
            val bedTargetNow: Boolean

            if (isCurrentBreakStillValid(eyes, maxRange)) {
                bedTargetNow = isBedBlock(currentTarget!!)
            } else {
                val nextTarget = findTarget()
                if (nextTarget == null) {
                    resetBreaking()
                    return@nonNullListener
                }
                resetBreaking()
                currentTarget = nextTarget.pos
                currentFace = nextTarget.face
                trackedBedPos = nextTarget.sourceBedPos
                trackedBedFace = nextTarget.sourceBedFace
                bedTargetNow = nextTarget.bedTarget
            }

            if (rotate) {
                val rot = calculate(currentAimPoint(bedTargetNow))
                if (bedTargetNow) {
                    RotationManager.INSTANCE.setRotations(rot, rotationSpeed.toDouble(), { candidate -> isBedHit(candidate, maxRange) }, Priority.Low)
                } else {
                    RotationManager.INSTANCE.setRotations(rot, rotationSpeed.toDouble(), Priority.Low)
                }
            }

            if (!startedBreaking) {
                interaction.startDestroyBlock(currentTarget!!, currentFace!!)
                startedBreaking = true
            }

            interaction.continueDestroyBlock(currentTarget!!, currentFace!!)

            if (swingHand) {
                swingHand(InteractionHand.MAIN_HAND)
            }

            if (world.getBlockState(currentTarget!!).isAir) {
                resetBreaking()
            }
        }

        nonNullListener<Render3DEvent> {
            if (!esp || currentTarget == null) return@nonNullListener

            val target = currentTarget ?: return@nonNullListener
            val shape = world.getBlockState(target).getShape(world, target)
            val box = if (shape.isEmpty) AABB(target) else shape.bounds().move(target)
            Render3DScheduler.addFilledBox(box, Color(255, 0, 0, 50))
        }
    }

    override fun onDisable() {
        resetBreaking()
        trackedBedPos = null
        trackedBedFace = null
    }

    fun getCurrentTarget(): BlockPos? {
        return currentTarget
    }

    val isBreakingTarget: Boolean
        get() = isEnabled && startedBreaking && currentTarget != null

    private fun isCurrentBreakStillValid(eyes: Vec3, maxRange: Double): Boolean {
        val target = currentTarget ?: return false
        val face = currentFace ?: return false
        val state = worldState(target) ?: return false
        if (state.isAir) {
            return false
        }
        if (eyes.distanceToSqr(Vec3.atCenterOf(target)) > maxRange * maxRange + 1.0) {
            return false
        }
        val bedPos = trackedBedPos
        if (bedPos != null && !isBedBlock(target) && !isBedBlock(bedPos)) {
            return false
        }
        return true
    }

    private fun currentAimPoint(bedTarget: Boolean): Vec3 {
        if (bedTarget) {
            return getFaceAimPoint(currentTarget!!, currentFace!!)
        }
        val center = Vec3.atCenterOf(currentTarget!!)
        val face = currentFace!!
        return center.add(face.stepX * 0.5, face.stepY * 0.5, face.stepZ * 0.5)
    }

    private fun resetBreaking() {
        if (startedBreaking) {
            NonNullContext.current?.interaction?.stopDestroyBlock()
        }
        clearBreakingState()
    }

    private fun shouldPauseBreaking(): Boolean {
        if (mc.options.keyAttack.isDown) return true
        if (Scaffold.isEnabled) return true
        return KillAura.isEnabled && KillAura.target != null
    }

    private fun findTarget(): TargetData? {
        val followUpTarget = getTrackedBedTarget()
        if (followUpTarget != null) {
            return followUpTarget
        }

        val manager = NonNullContext.current ?: return null
        val player = manager.player
        val world = manager.world
        val maxRange = range
        val maxRangeSq = maxRange * maxRange
        val eyes = player.getEyePosition()
        val playerPos = player.blockPosition()
        val radius = Mth.ceil(maxRange)
        var best: TargetData? = null

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = playerPos.offset(x, y, z)
                    if (world.getBlockState(pos).block !is BedBlock) {
                        continue
                    }

                    if (eyes.distanceToSqr(Vec3.atCenterOf(pos)) > maxRangeSq) {
                        continue
                    }

                    val candidate = getTargetData(pos, maxRange, eyes) ?: continue

                    if (best == null
                        || candidate.priority < best.priority
                        || (candidate.priority == best.priority && candidate.distanceSq < best.distanceSq)
                    ) {
                        best = candidate
                    }
                }
            }
        }

        return best
    }

    private fun getTrackedBedTarget(): TargetData? {
        val bedPos = trackedBedPos ?: return null
        val bedFace = trackedBedFace ?: return null

        if (worldState(bedPos)?.block !is BedBlock) {
            trackedBedPos = null
            trackedBedFace = null
            return null
        }

        val manager = NonNullContext.current ?: return null
        val maxRange = range
        val eyes = manager.player.getEyePosition()
        val target = getBedFaceTarget(bedPos, bedFace, maxRange, eyes)
        if (target != null && target.bedTarget) {
            return target
        }

        return null
    }

    private fun getTargetData(bedPos: BlockPos, maxRange: Double, eyes: Vec3): TargetData? {
        val directTarget = getDirectBedTarget(bedPos, maxRange, eyes)
        if (directTarget != null) {
            return directTarget
        }

        var coveredFaceTarget: TargetData? = null

        for (bedFace in Direction.values()) {
            val candidate = getBedFaceTarget(bedPos, bedFace, maxRange, eyes) ?: continue

            if (coveredFaceTarget == null || candidate.distanceSq < coveredFaceTarget.distanceSq) {
                coveredFaceTarget = candidate
            }
        }

        return coveredFaceTarget
    }

    private fun getDirectBedTarget(bedPos: BlockPos, maxRange: Double, eyes: Vec3): TargetData? {
        val world = NonNullContext.current?.world ?: return null
        var best: TargetData? = null

        for (face in Direction.values()) {
            if (face == Direction.DOWN) continue

            val adjacentPos = bedPos.relative(face)
            if (!world.getBlockState(adjacentPos).isAir) {
                continue
            }

            val aimPoint = getFaceAimPoint(bedPos, face)
            val distanceSq = eyes.distanceToSqr(aimPoint)
            if (distanceSq > maxRange * maxRange) {
                continue
            }

            val candidate = TargetData(
                bedPos,
                face,
                RotationUtils.calculate(aimPoint),
                distanceSq,
                0,
                true,
                bedPos,
                face,
            )

            if (best == null || candidate.distanceSq < best.distanceSq) {
                best = candidate
            }
        }

        return best
    }

    private fun getBedFaceTarget(bedPos: BlockPos, bedFace: Direction, maxRange: Double, eyes: Vec3): TargetData? {
        val world = NonNullContext.current?.world ?: return null
        val adjacentPos = bedPos.relative(bedFace)
        val adjacentState: BlockState = world.getBlockState(adjacentPos)
        if (adjacentState.isAir) {
            return null
        }

        if (!throughWalls) {
            val face = getClosestFace(adjacentPos, eyes)
            val blockerRotation = RotationUtils.calculate(adjacentPos, face)
            return TargetData(
                adjacentPos,
                face,
                blockerRotation,
                eyes.distanceToSqr(Vec3.atCenterOf(adjacentPos)),
                1,
                false,
                bedPos,
                bedFace,
            )
        }

        val face = getClosestFace(adjacentPos, eyes)
        val blockerRotation = RotationUtils.calculate(adjacentPos, face)
        return TargetData(
            adjacentPos,
            face,
            blockerRotation,
            eyes.distanceToSqr(Vec3.atCenterOf(adjacentPos)),
            1,
            false,
            bedPos,
            bedFace,
        )
    }

    private fun getFaceAimPoint(bedPos: BlockPos, bedFace: Direction): Vec3 {
        val center = Vec3.atCenterOf(bedPos)
        if (bedFace == Direction.UP) {
            return Vec3(center.x, bedPos.y + 0.5625, center.z)
        }

        return center.add(
            bedFace.stepX * 0.42,
            -0.10,
            bedFace.stepZ * 0.42,
        )
    }

    private fun getClosestFace(pos: BlockPos, eyes: Vec3): Direction {
        val center = Vec3.atCenterOf(pos)
        var bestFace = Direction.UP
        var bestDistance = Double.MAX_VALUE

        for (direction in Direction.values()) {
            val facePoint = center.add(
                direction.stepX * 0.5,
                direction.stepY * 0.5,
                direction.stepZ * 0.5,
            )
            val distance = eyes.distanceToSqr(facePoint)
            if (distance < bestDistance) {
                bestDistance = distance
                bestFace = direction
            }
        }

        return bestFace
    }

    private fun isBedHit(rotation: Rot2f, rayRange: Double): Boolean {
        val result = RaytraceUtils.raytrace(rotation, rayRange)
        return result is BlockHitResult && isBedBlock(result.blockPos)
    }

    private fun isBedBlock(pos: BlockPos): Boolean {
        return worldState(pos)?.block is BedBlock
    }

    private fun worldState(pos: BlockPos): BlockState? {
        return NonNullContext.current?.world?.getBlockState(pos)
    }

    private data class TargetData(
        val pos: BlockPos,
        val face: Direction,
        val rotation: Rot2f,
        val distanceSq: Double,
        val priority: Int,
        val bedTarget: Boolean,
        val sourceBedPos: BlockPos,
        val sourceBedFace: Direction,
    )

    private fun clearBreakingState() {
        currentTarget = null
        currentFace = null
        startedBreaking = false
        trackedBedPos = null
        trackedBedFace = null
    }

}
