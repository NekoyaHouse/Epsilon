package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.events.impl.StartDestroyBlockEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.EnchantmentUtils
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.timer.TimerUtils
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import java.awt.Color
import java.util.Timer
import java.util.TimerTask

object PacketMine : Module("Packet Mine", Category.COMBAT) {

    private enum class SwitchMode {
        None,
        Delay,
        Silent,
    }

    private enum class RenderMode {
        Box,
        Normal,
        Shrink,
        Grow,
    }

    private var usingPause by boolSetting("Pause On Use", true)
    private var onlyMain by boolSetting("Only Main", true, dependency = { usingPause })
    private var switchMode by enumSetting("Switch Mode", SwitchMode.Silent)
    private var range by intSetting("Range", 6, 0, 12, 1)
    private var maxBreaks by intSetting("Try Break Time", 6, 0, 10, 1)
    private var farCancel by boolSetting("Far Cancel", true)
    private var swing by boolSetting("Swing Hand", true)
    private var instantMine by boolSetting("Instant Mine", true)
    private var instantDelay by intSetting("Instant Delay", 10, 0, 1000, 10)
    private var fastBypass by boolSetting("Fast Bypass", true)
    private var doubleBreak by boolSetting("Double Break", false)
    private var checkGround by boolSetting("Check Ground", true)
    private var bypassGround by boolSetting("Bypass Ground", false)
    private var clientRemove by boolSetting("Client Remove", true)
    private var switchDamage by intSetting("Switch Damage", 95, 0, 100, 1)
    private var switchTime by intSetting("Switch Time", 100, 0, 1000, 10)
    private var mineDelay by intSetting("Mine Delay", 300, 0, 1000, 10)
    private var packetDelay by intSetting("Packet Delay", 200, 0, 1000, 10)
    private var mineDamage by doubleSetting("Damage", 0.8, 0.0, 2.0, 0.05)

    private var renderMode by enumSetting("Render Mode", RenderMode.Shrink)
    private var fading by boolSetting("Fading", true)
    private var renderTime by doubleSetting("Render Time", 0.1, 0.0, 5.0, 0.1, dependency = { fading })
    private var fadeTime by doubleSetting("Fade Time", 0.2, 0.0, 5.0, 0.1, dependency = { fading })
    private var fadeSideColor by colorSetting("Fade Side Color", Color(70, 200, 155, 31))
    private var fadeLineColor by colorSetting("Fade Line Color", Color(70, 200, 155, 233))
    private var sideStartColor by colorSetting("Side Start", Color(255, 0, 0, 31))
    private var sideEndColor by colorSetting("Side End", Color(0, 150, 10, 31))
    private var lineStartColor by colorSetting("Line Start", Color(255, 0, 0, 233))
    private var lineEndColor by colorSetting("Line End", Color(5, 160, 0, 233))
    private var secondSideStartColor by colorSetting("Second Side Start", Color(255, 0, 0, 31))
    private var secondSideEndColor by colorSetting("Second Side End", Color(0, 150, 10, 31))
    private var secondLineStartColor by colorSetting("Second Line Start", Color(255, 0, 0, 233))
    private var secondLineEndColor by colorSetting("Second Line End", Color(5, 160, 0, 233))

    var selfClickPos: BlockPos? = null
    var maxBreaksCount: Int = 0
    var mainProgressPercent: Int = 0
    var secondProgressPercent: Int = 0
    var completed: Boolean = false
    var targetPos: BlockPos? = null
    var secondPos: BlockPos? = null

    private var progress: Float = 0.0f
    private var secondProgress: Float = 0.0f
    private var lastTime: Long = 0L
    private var secondLastTime: Long = 0L
    private var fadeLastTime: Long = 0L
    private var started: Boolean = false
    private var secondStarted: Boolean = false
    private var renderPos: BlockPos? = null
    private var secondRenderPos: BlockPos? = null
    private var renderProgress: Double = 0.0
    private var secondRenderProgress: Double = 0.0
    private var oldSlot: Int = -1
    private var hasSwitch: Boolean = false
    private var secondHasSwitch: Boolean = false

    private val bypassTimer = TimerUtils()
    private val timer = TimerUtils()
    private val secondTimer = TimerUtils()
    val mineTimer = TimerUtils()
    private val instantTimer = TimerUtils()

