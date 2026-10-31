package com.github.epsilon.modules.impl.combat

import com.github.epsilon.Constants
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.managers.target.TargetRequest
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.group
import com.github.epsilon.utils.combat.DamageUtils
import com.github.epsilon.utils.player.EnchantmentUtils
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.render.WorldToScreen
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RaytraceUtils
import com.github.epsilon.utils.rotation.Rot2f
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.joml.Vector2f
import org.joml.Vector3f
import java.awt.Color
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Zealot Crystal+：客户端水晶放置/破坏计算模块。
 *
 * 客户端线程只做有界采样与渲染；快照求值在专用工作线程完成，结果通过 volatile 快照交接。
 */
object ZealotCrystalPlus : Module("Zealot Crystal+", Category.COMBAT) {

    // ---- 枚举 ----

    private enum class DamagePriority {
        Efficient {
            override fun score(selfDamage: Float, targetDamage: Float): Float {
                return targetDamage - selfDamage
            }
        },

        Aggressive {
            override fun score(selfDamage: Float, targetDamage: Float): Float {
                return targetDamage
            }
        };

        abstract fun score(selfDamage: Float, targetDamage: Float): Float
    }

    private enum class SwingHand {
        Auto,
        OffHand,
        MainHand,
    }

    private enum class SwitchMode {
        Off,
        Legit,
        Ghost,
    }

    private enum class PlaceMode {
        Off,
        Single,
        Multi,
    }

    private enum class PacketPlaceMode(val onRemove: Boolean, val onBreak: Boolean) {
        Off(false, false),
        Weak(true, false),
        Strong(true, true),
    }

    private enum class PlaceBypass {
        Up,
        Down,
        Closest,
    }

    private enum class BreakMode {
        Off,
        Target,
        Own,
        Smart,
        All,
    }

    private enum class RangeMode {
        Feet,
        Eyes,
    }

    private enum class RenderPredictMode {
        Off,
        Single,
        Multi,
    }

    private enum class HudInfo {
        Off,
        Speed,
        Target,
        Damage,
        CalculationTime,
    }

    private val sgGeneral: SettingGroup = settingGroup("General")
    private val sgForcePlace: SettingGroup = settingGroup("Force Place")
    private val sgCalculation: SettingGroup = settingGroup("Calculation")
    private val sgPlace: SettingGroup = settingGroup("Place")
    private val sgBreak: SettingGroup = settingGroup("Break")
    private val sgRender: SettingGroup = settingGroup("Render")

    // General
    private var players by boolSetting("Players", true).group(sgGeneral)
    private var mobs by boolSetting("Mobs", false).group(sgGeneral)
    private var animals by boolSetting("Animals", false).group(sgGeneral)
    private var maxTargets by intSetting("Max Targets", 4, 1..10, 1).group(sgGeneral)
    private var targetRange by doubleSetting("Target Range", 16.0, 0.0..32.0, 0.5).group(sgGeneral)
    private var yawSpeed by doubleSetting("Yaw Speed", 45.0, 5.0..180.0, 5.0).group(sgGeneral)
    private var preRotation by boolSetting("Pre Rotation", true).group(sgGeneral)
    private var placeRotationRange by doubleSetting("Place Rotation Range", 0.0, 0.0..180.0, 5.0).group(sgGeneral)
    private var breakRotationRange by doubleSetting("Break Rotation Range", 90.0, 0.0..180.0, 5.0).group(sgGeneral)
    private var eatingPause by boolSetting("Eating Pause", false).group(sgGeneral)
    private var updateDelay by intSetting("Update Delay", 5, 0..250, 1).group(sgGeneral)
    private var globalDelay by intSetting(
        "Global Delay",
        1_000_000,
        1_000..10_000_000,
        1_000
    ).group(sgGeneral)

    // Force Place
    private var forcePlaceHealth by doubleSetting(
        "Force Place Health",
        8.0,
        0.0..20.0,
        0.5
    ).group(sgForcePlace)
    private var forcePlaceArmorRate by intSetting("Force Place Armor Rate", 3, 0..25, 1).group(sgForcePlace)
    private var forcePlaceMinDamage by doubleSetting("Force Place Min Damage", 1.5, 0.0..10.0, 0.25).group(sgForcePlace)
    private var forcePlaceMotion by doubleSetting(
        "Force Place Motion",
        4.0,
        0.0..10.0,
        0.25
    ).group(sgForcePlace)
    private var forcePlaceBalance by doubleSetting("Force Place Balance", -1.0, -10.0..10.0, 0.25).group(sgForcePlace)
    private var forcePlaceWhileSwording by boolSetting(
        "Force Place While Swording",
        false
    ).group(sgForcePlace)

    // Calculation
    private var assumeInstantMine by boolSetting("Assume Instant Mine", true).group(sgCalculation)
    private var noSuicide by doubleSetting("No Suicide", 2.0, 0.0..20.0, 0.25).group(sgCalculation)
    private var wallRange by doubleSetting("Wall Range", 3.0, 0.0..8.0, 0.1).group(sgCalculation)
    private var motionPredict by boolSetting("Motion Predict", true).group(sgCalculation)
    private var predictTicks by intSetting("Predict Ticks", 8, 0..20, 1, { motionPredict }).group(sgCalculation)
    private var damagePriority by enumSetting(
        "Damage Priority",
        DamagePriority.Efficient
    ).group(sgCalculation)
    private var armorMode by enumSetting("Armor Mode", DamageUtils.ArmorEnchantmentMode.None).group(sgCalculation)
    private var lethalOverride by boolSetting("Lethal Override", true).group(sgCalculation)
    private var lethalThresholdAddition by doubleSetting(
        "Lethal Threshold Addition",
        0.5,
        -5.0..5.0,
        0.1, { lethalOverride }).group(sgCalculation)
    private var lethalMaxSelfDamage by doubleSetting(
        "Lethal Max Self Damage",
        16.0,
        0.0..20.0,
        0.25, { lethalOverride }).group(sgCalculation)
    private var safeMaxTargetDamageReduction by doubleSetting(
        "Safe Max Target Damage Reduction",
        1.0,
        0.0..10.0,
        0.1
    ).group(sgCalculation)
    private var safeMinSelfDamageReduction by doubleSetting(
        "Safe Min Self Damage Reduction",
        2.0,
        0.0..10.0,
        0.1
    ).group(sgCalculation)
    private var collidingCrystalExtraSelfDamageThreshold by doubleSetting(
        "Colliding Crystal Extra Self Damage Threshold",
        4.0,
        0.0..10.0,
        0.1
    ).group(sgCalculation)

    // Place
    private var placeMode by enumSetting("Place Mode", PlaceMode.Single).group(sgPlace)
    private var packetPlace by enumSetting("Packet Place", PacketPlaceMode.Weak).group(sgPlace)
    private var spamPlace by boolSetting("Spam Place", false).group(sgPlace)
    private var placeSwitchMode by enumSetting("Place Switch Mode", SwitchMode.Off).group(sgPlace)
    private var placeSwing by boolSetting("Place Swing", false).group(sgPlace)
    private var placeSideBypass by enumSetting("Place Side Bypass", PlaceBypass.Up).group(sgPlace)
    private var placeMinDamage by doubleSetting("Place Min Damage", 5.0, 0.0..20.0, 0.25).group(sgPlace)
    private var placeMaxSelfDamage by doubleSetting(
        "Place Max Self Damage",
        6.0,
        0.0..20.0,
        0.25
    ).group(sgPlace)
    private var placeBalance by doubleSetting("Place Balance", -3.0, -10.0..10.0, 0.25).group(sgPlace)
    private var placeDelay by intSetting("Place Delay", 50, 0..500, 1).group(sgPlace)
    private var placeRange by doubleSetting("Place Range", 5.0, 0.0..8.0, 0.1).group(sgPlace)
    private var placeRangeMode by enumSetting("Place Range Mode", RangeMode.Feet).group(sgPlace)

    // Break
    private var breakMode by enumSetting("Break Mode", BreakMode.Smart).group(sgBreak)
    private var bbtt by boolSetting("2B2T", false).group(sgBreak)
    private var bbttFactor by intSetting(
        "2B2T Factor",
        200,
        0..1000,
        25, { bbtt }).group(sgBreak)
    private var packetBreak by enumSetting("Packet Break", BreakMode.Target, { !bbtt }).group(sgBreak)
    private var ownTimeout by intSetting(
        "Own Timeout", 100, 0..2000, 25,
        { breakMode == BreakMode.Own || packetBreak == BreakMode.Own },
    ).group(sgBreak)
    private var antiWeakness by enumSetting("Anti Weakness", SwitchMode.Off).group(sgBreak)
    private var swapDelay by intSetting("Swap Delay", 0, 0..20, 1).group(sgBreak)
    private var breakMinDamage by doubleSetting("Break Min Damage", 4.0, 0.0..20.0, 0.25).group(sgBreak)
    private var breakMaxSelfDamage by doubleSetting(
        "Break Max Self Damage",
        8.0,
        0.0..20.0,
        0.25
    ).group(sgBreak)
    private var breakBalance by doubleSetting("Break Balance", -4.0, -10.0..10.0, 0.25).group(sgBreak)
    private var breakDelay by intSetting("Break Delay", 100, 0..500, 1).group(sgBreak)
    private var breakRange by doubleSetting("Break Range", 5.0, 0.0..8.0, 0.1).group(sgBreak)
    private var breakRangeMode by enumSetting("Break Range Mode", RangeMode.Feet).group(sgBreak)

    // Render
    private var swingMode by enumSetting("Swing Mode", SwingMode.Client).group(sgRender)
    private var swingHand by enumSetting("Swing Hand", SwingHand.Auto).group(sgRender)
    private var renderPredict by enumSetting("Render Predict", RenderPredictMode.Off).group(sgRender)
    private var hudInfo by enumSetting("Hud Info", HudInfo.Speed).group(sgRender)
    private var filledAlpha by intSetting("Filled Alpha", 63, 0..255, 1).group(sgRender)
    private var outlineAlpha by intSetting("Outline Alpha", 200, 0..255, 1).group(sgRender)
    private var renderTargetDamage by boolSetting("Target Damage", true).group(sgRender)
    private var renderSelfDamage by boolSetting("Self Damage", true).group(sgRender)
    private var renderColor by colorSetting("Render Color", Color(255, 150, 120, 255), true).group(sgRender)
    private var outlineWidth by doubleSetting("Outline Width", 3.0, 1.0..10.0, 0.5).group(sgRender)
    private var movingLength by intSetting("Moving Length", 400, 0..1000, 50).group(sgRender)
    private var fadeLength by intSetting("Fade Length", 200, 0..1000, 50).group(sgRender)

    private val placeTimer = TimerUtils()
    private val breakTimer = TimerUtils()
    private val snapshotTimer = TimerUtils()
    private val explosionSampleTimer = TimerUtils()

    private val placedPosMap: MutableMap<Long, Long> = HashMap()
    private val crystalSpawnMap: MutableMap<Int, Long> = HashMap()
    private val attackedCrystalMap: MutableMap<Int, Long> = HashMap()
    private val attackedPosMap: MutableMap<Long, Long> = HashMap()
    private var lastSwapTime: Long = 0L
    private var lastActiveTime: Long = 0L
    private var target: LivingEntity? = null

