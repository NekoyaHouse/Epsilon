package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.managers.ExtrapolationManager
import com.github.epsilon.managers.rotation.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.combat.DamageUtils
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.interaction
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.player
import com.github.epsilon.utils.timer.TimerUtils
import com.github.epsilon.utils.world.BlockUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sign

object SafeAnchor : Module("Safe Anchor", Category.COMBAT) {

    private enum class Mode {
        Assist,
        Auto,
    }

    private enum class PlaceMode {
        Adaptive,
        Cover,
    }

    private enum class Order {
        ShieldFirst,
        ChargeFirst,
    }

    private enum class Stage {
        IDLE,
        PLACE_ANCHOR,
        PLACE_SHIELD,
        CHARGE,
        DETONATE,
        WAIT_SHIELD,
        WAIT_ANCHOR,
    }

    private data class RenderBox(val pos: BlockPos, val startTime: Long)

    private val triggerKey by keybindSetting("Trigger Key", -1)

    private val mode by enumSetting("Mode", Mode.Auto)
    private val placeMode by enumSetting("Place Mode", PlaceMode.Adaptive)
    private val order by enumSetting("Order", Order.ShieldFirst)

    private val rotate by boolSetting("Rotate", true)
    private val rotationSpeedMin by intSetting("Rotation Speed Min", 15, 1, 360, 1)
    private val rotationSpeedMax by intSetting("Rotation Speed Max", 30, 1, 360, 1)
    private val strict by boolSetting("Strict", true)
    private val breakCrystal by boolSetting("Break Crystal", false)
    private val swapBack by boolSetting("SwapBack", true)

    private val anchorPlaceMinDelay by doubleSetting("Anchor Place Min Delay", 0.0, 0.0, 50.0, 0.1)
    private val anchorPlaceMaxDelay by doubleSetting("Anchor Place Max Delay", 2.5, 0.0, 50.0, 0.1)

    private val sgShield: SettingGroup = settingGroup("Shield")
    private val shieldPlaceMinDelay by doubleSetting("Shield Place Min Delay", 0.0, 0.0, 50.0, 0.1).apply { group(sgShield) }
    private val shieldPlaceMaxDelay by doubleSetting("Shield Place Max Delay", 2.5, 0.0, 50.0, 0.1).apply { group(sgShield) }
    private val multiShield by boolSetting("Multi Shield", false, dependency = { mode == Mode.Auto }).apply { group(sgShield) }
    private val multiShieldChance by intSetting("Multi Shield Chance", 50, 0, 100, 1, dependency = { multiShield }).apply { group(sgShield) }
    private val maxExtraShields by intSetting("Max Extra Shields", 1, 1, 5, 1, dependency = { multiShield }).apply { group(sgShield) }
    private val shieldDistanceMin by intSetting("Shield Distance Min", 1, 1, 3, 1, dependency = { mode == Mode.Auto }).apply { group(sgShield) }
    private val shieldDistanceMax by intSetting("Shield Distance Max", 1, 1, 3, 1, dependency = { mode == Mode.Auto }).apply { group(sgShield) }

    private val chargeMinDelay by doubleSetting("Charge Min Delay", 0.0, 0.0, 50.0, 0.1)
    private val chargeMaxDelay by doubleSetting("Charge Max Delay", 2.5, 0.0, 50.0, 0.1)

    private val detonateMinDelay by doubleSetting("Detonate Min Delay", 0.0, 0.0, 50.0, 0.1)
    private val detonateMaxDelay by doubleSetting("Detonate Max Delay", 2.5, 0.0, 50.0, 0.1)
    private val detonateSlot by intSetting("Detonate Slot", 1, 1, 9, 1)
    private val minHealth by doubleSetting("Min Health", 4.0, 0.0, 20.0, 0.5)

    val prediction by boolSetting("Prediction", false)
    val predictionStrength by doubleSetting("Prediction Strength", 3.0, 0.5, 20.0, 0.1, dependency = { prediction })

    private val multiCharge by boolSetting("Multi Charge", false)
    private val multiChargeChance by intSetting("Multi Charge Chance", 50, 0, 100, 1, dependency = { multiCharge })
    private val minExtraCharges by intSetting("Min Extra Charges", 1, 1, 3, 1, dependency = { multiCharge })
    private val maxExtraCharges by intSetting("Max Extra Charges", 1, 1, 3, 1, dependency = { multiCharge })

    private val render by boolSetting("Render", true)

    private val lineColor by colorSetting("Line Color", Color(255, 255, 255, 255), dependency = { render })
    private val sideColor by colorSetting("Side Color", Color(255, 255, 255, 50), dependency = { render })
    private val lineWidth by doubleSetting("Line Width", 1.0, 0.0, 5.0, 0.1, dependency = { render })

