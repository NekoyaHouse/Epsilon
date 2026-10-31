package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.utils.*
import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.NoSlowdown
import com.github.epsilon.modules.impl.movement.Scaffold
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object AutoMLG : Module("Auto MLG", Category.PLAYER) {

    private val interactDelay by intSetting("Interact Delay", 60, 0, 300, 5)
    private val collectDelayTicks by intSetting("Collect Delay Ticks", 2, 0, 10, 1)

    private var mlgCompleted = true
    private val interactTimer = TimerUtils()
    private var quickCollectDelayTicks = 0

    private var shouldInteract = false
    private var pendingSlot = -1

    private var lockedRotation: Rot2f? = null
    private var waitingForRotation = false

    private var grimShouldCollect = false
    private var grimCollectRotation: Rot2f? = null
    private var grimCollectSlot = -1
    private var grimWaitingCollectRotation = false
    private val swapTimer = TimerUtils()
    private var pendingSwapBack = false

    init {
        nonNullHandler<ClientTickEvent.Pre> {
            if (Scaffold.isEmergencyPlacementActive) return@nonNullHandler

            if ((isFalling() || player.isInWater || player.isInLava) && !mlgCompleted) {
                player.setSprinting(false)
                mc.options.keySprint.setDown(false)
            }

            if (pendingSwapBack && swapTimer.passedMillise(200L)) {
                InvUtils.swapBack()
                pendingSwapBack = false
            }

            if (grimShouldCollect) {
                if (quickCollectDelayTicks > 0) {
                    quickCollectDelayTicks--
                } else if (grimWaitingCollectRotation && grimCollectRotation != null) {
                    val rotation = grimCollectRotation!!
                    RotationManager.INSTANCE.setRotations(rotation, 180.0, Priority.High)

                    if (isFacing(rotation, 2.0f, 2.5f)) {
                        InvUtils.swap(grimCollectSlot, true)
                        if (useItem()) {
                            PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
                            pendingSwapBack = true
                            swapTimer.reset()
                            completeMlgCycle()
                        } else {
                            grimWaitingCollectRotation = false
                            grimCollectRotation = null
                            grimCollectSlot = -1
                        }
                    }
                } else {
                    if (InvUtils.findInHotbar(Items.WATER_BUCKET).found()) {
                        InvUtils.swapBack()
                        completeMlgCycle()
                        return@nonNullHandler
                    }

                    val bucket = InvUtils.findInHotbar(Items.BUCKET)
                    if (bucket.found()) {
                        val waterPos = grimFindCollectableWater()
                        if (waterPos != null) {
                            val eyesPos = player.getEyePosition()
                            val hitX = Mth.clamp(eyesPos.x, waterPos.x.toDouble(), waterPos.x + 1.0)
                            val hitZ = Mth.clamp(eyesPos.z, waterPos.z.toDouble(), waterPos.z + 1.0)
                            val hitVec = Vec3(hitX, waterPos.y + 0.875, hitZ)
                            val rotation = RotationUtils.calculate(hitVec)
                            RotationManager.INSTANCE.setRotations(rotation, 180.0, Priority.High)
                            grimCollectRotation = rotation
                            grimCollectSlot = bucket.slot
                            grimWaitingCollectRotation = true
                        }
                    }
                }
                return@nonNullHandler
            }

            if (waitingForRotation && lockedRotation != null) {
                val locked = lockedRotation!!
                RotationManager.INSTANCE.setRotations(locked, 180.0, Priority.High)

                if (!isFacing(locked, 1.6f, 2.0f)) {
                    return@nonNullHandler
                }

                if (shouldInteract && pendingSlot != -1) {
                    InvUtils.swap(pendingSlot, true)
                    if (interactTimer.passedMillise(interactDelay.toLong()) && useItem()) {
                        PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
                        interactTimer.reset()
                        quickCollectDelayTicks = collectDelayTicks
                        grimShouldCollect = true
                        resetPending()
                    }
                }
                return@nonNullHandler
            }

            if (isFalling() && !grimShouldCollect) {
                mlgCompleted = false

                val waterBucket = InvUtils.findInHotbar(Items.WATER_BUCKET)

                if (waterBucket.found()) {
                    val bestPos = getBestPos()
                    if (bestPos != null) {
                        if (shouldSkipMlgPlacement(bestPos)) {
                            mlgCompleted = true
                            resetPending()
                            return@nonNullHandler
                        }

                        val dist = player.getEyePosition().distanceTo(Vec3.atCenterOf(bestPos).add(0.0, 0.5, 0.0))

                        if (dist < 10) {
                            val targetVec = Vec3(bestPos.x + 0.5, bestPos.y + 1.0, bestPos.z + 0.5)
                            val rotation = RotationUtils.calculate(targetVec)

                            RotationManager.INSTANCE.setRotations(rotation, 180.0, Priority.High)

                            if (rotation.pitch > 45) {
                                val eyesPos = player.getEyePosition()
                                val neighbor = bestPos.below()

                                val hitX = Mth.clamp(eyesPos.x, neighbor.x.toDouble(), neighbor.x + 1.0)
                                val hitZ = Mth.clamp(eyesPos.z, neighbor.z.toDouble(), neighbor.z + 1.0)
                                val hitVec = Vec3(hitX, neighbor.y + 1.0, hitZ)

                                if (eyesPos.distanceTo(hitVec) > 4.5) {
                                    return@nonNullHandler
                                }

                                val preciseRotation = RotationUtils.calculate(hitVec)
                                RotationManager.INSTANCE.setRotations(preciseRotation, 180.0, Priority.High)

                                val noSlowdown = NoSlowdown
                                if (NoSlowdown.isWorking()) {
                                    NotificationManager.warning(noSlowdown.translatedName, EpsilonTranslations.Notifications.NO_SLOWDOWN_DISABLED_WATER.getTranslatedName())
                                    noSlowdown.stop()
                                }

                                pendingSlot = waterBucket.slot
                                shouldInteract = true

                                lockedRotation = preciseRotation
                                waitingForRotation = true
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onEnable() {
        resetAll()
    }

    override fun onDisable() {
        resetAll()
    }

    private fun resetAll() {
        mlgCompleted = true
        quickCollectDelayTicks = 0
        interactTimer.reset()
        swapTimer.reset()
        grimShouldCollect = false
        grimCollectRotation = null
        grimCollectSlot = -1
        grimWaitingCollectRotation = false
        pendingSwapBack = false
        resetPending()
    }

    context(nn: NonNullContext)
    private fun grimFindCollectableWater(): BlockPos? {
        val eyesPos = player.getEyePosition()
        val base = BlockPos.containing(player.x, player.y, player.z)
        var bestPos: BlockPos? = null
        var bestDistance = Double.MAX_VALUE

        for (x in -1..1) {
            for (y in -1..1) {
                for (z in -1..1) {
                    val check = base.offset(x, y, z)
                    val fluidState: FluidState = level.getFluidState(check)
                    if (!fluidState.isSourceOfType(Fluids.WATER)) continue

                    val hitX = Mth.clamp(eyesPos.x, check.x.toDouble(), check.x + 1.0)
                    val hitZ = Mth.clamp(eyesPos.z, check.z.toDouble(), check.z + 1.0)
                    val targetVec = Vec3(hitX, check.y + 0.875, hitZ)
                    val distance = eyesPos.distanceToSqr(targetVec)

                    if (distance >= bestDistance) continue
                    if (!canSeePosition(eyesPos, targetVec, check)) continue

                    bestDistance = distance
                    bestPos = check
                }
            }
        }

        return bestPos
    }

    context(nn: NonNullContext)
    private fun canSeePosition(from: Vec3, to: Vec3, targetBlock: BlockPos): Boolean {
        val clipContext = ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, player)
        val result: BlockHitResult = level.clip(clipContext)
        if (result.type == HitResult.Type.MISS) return true
        return result.blockPos == targetBlock
    }

    context(nn: NonNullContext)
    private fun shouldSkipMlgPlacement(bestPos: BlockPos): Boolean {
        val landingPos = bestPos.below()
        val landingState: BlockState = level.getBlockState(landingPos)
        if (landingState.isAir) return false
        if (!landingState.hasProperty(BlockStateProperties.WATERLOGGED)) return false

        if (landingState.block is SlabBlock) {
            val slabType: SlabType = landingState.getValue(SlabBlock.TYPE)
            if (slabType == SlabType.BOTTOM) {
                return false
            }
        }

        return true
    }

    private fun completeMlgCycle() {
        mlgCompleted = true
        quickCollectDelayTicks = 0
        grimShouldCollect = false
        grimCollectRotation = null
        grimCollectSlot = -1
        grimWaitingCollectRotation = false
        resetPending()
    }

    context(nn: NonNullContext)
    private fun getBestPos(): BlockPos? {
        val velocity = player.deltaMovement
        val predictedPos = Vec3(player.x + velocity.x * 3, player.y, player.z + velocity.z * 3)

        val base = BlockPos.containing(predictedPos.x, player.y - 1, predictedPos.z)
        val direct = BlockPos.containing(player.x, player.y - 1, player.z)

        if (!level.getBlockState(base).isAir) {
            return base.above()
        }
        if (!level.getBlockState(direct).isAir) {
            return direct.above()
        }

        for (i in 0..20) {
            val check = base.below(i)
            if (!level.getBlockState(check).isAir) {
                return check.above()
            }
        }

        for (x in -1..1) {
            for (z in -1..1) {
                if (x == 0 && z == 0) continue
                for (i in 0..20) {
                    val check = base.offset(x, -i, z)
                    if (!level.getBlockState(check).isAir) {
                        return check.above()
                    }
                }
            }
        }

        val fallbackBase = BlockPos.containing(player.x, player.y - 1, player.z)
        for (i in 0..20) {
            val check = fallbackBase.below(i)
            if (!level.getBlockState(check).isAir) {
                return check.above()
            }
        }

        return null
    }

    context(nn: NonNullContext)
    private fun isFalling(): Boolean {
        val falling = player.fallDistance > 3.0 && !player.onGround()
        if (player.deltaMovement.y > 0) return false
        return falling
    }

    private fun isFacing(target: Rot2f, yawTolerance: Float, pitchTolerance: Float): Boolean {
        val current = RotationManager.INSTANCE.getRotation()
        val yawDiff = Math.abs(Mth.wrapDegrees(current.yaw - target.yaw))
        val pitchDiff = Math.abs(Mth.wrapDegrees(current.pitch - target.pitch))
        return yawDiff <= yawTolerance && pitchDiff <= pitchTolerance
    }

    context(nn: NonNullContext)
    private fun useItem(): Boolean {
        val result = interaction.useItem(player, InteractionHand.MAIN_HAND)
        return result.consumesAction()
    }

    private fun resetPending() {
        shouldInteract = false
        pendingSlot = -1
        lockedRotation = null
        waitingForRotation = false
    }

}