    private val workerMonitor = ReentrantLock()
    private val workerCondition = workerMonitor.newCondition()
    private val workerThread = Thread({ workerLoop() }, "Zealot+")

    @Volatile
    private var pendingSnapshot: SnapshotData? = null

    @Volatile
    private var latestSnapshot: SnapshotData? = null

    @Volatile
    private var asyncResult: AsyncResult = AsyncResult.EMPTY

    @Volatile
    private var cachedRotationPlaceInfo: PlaceInfo? = null

    @Volatile
    private var cachedPlaceInfo: PlaceInfo? = null

    @Volatile
    private var cachedRotationBreakPlan: BreakPlan? = null

    @Volatile
    private var cachedBreakPlan: BreakPlan? = null

    @Volatile
    private var cachedRawPosList: List<BlockPos> = emptyList()

    @Volatile
    private var rawPosListExpireAt: Long = 0L

    private var renderBlockPos: BlockPos? = null
    private var renderPrevPos: Vec3? = null
    private var renderCurrentPos: Vec3? = null
    private var renderLastRenderedPos: Vec3? = null
    private var renderMoveStartTime: Long = 0L
    private var renderFadeStartTime: Long = 0L
    private var renderScale: Float = 0.0f
    private var renderDamage: Float = 0.0f
    private var renderSelfDamageValue: Float = 0.0f
    private var renderHasTarget: Boolean = false

    private val textRenderer: TextRenderer by lazy { TextRenderer.create((128 * 1024).toLong()) }
    private val explosionSamples: Deque<Int> = ArrayDeque()
    private var explosionsThisWindow: Int = 0

    init {
        workerThread.isDaemon = true
        workerThread.start()

        nonNullHandler<PlayerTickEvent.Pre> { onTick() }
        handler<PacketEvent.Receive> { onPacketReceive(it) }
        handler<Render3DEvent> { onRender3D() }
        handler<Render2DEvent.Level> { onRender2D(it) }
    }

    override fun onEnable() {
        placeTimer.reset()
        breakTimer.reset()
        snapshotTimer.setMs(updateDelay.toLong())
        explosionSampleTimer.reset()
        placedPosMap.clear()
        crystalSpawnMap.clear()
        attackedCrystalMap.clear()
        attackedPosMap.clear()
        explosionSamples.clear()
        explosionsThisWindow = 0
        lastSwapTime = 0L
        lastActiveTime = 0L
        target = null
        pendingSnapshot = null
        latestSnapshot = null
        asyncResult = AsyncResult.EMPTY
        cachedRotationPlaceInfo = null
        cachedPlaceInfo = null
        cachedRotationBreakPlan = null
        cachedBreakPlan = null
        resetRenderState()
        signalWorker()
    }

    override fun onDisable() {
        placedPosMap.clear()
        crystalSpawnMap.clear()
        attackedCrystalMap.clear()
        attackedPosMap.clear()
        pendingSnapshot = null
        latestSnapshot = null
        asyncResult = AsyncResult.EMPTY
        cachedRotationPlaceInfo = null
        cachedPlaceInfo = null
        cachedRotationBreakPlan = null
        cachedBreakPlan = null
        target = null
        explosionSamples.clear()
        explosionsThisWindow = 0
        resetRenderState()
        signalWorker()
    }

    context(nn: NonNullContext)
    private fun onTick() {
        updateTimeouts()
        updateExplosionSamples()
        if (isEatingPaused()) return

        val snapshot = captureSnapshotIfNeeded()
        val result = asyncResult
        val preBreak = getValidBreakPlan(cachedRotationBreakPlan)
        val prePlace = getValidPlaceInfo(cachedRotationPlaceInfo, false)
        target = resolveCurrentTarget(result, prePlace)

        val prioritizeBreak = preRotation && shouldPrioritizeBreak(preBreak)
        if (preRotation) {
            prepareRotation(preBreak, prePlace)
        }

        var acted = false
        val actionBreak = getActionBreakPlan()
        if (breakMode != BreakMode.Off && breakTimer.passedMillise(breakDelay.toLong()) && actionBreak != null) {
            acted = breakDirect(actionBreak)
        }

        val actionPlace = getActionPlaceInfo()
        if (!acted && !prioritizeBreak && placeMode != PlaceMode.Off &&
            placeTimer.passedMillise(placeDelay.toLong()) && actionPlace != null && shouldAttemptPlace(actionPlace)
        ) {
            acted = placeDirect(actionPlace, false)
        }

        if (!acted && (snapshot == null ||
                    (preBreak == null && prePlace == null && actionBreak == null && actionPlace == null))
        ) {
            if (System.currentTimeMillis() - lastActiveTime > 250L) {
                deactivateRenderTarget()
            }
        }
    }

    private fun onPacketReceive(event: PacketEvent.Receive) {
        if (nullCheck() || !isEnabled) return

        when (val packet = event.packet) {
            is ClientboundAddEntityPacket -> if (packet.type == EntityTypes.END_CRYSTAL) {
                handleSpawnPacket(packet)
            }

            is ClientboundSoundPacket -> handleExplosionPacket(packet)
        }
    }

    private fun onRender3D() {
        if (nullCheck()) return

        renderTargetPredictions()

        val prevPos = renderPrevPos ?: return
        val currentPos = renderCurrentPos ?: return

        val moveDelta = toDelta(renderMoveStartTime, movingLength)
        val moveMultiplier = easeOutQuart(moveDelta)
        val renderPos = prevPos.add(currentPos.subtract(prevPos).scale(moveMultiplier.toDouble()))

        val fadeDelta = toDelta(renderFadeStartTime, fadeLength)
        renderScale = if (renderHasTarget) easeOutCubic(fadeDelta) else 1.0f - easeInCubic(fadeDelta)
        if (renderScale <= 0.01f) return

        val halfSize = 0.5 * renderScale
        val box = AABB(
            renderPos.x - halfSize, renderPos.y - halfSize, renderPos.z - halfSize,
            renderPos.x + halfSize, renderPos.y + halfSize, renderPos.z + halfSize,
        )

        val base = renderColor
        val filled = Color(
            base.red, base.green, base.blue,
            (filledAlpha * renderScale).toInt().coerceIn(0, 255),
        )
        val outline = Color(
            base.red, base.green, base.blue,
            (outlineAlpha * renderScale).toInt().coerceIn(0, 255),
        )

        if (filledAlpha > 0) {
            Render3DScheduler.addFilledBox(box, filled)
        }
        if (outlineAlpha > 0) {
            Render3DScheduler.addOutlineBox(box, outline.rgb, outlineWidth.toFloat())
        }

        renderLastRenderedPos = renderPos
    }

    private fun onRender2D(event: Render2DEvent.Level) {
        val prevPos = renderPrevPos ?: return
        val currentPos = renderCurrentPos ?: return
        if (!renderTargetDamage && !renderSelfDamage) return

        val moveDelta = toDelta(renderMoveStartTime, movingLength)
        val moveMultiplier = easeOutQuart(moveDelta)
        val renderPos = prevPos.add(currentPos.subtract(prevPos).scale(moveMultiplier.toDouble()))
        val screenPos = projectToScreen(renderPos) ?: return

        val text = StringBuilder()
        if (renderTargetDamage) {
            text.append(String.format(Locale.ROOT, "%.1f", renderDamage))
        }
        if (renderSelfDamage) {
            if (text.isNotEmpty()) text.append('/')
            text.append(String.format(Locale.ROOT, "%.1f", renderSelfDamageValue))
        }
        if (text.isEmpty()) return

        val renderer = textRenderer
        val scale = 1.0f
        val width = renderer.getWidth(text.toString(), scale)
        val height = renderer.getHeight(scale)
        val color = Color(255, 255, 255, (220 * renderScale).toInt().coerceIn(0, 255))
        renderer.addText(text.toString(), screenPos.x - width / 2.0f, screenPos.y - height / 2.0f, scale, color)
        renderer.drawAndClear()
    }

    override fun getInfo(): String? {
        return when (hudInfo) {
            HudInfo.Off -> ""
            HudInfo.Speed -> String.format(Locale.ROOT, "%.1f", getExplosionSpeed())
            HudInfo.Target -> {
                var currentTarget = this.target
                val primary = asyncResult.primaryTarget
                if (currentTarget == null && primary != null) {
                    currentTarget = primary.entity
                }
                if (currentTarget != null) currentTarget.name.string else "None"
            }

            HudInfo.Damage -> {
                var info = getValidPlaceInfo(cachedPlaceInfo, false)
                if (info == null) {
                    info = getValidPlaceInfo(cachedRotationPlaceInfo, false)
                }
                if (info != null) {
                    String.format(Locale.ROOT, "%.1f/%.1f", info.targetDamage, info.selfDamage)
                } else {
                    "0.0/0.0"
                }
            }

            HudInfo.CalculationTime -> String.format(Locale.ROOT, "%.2f ms", asyncResult.calculationNanos / 1_000_000.0)
        }
    }