    init {
        nonNullListener<StartDestroyBlockEvent> { onStartBreakingBlock(it) }
        nonNullListener<Render3DEvent> { onRender(it) }
    }

    override fun onEnable() {
        maxBreaksCount = 0
        hasSwitch = false
        secondHasSwitch = false
        bypassTimer.setMs(917813L)
        mineTimer.setMs(917813L)
        instantTimer.setMs(917813L)
        timer.setMs(917813L)
        secondTimer.setMs(917813L)
        targetPos = null
        secondPos = null
        started = false
        secondStarted = false
        mainProgressPercent = 0
        secondProgressPercent = 0
        progress = 0.0f
        secondProgress = 0.0f
        lastTime = System.currentTimeMillis()
        secondLastTime = System.currentTimeMillis()
        fadeLastTime = System.currentTimeMillis()
        renderPos = null
        secondRenderPos = null
        renderProgress = 0.0
        secondRenderProgress = 0.0
    }

    override fun onDisable() {
        val nn = NonNullContext.current ?: return
        context(nn) {
            if (hasSwitch) {
                InvUtils.swap(oldSlot, false)
                hasSwitch = false
            }
            if (secondHasSwitch) {
                InvUtils.swap(oldSlot, false)
                secondHasSwitch = false
            }
        }
    }

    private fun onStartBreakingBlock(event: StartDestroyBlockEvent) {
        if (!canBreak(event.blockPos)) return
        event.cancel()
        if (!mineTimer.passedMillise(mineDelay.toLong())) return
        selfClickPos = event.blockPos
        mine(event.blockPos)
    }

    fun isInstantMining(pos: BlockPos?): Boolean {
        if (!isEnabled || !instantMine || pos == null) return false
        if (!completed || targetPos == null || targetPos != pos) return false
        val level = this.mc.level ?: return false
        val state = level.getBlockState(pos)
        return !state.isAir && !state.canBeReplaced()
    }

    /** 返回当前的距离上限，供依赖 Packet Mine 的模块筛选可达方块。 */
    fun rangeLimit(): Int {
        return range
    }

    /** 判断是否还有尚未被破坏的挖掘目标。 */
    fun isBusy(): Boolean {
        if (!isEnabled || this.mc.level == null) return false
        return isPendingBreak(targetPos) || isPendingBreak(secondPos)
    }

    private fun isPendingBreak(pos: BlockPos?): Boolean {
        val level = this.mc.level ?: return false
        if (pos == null || !level.isLoaded(pos)) return false
        val state = level.getBlockState(pos)
        return !state.isAir && !state.canBeReplaced()
    }

    fun mine(pos: BlockPos) {
        mineTimer.reset()
        maxBreaksCount = 0
        if (doubleBreak) {
            val target = targetPos
            if (target != null && secondPos == null && target != pos) {
                if (completed) {
                    targetPos = pos
                    secondStarted = false
                    secondProgress = 0.0f
                    secondProgressPercent = 0
                    mainProgressPercent = 0
                    started = false
                    progress = 0.0f
                    completed = false
                } else {
                    secondPos = target
                    targetPos = pos
                    secondStarted = false
                    secondProgress = 0.0f
                    secondProgressPercent = 0
                    started = false
                }
            } else if (target == null || target != pos) {
                mainProgressPercent = 0
                targetPos = pos
                started = false
                progress = 0.0f
                completed = false
            }
        } else {
            if (pos != targetPos) {
                mainProgressPercent = 0
                targetPos = pos
                started = false
                progress = 0.0f
                completed = false
            }
        }
    }