    private var stage = Stage.IDLE
    private var currentAnchorPos: BlockPos? = null
    private var shieldPos: BlockPos? = null
    private var shieldSide: Direction? = null
    private var shieldSidePlacement = false
    private var shieldDiagonal = false
    private var targetRotation: Rot2f? = null
    private var targetBlockPos: BlockPos? = null
    private var originalRotation: Rot2f? = null
    private var returningRotation = false
    private var originalSlot = -1
    private var pendingAnchorPos: BlockPos? = null
    private var pendingAnchorHit: BlockHitResult? = null
    private var stageTicksElapsed = 0
    private var wasKeyDown = false
    private var holdingSequenceStarted = false
    private var cooldownMs = 0
    private var chargesRemaining = -1
    private val placedShields: MutableList<BlockPos> = ArrayList()
    private val renderBoxes: MutableList<RenderBox> = ArrayList()
    private val actionTimer = TimerUtils()

    private const val STAGE_TIMEOUT_TICKS = 60

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            if (mc.gui.screen() != null) return@nonNullHandler

            val key = triggerKey
            if (key == -1) return@nonNullHandler

            val keyDown = KeybindUtils.isPressed(key)
            val newPress = keyDown && !wasKeyDown
            wasKeyDown = keyDown

            if (stage == Stage.IDLE && !keyDown) {
                holdingSequenceStarted = false
            }

            if (newPress || (keyDown && stage == Stage.IDLE && holdingSequenceStarted)) {
                abortConflicting()
                resetState()
                holdingSequenceStarted = true
                originalSlot = player.inventory.selectedSlot
                originalRotation = Rot2f(player.yRot, player.xRot)
                stage = Stage.PLACE_ANCHOR
                scheduleCooldown(anchorPlaceMinDelay, anchorPlaceMaxDelay)
            }

            if (stage == Stage.IDLE) return@nonNullHandler