    private fun workerLoop() {
        var lastProcessedId = Long.MIN_VALUE

        while (!Thread.currentThread().isInterrupted) {
            try {
                if (!isEnabled) {
                    waitForSignal(100L, 0)
                    continue
                }

                val snapshot = pendingSnapshot
                if (snapshot == null) {
                    waitForSignal(50L, 0)
                    continue
                }

                if (snapshot.id == lastProcessedId) {
                    waitForSignal(0L, maxOf(1, snapshot.settings.globalDelayNanos))
                    continue
                }

                lastProcessedId = snapshot.id
                val start = System.nanoTime()
                val nn = NonNullContext.current
                if (nn == null) {
                    waitForSignal(50L, 0)
                    continue
                }
                asyncResult = context(nn) { evaluateSnapshot(snapshot, start) }
                cachedRotationPlaceInfo = asyncResult.rotationPlaceInfo
                cachedPlaceInfo = asyncResult.placeInfo
                cachedRotationBreakPlan = asyncResult.rotationBreakPlan
                cachedBreakPlan = asyncResult.breakPlan
                waitForSignal(0L, maxOf(1, snapshot.settings.globalDelayNanos))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (t: Throwable) {
                Constants.LOGGER.warn("Error in ZealotCrystalPlus worker loop", t)
                try {
                    waitForSignal(100L, 0)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
    }

    private fun waitForSignal(millis: Long, nanos: Int) {
        var normalizedMillis = maxOf(0L, millis)
        var normalizedNanos = maxOf(0, nanos)
        if (normalizedNanos >= 1_000_000) {
            normalizedMillis += normalizedNanos / 1_000_000L
            normalizedNanos %= 1_000_000
        }

        workerMonitor.lock()
        try {
            val waitNanos = if (normalizedMillis > 0L || normalizedNanos > 0) {
                normalizedMillis * 1_000_000L + normalizedNanos
            } else {
                1_000_000L
            }
            workerCondition.awaitNanos(waitNanos)
        } finally {
            workerMonitor.unlock()
        }
    }

    private fun signalWorker() {
        workerMonitor.lock()
        try {
            workerCondition.signalAll()
        } finally {
            workerMonitor.unlock()
        }
    }

    context(nn: NonNullContext)
    private fun captureSnapshotIfNeeded(): SnapshotData? {
        if (!snapshotTimer.passedMillise(updateDelay.toLong()) && latestSnapshot != null) {
            return latestSnapshot
        }

        snapshotTimer.reset()
        val snapshot = captureSnapshot()
        latestSnapshot = snapshot
        pendingSnapshot = snapshot
        signalWorker()
        return snapshot
    }

    context(nn: NonNullContext)
    private fun captureSnapshot(): SnapshotData {
        val player: Player = this.mc.player ?: return SnapshotData.EMPTY
        val level: net.minecraft.client.multiplayer.ClientLevel = this.mc.level ?: return SnapshotData.EMPTY

        val settings = SettingsSnapshot(
            globalDelay,
            noSuicide.toFloat(),
            placeMaxSelfDamage.toFloat(),
            breakMaxSelfDamage.toFloat(),
            placeMinDamage.toFloat(),
            breakMinDamage.toFloat(),
            placeBalance.toFloat(),
            breakBalance.toFloat(),
            forcePlaceMinDamage.toFloat(),
            forcePlaceBalance.toFloat(),
            lethalOverride,
            lethalThresholdAddition.toFloat(),
            lethalMaxSelfDamage.toFloat(),
            safeMaxTargetDamageReduction.toFloat(),
            safeMinSelfDamageReduction.toFloat(),
            collidingCrystalExtraSelfDamageThreshold.toFloat(),
            damagePriority,
            armorMode,
            placeSideBypass,
            packetPlace,
            breakMode,
            packetBreak,
        )

        val self = captureSelfSnapshot(player, level, settings.armorMode)
        val targets = captureTargets(level, settings.armorMode)
        if (targets.isEmpty()) {
            return SnapshotData(
                System.nanoTime(),
                settings,
                self,
                emptyList(),
                emptyList(),
                emptyList(),
                ResistantBlockCache.EMPTY,
                System.currentTimeMillis()
            )
        }

        val placePositions = capturePlacePositions(level)
        val crystals = captureCrystals(level)
        val resistantBlocks = ResistantBlockCache(level, assumeInstantMine)

        return SnapshotData(
            System.nanoTime(),
            settings,
            self,
            targets.toList(),
            placePositions.toList(),
            crystals.toList(),
            resistantBlocks,
            System.currentTimeMillis(),
        )
    }

    private fun captureSelfSnapshot(
        player: Player,
        level: net.minecraft.client.multiplayer.ClientLevel,
        armorMode: DamageUtils.ArmorEnchantmentMode,
    ): SelfSnapshot {
        val currentRotation = RotationManager.INSTANCE.rotations
        return SelfSnapshot(
            player,
            player.position(),
            player.eyePosition,
            player.boundingBox,
            getTotalHealth(player),
            player.hasEffect(MobEffects.WEAKNESS) &&
                    ((player.getEffect(MobEffects.STRENGTH)?.amplifier ?: 0) <= 0),
            isToolLike(player.mainHandItem),
            player.mainHandItem.`is`(ItemTags.SWORDS),
            DamageReductionData.fromEntity(player, armorMode),
            level.difficulty,
            currentRotation,
        )
    }

    context(nn: NonNullContext)
    private fun captureTargets(
        level: net.minecraft.client.multiplayer.ClientLevel,
        armorMode: DamageUtils.ArmorEnchantmentMode,
    ): List<TargetSnapshot> {
        if (this.mc.player == null) return emptyList()

        val ticks = if (motionPredict) predictTicks else 0
        val targets = TargetManager.acquireTargets(
            TargetRequest.of(
                targetRange,
                360.0f,
                players,
                mobs,
                animals,
                false,
                false,
                false,
                false,
                true,
                { living -> living.position().y > -64.0 },
                maxTargets,
            )
        )

        if (targets.isEmpty()) {
            return emptyList()
        }

        val list = ArrayList<TargetSnapshot>(targets.size)
        for (living in targets) {
            list.add(buildTargetSnapshot(living, ticks, armorMode))
        }

        return list.toList()
    }

    context(nn: NonNullContext)
    private fun buildTargetSnapshot(
        entity: LivingEntity,
        ticks: Int,
        armorMode: DamageUtils.ArmorEnchantmentMode,
    ): TargetSnapshot {
        val motionX = (entity.x - entity.xo).coerceIn(-0.6, 0.6)
        val motionY = (entity.y - entity.yo).coerceIn(-0.5, 0.5)
        val motionZ = (entity.z - entity.zo).coerceIn(-0.6, 0.6)

        val entityBox = entity.boundingBox
        var targetBox = entityBox
        for (tick in 0..ticks) {
            var moved = canMove(entity, targetBox, motionX, motionY, motionZ)
            if (moved == null) moved = canMove(entity, targetBox, motionX, 0.0, motionZ)
            if (moved == null) moved = canMove(entity, targetBox, 0.0, motionY, 0.0)
            if (moved == null) break
            targetBox = moved
        }

        val offsetX = targetBox.minX - entityBox.minX
        val offsetY = targetBox.minY - entityBox.minY
        val offsetZ = targetBox.minZ - entityBox.minZ
        val motion = Vec3(offsetX, offsetY, offsetZ)
        val currentPos = entity.position()
        val predictedPos = currentPos.add(motion)

        return TargetSnapshot(
            entity,
            predictedPos,
            targetBox,
            currentPos,
            motion,
            getTotalHealth(entity),
            entity is Player,
            getRealSpeed(entity),
            getMinArmorRate(entity),
            DamageReductionData.fromEntity(entity, armorMode),
        )
    }

    context(nn: NonNullContext)
    private fun capturePlacePositions(level: net.minecraft.client.multiplayer.ClientLevel): List<BlockPos> {
        val rawPosList = getRawPosList(level)
        if (rawPosList.isEmpty()) return rawPosList

        val collidingEntities = getCollidingEntities(level)
        val list = ArrayList<BlockPos>()
        for (pos in rawPosList) {
            if (!checkPlaceCollision(pos, collidingEntities)) continue
            list.add(pos)
        }
        return list
    }

    context(nn: NonNullContext)
    private fun captureCrystals(level: net.minecraft.client.multiplayer.ClientLevel): List<CrystalSnapshot> {
        if (this.mc.player == null) return emptyList()

        val current = System.currentTimeMillis()
        val list = ArrayList<CrystalSnapshot>()
        for (entity in level.entitiesForRendering()) {
            if (entity !is EndCrystal) continue
            if (!entity.isAlive) continue

            var breakable = !bbtt || current - getSpawnTime(entity) >= bbttFactor
            breakable = breakable && checkBreakRange(entity.position())

            list.add(
                CrystalSnapshot(
                    entity,
                    entity.id,
                    entity.position(),
                    entity.boundingBox,
                    breakable,
                    placedPosMap.containsKey(toLong(entity.x, entity.y - 1.0, entity.z)),
                )
            )
        }
        return list.toList()
    }

    private fun captureResistantBlocks(
        targets: List<TargetSnapshot>,
        placePositions: List<BlockPos>,
        crystals: List<CrystalSnapshot>,
        playerPos: BlockPos,
        level: net.minecraft.client.multiplayer.ClientLevel,
    ): Set<Long> {
        var minX = playerPos.x
        var minY = playerPos.y
        var minZ = playerPos.z
        var maxX = minX
        var maxY = minY
        var maxZ = minZ

        for (target in targets) {
            minX = minOf(minX, Mth.floor(target.pos.x))
            minY = minOf(minY, Mth.floor(target.pos.y))
            minZ = minOf(minZ, Mth.floor(target.pos.z))
            maxX = maxOf(maxX, Mth.ceil(target.pos.x))
            maxY = maxOf(maxY, Mth.ceil(target.pos.y + target.box.ysize))
            maxZ = maxOf(maxZ, Mth.ceil(target.pos.z))
        }

        for (pos in placePositions) {
            minX = minOf(minX, pos.x)
            minY = minOf(minY, pos.y)
            minZ = minOf(minZ, pos.z)
            maxX = maxOf(maxX, pos.x)
            maxY = maxOf(maxY, pos.y + 2)
            maxZ = maxOf(maxZ, pos.z)
        }

        for (crystal in crystals) {
            minX = minOf(minX, Mth.floor(crystal.pos.x))
            minY = minOf(minY, Mth.floor(crystal.pos.y))
            minZ = minOf(minZ, Mth.floor(crystal.pos.z))
            maxX = maxOf(maxX, Mth.ceil(crystal.pos.x))
            maxY = maxOf(maxY, Mth.ceil(crystal.pos.y + 2.0))
            maxZ = maxOf(maxZ, Mth.ceil(crystal.pos.z))
        }

        val expand = 12
        minX -= expand
        minY -= expand
        minZ -= expand
        maxX += expand
        maxY += expand
        maxZ += expand

        val resistant = HashSet<Long>()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val pos = BlockPos(x, y, z)
                    val state = level.getBlockState(pos)
                    if (isResistantState(state) && (!assumeInstantMine || !PacketMine.isInstantMining(pos))) {
                        resistant.add(pos.asLong())
                    }
                }
            }
        }
        return resistant
    }

    context(nn: NonNullContext)
    private fun evaluateSnapshot(snapshot: SnapshotData, startTime: Long): AsyncResult {
        if (snapshot.targets.isEmpty()) {
            return AsyncResult(snapshot.id, null, null, null, null, null, System.nanoTime() - startTime)
        }

        val rotationPlaceInfo = evaluatePlace(snapshot, false)
        val placeInfo = evaluatePlace(snapshot, true)
        val rotationBreakPlan = evaluateBreak(snapshot, rotationPlaceInfo, false)
        val breakPlan = evaluateBreak(snapshot, placeInfo, true)
        val targets = snapshot.targets
        val primary = when {
            placeInfo != null -> targets.firstOrNull { targetInfo -> targetInfo.entity === placeInfo.target }
                ?: targets.first()

            rotationPlaceInfo != null -> targets.firstOrNull { targetInfo -> targetInfo.entity === rotationPlaceInfo.target }
                ?: targets.first()

            else -> targets.first()
        }

        return AsyncResult(
            snapshot.id,
            rotationPlaceInfo,
            placeInfo,
            rotationBreakPlan,
            breakPlan,
            primary,
            System.nanoTime() - startTime
        )
    }

    context(nn: NonNullContext)
    private fun evaluatePlace(snapshot: SnapshotData, requireRotation: Boolean): PlaceInfo? {
        if (snapshot.placePositions.isEmpty()) return null

        val max = PlaceChoice()
        val safe = PlaceChoice()
        val lethal = PlaceChoice()

        for (pos in snapshot.placePositions) {
            if (requireRotation && !checkPlaceRotation(pos, snapshot.self.currentRotation)) continue

            val placeBox = getCrystalPlaceBox(pos)
            val crystalPos = getCrystalPos(pos)

            val selfDamage = calcDamage(snapshot.self, crystalPos, snapshot.resistantBlocks)
            val collidingDamage = calcCollidingCrystalDamage(snapshot, placeBox)
            val adjustedDamage =
                maxOf(selfDamage, collidingDamage - snapshot.settings.collidingCrystalExtraSelfDamageThreshold)

            if (snapshot.self.totalHealth - adjustedDamage <= snapshot.settings.noSuicide) continue
            if (snapshot.self.totalHealth - collidingDamage <= snapshot.settings.noSuicide) continue
            if (!snapshot.settings.lethalOverride && adjustedDamage > snapshot.settings.placeMaxSelfDamage) continue

            for (targetInfo in snapshot.targets) {
                if (targetInfo.box.intersects(placeBox)) continue
                val targetDamage =
                    calcDamage(targetInfo, crystalPos, snapshot.resistantBlocks, snapshot.self.difficulty)

                if (snapshot.settings.lethalOverride &&
                    targetDamage - targetInfo.totalHealth > snapshot.settings.lethalThresholdAddition &&
                    selfDamage < lethal.selfDamage &&
                    selfDamage <= snapshot.settings.lethalMaxSelfDamage
                ) {
                    lethal.update(targetInfo.entity, pos, adjustedDamage, targetDamage)
                }

                if (adjustedDamage > snapshot.settings.placeMaxSelfDamage) continue

                val minDamage = if (shouldForcePlace(snapshot.self, targetInfo)) {
                    snapshot.settings.forcePlaceMinDamage
                } else {
                    snapshot.settings.placeMinDamage
                }
                val balance = if (shouldForcePlace(snapshot.self, targetInfo)) {
                    snapshot.settings.forcePlaceBalance
                } else {
                    snapshot.settings.placeBalance
                }

                if (targetDamage < minDamage || targetDamage - adjustedDamage < balance) continue

                val score = snapshot.settings.damagePriority.score(adjustedDamage, targetDamage)
                val maxScore = snapshot.settings.damagePriority.score(max.selfDamage, max.targetDamage)
                if (score > maxScore) {
                    max.update(targetInfo.entity, pos, adjustedDamage, targetDamage)
                } else if (max.targetDamage - targetDamage <= snapshot.settings.safeMaxTargetDamageReduction &&
                    max.selfDamage - adjustedDamage >= snapshot.settings.safeMinSelfDamageReduction
                ) {
                    safe.update(targetInfo.entity, pos, adjustedDamage, targetDamage)
                }
            }
        }

        if (max.targetDamage - safe.targetDamage > snapshot.settings.safeMaxTargetDamageReduction ||
            max.selfDamage - safe.selfDamage <= snapshot.settings.safeMinSelfDamageReduction
        ) {
            safe.clear()
        }

        var choice = lethal.takeValid()
        if (choice == null) choice = safe.takeValid()
        if (choice == null) choice = max.takeValid()
        if (choice == null) return null

        return buildPlaceInfo(choice, snapshot.settings.placeSideBypass, snapshot.self.eyePos)
    }

    context(nn: NonNullContext)
    private fun evaluateBreak(snapshot: SnapshotData, placeInfo: PlaceInfo?, requireRotation: Boolean): BreakPlan? {
        if (snapshot.crystals.isEmpty()) return null

        val crystalList = snapshot.crystals
            .filter { crystal -> crystal.breakable }
            .filter { crystal ->
                !requireRotation || checkCrystalRotation(crystal.pos, breakRotationRange, snapshot.self.currentRotation)
            }
        if (crystalList.isEmpty()) return null

        val crystal = when (snapshot.settings.breakMode) {
            BreakMode.Own -> {
                val targetCrystal = getTargetCrystal(placeInfo, crystalList)
                targetCrystal ?: evaluateBestBreak(snapshot, crystalList.filter { info -> info.ownPlaced })
            }

            BreakMode.Target -> getTargetCrystal(placeInfo, crystalList)

            BreakMode.Smart -> {
                val targetCrystal = getTargetCrystal(placeInfo, crystalList)
                targetCrystal ?: evaluateBestBreak(snapshot, crystalList)
            }

            BreakMode.All -> {
                val ref: Entity = (if (placeInfo != null) placeInfo.target else snapshot.self.player) ?: return null
                crystalList.minByOrNull { info -> ref.distanceToSqr(info.entity) }
            }

            BreakMode.Off -> null
        }

        if (crystal == null) return null
        val selfDamage = calcDamage(snapshot.self, crystal.pos, snapshot.resistantBlocks)
        var targetDamage = 0.0f
        if (snapshot.targets.isNotEmpty()) {
            targetDamage =
                calcDamage(snapshot.targets.first(), crystal.pos, snapshot.resistantBlocks, snapshot.self.difficulty)
        }
        return BreakPlan(crystal.entity, crystal.id, crystal.pos, selfDamage, targetDamage)
    }

    private fun evaluateBestBreak(snapshot: SnapshotData, crystalList: List<CrystalSnapshot>): CrystalSnapshot? {
        if (crystalList.isEmpty()) return null

        val max = BreakChoice()
        val safe = BreakChoice()
        val lethal = BreakChoice()

        for (crystal in crystalList) {
            val selfDamage = calcDamage(snapshot.self, crystal.pos, snapshot.resistantBlocks)
            if (snapshot.self.totalHealth - selfDamage <= snapshot.settings.noSuicide) continue
            if (!snapshot.settings.lethalOverride && selfDamage > snapshot.settings.breakMaxSelfDamage) continue

            for (targetInfo in snapshot.targets) {
                val targetDamage =
                    calcDamage(targetInfo, crystal.pos, snapshot.resistantBlocks, snapshot.self.difficulty)
                if (snapshot.settings.lethalOverride &&
                    targetDamage - targetInfo.totalHealth > snapshot.settings.lethalThresholdAddition &&
                    selfDamage < lethal.selfDamage &&
                    selfDamage <= snapshot.settings.lethalMaxSelfDamage
                ) {
                    lethal.update(crystal, selfDamage, targetDamage)
                }

                if (selfDamage > snapshot.settings.breakMaxSelfDamage) continue

                val minDamage = if (shouldForcePlace(snapshot.self, targetInfo)) {
                    snapshot.settings.forcePlaceMinDamage
                } else {
                    snapshot.settings.breakMinDamage
                }
                val balance = if (shouldForcePlace(snapshot.self, targetInfo)) {
                    snapshot.settings.forcePlaceBalance
                } else {
                    snapshot.settings.breakBalance
                }
                if (targetDamage < minDamage || targetDamage - selfDamage < balance) continue

                val score = snapshot.settings.damagePriority.score(selfDamage, targetDamage)
                val maxScore = snapshot.settings.damagePriority.score(max.selfDamage, max.targetDamage)
                if (score > maxScore) {
                    max.update(crystal, selfDamage, targetDamage)
                } else if (max.targetDamage - targetDamage <= snapshot.settings.safeMaxTargetDamageReduction &&
                    max.selfDamage - selfDamage >= snapshot.settings.safeMinSelfDamageReduction
                ) {
                    safe.update(crystal, selfDamage, targetDamage)
                }
            }
        }

        if (max.targetDamage - safe.targetDamage > snapshot.settings.safeMaxTargetDamageReduction ||
            max.selfDamage - safe.selfDamage <= snapshot.settings.safeMinSelfDamageReduction
        ) {
            safe.clear()
        }

        var choice = lethal.takeValid()
        if (choice == null) choice = safe.takeValid()
        if (choice == null) choice = max.takeValid()
        return choice?.crystal
    }

    private fun calcCollidingCrystalDamage(snapshot: SnapshotData, placeBox: AABB): Float {
        var max = 0.0f
        for (crystal in snapshot.crystals) {
            if (!placeBox.intersects(crystal.box)) continue
            val damage = calcDamage(snapshot.self, crystal.pos, snapshot.resistantBlocks)
            if (damage > max) {
                max = damage
            }
        }
        return max
    }

    context(nn: NonNullContext)
    private fun buildPlaceInfo(choice: PlaceChoice, bypass: PlaceBypass, eyePos: Vec3): PlaceInfo {
        val blockPos = choice.blockPos ?: error("PlaceChoice has no block position")
        val side: Direction
        val hitVec: Vec3

        when (bypass) {
            PlaceBypass.Up -> {
                side = Direction.UP
                hitVec = Vec3(blockPos.x + 0.5, blockPos.y + 1.0, blockPos.z + 0.5)
            }

            PlaceBypass.Down -> {
                side = Direction.DOWN
                hitVec = Vec3(blockPos.x + 0.5, blockPos.y.toDouble(), blockPos.z + 0.5)
            }

            PlaceBypass.Closest -> {
                side = calcDirection(eyePos, Vec3.atCenterOf(blockPos))
                hitVec = Vec3(
                    blockPos.x + 0.5 + side.stepX * 0.5,
                    blockPos.y + 0.5 + side.stepY * 0.5,
                    blockPos.z + 0.5 + side.stepZ * 0.5,
                )
            }
        }

        return PlaceInfo(
            choice.target,
            blockPos,
            choice.selfDamage,
            choice.targetDamage,
            side,
            hitVec,
            RotationUtils.calculate(hitVec),
        )
    }

    private fun resolveCurrentTarget(result: AsyncResult, prePlace: PlaceInfo?): LivingEntity? {
        if (prePlace != null) {
            return prePlace.target
        }
        return result.primaryTarget?.entity
    }

    private fun getValidPlaceInfo(placeInfo: PlaceInfo?): PlaceInfo? {
        return getValidPlaceInfo(placeInfo, true)
    }

    private fun getValidPlaceInfo(placeInfo: PlaceInfo?, requirePlaceable: Boolean): PlaceInfo? {
        if (placeInfo == null || nullCheck()) return null
        val player = this.mc.player ?: return null
        if (this.mc.level == null) return null
        if (placeDistanceSq(
                player,
                placeInfo.hitVec.x,
                placeInfo.hitVec.y,
                placeInfo.hitVec.z
            ) > placeRange * placeRange
        ) {
            return null
        }
        if (!isCrystalSupport(placeInfo.blockPos)) {
            return null
        }
        return if (!requirePlaceable || isPlaceable(placeInfo.blockPos)) placeInfo else null
    }

    context(nn: NonNullContext)
    private fun getValidBreakPlan(breakPlan: BreakPlan?): BreakPlan? {
        if (breakPlan == null || nullCheck()) return null
        val level = this.mc.level ?: return null
        val entity = level.getEntity(breakPlan.entityId)
        if (entity !is EndCrystal || !entity.isAlive) {
            return null
        }
        return if (checkBreakRange(entity.position())) {
            BreakPlan(entity, entity.id, entity.position(), breakPlan.selfDamage, breakPlan.targetDamage)
        } else {
            null
        }
    }

    context(nn: NonNullContext)
    private fun getActionPlaceInfo(): PlaceInfo? {
        val action = getValidPlaceInfo(cachedPlaceInfo, true)
        if (action != null) return action

        val fallback = getValidPlaceInfo(cachedRotationPlaceInfo, true)
        if (fallback != null && checkPlaceRotation(fallback.blockPos)) {
            return fallback
        }

        return null
    }

    context(nn: NonNullContext)
    private fun getActionBreakPlan(): BreakPlan? {
        val action = getValidBreakPlan(cachedBreakPlan)
        if (action != null) return action

        val fallback = getValidBreakPlan(cachedRotationBreakPlan)
        if (fallback != null && checkCrystalRotation(fallback.pos, breakRotationRange)) {
            return fallback
        }

        return null
    }

    context(nn: NonNullContext)
    private fun placeDirect(placeInfo: PlaceInfo, ignoreTimer: Boolean): Boolean {
        val player = this.mc.player ?: return false
        if (!ignoreTimer && !placeTimer.passedMillise(placeDelay.toLong())) return false

        val crystals = findCrystalItem()
        if (!crystals.found()) return false

        val hand = crystals.getHand()
        val hitResult = BlockHitResult(placeInfo.hitVec, placeInfo.side, placeInfo.blockPos, false)

        val rotation = placeInfo.rotation
        RotationManager.INSTANCE.setRotations(rotation, getRotationSpeed(), null, Priority.High)
        if (preRotation && !isRotationReady(rotation)) {
            return true
        }

        if (hand == InteractionHand.MAIN_HAND && crystals.slot != player.inventory.selectedSlot && crystals.slot != 40) {
            when (placeSwitchMode) {
                SwitchMode.Off -> {
                    return false
                }

                SwitchMode.Legit -> {
                    InvUtils.swap(crystals.slot, false)
                    lastSwapTime = System.currentTimeMillis()
                }

                SwitchMode.Ghost -> {
                    InvUtils.swap(crystals.slot, true)
                    lastSwapTime = System.currentTimeMillis()
                }
            }
        }

        val finalHand = hand

        val gameMode = this.mc.gameMode ?: return false
        val result: InteractionResult = gameMode.useItemOn(player, finalHand, hitResult)
        if (result.consumesAction()) {
            if (placeSwing) {
                doSwing(resolveSwingHand(true), false)
            }
            placedPosMap[placeInfo.blockPos.asLong()] = System.currentTimeMillis() + ownTimeout
            placeTimer.reset()
            lastActiveTime = System.currentTimeMillis()
            target = placeInfo.target
            updateRenderTarget(placeInfo.blockPos, placeInfo.targetDamage, placeInfo.selfDamage)
        }
        InvUtils.swapBack()

        return true
    }

    context(nn: NonNullContext)
    private fun breakDirect(breakPlan: BreakPlan?): Boolean {
        if (breakPlan == null) return false
        if (placeSwitchMode != SwitchMode.Ghost &&
            antiWeakness != SwitchMode.Ghost &&
            System.currentTimeMillis() - lastSwapTime < swapDelay * 50L
        ) {
            return false
        }

        val player = this.mc.player ?: return false
        val needsAntiWeaknessSwap = player.hasEffect(MobEffects.WEAKNESS) && !isHoldingTool()
        var weaponSlot = -1
        if (needsAntiWeaknessSwap) {
            if (antiWeakness == SwitchMode.Off) return false
            weaponSlot = findWeaponSlot()
            if (weaponSlot == -1) return false
        }

        val rotation = RotationUtils.calculate(breakPlan.pos)
        RotationManager.INSTANCE.setRotations(rotation, getRotationSpeed(), null, Priority.High)
        if (preRotation && !isRotationReady(rotation)) {
            return true
        }

        if (needsAntiWeaknessSwap) {
            InvUtils.swap(weaponSlot, antiWeakness == SwitchMode.Ghost)
            lastSwapTime = System.currentTimeMillis()
        }

        val level = this.mc.level
        if (level == null) {
            InvUtils.swapBack()
            return true
        }
        val current = level.getEntity(breakPlan.entityId)
        if (current !is EndCrystal || !current.isAlive) {
            InvUtils.swapBack()
            return true
        }
        if (!checkBreakRange(current.position())) {
            InvUtils.swapBack()
            return true
        }

        val gameMode = this.mc.gameMode
        if (gameMode == null) {
            InvUtils.swapBack()
            return true
        }
        gameMode.attack(player, current)
        doSwing(resolveSwingHand(false), true)
        breakTimer.reset()
        lastActiveTime = System.currentTimeMillis()
        attackedCrystalMap[current.id] = System.currentTimeMillis() + 1000L
        attackedPosMap[BlockPos.containing(current.position()).asLong()] = System.currentTimeMillis() + 1000L
        updateRenderTarget(current.blockPosition().below(), breakPlan.targetDamage, breakPlan.selfDamage)

        val placeInfo = getActionPlaceInfo()
        if (packetPlace.onBreak && placeInfo != null && crystalPlaceBoxIntersects(
                placeInfo.blockPos,
                current.boundingBox
            )
        ) {
            placeDirect(placeInfo, true)
        }

        InvUtils.swapBack()

        return true
    }

    private fun handleSpawnPacket(packet: ClientboundAddEntityPacket) {
        val nn = NonNullContext.current ?: return
        context(nn) { handleSpawnPacketImpl(packet) }
    }

    context(nn: NonNullContext)
    private fun handleSpawnPacketImpl(packet: ClientboundAddEntityPacket) {
        crystalSpawnMap[packet.id] = System.currentTimeMillis()
        val snapshot = latestSnapshot
        if (snapshot == null || snapshot.targets.isEmpty() || bbtt) return

        val crystalPos = Vec3(packet.x, packet.y, packet.z)
        if (!checkBreakRange(crystalPos) || !checkCrystalRotation(crystalPos, breakRotationRange)) return
        var placeInfo = getActionPlaceInfo()
        if (placeInfo == null) {
            placeInfo = getValidPlaceInfo(cachedRotationPlaceInfo, false)
        }
        if (placeInfo == null) return

        val shouldBreak = when (packetBreak) {
            BreakMode.Target -> crystalPlaceBoxIntersects(placeInfo.blockPos, getCrystalBoundingBox(crystalPos))
            BreakMode.Own -> crystalPlaceBoxIntersects(placeInfo.blockPos, getCrystalBoundingBox(crystalPos)) ||
                    (placedPosMap.containsKey(toLong(packet.x, packet.y - 1.0, packet.z)) && checkBreakDamage(
                        snapshot,
                        crystalPos
                    ))

            BreakMode.Smart -> crystalPlaceBoxIntersects(placeInfo.blockPos, getCrystalBoundingBox(crystalPos)) ||
                    checkBreakDamage(snapshot, crystalPos)

            BreakMode.All -> true
            BreakMode.Off -> false
        }

        if (!shouldBreak) return

        val immediate = BreakPlan(
            null,
            packet.id,
            crystalPos,
            calcDamage(snapshot.self, crystalPos, snapshot.resistantBlocks),
            if (snapshot.targets.isEmpty()) 0.0f else calcDamage(
                snapshot.targets.first(),
                crystalPos,
                snapshot.resistantBlocks,
                snapshot.self.difficulty
            ),
        )
        breakDirect(immediate)
    }

    private fun handleExplosionPacket(packet: ClientboundSoundPacket) {
        val nn = NonNullContext.current ?: return
        context(nn) { handleExplosionPacketImpl(packet) }
    }

    context(nn: NonNullContext)
    private fun handleExplosionPacketImpl(packet: ClientboundSoundPacket) {
        if (packet.sound !== SoundEvents.GENERIC_EXPLODE) return

        val soundPos = Vec3(packet.x, packet.y, packet.z)
        if (attackedPosMap.containsKey(BlockPos.containing(soundPos).asLong())) {
            explosionsThisWindow++
        }

        val placeInfo = getValidPlaceInfo(cachedPlaceInfo)
        if (placeInfo != null) {
            val placePos = getCrystalPos(placeInfo.blockPos)
            if (placePos.distanceToSqr(soundPos) <= 144.0) {
                placedPosMap.clear()
                crystalSpawnMap.clear()
                attackedCrystalMap.clear()
                attackedPosMap.clear()
                if (packetPlace.onRemove) {
                    placeDirect(placeInfo, true)
                }
                return
            }
        }

        val player = this.mc.player
        if (player != null && player.distanceToSqr(soundPos) <= 144.0) {
            placedPosMap.clear()
            crystalSpawnMap.clear()
            attackedCrystalMap.clear()
            attackedPosMap.clear()
        }
    }

    private fun checkBreakDamage(snapshot: SnapshotData, crystalPos: Vec3): Boolean {
        val selfDamage = calcDamage(snapshot.self, crystalPos, snapshot.resistantBlocks)
        if (snapshot.self.totalHealth - selfDamage <= snapshot.settings.noSuicide) return false

        for (targetInfo in snapshot.targets) {
            val targetDamage = calcDamage(targetInfo, crystalPos, snapshot.resistantBlocks, snapshot.self.difficulty)
            if (snapshot.settings.lethalOverride &&
                targetDamage - targetInfo.totalHealth > snapshot.settings.lethalThresholdAddition &&
                selfDamage <= snapshot.settings.lethalMaxSelfDamage
            ) {
                return true
            }

            if (selfDamage > snapshot.settings.breakMaxSelfDamage) continue
            val minDamage = if (shouldForcePlace(snapshot.self, targetInfo)) {
                snapshot.settings.forcePlaceMinDamage
            } else {
                snapshot.settings.breakMinDamage
            }
            val balance = if (shouldForcePlace(snapshot.self, targetInfo)) {
                snapshot.settings.forcePlaceBalance
            } else {
                snapshot.settings.breakBalance
            }
            if (targetDamage >= minDamage && targetDamage - selfDamage >= balance) {
                return true
            }
        }

        return false
    }

    private fun getTargetCrystal(placeInfo: PlaceInfo?, crystalList: List<CrystalSnapshot>): CrystalSnapshot? {
        if (placeInfo == null) return null
        for (crystal in crystalList) {
            if (crystalPlaceBoxIntersects(placeInfo.blockPos, crystal.box)) {
                return crystal
            }
        }
        return null
    }

    private fun calcDamage(self: SelfSnapshot, crystalPos: Vec3, resistantBlocks: ResistantBlockCache): Float {
        return calcDamage(self.pos, self.box, self.reduction, true, self.difficulty, crystalPos, resistantBlocks)
    }

    private fun calcDamage(
        target: TargetSnapshot,
        crystalPos: Vec3,
        resistantBlocks: ResistantBlockCache,
        difficulty: Difficulty,
    ): Float {
        return calcDamage(
            target.pos,
            target.box,
            target.reduction,
            target.player,
            difficulty,
            crystalPos,
            resistantBlocks
        )
    }

    private fun calcDamage(
        entityPos: Vec3,
        entityBox: AABB,
        reduction: DamageReductionData,
        playerEntity: Boolean,
        difficulty: Difficulty,
        crystalPos: Vec3,
        resistantBlocks: ResistantBlockCache,
    ): Float {
        if (playerEntity && difficulty == Difficulty.PEACEFUL) {
            return 0.0f
        }

        val damage: Float
        val supportPos = BlockPos.containing(crystalPos.x, crystalPos.y - 1.0, crystalPos.z)
        if (playerEntity && crystalPos.y - entityPos.y > 1.5652173822904127 && resistantBlocks.isResistant(supportPos.asLong())) {
            damage = 1.0f
        } else {
            damage = calcRawDamage(entityPos, entityBox, crystalPos, resistantBlocks)
        }

        return if (playerEntity) {
            reduction.apply(applyDifficultyDamage(difficulty, damage))
        } else {
            reduction.apply(damage)
        }
    }

    private fun calcRawDamage(
        entityPos: Vec3,
        entityBox: AABB,
        crystalPos: Vec3,
        resistantBlocks: ResistantBlockCache
    ): Float {
        val scaledDist = (entityPos.distanceTo(crystalPos) / DOUBLE_SIZE).toFloat()
        if (scaledDist > 1.0f) return 0.0f

        val factor = (1.0f - scaledDist) * getExposureAmount(entityBox, crystalPos, resistantBlocks)
        return (factor * factor + factor) * DAMAGE_FACTOR + 1.0f
    }

    private fun getExposureAmount(entityBox: AABB, explosionPos: Vec3, resistantBlocks: ResistantBlockCache): Float {
        val width = entityBox.maxX - entityBox.minX
        val height = entityBox.maxY - entityBox.minY
        val gridMultiplierXZ = 1.0 / (width * 2.0 + 1.0)
        val gridMultiplierY = 1.0 / (height * 2.0 + 1.0)
        val gridXZ = width * gridMultiplierXZ
        val gridY = height * gridMultiplierY
        val sizeXZ = Mth.floor(1.0 / gridMultiplierXZ)
        val sizeY = Mth.floor(1.0 / gridMultiplierY)
        val xzOffset = (1.0 - gridMultiplierXZ * sizeXZ) / 2.0

        var total = 0
        var count = 0
        for (yIndex in 0..sizeY) {
            for (xIndex in 0..sizeXZ) {
                for (zIndex in 0..sizeXZ) {
                    val x = gridXZ * xIndex + xzOffset + entityBox.minX
                    val y = gridY * yIndex + entityBox.minY
                    val z = gridXZ * zIndex + xzOffset + entityBox.minZ
                    total++
                    if (!rayTraceResistant(Vec3(x, y, z), explosionPos, resistantBlocks)) {
                        count++
                    }
                }
            }
        }
        return if (total == 0) 0.0f else count / total.toFloat()
    }

    private fun rayTraceResistant(from: Vec3, to: Vec3, resistantBlocks: ResistantBlockCache): Boolean {
        var x = Mth.floor(from.x)
        var y = Mth.floor(from.y)
        var z = Mth.floor(from.z)
        val endX = Mth.floor(to.x)
        val endY = Mth.floor(to.y)
        val endZ = Mth.floor(to.z)

        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z

        val stepX = if (dx > 0.0) 1 else if (dx < 0.0) -1 else 0
        val stepY = if (dy > 0.0) 1 else if (dy < 0.0) -1 else 0
        val stepZ = if (dz > 0.0) 1 else if (dz < 0.0) -1 else 0

        var tMaxX = intBound(from.x, dx)
        var tMaxY = intBound(from.y, dy)
        var tMaxZ = intBound(from.z, dz)
        val tDeltaX = if (stepX == 0) Double.POSITIVE_INFINITY else abs(1.0 / dx)
        val tDeltaY = if (stepY == 0) Double.POSITIVE_INFINITY else abs(1.0 / dy)
        val tDeltaZ = if (stepZ == 0) Double.POSITIVE_INFINITY else abs(1.0 / dz)

        for (i in 0 until 200) {
            if (resistantBlocks.isResistant(BlockPos.asLong(x, y, z))) {
                return true
            }
            if (x == endX && y == endY && z == endZ) {
                break
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX
                    tMaxX += tDeltaX
                } else {
                    z += stepZ
                    tMaxZ += tDeltaZ
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY
                    tMaxY += tDeltaY
                } else {
                    z += stepZ
                    tMaxZ += tDeltaZ
                }
            }
        }

        return false
    }