    context(nn: NonNullContext)
    private fun onRender(event: Render3DEvent) {
        val player = this.mc.player ?: return
        val level = this.mc.level ?: return
        val now = System.currentTimeMillis()
        val fadeDelta = (now - fadeLastTime) / 1000.0
        fadeLastTime = now

        if (targetPos == null && secondPos == null) selfClickPos = null
        if (mainProgressPercent >= 100) {
            if (!instantMine) targetPos = null
        }
        if (secondProgressPercent >= 100) {
            secondPos = null
        }
        if (timer.passedMillise(switchTime.toLong()) && hasSwitch && switchMode != SwitchMode.None) {
            if (switchMode == SwitchMode.Delay) {
                InvUtils.swap(oldSlot, false)
            } else if (switchMode == SwitchMode.Silent) {
                this.mc.connection?.send(ServerboundSetCarriedItemPacket(oldSlot))
            }
            hasSwitch = false
        }
        if (maxBreaksCount >= maxBreaks * 10) {
            maxBreaksCount = 0
            targetPos = null
        }
        if (targetPos != null) {
            renderPos = targetPos
        }
        if (secondPos != null) {
            secondRenderPos = secondPos
        }

        updateFadeProgress(fadeDelta)
        renderFadeBoxes(event.poseStack)

        val secondPosValue = secondPos
        if (secondPosValue != null && doubleBreak) {
            if (farCancel && Math.sqrt(player.eyePosition.distanceToSqr(Vec3.atCenterOf(secondPosValue))) > range) {
                secondPos = null
                return
            }
            val secondMax = getMineTicksSecond(getTool(secondPosValue))
            val secondDelta = (System.currentTimeMillis() - secondLastTime) / 1000.0
            secondProgressPercent = (secondProgress / (secondMax * mineDamage) * 100).toInt()
            secondLastTime = System.currentTimeMillis()
            if (!secondStarted) {
                sendStart(secondPosValue)
                secondStarted = true
                secondProgress = 0.0f
                return
            }
            val secondDamage = mineDamage
            if (!checkGround || player.onGround()) {
                secondProgress += (secondDelta * 20).toFloat()
            } else if (checkGround && !player.onGround()) {
                secondProgress += (secondDelta * 4).toFloat()
            }
            secondBlockRender(event.poseStack)
            if (secondProgress >= secondMax * secondDamage) {
                sendStopSecond()
            }
        }
        if (doubleBreak &&
            (!usingPause || !checkPause(onlyMain)) &&
            ((secondProgressPercent >= switchDamage || mainProgressPercent >= switchDamage) && !hasSwitch && secondPosValue != null)
        ) {
            val bestSlot = getTool(secondPosValue)
            if (!hasSwitch) oldSlot = player.inventory.selectedSlot
            if (switchMode != SwitchMode.None && bestSlot != -1) {
                if (switchMode == SwitchMode.Delay) {
                    InvUtils.swap(bestSlot, false)
                } else if (switchMode == SwitchMode.Silent) {
                    this.mc.connection?.send(ServerboundSetCarriedItemPacket(bestSlot))
                }
                timer.reset()
                hasSwitch = true
            }
        }
        val targetPosValue = targetPos
        if (targetPosValue != null) {
            if (farCancel && Math.sqrt(player.eyePosition.distanceToSqr(Vec3.atCenterOf(targetPosValue))) > range) {
                targetPos = null
                return
            }
            val max = getMineTicks(getTool(targetPosValue))
            mainProgressPercent = (progress / (max * mineDamage) * 100).toInt()
            if (progress >= max * mineDamage && completed) {
                if (isAir(targetPosValue) || level.getBlockState(targetPosValue).canBeReplaced()) maxBreaksCount = 0
                if (!isAir(targetPosValue) && !level.getBlockState(targetPosValue).canBeReplaced() &&
                    !(usingPause && checkPause(onlyMain))
                ) {
                    maxBreaksCount++
                }
            }
            if (instantMine && completed) {
                val color1 = if (progress.toDouble() >= 0.95) sideEndColor else sideStartColor
                val color2 = if (progress.toDouble() >= 0.95) lineEndColor else lineStartColor

                val box = AABB(targetPosValue)
                Render3DScheduler.addFilledBox(box, color1)
                Render3DScheduler.addOutlineBox(box, color2)

                if (!level.getBlockState(targetPosValue).isAir && !level.getBlockState(targetPosValue).canBeReplaced() &&
                    instantTimer.passedMillise(instantDelay.toLong())
                ) {
                    sendStop()
                    instantTimer.reset()
                }
                return
            }
            val delta = (System.currentTimeMillis() - lastTime) / 1000.0
            lastTime = System.currentTimeMillis()
            if (!started) {
                sendStart(targetPosValue)
                return
            }
            val damage = mineDamage
            if (!checkGround || player.onGround()) {
                progress += (delta * 20).toFloat()
            } else if (checkGround && !player.onGround()) {
                progress += (delta * 4).toFloat()
            }
            mainBlockRender(event.poseStack)
            if (progress >= max * damage) {
                sendStop()
                completed = true
                if (!instantMine && secondPos == null) {
                    targetPos = null
                }
            }
        }
    }