            while (stage != Stage.IDLE && actionTimer.passedMillise(cooldownMs.toLong())) {
                if (stage != Stage.WAIT_SHIELD && stage != Stage.WAIT_ANCHOR) {
                    stageTicksElapsed++
                    if (stageTicksElapsed > STAGE_TIMEOUT_TICKS) {
                        resetState()
                        return@nonNullHandler
                    }
                }

                val anchorPos = currentAnchorPos
                if (anchorPos != null && stage != Stage.PLACE_ANCHOR && !level.getBlockState(anchorPos).`is`(Blocks.RESPAWN_ANCHOR)) {
                    resetState()
                    return@nonNullHandler
                }

                applyTargetRotation()

                if (returningRotation) {
                    if (rotationAimed()) {
                        targetRotation = null
                        returningRotation = false
                        resetState()
                    }
                    return@nonNullHandler
                }

                val rotation = targetRotation
                if (rotation != null && !rotationAimed()) {
                    return@nonNullHandler
                }

                when (stage) {
                    Stage.PLACE_ANCHOR -> doPlaceAnchor()

                    Stage.PLACE_SHIELD -> doPlaceShield()

                    Stage.CHARGE -> doCharge()

                    Stage.DETONATE -> doDetonate()

                    Stage.WAIT_SHIELD -> {
                        doWaitShield()
                        if (stage == Stage.WAIT_SHIELD) return@nonNullHandler
                    }

                    Stage.WAIT_ANCHOR -> {
                        doWaitAnchor()
                        if (stage == Stage.WAIT_ANCHOR) return@nonNullHandler
                    }

                    Stage.IDLE -> return@nonNullHandler
                }
            }
        }

        nonNullHandler<Render3DEvent> {
            val time = System.currentTimeMillis()
            renderBoxes.removeAll { box -> time - box.startTime > 1000 }

            for (box in renderBoxes) {
                val progress = Mth.clamp((time - box.startTime).toFloat() / 1000.0f, 0.0f, 1.0f)
                val alpha = 1.0f - progress

                val fc = sideColor
                val fillAlpha = Mth.clamp((fc.alpha * alpha).toInt(), 0, 255)
                Render3DScheduler.addFilledBox(AABB(box.pos), Color(fc.red, fc.green, fc.blue, fillAlpha))

                val bc = lineColor
                val lineAlpha = Mth.clamp((bc.alpha * alpha).toInt(), 0, 255)
                Render3DScheduler.addOutlineBox(AABB(box.pos), Color(bc.red, bc.green, bc.blue, lineAlpha).rgb, lineWidth.toFloat())
            }

            if (mode != Mode.Assist || currentAnchorPos == null) return@nonNullHandler

            val anchorCenter = Vec3.atCenterOf(currentAnchorPos!!)
            val playerFeet = player.position()
            Render3DScheduler.addLine(anchorCenter, playerFeet, lineColor, lineWidth.toFloat())

            if (stage == Stage.WAIT_ANCHOR) {
                val box = AABB(currentAnchorPos!!)
                Render3DScheduler.addFilledBox(box, sideColor)
                Render3DScheduler.addOutlineBox(box, lineColor.rgb, lineWidth.toFloat())
            }
        }
    }

    override fun onEnable() {
        resetState()
        wasKeyDown = false
        holdingSequenceStarted = false
    }

    override fun onDisable() {
        resetState()
    }

    context(nn: NonNullContext)
    private fun doPlaceAnchor() {
        if (breakCrystal && DamageUtils.breakCrosshairCrystal()) {
            scheduleCooldown(anchorPlaceMinDelay, anchorPlaceMaxDelay)
            return
        }

        if (pendingAnchorPos == null) {
            val hit = mc.hitResult
            if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) {
                resetState()
                return
            }

            val hitPos = hit.blockPos
            if (level.getBlockState(hitPos).`is`(Blocks.RESPAWN_ANCHOR)) {
                currentAnchorPos = hitPos
                targetRotation = null
                targetBlockPos = null
                advanceFromPlaceAnchor()
                return
            }

            val hitState = level.getBlockState(hitPos)
            val anchorStack = Items.RESPAWN_ANCHOR.defaultInstance
            val placePos = if (!hitState.isAir && BlockUtils.canBeReplacedWith(hitState, anchorStack)) {
                hitPos
            } else {
                hitPos.relative(hit.direction)
            }
            if (!BlockUtils.isWithinRange(Vec3.atCenterOf(placePos)) || !canPlaceAnchor(placePos, anchorStack)) {
                resetState()
                return
            }

            pendingAnchorPos = placePos
            pendingAnchorHit = hit

            if (rotate) {
                targetRotation = getTargetRotation(hit.location)
                targetBlockPos = pendingAnchorPos
                return
            }
        }

        if (rotate) {
            if (!rotationAimed()) return
        }

        val anchor = InvUtils.findInHotbar(Items.RESPAWN_ANCHOR)
        if (!anchor.found()) {
            resetState()
            return
        }

        InvUtils.swap(anchor.slot, false)
        interaction.useItemOn(player, InteractionHand.MAIN_HAND, pendingAnchorHit!!)
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
        currentAnchorPos = pendingAnchorPos
        targetRotation = null
        targetBlockPos = null
        pendingAnchorPos = null
        pendingAnchorHit = null
        addRenderBox(currentAnchorPos!!)
        advanceFromPlaceAnchor()
    }

    context(nn: NonNullContext)
    private fun canPlaceAnchor(pos: BlockPos, item: ItemStack): Boolean {
        return BlockUtils.canBeReplacedWith(level.getBlockState(pos), item) && !BlockUtils.hasBlockingEntity(AABB(pos))
    }

    context(nn: NonNullContext)
    private fun doPlaceShield() {
        shieldPos = null

        if (hasExistingShield() && !shouldPlaceExtraShield()) {
            targetRotation = null
            targetBlockPos = null
            advanceFromPlaceShield()
            return
        }

        shieldPos = findShieldPos()
        if (shieldPos == null) {
            shieldPos = findFallbackShieldPos()
        }
        val shield = shieldPos
        if (shield == null) {
            if (placedShields.isNotEmpty()) {
                targetRotation = null
                targetBlockPos = null
                advanceFromPlaceShield()
            } else if (isExplosionSafe()) {
                targetRotation = null
                targetBlockPos = null
                scheduleCooldown(chargeMinDelay, chargeMaxDelay)
                stage = Stage.CHARGE
            } else {
                abortSequence()
            }
            return
        }

        val glowstone = InvUtils.findInHotbar(Items.GLOWSTONE)
        if (!glowstone.found()) {
            abortSequence()
            return
        }

        InvUtils.swap(glowstone.slot, false)

        val shieldInteractPos = if (shieldSidePlacement && shieldSide != null) {
            Vec3.atCenterOf(currentAnchorPos!!)
        } else {
            Vec3.atCenterOf(shield.below())
        }
        if (!BlockUtils.isWithinRange(shieldInteractPos)) {
            abortSequence()
            return
        }

        if (rotate) {
            if (targetRotation == null) {
                val lookTarget = if (shieldSidePlacement && shieldSide != null) {
                    val side = shieldSide!!
                    Vec3.atCenterOf(currentAnchorPos!!).add(side.stepX * 0.5, side.stepY * 0.5, side.stepZ * 0.5)
                } else {
                    Vec3.atCenterOf(shield.below())
                }
                targetRotation = getTargetRotation(lookTarget)
                targetBlockPos = if (shieldSidePlacement && shieldSide != null) currentAnchorPos else shield.below()
                return
            }
            if (!rotationAimed()) return
        }

        if (shieldSidePlacement && shieldSide != null) {
            setShiftState(true)
            if (!placeAtAvoiding(shield, currentAnchorPos!!)) {
                setShiftState(false)
                val fallback = findFallbackShieldPos()
                if (fallback == null) {
                    abortSequence()
                    return
                }
                shieldPos = fallback
                shieldSidePlacement = false
                if (!BlockUtils.isWithinRange(Vec3.atCenterOf(fallback.below()))) {
                    abortSequence()
                    return
                }
                if (rotate) {
                    targetRotation = getTargetRotation(Vec3.atCenterOf(fallback.below()))
                    targetBlockPos = fallback.below()
                    return
                }
            }
            setShiftState(false)
        }
        interactBlock(shield.below(), Direction.UP)

        placedShields.add(shield)
        addRenderBox(shield)
        targetRotation = null
        targetBlockPos = null

        if (shouldPlaceExtraShield()) {
            shieldPos = null
            scheduleCooldown(shieldPlaceMinDelay, shieldPlaceMaxDelay)
            return
        }

        shieldPos = null
        advanceFromPlaceShield()
    }

    context(nn: NonNullContext)
    private fun doCharge() {
        val state = level.getBlockState(currentAnchorPos!!)
        if (!state.`is`(Blocks.RESPAWN_ANCHOR)) {
            abortSequence()
            return
        }

        if (state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
            advanceFromCharge()
            return
        }

        if (!BlockUtils.isWithinRange(Vec3.atCenterOf(currentAnchorPos!!))) {
            abortSequence()
            return
        }

        if (chargesRemaining < 0) {
            chargesRemaining = calculateChargeActions(state)
        }

        if (chargesRemaining <= 0) {
            scheduleCooldown(detonateMinDelay, detonateMaxDelay)
            stage = Stage.DETONATE
            return
        }

        if (rotate) {
            if (targetRotation == null) {
                targetRotation = getTargetRotation(Vec3.atCenterOf(currentAnchorPos!!))
                targetBlockPos = currentAnchorPos
                return
            }
            if (!rotationAimed()) return
        }

        val glowstone = InvUtils.findInHotbar(Items.GLOWSTONE)
        if (!glowstone.found()) {
            abortSequence()
            return
        }

        InvUtils.swap(glowstone.slot, false)
        targetRotation = null
        targetBlockPos = null
        interactBlock(currentAnchorPos!!, BlockUtils.getVisibleFace(currentAnchorPos!!))
        chargesRemaining--

        if (chargesRemaining > 0) {
            scheduleCooldown(chargeMinDelay, chargeMaxDelay)
            stage = Stage.CHARGE
        } else {
            advanceFromCharge()
        }
    }

    private fun calculateChargeActions(state: BlockState): Int {
        val currentCharge = state.getValue(RespawnAnchorBlock.CHARGE)
        val availableCharges = maxOf(0, 4 - currentCharge)
        if (availableCharges <= 0) return 0

        var charges = 1
        if (multiCharge && ThreadLocalRandom.current().nextInt(100) < multiChargeChance) {
            charges += randomInclusive(minExtraCharges, maxExtraCharges)
        }

        return minOf(charges, availableCharges)
    }

    context(nn: NonNullContext)
    private fun doDetonate() {
        val state = level.getBlockState(currentAnchorPos!!)
        if (!state.`is`(Blocks.RESPAWN_ANCHOR)) {
            abortSequence()
            return
        }

        if (!BlockUtils.isWithinRange(Vec3.atCenterOf(currentAnchorPos!!))) {
            abortSequence()
            return
        }

        if (!isExplosionSafe()) {
            abortSequence()
            return
        }

        if (rotate) {
            if (targetRotation == null) {
                targetRotation = getTargetRotation(Vec3.atCenterOf(currentAnchorPos!!))
                targetBlockPos = currentAnchorPos
                return
            }
            if (!rotationAimed()) return
        }

        if (!selectExplosionItem()) {
            abortSequence()
            return
        }

        targetRotation = null
        targetBlockPos = null
        interactBlock(currentAnchorPos!!, BlockUtils.getVisibleFace(currentAnchorPos!!))
        finishSequence()
    }

    context(nn: NonNullContext)
    private fun finishSequence() {
        switchBackToOriginal()
        if (rotate && originalRotation != null) {
            targetRotation = originalRotation
            targetBlockPos = null
            returningRotation = true
        } else {
            resetState()
        }
    }

    context(nn: NonNullContext)
    private fun advanceFromPlaceAnchor() {
        if (mode == Mode.Assist) {
            cooldownMs = 0
            actionTimer.reset()
            stage = Stage.WAIT_SHIELD
            return
        }

        val alreadyCharged = isAnchorCharged(currentAnchorPos)

        if (hasExistingShield()) {
            if (alreadyCharged) {
                scheduleCooldown(detonateMinDelay, detonateMaxDelay)
                stage = Stage.DETONATE
            } else {
                scheduleCooldown(chargeMinDelay, chargeMaxDelay)
                stage = Stage.CHARGE
            }
            return
        }

        if (order == Order.ChargeFirst) {
            if (alreadyCharged) {
                scheduleCooldown(shieldPlaceMinDelay, shieldPlaceMaxDelay)
                stage = Stage.PLACE_SHIELD
            } else {
                scheduleCooldown(chargeMinDelay, chargeMaxDelay)
                stage = Stage.CHARGE
            }
        } else {
            scheduleCooldown(shieldPlaceMinDelay, shieldPlaceMaxDelay)
            stage = Stage.PLACE_SHIELD
        }
    }

    context(nn: NonNullContext)
    private fun advanceFromPlaceShield() {
        if (mode == Mode.Assist) {
            cooldownMs = 0
            actionTimer.reset()
            stage = Stage.WAIT_ANCHOR
            return
        }

        val alreadyCharged = isAnchorCharged(currentAnchorPos)

        if (alreadyCharged) {
            scheduleCooldown(detonateMinDelay, detonateMaxDelay)
            stage = Stage.DETONATE
        } else if (order == Order.ChargeFirst) {
            scheduleCooldown(detonateMinDelay, detonateMaxDelay)
            stage = Stage.DETONATE
        } else {
            scheduleCooldown(chargeMinDelay, chargeMaxDelay)
            stage = Stage.CHARGE
        }
    }

    context(nn: NonNullContext)
    private fun advanceFromCharge() {
        if (order == Order.ChargeFirst) {
            if (hasExistingShield()) {
                scheduleCooldown(detonateMinDelay, detonateMaxDelay)
                stage = Stage.DETONATE
            } else {
                scheduleCooldown(shieldPlaceMinDelay, shieldPlaceMaxDelay)
                stage = Stage.PLACE_SHIELD
            }
        } else {
            scheduleCooldown(detonateMinDelay, detonateMaxDelay)
            stage = Stage.DETONATE
        }
    }

    context(nn: NonNullContext)
    private fun findShieldPos(): BlockPos? {
        shieldSidePlacement = false
        shieldDiagonal = false

        val min = minOf(shieldDistanceMin, shieldDistanceMax)
        val max = maxOf(shieldDistanceMin, shieldDistanceMax)

        var pos = findDistanceShieldPos(min, max)
        if (pos != null) return pos

        if (min > 1 || max < 1) {
            pos = findDistanceShieldPos(1, 1)
            if (pos != null) return pos
        }

        val playerPos = getShieldPlayerPos()
        val anchorPos = Vec3.atCenterOf(currentAnchorPos!!)
        val toAnchor = anchorPos.subtract(playerPos)
        val distance = toAnchor.length()
        if (distance < 1.0) return null

        val dir = toAnchor.scale(1.0 / distance)
        val start = minOf(distance - 0.5, 3.5)
        val end = 1.5
        var d = start
        while (d >= end) {
            val point = playerPos.add(dir.scale(d))
            val pathPos = BlockPos.containing(point)
            if (trySetShieldPlacement(pathPos) && isShieldBlocking(pathPos)) {
                return pathPos
            }
            d -= 0.5
        }

        if (placeMode == PlaceMode.Cover && player.isShiftKeyDown) {
            val dx = playerPos.x - (currentAnchorPos!!.x + 0.5)
            val dz = playerPos.z - (currentAnchorPos!!.z + 0.5)
            val absX = abs(dx)
            val absZ = abs(dz)
            val xDir = if (dx > 0) Direction.EAST else Direction.WEST
            val zDir = if (dz > 0) Direction.SOUTH else Direction.NORTH
            val diagonalArea = absX > 0 && absZ > 0 && minOf(absX, absZ) / maxOf(absX, absZ) > 0.5

            val sides = if (diagonalArea) {
                arrayOf(xDir, zDir)
            } else {
                arrayOf(if (absX > absZ) xDir else zDir)
            }
            val glowStack = Items.GLOWSTONE.defaultInstance
            for (side in sides) {
                val sidePos = currentAnchorPos!!.relative(side)
                if (isValidShieldPos(sidePos, side, glowStack)) {
                    if (isSupportedOnGround(sidePos)) {
                        shieldSide = Direction.UP
                        shieldSidePlacement = false
                        shieldDiagonal = false
                        return sidePos
                    }
                    if (trySetShieldPlacement(sidePos)) {
                        shieldDiagonal = false
                        return sidePos
                    }
                }
            }
        }

        return null
    }

    context(nn: NonNullContext)
    private fun findDistanceShieldPos(min: Int, max: Int): BlockPos? {
        val candidates = ArrayList<BlockPos>()
        for (dx in -max..max) {
            for (dz in -max..max) {
                val dist = maxOf(abs(dx), abs(dz))
                if (dist < min || dist > max) continue
                val pos = currentAnchorPos!!.offset(dx, 0, dz)
                val glowStack = Items.GLOWSTONE.defaultInstance
                if (isValidShieldPos(pos, Direction.UP, glowStack) && isSupportedOnGround(pos)) {
                    candidates.add(pos)
                }
            }
        }

        if (candidates.isEmpty()) return null
        return candidates[ThreadLocalRandom.current().nextInt(candidates.size)]
    }

    context(nn: NonNullContext)
    private fun findFallbackShieldPos(): BlockPos? {
        if (!player.isShiftKeyDown) return null

        val playerPos = getShieldPlayerPos()
        val anchorPos = Vec3.atCenterOf(currentAnchorPos!!)
        val dx = playerPos.x - anchorPos.x
        val dz = playerPos.z - anchorPos.z
        val facing = Direction.getNearest(sign(dx).toInt(), 0, sign(dz).toInt(), Direction.NORTH) ?: Direction.NORTH
        val glowStack = Items.GLOWSTONE.defaultInstance

        val sidePos = currentAnchorPos!!.relative(facing)
        if (isValidShieldPos(sidePos, facing, glowStack) && isSupportedOnGround(sidePos)) {
            shieldSide = Direction.UP
            shieldSidePlacement = false
            return sidePos
        }

        for (side in Direction.Plane.HORIZONTAL) {
            if (side == facing) continue
            val pos = currentAnchorPos!!.relative(side)
            if (!isValidShieldPos(pos, side, glowStack)) continue
            if (isSupportedOnGround(pos)) {
                shieldSide = Direction.UP
                shieldSidePlacement = false
                return pos
            }
        }

        for (side in Direction.Plane.HORIZONTAL) {
            val pos = currentAnchorPos!!.relative(side)
            if (!isValidShieldPos(pos, side, glowStack)) continue
            if (trySetShieldPlacement(pos)) return pos
        }

        return null
    }

    context(nn: NonNullContext)
    private fun trySetShieldPlacement(pos: BlockPos): Boolean {
        if (pos == currentAnchorPos) return false
        val glowStack = Items.GLOWSTONE.defaultInstance
        if (!BlockUtils.canBeReplacedWith(level.getBlockState(pos), glowStack)) return false
        if (BlockUtils.hasBlockingEntity(AABB(pos))) return false

        val below = pos.below()
        if (below != currentAnchorPos) {
            val belowState = level.getBlockState(below)
            if (!belowState.isAir && !BlockUtils.canBeReplacedWith(belowState, glowStack) &&
                !belowState.getCollisionShape(level, below).isEmpty
            ) {
                shieldSide = Direction.UP
                shieldSidePlacement = false
                return true
            }
        }

        for (side in Direction.values()) {
            if (side == Direction.DOWN) continue
            val neighbor = pos.relative(side)
            if (neighbor == currentAnchorPos) continue
            if (!BlockUtils.isSolidBlock(neighbor)) continue
            val clickFace = side.opposite
            if (strict && !BlockUtils.isFaceVisible(neighbor, clickFace)) continue
            shieldSide = clickFace
            shieldSidePlacement = true
            return true
        }

        return false
    }

    context(nn: NonNullContext)
    private fun isShieldBlocking(pos: BlockPos): Boolean {
        val anchor = Vec3.atCenterOf(currentAnchorPos!!)
        val playerPos = getShieldPlayerPos()
        val block = Vec3.atCenterOf(pos)
        val vx = playerPos.x - anchor.x
        val vy = playerPos.y - anchor.y
        val vz = playerPos.z - anchor.z
        val wx = block.x - anchor.x
        val wy = block.y - anchor.y
        val wz = block.z - anchor.z
        val vv = vx * vx + vy * vy + vz * vz
        if (vv < 1.0E-6) return false
        val t = (wx * vx + wy * vy + wz * vz) / vv
        if (t <= 0.0 || t >= 1.0) return false
        val nx = wx - t * vx
        val ny = wy - t * vy
        val nz = wz - t * vz
        return nx * nx + ny * ny + nz * nz <= 0.64
    }

    context(nn: NonNullContext)
    private fun isValidShieldPos(pos: BlockPos, side: Direction, item: ItemStack): Boolean {
        if (pos == currentAnchorPos) return false
        if (placedShields.contains(pos)) return false
        if (BlockUtils.hasBlockingEntity(AABB(pos))) return false
        if (!BlockUtils.canBeReplacedWith(level.getBlockState(pos), item)) return false
        return isShieldBlocking(pos)
    }

    context(nn: NonNullContext)
    private fun isSupportedOnGround(pos: BlockPos): Boolean {
        val below = pos.below()
        if (below == currentAnchorPos) return false
        val state = level.getBlockState(below)
        return !state.isAir && !BlockUtils.canBeReplacedWith(state, Items.GLOWSTONE.defaultInstance) &&
            !state.getCollisionShape(level, below).isEmpty
    }

    context(nn: NonNullContext)
    private fun isAnchorCharged(pos: BlockPos?): Boolean {
        if (pos == null) return false
        val state = level.getBlockState(pos)
        return state.`is`(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0
    }

    context(nn: NonNullContext)
    private fun hasExistingShield(): Boolean {
        val playerPos = getShieldPlayerPos()
        val anchorPos = Vec3.atCenterOf(currentAnchorPos!!)
        val toAnchor = anchorPos.subtract(playerPos)
        val distance = toAnchor.length()
        if (distance < 1.0) return false

        val dir = toAnchor.scale(1.0 / distance)
        val start = minOf(distance, 3.5)
        val end = 0.5
        var d = start
        while (d >= end) {
            val point = playerPos.add(dir.scale(d))
            val pos = BlockPos.containing(point)
            if (isExistingShield(pos)) return true
            d -= 0.3
        }

        if (placeMode == PlaceMode.Cover) {
            val dx = playerPos.x - (currentAnchorPos!!.x + 0.5)
            val dz = playerPos.z - (currentAnchorPos!!.z + 0.5)
            val xDir = if (dx > 0) Direction.EAST else Direction.WEST
            val zDir = if (dz > 0) Direction.SOUTH else Direction.NORTH
            for (side in arrayOf(xDir, zDir)) {
                val pos = currentAnchorPos!!.relative(side)
                if (isShieldBlocking(pos) && isExistingShield(pos)) {
                    return true
                }
            }
        }

        return false
    }

    context(nn: NonNullContext)
    private fun isExistingShield(pos: BlockPos): Boolean {
        if (pos == currentAnchorPos) return false
        val state = level.getBlockState(pos)
        return !BlockUtils.canBeReplacedWith(state, Items.GLOWSTONE.defaultInstance) && Block.isShapeFullBlock(state.getCollisionShape(level, pos))
    }

    context(nn: NonNullContext)
    private fun isExplosionSafe(): Boolean {
        val anchorPos = currentAnchorPos ?: return false
        val explosionCenter = Vec3.atCenterOf(anchorPos)
        val health = player.health + player.absorptionAmount
        val threshold = minHealth.toFloat()

        val actual = DamageUtils.anchorDamage(player, explosionCenter, DamageUtils.ArmorEnchantmentMode.None)
        val pppp = DamageUtils.anchorDamage(player, explosionCenter, DamageUtils.ArmorEnchantmentMode.PPPP)
        val ppbp = DamageUtils.anchorDamage(player, explosionCenter, DamageUtils.ArmorEnchantmentMode.PPBP)

        var worstDamage = maxOf(actual, maxOf(pppp, ppbp))
        worstDamage *= 1.12f

        return health - worstDamage > threshold
    }

    context(nn: NonNullContext)
    private fun selectExplosionItem(): Boolean {
        val preferredSlot = detonateSlot - 1
        if (isHotbarSlotSafe(preferredSlot)) {
            InvUtils.swap(preferredSlot, false)
            return true
        }

        if (isExplosionHandSafe(player.mainHandItem)) return true

        if (isHotbarSlotSafe(originalSlot)) {
            InvUtils.swap(originalSlot, false)
            return true
        }

        for (slot in 0..8) {
            if (isHotbarSlotSafe(slot)) {
                InvUtils.swap(slot, false)
                return true
            }
        }

        return false
    }

    context(nn: NonNullContext)
    private fun isHotbarSlotSafe(slot: Int): Boolean {
        return !(slot < 0 || slot > 8) && isExplosionHandSafe(player.inventory.getItem(slot))
    }

    private fun isExplosionHandSafe(stack: ItemStack): Boolean {
        return !stack.`is`(Items.GLOWSTONE) && !stack.`is`(Items.RESPAWN_ANCHOR)
    }

    context(nn: NonNullContext)
    private fun switchBackToOriginal() {
        if (!swapBack) return
        if (originalSlot in 0..8) {
            InvUtils.swap(originalSlot, false)
        }
    }

    context(nn: NonNullContext)
    private fun abortSequence() {
        switchBackToOriginal()
        resetState()
    }

    private fun abortConflicting() {
        if (DoubleAnchor.isActive()) {
            return
        }
    }

    context(nn: NonNullContext)
    private fun applyTargetRotation() {
        val rotation = targetRotation ?: return
        if (!rotate) return
        val patchedTarget = RotationUtils.applySensitivityPatch(rotation)
        val minSpeed = minOf(rotationSpeedMin, rotationSpeedMax)
        val maxSpeed = maxOf(rotationSpeedMin, rotationSpeedMax)
        val speedValue = ThreadLocalRandom.current().nextInt(minSpeed, maxSpeed + 1)
        RotationManager.INSTANCE.setRotations(patchedTarget, speedValue.toDouble(), Priority.High)
    }

    context(nn: NonNullContext)
    private fun rotationAimed(): Boolean {
        val rotation = targetRotation
        if (rotation == null || !rotate) return true
        val currentRot = if (RotationManager.INSTANCE.isActive()) {
            RotationManager.INSTANCE.getRotation()
        } else {
            Rot2f(player.yRot, player.xRot)
        }
        val yawDiff = abs(Mth.wrapDegrees(rotation.yaw - currentRot.yaw))
        val pitchDiff = abs(rotation.pitch - currentRot.pitch)
        return yawDiff < 0.5f && pitchDiff < 0.5f
    }


    context(nn: NonNullContext)
    private fun getTargetRotation(targetPos: Vec3): Rot2f {
        return RotationUtils.calculate(player.eyePosition, targetPos)
    }

    context(nn: NonNullContext)
    private fun placeAtAvoiding(placePos: BlockPos, avoidPos: BlockPos): Boolean {
        setShiftState(true)
        val handItem = player.mainHandItem
        for (dir in Direction.values()) {
            val neighbor = placePos.relative(dir)
            if (neighbor == avoidPos) continue
            val state = level.getBlockState(neighbor)
            if (state.isAir || BlockUtils.canBeReplacedWith(state, handItem)) continue
            interactBlock(neighbor, dir.opposite)
            setShiftState(false)
            return true
        }
        setShiftState(false)
        return false
    }

    context(nn: NonNullContext)
    private fun interactBlock(pos: BlockPos, side: Direction) {
        val hitVec = Vec3(
            pos.x + 0.5 + side.stepX * 0.45,
            pos.y + 0.5 + side.stepY * 0.45,
            pos.z + 0.5 + side.stepZ * 0.45,
        )
        val hit = BlockHitResult(hitVec, side, pos, false)
        interaction.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
    }

    context(nn: NonNullContext)
    private fun setShiftState(state: Boolean) {
        player.isShiftKeyDown = state
    }

    private fun addRenderBox(pos: BlockPos) {
        if (render) {
            renderBoxes.add(RenderBox(pos, System.currentTimeMillis()))
        }
    }

    context(nn: NonNullContext)
    private fun doWaitShield() {
        if (hasAssistShield()) {
            cooldownMs = 0
            actionTimer.reset()
            stage = Stage.WAIT_ANCHOR
            return
        }

        val hit = mc.hitResult
        if (hit !is BlockHitResult) return

        val hitPos = hit.blockPos
        val hitSide = hit.direction

        if (tryAssistShieldPlace(hitPos, hitPos.below(), Direction.UP)) return
        if (hitPos == currentAnchorPos) return
        val placePos = hitPos.relative(hitSide)
        if (tryAssistShieldPlace(placePos, hitPos, hitSide)) return
    }

    context(nn: NonNullContext)
    private fun hasAssistShield(): Boolean {
        for (pos in placedShields) {
            val state = level.getBlockState(pos)
            if (Block.isShapeFullBlock(state.getCollisionShape(level, pos))) return true
        }
        return hasExistingShield()
    }

    context(nn: NonNullContext)
    private fun tryAssistShieldPlace(pos: BlockPos, interactPos: BlockPos, interactSide: Direction): Boolean {
        if (pos == currentAnchorPos || placedShields.contains(pos)) return false
        if (!BlockUtils.canBeReplacedWith(level.getBlockState(pos), Items.GLOWSTONE.defaultInstance)) return false
        if (BlockUtils.hasBlockingEntity(AABB(pos))) return false

        val glowstone = InvUtils.findInHotbar(Items.GLOWSTONE)
        if (!glowstone.found()) return false

        InvUtils.swap(glowstone.slot, false)
        setShiftState(true)
        interactBlock(interactPos, interactSide)
        setShiftState(false)

        placedShields.add(pos)
        return true
    }

    context(nn: NonNullContext)
    private fun doWaitAnchor() {
        if (!isAimingAtAnchor()) return

        if (mode == Mode.Assist && !hasAssistShield()) {
            cooldownMs = 0
            actionTimer.reset()
            stage = Stage.WAIT_SHIELD
            return
        }

        val state = level.getBlockState(currentAnchorPos!!)
        if (!state.`is`(Blocks.RESPAWN_ANCHOR)) {
            abortSequence()
            return
        }

        if (chargesRemaining < 0) {
            chargesRemaining = calculateChargeActions(state)
        }

        if (chargesRemaining > 0) {
            if (!isExplosionSafe()) {
                abortSequence()
                return
            }
            val glowstone = InvUtils.findInHotbar(Items.GLOWSTONE)
            if (!glowstone.found()) return
            InvUtils.swap(glowstone.slot, false)
            interactBlock(currentAnchorPos!!, BlockUtils.getVisibleFace(currentAnchorPos!!))
            chargesRemaining--

            if (chargesRemaining > 0) {
                scheduleCooldown(chargeMinDelay, chargeMaxDelay)
            } else {
                scheduleCooldown(detonateMinDelay, detonateMaxDelay)
            }
            return
        }

        if (!isExplosionSafe()) {
            abortSequence()
            return
        }
        if (selectExplosionItem()) {
            interactBlock(currentAnchorPos!!, BlockUtils.getVisibleFace(currentAnchorPos!!))
            finishSequence()
        } else {
            abortSequence()
        }
    }

    private fun isAimingAtAnchor(): Boolean {
        val hit = mc.hitResult
        return hit is BlockHitResult && hit.blockPos == currentAnchorPos
    }

    private fun resetState() {
        stage = Stage.IDLE
        currentAnchorPos = null
        shieldPos = null
        shieldSide = null
        shieldSidePlacement = false
        shieldDiagonal = false
        targetRotation = null
        targetBlockPos = null
        originalRotation = null
        returningRotation = false
        originalSlot = -1
        stageTicksElapsed = 0
        cooldownMs = 0
        chargesRemaining = -1
        placedShields.clear()
        renderBoxes.clear()
        pendingAnchorPos = null
        pendingAnchorHit = null
        actionTimer.setMs(0L)
    }

    context(nn: NonNullContext)
    private fun getShieldPlayerPos(): Vec3 {
        if (prediction) {
            val extrapolatedBox: AABB? = ExtrapolationManager.extrapolate(
                player,
                ceil(predictionStrength).toInt(),
                2,
            )
            if (extrapolatedBox != null) {
                return extrapolatedBox.center
            }
        }
        return player.position()
    }

    fun isActive(): Boolean {
        return isEnabled && stage != Stage.IDLE
    }

    fun abort() {
        resetState()
    }

    private fun scheduleCooldown(minDelay: Double, maxDelay: Double) {
        cooldownMs = getDelayMs(minDelay, maxDelay)
        actionTimer.reset()
    }

    private fun getDelayMs(minDelay: Double, maxDelay: Double): Int {
        val min = maxOf(0.0, minDelay * 10).toInt()
        val max = maxOf(min + 1.0, maxDelay * 10).toInt()
        return randomInclusive(min, max)
    }

    private fun shouldPlaceExtraShield(): Boolean {
        return mode != Mode.Assist && multiShield &&
                placedShields.size < maxExtraShields &&
                ThreadLocalRandom.current().nextInt(100) < multiShieldChance
    }

    private fun randomInclusive(min: Int, max: Int): Int {
        val actualMin = maxOf(0, minOf(min, max))
        val actualMax = maxOf(actualMin, maxOf(min, max))
        return ThreadLocalRandom.current().nextInt(actualMin, actualMax + 1)
    }

}