    private fun intBound(s: Double, ds: Double): Double {
        if (ds > 0.0) {
            return (floor(s + 1.0) - s) / ds
        }
        if (ds < 0.0) {
            return (s - floor(s)) / -ds
        }
        return Double.POSITIVE_INFINITY
    }

    private fun applyDifficultyDamage(difficulty: Difficulty, damage: Float): Float {
        return when (difficulty) {
            Difficulty.PEACEFUL -> 0.0f
            Difficulty.EASY -> minOf(damage * 0.5f + 1.0f, damage)
            Difficulty.HARD -> damage * 1.5f
            else -> damage
        }
    }

    private fun shouldForcePlace(self: SelfSnapshot, targetInfo: TargetSnapshot): Boolean {
        return (!forcePlaceWhileSwording || !self.swording) &&
                (targetInfo.totalHealth.toDouble() <= forcePlaceHealth ||
                        targetInfo.speed >= forcePlaceMotion ||
                        targetInfo.minArmorRate <= forcePlaceArmorRate)
    }

    context(nn: NonNullContext)
    private fun getRawPosList(level: net.minecraft.client.multiplayer.ClientLevel): List<BlockPos> {
        val now = System.currentTimeMillis()
        var cached = cachedRawPosList
        if (now < rawPosListExpireAt) return cached
        cached = buildRawPosList(level)
        cachedRawPosList = cached
        rawPosListExpireAt = now + 60L
        return cached
    }