    context(nn: NonNullContext)
    private fun sendStart(pos: BlockPos) {
        val player = this.mc.player ?: return
        val level = this.mc.level ?: return
        val connection = this.mc.connection ?: return
        val direction = RotationUtils.getDirection(pos)
        connection.send(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction,
            )
        )
        if (fastBypass) {
            val bypassPos = BlockPos.containing(player.x, 321.0, player.z)
            connection.send(
                ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, bypassPos, Direction.DOWN,
                    level.blockStatePredictionHandler.startPredicting().currentSequence(),
                )
            )
        }
        if (doubleBreak) {
            val delay = packetDelay.toLong()
            val scheduledTimer = Timer()
            scheduledTimer.schedule(object : TimerTask() {
                override fun run() {
                    mc.execute {
                        mc.connection?.send(
                            ServerboundPlayerActionPacket(
                                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction,
                            )
                        )
                    }
                    scheduledTimer.cancel()
                }
            }, delay)
        }
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
        if (pos == targetPos) {
            started = true
            progress = 0.0f
        } else {
            secondStarted = true
            secondProgress = 0.0f
        }
    }

    context(nn: NonNullContext)
    private fun sendStop() {
        val player = this.mc.player ?: return
        val level = this.mc.level ?: return
        if (usingPause && checkPause(onlyMain)) {
            return
        }
        if (!doubleBreak || secondPos == null) {
            val bestSlot = getTool(targetPos)
            if (!hasSwitch) oldSlot = player.inventory.selectedSlot
            if (switchMode != SwitchMode.None && bestSlot != -1) {
                if (switchMode == SwitchMode.Delay) {
                    InvUtils.swap(bestSlot, false)
                }
                if (switchMode == SwitchMode.Silent) {
                    this.mc.connection?.send(ServerboundSetCarriedItemPacket(bestSlot))
                }
                timer.reset()
                hasSwitch = true
            }
        }
        val target = targetPos
        if (bypassGround && !player.isFallFlying && target != null && !isAir(target) && !player.onGround()) {
            this.mc.connection?.send(
                ServerboundMovePlayerPacket.PosRot(
                    player.x, player.y + 1.0e-9, player.z,
                    player.yRot, player.xRot, true, player.horizontalCollision,
                )
            )
            player.resetFallDistance()
        }
        if (swing) PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
        if (target == null) return
        this.mc.connection?.send(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, target, RotationUtils.getDirection(target),
                level.blockStatePredictionHandler.startPredicting().currentSequence(),
            )
        )
        if (clientRemove && !isAir(target)) {
            this.mc.gameMode?.destroyBlock(target)
        }
    }

    context(nn: NonNullContext)
    private fun sendStopSecond() {
        val player = this.mc.player ?: return
        val second = secondPos
        if (bypassGround && !player.isFallFlying && second != null && !isAir(second) && !player.onGround()) {
            this.mc.connection?.send(
                ServerboundMovePlayerPacket.PosRot(
                    player.x, player.y + 1.0e-9, player.z,
                    player.yRot, player.xRot, true, player.horizontalCollision,
                )
            )
            player.resetFallDistance()
        }
        if (swing) {
            PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
        }
        if (clientRemove && second != null && !isAir(second)) {
            this.mc.gameMode?.destroyBlock(second)
        }
    }

    private fun getMineTicks(slot: Int): Float {
        val target = targetPos ?: return 20.0f
        val player = this.mc.player ?: return 20.0f
        val level = this.mc.level ?: return 20.0f
        val state = level.getBlockState(target)
        val hardness = state.getDestroySpeed(level, target)
        if (hardness < 0.0f) return Float.MAX_VALUE
        if (hardness == 0.0f) return 1.0f
        val stack = if (slot == -1) ItemStack.EMPTY else player.inventory.getItem(slot)
        val canHarvest = stack.isCorrectToolForDrops(state)
        var speed = stack.getDestroySpeed(state)
        val efficiency = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY)
        if (efficiency > 0 && speed > 1.0f) {
            speed += efficiency * efficiency + 1
        }
        if (player.hasEffect(MobEffects.HASTE)) {
            val amp = player.getEffect(MobEffects.HASTE)?.amplifier ?: 0
            speed *= 1.0f + (amp + 1) * 0.2f
        }
        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            val amp = player.getEffect(MobEffects.MINING_FATIGUE)?.amplifier ?: 0
            speed *= when (amp) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                else -> 0.00081f
            }
        }
        val damage = speed / hardness / (if (canHarvest) 30.0f else 100.0f)
        if (damage <= 0.0f) return Float.MAX_VALUE
        return 1.0f / damage
    }

    private fun getMineTicksSecond(slot: Int): Float {
        val second = secondPos ?: return 20.0f
        val player = this.mc.player ?: return 20.0f
        val level = this.mc.level ?: return 20.0f
        val state = level.getBlockState(second)
        val hardness = state.getDestroySpeed(level, second)
        if (hardness < 0.0f) return Float.MAX_VALUE
        if (hardness == 0.0f) return 1.0f
        val stack = if (slot == -1) ItemStack.EMPTY else player.inventory.getItem(slot)
        val canHarvest = stack.isCorrectToolForDrops(state)
        var speed = stack.getDestroySpeed(state)
        val efficiency = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY)
        if (efficiency > 0 && speed > 1.0f) {
            speed += efficiency * efficiency + 1
        }
        if (player.hasEffect(MobEffects.HASTE)) {
            val amp = player.getEffect(MobEffects.HASTE)?.amplifier ?: 0
            speed *= 1.0f + (amp + 1) * 0.2f
        }
        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            val amp = player.getEffect(MobEffects.MINING_FATIGUE)?.amplifier ?: 0
            speed *= when (amp) {
                0 -> 0.3f
                1 -> 0.09f
                2 -> 0.0027f
                else -> 0.00081f
            }
        }
        val damage = speed / hardness / (if (canHarvest) 30.0f else 100.0f)
        if (damage <= 0.0f) return Float.MAX_VALUE
        return 1.0f / damage
    }

    private fun isAir(breakPos: BlockPos?): Boolean {
        val level = this.mc.level ?: return true
        if (breakPos == null) return true
        val state = level.getBlockState(breakPos)
        return state.isAir || (state.block === Blocks.FIRE && hasCrystal(breakPos))
    }

    private fun isSolidForFade(pos: BlockPos?): Boolean {
        val level = this.mc.level ?: return false
        if (pos == null) return false
        val state = level.getBlockState(pos)
        return !isAir(pos) && !state.canBeReplaced()
    }

    private fun hasCrystal(pos: BlockPos): Boolean {
        val level = this.mc.level ?: return false
        for (entity in level.getEntities(null, AABB(pos))) {
            if (entity is EndCrystal && entity.isAlive) {
                return true
            }
        }
        return false
    }

    private fun getTool(pos: BlockPos?): Int {
        val player = this.mc.player ?: return -1
        val level = this.mc.level ?: return -1
        if (pos == null) return -1
        var index = -1
        var currentFastest = 1.0f
        for (i in 0 until 9) {
            val stack = player.inventory.getItem(i)
            if (stack !== ItemStack.EMPTY) {
                val digSpeed = EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY).toFloat()
                val destroySpeed = stack.getDestroySpeed(level.getBlockState(pos))
                if (digSpeed + destroySpeed > currentFastest) {
                    currentFastest = digSpeed + destroySpeed
                    index = i
                }
            }
        }
        return index
    }

    private fun checkPause(onlyMain: Boolean): Boolean {
        val player = this.mc.player ?: return false
        return this.mc.options.keyUse.isDown && (!onlyMain || player.usedItemHand == InteractionHand.MAIN_HAND)
    }

    private fun canBreak(blockPos: BlockPos): Boolean {
        val player = this.mc.player ?: return false
        val level = this.mc.level ?: return false
        val state = level.getBlockState(blockPos)
        if (!player.isCreative && state.getDestroySpeed(level, blockPos) < 0) return false
        return state.getCollisionShape(level, blockPos) !== Shapes.empty()
    }

    private fun updateFadeProgress(delta: Double) {
        if (!fading) {
            renderProgress = 0.0
            secondRenderProgress = 0.0
            return
        }

        val paused = usingPause && checkPause(onlyMain)
        if (isSolidForFade(renderPos) && !paused) {
            renderProgress = fadeTime + renderTime
        } else {
            renderProgress = Math.max(0.0, renderProgress - delta)
        }

        if (isSolidForFade(secondRenderPos) && !paused) {
            secondRenderProgress = fadeTime + renderTime
        } else {
            secondRenderProgress = Math.max(0.0, secondRenderProgress - delta)
        }
    }

    private fun renderFadeBoxes(stack: PoseStack) {
        if (fading) {
            renderFadeBox(stack, renderPos, renderProgress)
            renderFadeBox(stack, secondRenderPos, secondRenderProgress)
        }
    }

    private fun renderFadeBox(stack: PoseStack, pos: BlockPos?, progress: Double) {
        if (pos == null || progress <= 0 || isSolidForFade(pos)) return

        val side = fadeSideColor
        val line = fadeLineColor
        val alphaScale = Math.min(1.0, progress / fadeTime)
        val color1 = Color(
            side.red, side.green, side.blue,
            Math.round(side.alpha * alphaScale).toInt(),
        )
        val color2 = Color(
            line.red, line.green, line.blue,
            Math.round(line.alpha * alphaScale).toInt(),
        )
        val box = AABB(pos)
        Render3DScheduler.addFilledBox(box, color1)
        Render3DScheduler.addOutlineBox(box, color2)
    }

    private fun mainBlockRender(stack: PoseStack) {
        val target = targetPos ?: return

        val max = getMineTicks(getTool(target))
        val rawProgress = Mth.clamp(progress / (max * mineDamage), 0.0, 1.0)

        val color1 = if (rawProgress >= 0.95) sideEndColor else sideStartColor
        val color2 = if (rawProgress >= 0.95) lineEndColor else lineStartColor

        when (renderMode) {
            RenderMode.Box -> {
                val box = AABB(target)
                Render3DScheduler.addFilledBox(box, color1)
                Render3DScheduler.addOutlineBox(box, color2)
            }

            RenderMode.Normal -> {
                val box = AABB.ofSize(Vec3.atCenterOf(target), rawProgress, rawProgress, rawProgress)
                Render3DScheduler.addFilledBox(box, color1)
                Render3DScheduler.addOutlineBox(box, color2)
            }

            RenderMode.Grow -> {
                val box = AABB(target).setMaxY(target.y + rawProgress)
                Render3DScheduler.addFilledBox(box, color1)
                Render3DScheduler.addOutlineBox(box, color2)
            }

            RenderMode.Shrink -> {
                val maxBound = Math.round(rawProgress * 100.0) / 100.0
                val box = AABB(target).deflate(1.0 - maxBound)
                Render3DScheduler.addFilledBox(box, color1)
                Render3DScheduler.addOutlineBox(box, color2)
            }
        }
    }

    private fun secondBlockRender(stack: PoseStack) {
        val second = secondPos ?: return

        val max = getMineTicksSecond(getTool(second))
        val rawProgress = Mth.clamp(secondProgress / (max * mineDamage), 0.0, 1.0)

        val color1 = if (rawProgress >= 0.95) secondSideEndColor else secondSideStartColor
        val color2 = if (rawProgress >= 0.95) secondLineEndColor else secondLineStartColor

        when (renderMode) {
            RenderMode.Box -> {
                val box = AABB(second)
                Render3DScheduler.addFilledBox(box, color1)
                Render3DScheduler.addOutlineBox(box, color2)
            }

            RenderMode.Normal -> {
                val box = AABB.ofSize(Vec3.atCenterOf(second), rawProgress, rawProgress, rawProgress)
                Render3DScheduler.addFilledBox(box, color1)
                Render3DScheduler.addOutlineBox(box, color2)
            }

            RenderMode.Grow -> {
                val box = AABB(second).setMaxY(second.y + rawProgress)
                Render3DScheduler.addFilledBox(box, color1)
                Render3DScheduler.addOutlineBox(box, color2)
            }

            RenderMode.Shrink -> {
                val maxBound = Math.round(rawProgress * 100.0) / 100.0
                val box = AABB(second).deflate(1.0 - maxBound)
                Render3DScheduler.addFilledBox(box, color1)
                Render3DScheduler.addOutlineBox(box, color2)
            }
        }
    }

}