    context(nn: NonNullContext)
    private fun buildRawPosList(level: net.minecraft.client.multiplayer.ClientLevel): List<BlockPos> {
        val player = this.mc.player ?: return emptyList()

        val list = ArrayList<BlockPos>()
        val range = placeRange
        val rangeSq = range * range
        val wallRangeSq = wallRange * wallRange
        val floor = Mth.floor(range)
        val ceil = Mth.ceil(range)
        val feetPos = player.position()
        val eyePos = player.eyePosition

        val feetX = Mth.floor(feetPos.x)
        val feetY = Mth.floor(feetPos.y)
        val feetZ = Mth.floor(feetPos.z)

        for (x in feetX - floor..feetX + ceil) {
            for (z in feetZ - floor..feetZ + ceil) {
                for (y in feetY - floor..feetY + ceil) {
                    val pos = BlockPos(x, y, z)
                    if (!level.worldBorder.isWithinBounds(pos)) continue

                    val crystalX = x + 0.5
                    val crystalY = y + 1.0
                    val crystalZ = z + 0.5
                    if (placeDistanceSq(player, crystalX, crystalY, crystalZ) > rangeSq) continue
                    if (!isPotentialPlacePosition(level, pos)) continue

                    val feetDistSq = feetPos.distanceToSqr(crystalX, crystalY, crystalZ)
                    if (feetDistSq > wallRangeSq && !RaytraceUtils.canSeePointFrom(
                            eyePos,
                            Vec3(crystalX, crystalY + 1.7, crystalZ)
                        )
                    ) {
                        continue
                    }

                    list.add(pos)
                }
            }
        }

        list.sortWith(
            Comparator.comparingDouble<BlockPos> { pos ->
                pos.distToCenterSqr(feetX.toDouble(), feetY.toDouble(), feetZ.toDouble())
            }.reversed()
        )
        return list
    }

    context(nn: NonNullContext)
    private fun getCollidingEntities(level: net.minecraft.client.multiplayer.ClientLevel): List<Entity> {
        val player = this.mc.player ?: return emptyList()

        val colliding = ArrayList<Entity>()
        val rangeSq = placeRange * placeRange
        val feetX = Mth.floor(player.x)
        val feetY = Mth.floor(player.y)
        val feetZ = Mth.floor(player.z)
        val single = placeMode == PlaceMode.Single

        for (entity in level.entitiesForRendering()) {
            if (!entity.isAlive) continue

            val adjustedRange =
                Mth.ceil(rangeSq) - ceil(((entity.bbWidth / 2.0f) * (entity.bbWidth / 2.0f) * 2.0f).toDouble())
            val dist = entity.distanceToSqr(feetX + 0.5, feetY + 0.5, feetZ + 0.5)
            if (dist > adjustedRange) continue

            if (entity !is EndCrystal) {
                colliding.add(entity)
            } else if (!single || !checkBreakRange(entity.position())) {
                colliding.add(entity)
            }
        }

        return colliding
    }

    private fun checkPlaceCollision(pos: BlockPos, collidingEntities: List<Entity>): Boolean {
        val minX = pos.x + 0.001
        val minY = pos.y + 1.0
        val minZ = pos.z + 0.001
        val maxX = pos.x + 0.999
        val maxY = pos.y + 3.0
        val maxZ = pos.z + 0.999

        for (entity in collidingEntities) {
            if (entity.boundingBox.intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
                return false
            }
        }
        return true
    }

    context(nn: NonNullContext)
    private fun checkPlaceRotation(pos: BlockPos): Boolean {
        if (placeRotationRange <= 0.0) return true
        return checkPlaceRotation(pos, RotationManager.INSTANCE.rotations)
    }

    context(nn: NonNullContext)
    private fun checkPlaceRotation(pos: BlockPos, currentRotation: Rot2f): Boolean {
        if (placeRotationRange <= 0.0) return true
        return getRotationDelta(currentRotation, RotationUtils.calculate(getCrystalPos(pos))) <= placeRotationRange
    }

    context(nn: NonNullContext)
    private fun checkCrystalRotation(crystalPos: Vec3, range: Double): Boolean {
        if (range <= 0.0) return true
        return checkCrystalRotation(crystalPos, range, RotationManager.INSTANCE.rotations)
    }

    context(nn: NonNullContext)
    private fun checkCrystalRotation(crystalPos: Vec3, range: Double, currentRotation: Rot2f): Boolean {
        if (range <= 0.0) return true
        return getRotationDelta(currentRotation, RotationUtils.calculate(crystalPos)) <= range
    }

    context(nn: NonNullContext)
    private fun checkBreakRange(crystalPos: Vec3): Boolean {
        val player = this.mc.player ?: return false
        val rangeSq = breakRange * breakRange
        if (breakDistanceSq(player, crystalPos.x, crystalPos.y, crystalPos.z) > rangeSq) return false

        val eyePos = player.eyePosition
        return eyePos.distanceToSqr(crystalPos) <= wallRange * wallRange ||
                RaytraceUtils.canSeePointFrom(eyePos, Vec3(crystalPos.x, crystalPos.y + 1.7, crystalPos.z))
    }

    private fun canMove(entity: Entity, box: AABB, motionX: Double, motionY: Double, motionZ: Double): AABB? {
        val level = this.mc.level ?: return null
        val moved = box.move(motionX, motionY, motionZ)
        return if (level.noCollision(entity, moved)) moved else null
    }

    private fun isPlaceable(pos: BlockPos): Boolean {
        if (!isPotentialPlacePosition(pos)) return false
        val level = this.mc.level ?: return false
        return level.getEntities(null, getCrystalPlaceBox(pos)).isEmpty()
    }

    private fun isPotentialPlacePosition(pos: BlockPos): Boolean {
        val level = this.mc.level ?: return false
        return isPotentialPlacePosition(level, pos)
    }

    private fun isPotentialPlacePosition(level: net.minecraft.client.multiplayer.ClientLevel, pos: BlockPos): Boolean {
        if (!isCrystalSupport(level, pos)) return false

        val crystalPos = pos.above()
        return level.getBlockState(crystalPos).canBeReplaced() && level.getBlockState(crystalPos.above())
            .canBeReplaced()
    }

    private fun isCrystalSupport(pos: BlockPos): Boolean {
        val level = this.mc.level ?: return false
        return isCrystalSupport(level, pos)
    }

    private fun isCrystalSupport(level: net.minecraft.client.multiplayer.ClientLevel, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return state.`is`(Blocks.OBSIDIAN) || state.`is`(Blocks.BEDROCK)
    }

    private fun placeDistanceSq(entity: Entity, x: Double, y: Double, z: Double): Double {
        return if (placeRangeMode == RangeMode.Feet) entity.distanceToSqr(x, y, z) else eyeDistanceSq(entity, x, y, z)
    }

    private fun breakDistanceSq(entity: Entity, x: Double, y: Double, z: Double): Double {
        return if (breakRangeMode == RangeMode.Feet) entity.distanceToSqr(x, y, z) else eyeDistanceSq(entity, x, y, z)
    }

    private fun eyeDistanceSq(entity: Entity, x: Double, y: Double, z: Double): Double {
        val dx = entity.x - x
        val dy = entity.y + entity.eyeHeight - y
        val dz = entity.z - z
        return dx * dx + dy * dy + dz * dz
    }

    private fun getMinArmorRate(entity: LivingEntity): Int {
        var minDura = 100
        for (slot in arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            val armor = entity.getItemBySlot(slot)
            if (!armor.isDamageableItem) continue
            val maxDamage = armor.maxDamage
            if (maxDamage <= 0) continue
            val remaining = maxDamage - armor.damageValue
            val percent = ((remaining / maxDamage.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            minDura = minOf(minDura, percent)
        }
        return minDura
    }

    private fun getRealSpeed(entity: LivingEntity): Double {
        return hypot(entity.x - entity.xo, entity.z - entity.zo) * 20.0
    }

    private fun getTotalHealth(entity: LivingEntity): Float {
        return entity.health + entity.absorptionAmount
    }

    private fun getSpawnTime(crystal: EndCrystal): Long {
        return crystalSpawnMap.getOrPut(crystal.id) { System.currentTimeMillis() - crystal.tickCount * 50L }
    }

    private fun isHoldingTool(): Boolean {
        val player = this.mc.player ?: return false
        return isToolLike(player.mainHandItem)
    }

    private fun isToolLike(stack: ItemStack): Boolean {
        return stack.`is`(ItemTags.SWORDS) ||
                stack.`is`(ItemTags.AXES) ||
                stack.`is`(ItemTags.PICKAXES) ||
                stack.`is`(ItemTags.SHOVELS) ||
                stack.`is`(ItemTags.HOES)
    }

    private fun findWeaponSlot(): Int {
        val player = this.mc.player ?: return -1
        for (i in 0 until 9) {
            val stack = player.inventory.getItem(i)
            if (isToolLike(stack)) return i
        }
        return -1
    }

    context(nn: NonNullContext)
    private fun findCrystalItem(): FindItemResult {
        return InvUtils.findInHotbar(Items.END_CRYSTAL)
    }

    private fun resolveSwingHand(placing: Boolean): InteractionHand {
        val player = this.mc.player ?: return InteractionHand.MAIN_HAND
        return when (swingHand) {
            SwingHand.OffHand -> InteractionHand.OFF_HAND
            SwingHand.MainHand -> InteractionHand.MAIN_HAND
            SwingHand.Auto ->
                if ((placing && player.offhandItem.`is`(Items.END_CRYSTAL)) || !player.offhandItem.`is`(Items.GOLDEN_APPLE)) {
                    InteractionHand.OFF_HAND
                } else {
                    InteractionHand.MAIN_HAND
                }
        }
    }

    private fun doSwing(hand: InteractionHand, attacking: Boolean) {
        if (attacking) swingMode.attackSwing(hand) else swingMode.interactSwing(hand)
    }

    private fun getRotationSpeed(): Double {
        return maxOf(1.8, yawSpeed)
    }

    context(nn: NonNullContext)
    private fun prepareRotation(breakPlan: BreakPlan?, placeInfo: PlaceInfo?) {
        if (breakPlan != null && shouldPrioritizeBreak(breakPlan)) {
            RotationManager.INSTANCE.setRotations(
                RotationUtils.calculate(breakPlan.pos),
                getRotationSpeed(),
                null,
                Priority.High,
            )
            return
        }

        if (placeMode != PlaceMode.Off && placeInfo != null) {
            RotationManager.INSTANCE.setRotations(placeInfo.rotation, getRotationSpeed(), null, Priority.High)
        }
    }

    private fun shouldPrioritizeBreak(breakPlan: BreakPlan?): Boolean {
        return preRotation &&
                breakMode != BreakMode.Off &&
                breakTimer.passedMillise(breakDelay.toLong()) &&
                breakPlan != null &&
                canAttemptBreak()
    }

    private fun canAttemptBreak(): Boolean {
        if (placeSwitchMode != SwitchMode.Ghost &&
            antiWeakness != SwitchMode.Ghost &&
            System.currentTimeMillis() - lastSwapTime < swapDelay * 50L
        ) {
            return false
        }

        val player = this.mc.player
        return player == null || !player.hasEffect(MobEffects.WEAKNESS) || isHoldingTool() || antiWeakness != SwitchMode.Off && findWeaponSlot() != -1
    }

    private fun isRotationReady(targetRotation: Rot2f): Boolean {
        return getRotationDelta(RotationManager.INSTANCE.rotations, targetRotation) <= ROTATION_READY_EPSILON
    }

    private fun getRotationDelta(from: Rot2f, to: Rot2f): Float {
        val yawDiff = abs(Mth.wrapDegrees(to.yaw - from.yaw).toDouble())
        val pitchDiff = abs((to.pitch - from.pitch).toDouble())
        return hypot(yawDiff, pitchDiff).toFloat()
    }

    private fun getCrystalPos(supportPos: BlockPos): Vec3 {
        return Vec3(supportPos.x + 0.5, supportPos.y + 1.0, supportPos.z + 0.5)
    }

    private fun getCrystalPlaceBox(supportPos: BlockPos): AABB {
        return AABB(
            supportPos.x.toDouble(), supportPos.y + 1.0, supportPos.z.toDouble(),
            supportPos.x + 1.0, supportPos.y + 3.0, supportPos.z + 1.0,
        )
    }

    private fun getCrystalBoundingBox(crystalPos: Vec3): AABB {
        return AABB(
            crystalPos.x - 1.0, crystalPos.y, crystalPos.z - 1.0,
            crystalPos.x + 1.0, crystalPos.y + 2.0, crystalPos.z + 1.0,
        )
    }

    private fun crystalPlaceBoxIntersects(supportPos: BlockPos, crystalBox: AABB): Boolean {
        return getCrystalPlaceBox(supportPos).intersects(crystalBox)
    }

    private fun toLong(x: Double, y: Double, z: Double): Long {
        return BlockPos.containing(x, y, z).asLong()
    }

    private fun updateTimeouts() {
        val current = System.currentTimeMillis()
        placedPosMap.values.removeAll { time -> time < current }
        crystalSpawnMap.values.removeAll { time -> time + 5000L < current }
        attackedCrystalMap.values.removeAll { time -> time < current }
        attackedPosMap.values.removeAll { time -> time < current }
    }

    private fun isEatingPaused(): Boolean {
        if (!eatingPause) {
            return false
        }

        val player = this.mc.player ?: return false
        return player.isUsingItem
    }

    private fun updateExplosionSamples() {
        if (!explosionSampleTimer.passedMillise(250L)) {
            return
        }

        explosionSampleTimer.reset()
        explosionSamples.addLast(explosionsThisWindow)
        while (explosionSamples.size > EXPLOSION_SAMPLE_SIZE) {
            explosionSamples.removeFirst()
        }
        explosionsThisWindow = 0
    }

    private fun getExplosionSpeed(): Double {
        if (explosionSamples.isEmpty()) {
            return 0.0
        }

        var total = 0
        for (value in explosionSamples) {
            total += value
        }
        return total / explosionSamples.size.toDouble() * 4.0
    }

    private fun shouldAttemptPlace(placeInfo: PlaceInfo): Boolean {
        if (spamPlace) {
            return true
        }
        val level = this.mc.level ?: return false

        val placeBox = getCrystalPlaceBox(placeInfo.blockPos)
        for (entity in level.entitiesForRendering()) {
            if (entity !is EndCrystal || !entity.isAlive) continue
            if (!placeBox.intersects(entity.boundingBox)) continue
            if (attackedCrystalMap.containsKey(entity.id)) continue
            return false
        }

        return true
    }

    private fun renderTargetPredictions() {
        if (renderPredict == RenderPredictMode.Off) return

        val snapshot = latestSnapshot
        if (snapshot == null || snapshot.targets.isEmpty()) return

        var focus = this.target
        val primary = asyncResult.primaryTarget
        if (focus == null && primary != null) {
            focus = primary.entity
        }

        val outline = Color(100, 255, 100, 150)
        val filled = Color(100, 255, 100, 28)

        for (targetInfo in snapshot.targets) {
            if (renderPredict == RenderPredictMode.Single && targetInfo.entity !== focus) {
                continue
            }

            Render3DScheduler.addFilledBox(targetInfo.box, filled)
            Render3DScheduler.addOutlineBox(targetInfo.box, outline.rgb, 1.5f)
        }
    }

    private fun updateRenderTarget(pos: BlockPos, damage: Float, selfDamage: Float) {
        val now = System.currentTimeMillis()
        if (renderBlockPos != pos) {
            renderCurrentPos = Vec3.atCenterOf(pos)
            renderPrevPos = renderLastRenderedPos ?: renderCurrentPos
            renderMoveStartTime = now
            if (renderBlockPos == null) {
                renderFadeStartTime = now
            }
            renderBlockPos = pos
        }
        if (!renderHasTarget) {
            renderFadeStartTime = now
        }
        renderHasTarget = true
        renderDamage = damage
        renderSelfDamageValue = selfDamage
    }

    private fun deactivateRenderTarget() {
        if (renderHasTarget) {
            renderHasTarget = false
            renderFadeStartTime = System.currentTimeMillis()
        }
    }

    private fun resetRenderState() {
        renderBlockPos = null
        renderPrevPos = null
        renderCurrentPos = null
        renderLastRenderedPos = null
        renderMoveStartTime = 0L
        renderFadeStartTime = 0L
        renderScale = 0.0f
        renderDamage = 0.0f
        renderSelfDamageValue = 0.0f
        renderHasTarget = false
    }

    private fun projectToScreen(pos: Vec3): Vector2f? {
        val projected: Vector3f = WorldToScreen.calcWorld2Screen(pos) ?: return null

        val centerX = projected.x
        val centerY = projected.y
        if (centerX < 0.0f || centerY < 0.0f ||
            centerX > LuminRenderSystem.getScaledWidth() ||
            centerY > LuminRenderSystem.getScaledHeight()
        ) {
            return null
        }
        return Vector2f(centerX, centerY)
    }

    private fun calcDirection(eyePos: Vec3, hitVec: Vec3): Direction {
        val x = eyePos.x - hitVec.x
        val y = eyePos.y - hitVec.y
        val z = eyePos.z - hitVec.z

        var best = Direction.NORTH
        var bestDot = Double.NEGATIVE_INFINITY
        for (direction in Direction.values()) {
            val stepX = direction.stepX.toDouble()
            val stepY = direction.stepY.toDouble()
            val stepZ = direction.stepZ.toDouble()
            val dot = x * stepX + y * stepY + z * stepZ
            if (dot > bestDot) {
                bestDot = dot
                best = direction
            }
        }
        return best
    }

    private fun isResistantState(state: BlockState): Boolean {
        return isResistantStateStatic(state)
    }

    // ---- 常量 ----

    private const val EXPLOSION_SAMPLE_SIZE = 8
    private const val ROTATION_READY_EPSILON = 1.0f
    private const val DOUBLE_SIZE = 12.0f
    private const val DAMAGE_FACTOR = 42.0f

    // ---- 嵌套数据结构 ----

    private class ResistantBlockCache(private val level: Level?, private val assumeInstantMine: Boolean) {

        private val cache: MutableMap<Long, Boolean> = HashMap()

        fun isResistant(posLong: Long): Boolean {
            val cached = cache[posLong]
            if (cached != null) return cached
            var resistant = false
            if (level != null) {
                val pos = BlockPos.of(posLong)
                val state = level.getBlockState(pos)
                resistant = isResistantStateStatic(state) && (!assumeInstantMine || !PacketMine.isInstantMining(pos))
            }
            cache[posLong] = resistant
            return resistant
        }

        companion object {

            val EMPTY: ResistantBlockCache = ResistantBlockCache(null, false)

        }

    }

    private class DamageReductionData private constructor(
        private val armorValue: Float,
        private val toughness: Float,
        private val resistanceMultiplier: Float,
        private val blastReduction: Float,
    ) {

        fun apply(damage: Float): Float {
            val toughnessFactor = 2.0f + toughness / 4.0f
            val effectiveArmor = (armorValue - damage / toughnessFactor).coerceIn(armorValue * 0.2f, 20.0f)
            val afterArmor = damage * (1.0f - effectiveArmor / 25.0f)
            return afterArmor * resistanceMultiplier * blastReduction
        }

        companion object {

            val DEFAULT: DamageReductionData = DamageReductionData(0.0f, 0.0f, 1.0f, 1.0f)

            fun fromEntity(entity: LivingEntity, armorMode: DamageUtils.ArmorEnchantmentMode): DamageReductionData {
                val armorValue = entity.getAttributeValue(Attributes.ARMOR).toFloat()
                val toughness = entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat()
                var resistanceMultiplier = 1.0f
                val resistance = entity.getEffect(MobEffects.RESISTANCE)
                if (entity.hasEffect(MobEffects.RESISTANCE) && resistance != null) {
                    resistanceMultiplier = maxOf(1.0f - (resistance.amplifier + 1) * 0.2f, 0.0f)
                }

                val epf = when (armorMode) {
                    DamageUtils.ArmorEnchantmentMode.PPPP -> 16
                    DamageUtils.ArmorEnchantmentMode.PPBP -> 20
                    DamageUtils.ArmorEnchantmentMode.None -> {
                        var actualEpf = 0
                        for (slot in arrayOf(
                            EquipmentSlot.HEAD,
                            EquipmentSlot.CHEST,
                            EquipmentSlot.LEGS,
                            EquipmentSlot.FEET
                        )) {
                            val stack = entity.getItemBySlot(slot)
                            actualEpf += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.PROTECTION)
                            actualEpf += EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.BLAST_PROTECTION) * 2
                        }
                        actualEpf
                    }
                }

                val blastReduction = 1.0f - minOf(epf, 20) / 25.0f
                return DamageReductionData(armorValue, toughness, resistanceMultiplier, blastReduction)
            }

        }

    }

    private class PlaceChoice {

        var target: LivingEntity? = null
        var blockPos: BlockPos? = null
        var selfDamage: Float = Float.MAX_VALUE
        var targetDamage: Float = Float.NEGATIVE_INFINITY

        fun update(target: LivingEntity?, blockPos: BlockPos?, selfDamage: Float, targetDamage: Float) {
            this.target = target
            this.blockPos = blockPos
            this.selfDamage = selfDamage
            this.targetDamage = targetDamage
        }

        fun clear() {
            target = null
            blockPos = null
            selfDamage = Float.MAX_VALUE
            targetDamage = Float.NEGATIVE_INFINITY
        }

        fun takeValid(): PlaceChoice? {
            return if (target != null && blockPos != null && selfDamage != Float.MAX_VALUE && targetDamage > Float.NEGATIVE_INFINITY) this else null
        }

    }

    private class BreakChoice {

        var crystal: CrystalSnapshot? = null
        var selfDamage: Float = Float.MAX_VALUE
        var targetDamage: Float = Float.NEGATIVE_INFINITY

        fun update(crystal: CrystalSnapshot?, selfDamage: Float, targetDamage: Float) {
            this.crystal = crystal
            this.selfDamage = selfDamage
            this.targetDamage = targetDamage
        }

        fun clear() {
            crystal = null
            selfDamage = Float.MAX_VALUE
            targetDamage = Float.NEGATIVE_INFINITY
        }

        fun takeValid(): BreakChoice? {
            return if (crystal != null && selfDamage != Float.MAX_VALUE && targetDamage > Float.NEGATIVE_INFINITY) this else null
        }

    }

    private data class SettingsSnapshot(
        val globalDelayNanos: Int,
        val noSuicide: Float,
        val placeMaxSelfDamage: Float,
        val breakMaxSelfDamage: Float,
        val placeMinDamage: Float,
        val breakMinDamage: Float,
        val placeBalance: Float,
        val breakBalance: Float,
        val forcePlaceMinDamage: Float,
        val forcePlaceBalance: Float,
        val lethalOverride: Boolean,
        val lethalThresholdAddition: Float,
        val lethalMaxSelfDamage: Float,
        val safeMaxTargetDamageReduction: Float,
        val safeMinSelfDamageReduction: Float,
        val collidingCrystalExtraSelfDamageThreshold: Float,
        val damagePriority: DamagePriority,
        val armorMode: DamageUtils.ArmorEnchantmentMode,
        val placeSideBypass: PlaceBypass,
        val packetPlaceMode: PacketPlaceMode,
        val breakMode: BreakMode,
        val packetBreakMode: BreakMode,
    )

    private data class SelfSnapshot(
        val player: Player?,
        val pos: Vec3,
        val eyePos: Vec3,
        val box: AABB,
        val totalHealth: Float,
        val weaknessActive: Boolean,
        val holdingTool: Boolean,
        val swording: Boolean,
        val reduction: DamageReductionData,
        val difficulty: Difficulty,
        val currentRotation: Rot2f,
    )

    private data class TargetSnapshot(
        val entity: LivingEntity,
        val pos: Vec3,
        val box: AABB,
        val currentPos: Vec3,
        val predictMotion: Vec3,
        val totalHealth: Float,
        val player: Boolean,
        val speed: Double,
        val minArmorRate: Int,
        val reduction: DamageReductionData,
    )

    private data class CrystalSnapshot(
        val entity: EndCrystal,
        val id: Int,
        val pos: Vec3,
        val box: AABB,
        val breakable: Boolean,
        val ownPlaced: Boolean,
    )

    private data class SnapshotData(
        val id: Long,
        val settings: SettingsSnapshot,
        val self: SelfSnapshot,
        val targets: List<TargetSnapshot>,
        val placePositions: List<BlockPos>,
        val crystals: List<CrystalSnapshot>,
        val resistantBlocks: ResistantBlockCache,
        val capturedAt: Long,
    ) {

        companion object {

            private val DEFAULT_SETTINGS: SettingsSnapshot = SettingsSnapshot(
                0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                false, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                DamagePriority.Efficient,
                DamageUtils.ArmorEnchantmentMode.None,
                PlaceBypass.Up,
                PacketPlaceMode.Off,
                BreakMode.Off,
                BreakMode.Off,
            )

            private val DEFAULT_SELF: SelfSnapshot = SelfSnapshot(
                null,
                Vec3.ZERO,
                Vec3.ZERO,
                AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                0.0f,
                false,
                false,
                false,
                DamageReductionData.DEFAULT,
                Difficulty.NORMAL,
                Rot2f(0.0f, 0.0f),
            )

            val EMPTY: SnapshotData = SnapshotData(
                -1L,
                DEFAULT_SETTINGS,
                DEFAULT_SELF,
                emptyList(),
                emptyList(),
                emptyList(),
                ResistantBlockCache.EMPTY,
                0L,
            )

        }

    }

    private data class AsyncResult(
        val snapshotId: Long,
        val rotationPlaceInfo: PlaceInfo?,
        val placeInfo: PlaceInfo?,
        val rotationBreakPlan: BreakPlan?,
        val breakPlan: BreakPlan?,
        val primaryTarget: TargetSnapshot?,
        val calculationNanos: Long,
    ) {

        companion object {

            val EMPTY: AsyncResult = AsyncResult(-1L, null, null, null, null, null, 0L)

        }

    }

    private data class PlaceInfo(
        val target: LivingEntity?,
        val blockPos: BlockPos,
        val selfDamage: Float,
        val targetDamage: Float,
        val side: Direction,
        val hitVec: Vec3,
        val rotation: Rot2f,
    )

    private data class BreakPlan(
        val crystal: EndCrystal?,
        val entityId: Int,
        val pos: Vec3,
        val selfDamage: Float,
        val targetDamage: Float,
    )

    // ---- 静态工具 ----

    private fun isResistantStateStatic(state: BlockState): Boolean {
        return state.`is`(Blocks.BEDROCK) ||
                state.`is`(Blocks.OBSIDIAN) ||
                state.`is`(Blocks.CRYING_OBSIDIAN) ||
                state.`is`(Blocks.ENDER_CHEST) ||
                state.`is`(Blocks.RESPAWN_ANCHOR) ||
                state.`is`(Blocks.ENCHANTING_TABLE) ||
                state.`is`(Blocks.ANVIL) ||
                state.`is`(Blocks.CHIPPED_ANVIL) ||
                state.`is`(Blocks.DAMAGED_ANVIL) ||
                state.`is`(Blocks.NETHERITE_BLOCK)
    }

    private fun easeOutQuart(t: Float): Float {
        val u = 1.0f - t
        return 1.0f - u * u * u * u
    }

    private fun easeOutCubic(t: Float): Float {
        val u = 1.0f - t
        return 1.0f - u * u * u
    }

    private fun easeInCubic(t: Float): Float {
        return t * t * t
    }

    private fun toDelta(startTime: Long, lengthMs: Int): Float {
        val elapsed = System.currentTimeMillis() - startTime
        return (elapsed.toFloat() / maxOf(1, lengthMs)).coerceIn(0.0f, 1.0f)
    }

}
