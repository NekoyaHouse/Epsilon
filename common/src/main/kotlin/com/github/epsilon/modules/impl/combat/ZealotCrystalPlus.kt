package com.github.epsilon.modules.impl.combat

import com.github.epsilon.Constants
import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.*
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.combat.autocrystal.*
import com.github.epsilon.modules.impl.movement.Blink
import com.github.epsilon.settings.group
import com.github.epsilon.utils.combat.CrystalDamageUtils
import com.github.epsilon.utils.player.ClickSlotUtils
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.render.WorldToScreen
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rotation
import com.github.epsilon.utils.rotation.RotationUtils
import com.google.common.base.Suppliers
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.*
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityEvent
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ambient.AmbientCreature
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.bee.Bee
import net.minecraft.world.entity.animal.golem.IronGolem
import net.minecraft.world.entity.animal.squid.Squid
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enderman
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemAttributeModifiers
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.abs
import kotlin.math.ceil

object ZealotCrystalPlus : Module("Zealot Crystal+", Category.COMBAT) {

    private enum class SwapMode {
        Off,
        Normal,
        Silent
    }

    private enum class SwapActionMode {
        HotbarSwap,
        InvSwitch
    }

    private enum class RotateMode {
        None,
        Place,
        Break,
        Normal,
        Packet,
        Grim
    }

    private enum class YawStepMode {
        Off,
        Full,
        Semi
    }

    private enum class PlacementsMode {
        Native,
        Protocol
    }

    private enum class SequentialMode {
        None,
        Normal,
        Strict
    }

    private enum class ShieldMode {
        None,
        Bypass
    }

    private enum class RenderMode {
        Off,
        Box,
        Fade
    }

    private enum class BoxMode {
        Both,
        Fill,
        Lines
    }

    private val sgPlace = settingGroup("Placement")
    private val sgBreak = settingGroup("Breaking")
    private val sgGeneral = settingGroup("General")
    private val sgDamage = settingGroup("Damage")
    private val sgTarget = settingGroup("Targets")
    private val sgSupport = settingGroup("Support")
    private val sgRender = settingGroup("Rendering")

    private val place by boolSetting("Place", true).group(sgPlace)
    private val placeDelay by intSetting("Place Delay", 0, 0..1000, 10, { place }).group(sgPlace)
    private val placeRange by doubleSetting("Place Range", 5.0, 0.1..6.0, 0.1, { place }).group(sgPlace)
    private val placeWallRange by doubleSetting("Place Wall Range", 3.0, 0.1..6.0, 0.1, { place }).group(sgPlace)
    private val swap by enumSetting("Swap", SwapMode.Silent, { place }, onChanged = { restoreSlot() }).group(sgPlace)
    private val swapAction by enumSetting(
        "Swap Action",
        SwapActionMode.HotbarSwap,
        { place && swap != SwapMode.Off },
        onChanged = { restoreSlot() }).group(sgPlace)
    private val placements by enumSetting("Placements", PlacementsMode.Native, { place }).group(sgPlace)
    private val sequential by enumSetting("Sequential", SequentialMode.Normal, { place }).group(sgPlace)
    private val forcePlace by boolSetting("Force Place", true).group(sgPlace)
    private val minHealth by doubleSetting("Min Health", 6.0, 0.1..36.0, 0.1, { forcePlace }).group(sgPlace)
    private val forceDamage by doubleSetting("Force Damage", 2.0, 0.1..36.0, 0.1, { forcePlace }).group(sgPlace)
    private val placeSwing by enumSetting("Place Swing", SwingMode.Packet, { place }).group(sgPlace)

    private val instant by boolSetting("Instant", true).group(sgBreak)
    private val instantDelay by intSetting("Instant Delay", 0, 0..1000, 10, { instant }).group(sgBreak)
    private val inhibit by boolSetting("Inhibit", true).group(sgBreak)
    private val breakDelay by intSetting("Break Delay", 0, 0..1000, 10).group(sgBreak)
    private val attackDelay by doubleSetting("Attack Delay", 0.0, 0.0..5.0, 0.1).group(sgBreak)
    private val attackFactor by intSetting("Attack Factor", 0, 0..3, 1, { attackDelay > 0.0 }).group(sgBreak)
    private val attackLimit by doubleSetting("Attack Limit", 2.0, 0.5..20.0, 0.1).group(sgBreak)
    private val timeout by boolSetting("Timeout", false).group(sgBreak)
    private val breakTimeout by doubleSetting("Break Timeout", 3.0, 0.0..10.0, 0.1, { timeout }).group(sgBreak)
    private val minTimeout by doubleSetting("Min Timeout", 5.0, 0.0..20.0, 0.1, { timeout }).group(sgBreak)
    private val ticksExisted by intSetting("Ticks Existed", 0, 0..10, 1).group(sgBreak)
    private val breakRange by doubleSetting("Break Range", 3.0, 0.1..6.0, 0.1).group(sgBreak)
    private val breakWallRange by doubleSetting("Break Wall Range", 3.0, 0.1..6.0, 0.1).group(sgBreak)
    private val breakSwing by enumSetting("Break Swing", SwingMode.Packet).group(sgBreak)
    private val instantCalc by boolSetting("Instant Calc", true, { instant }).group(sgBreak)
    private val instantDamage by doubleSetting("Instant Damage", 4.0, 1.0..10.0, 0.1, { instant && instantCalc }).group(
        sgBreak
    )
    private val instantMax by boolSetting("Instant Max", true, { instant && instantCalc }).group(sgBreak)

    private val rotate by enumSetting("Rotate", RotateMode.Grim).group(sgGeneral)
    private val yawStep by enumSetting("Yaw Step", YawStepMode.Off, { rotate != RotateMode.None }).group(sgGeneral)
    private val yawStepLimit by intSetting("Yaw Step Limit", 180, 1..180, 1, { yawStep != YawStepMode.Off }).group(
        sgGeneral
    )
    private val whileUsing by boolSetting("While Using", false).group(sgGeneral)
    private val whileMining by boolSetting("While Mining", true).group(sgGeneral)
    private val swapPenalty by doubleSetting("Swap Penalty", 0.0, 0.0..10.0, 0.1).group(sgGeneral)
    private val shieldMode by enumSetting("Shield Mode", ShieldMode.Bypass).group(sgGeneral)
    private val surroundFucker by boolSetting("Surround Fucker", true).group(sgGeneral)
    private val antiSurround by boolSetting("Anti Surround", false).group(sgGeneral)
    private val prePlacement by boolSetting("Pre Placement", true).group(sgGeneral)
    private val cevBomber by boolSetting("Cev Bomber", false, { antiSurround }).group(sgGeneral)
    private val cevDamage by doubleSetting("Cev Damage", 8.0, 1.0..10.0, 0.1, { antiSurround && cevBomber }).group(
        sgGeneral
    )
    private val miningProgress by doubleSetting("Mining Progress", 80.0, 0.0..100.0, 1.0, { antiSurround }).group(
        sgGeneral
    )
    private val resetCrystal by boolSetting("Reset Crystal", false, { antiSurround }).group(sgGeneral)
    private val asynchronous by boolSetting("Asynchronous", true, onChanged = { cancelPlacement() }).group(sgGeneral)
    private val antiWeakness by boolSetting("Anti Weakness", false).group(sgGeneral)

    private val armorBreaker by boolSetting("Armor Breaker", false).group(sgDamage)
    private val armorScale by intSetting("Armor Scale", 5, 1..20, 1, { armorBreaker }).group(sgDamage)
    private val minDamage by doubleSetting("Min Damage", 4.0, 1.0..10.0, 0.1).group(sgDamage)
    private val maxLocalDamage by doubleSetting("Max Local Damage", 12.0, 4.0..20.0, 0.1).group(sgDamage)
    private val damageRatio by doubleSetting("Damage Ratio", 1.2, 0.1..2.0, 0.1).group(sgDamage)
    private val ignoreItem by boolSetting("Ignore Item", false).group(sgDamage)
    private val assumeBestArmor by boolSetting("Assume Best Armor", false).group(sgDamage)
    private val lethalMultiplier by doubleSetting("Lethal Multiplier", 1.5, 0.0..4.0, 0.1).group(sgDamage)
    private val lethalTotem by boolSetting("Lethal Totem", true).group(sgDamage)
    private val lethalDamageTick by boolSetting("Lethal Damage Tick", false).group(sgDamage)
    private val safety by boolSetting("Safety", true).group(sgDamage)
    private val safetyOverride by boolSetting("Safety Override", false).group(sgDamage)
    private val blockDestruction by boolSetting("Block Destruction", true).group(sgDamage)
    private val selfExtrapolate by boolSetting("Self Extrapolate", true).group(sgDamage)
    private val extrapolationTicks by intSetting("Extrapolation Ticks", 5, 0..10, 1).group(sgDamage)

    private val enemyRange by doubleSetting("Enemy Range", 10.0, 1.0..13.0, 0.5).group(sgTarget)
    private val ignoreNakeds by boolSetting("Ignore Nakeds", false).group(sgTarget)
    private val players by boolSetting("Players", true).group(sgTarget)
    private val monsters by boolSetting("Monsters", false).group(sgTarget)
    private val neutrals by boolSetting("Neutrals", false).group(sgTarget)
    private val animals by boolSetting("Animals", false).group(sgTarget)
    private val shulkers by boolSetting("Shulkers", true).group(sgTarget)

    private val support by boolSetting("Support", true).group(sgSupport)
    private val baseRotate by boolSetting("Base Rotate", true, { support }).group(sgSupport)
    private val airPlace by boolSetting("Air Place", false, { support }).group(sgSupport)
    private val baseDelay by intSetting("Base Delay", 4, 1..20, 1, { support }).group(sgSupport)
    private val maxOverride by doubleSetting("Max Override", 7.0, 0.0..36.0, 0.1, { support }).group(sgSupport)
    private val baseSwapAction by enumSetting("Base Swap Action", SwapActionMode.HotbarSwap, { support }).group(
        sgSupport
    )

    private val renderMode by enumSetting("Render", RenderMode.Box).group(sgRender)
    private val shape by enumSetting("Box Mode", BoxMode.Both, { renderMode != RenderMode.Off }).group(sgRender)
    private val sideColor by colorSetting(
        "Side",
        Color(255, 183, 197, 70),
        true,
        { renderMode != RenderMode.Off }).group(sgRender)
    private val lineColor by colorSetting(
        "Line",
        Color(255, 183, 197, 211),
        true,
        { renderMode != RenderMode.Off }).group(sgRender)
    private val fadeTime by intSetting("Fade Time", 250, 0..1000, 10, { renderMode == RenderMode.Fade }).group(sgRender)
    private val renderDamage by boolSetting("Render Damage", false, { renderMode != RenderMode.Off }).group(sgRender)
    private val targetDamage by boolSetting("Target Damage", true, { renderDamage }).group(sgRender)
    private val selfDamage by boolSetting("Self Damage", false, { renderDamage }).group(sgRender)

    private val timing = CrystalTiming()
    private val totems = HashMap<Int, Long>()
    private val textRenderer = Suppliers.memoize { TextRenderer.create() }
    private var breakCandidate: CrystalCandidate? = null
    private val placementResult = CrystalResult<CrystalCandidate>()
    private val placeCandidate: CrystalCandidate? get() = placementResult.value
    private var placementTask: Future<*>? = null
    private var executor: ExecutorService? = null
    private var rotationRequest: Rotation? = null
    private var rotationAim: Vec3? = null
    private var yawComplete = false
    private var serverYaw: Float? = null
    private var rendered: CrystalCandidate? = null
    private var renderedAt = 0L
    private var originalSlot = -1
    private var normalSlot = -1

    private data class InventorySwap(val slot: Int, val hotbar: Int, val displaced: ItemStack, val selected: ItemStack)

    private val normalInventorySwaps = ArrayList<InventorySwap>()

    init {
        onEnable {
            clearState()
        }

        onDisable {
            restoreSlot()
            clearState()
        }

        safeEventHandler<ClientTickEvent.Pre> {
            timing.tick()
        }

        safeEventHandler<ClientEntityEvent.TickPre>(EventPriority.HIGH) { event ->
            if (event.level === level && !player.isSpectator) tickEntities()
        }

        safeEventHandler<ClientTickEvent.Post> {
            if (!player.isSpectator && asynchronous) submitPlacement()
        }

        safeEventHandler<ClientLoopEvent> {
            if (attackDelay > 0.0 && timing.attackReady(timing.attackInterval(attackDelay, attackFactor))) {
                val candidate = breakCandidate ?: return@safeEventHandler
                attackRegular(candidate)
                timing.resetAttack()
            }
        }

        safeEventHandler<ClientEntityEvent.Added> { event ->
            if (event.level === level && event.entity is EndCrystal) onCrystalSpawn(event.entity)
        }

        safeEventHandler<ClientEntityEvent.Removed> { event ->
            if (event.level === level) timing.confirm(event.entityId)
        }

        handler<PacketEvent.Send>(EventPriority.LOWEST) { event ->
            if (!event.isCancelled) when (val packet = event.packet) {
                is ServerboundSetCarriedItemPacket -> timing.resetSwap()
                is ServerboundMovePlayerPacket -> if (packet.hasRotation()) serverYaw = packet.getYRot(0.0f)
            }
        }

        safeEventHandler<PacketEvent.Apply> { event ->
            val packet = event.packet
            when (packet) {
                is ClientboundExplodePacket -> confirmExplosion(packet.center())
                is ClientboundSoundPacket -> if (packet.sound.value() == SoundEvents.GENERIC_EXPLODE.value() && packet.source == SoundSource.BLOCKS) {
                    confirmExplosion(Vec3(packet.x, packet.y, packet.z))
                }

                is ClientboundRemoveEntitiesPacket -> packet.entityIds.forEach(java.util.function.IntConsumer {
                    timing.confirm(
                        it
                    )
                })

                is ClientboundEntityEventPacket -> if (packet.eventId == EntityEvent.PROTECTED_FROM_DEATH) {
                    packet.getEntity(level)?.let { totems[it.id] = now() }
                }

                is ClientboundPlayerPositionPacket -> {
                    releaseRotation()
                    rotationAim = null
                    yawComplete = false
                    serverYaw = null
                    breakCandidate = null
                    cancelPlacement()
                }
            }
        }

        safeEventHandler<PacketMineEvent.BreakAttempt> {
            if (surroundFucker) onMiningAttempt(it.pos)
        }

        safeEventHandler<Render3DEvent> {
            val candidate = rendered ?: return@safeEventHandler
            val alpha = renderAlpha()
            if (alpha <= 0.0) return@safeEventHandler
            val box = AABB(candidate.pos)
            if (shape != BoxMode.Lines) Render3DScheduler.addFilledBox(box, faded(sideColor, alpha))
            if (shape != BoxMode.Fill) Render3DScheduler.addOutlineBox(box, faded(lineColor, alpha))
        }
        safeEventHandler<Render2DEvent.Level> {
            val candidate = rendered ?: return@safeEventHandler
            val alpha = renderAlpha()
            if (!renderDamage || alpha <= 0.0) return@safeEventHandler
            val labels = buildList {
                if (targetDamage) add(String.format(Locale.ROOT, "%.1f", candidate.damage))
                if (selfDamage) add(String.format(Locale.ROOT, "%.1f", candidate.selfDamage))
            }
            if (labels.isEmpty()) return@safeEventHandler
            val screen = WorldToScreen.calcWorld2Screen(Vec3.atCenterOf(candidate.pos).add(0.0, 0.6, 0.0))
                ?: return@safeEventHandler
            val renderer = textRenderer.get()
            val font = StaticFontLoader.defaultFont()
            val scale = 0.8f
            val label = labels.joinToString(" / ")
            renderer.addText(
                label,
                screen.x - renderer.getWidth(label, scale, font) * 0.5f,
                screen.y,
                scale,
                faded(Color.WHITE, alpha),
                font
            )
            renderer.drawAndClear()
        }
    }

    context(nn: NonNullContext)
    private fun tickEntities() {
        val targets = targets()
        val self = selfSnapshot()
        breakCandidate = bestCrystal(targets, self)
        if (!asynchronous) {
            cancelPlacement()
            placementResult.replace(preparePlacement(targets, self)?.calculate())
        }
        val placement = placeCandidate
        if (breakCandidate == null && placement != null && (!placement.antiSurround || resetCrystal)) {
            val blocking = crystalAt(placement.pos.above()) ?: crystalAt(placement.pos.above(2))
            if (blocking != null && blocking.blockPosition() != placement.pos.above()) {
                val local = CrystalDamageUtils.damage(self, blocking.position(), blockDestruction)
                if (!safety || !rules().unsafe(local, self.health, self.creative)) {
                    breakCandidate = placement.copy(
                        pos = blocking.blockPosition().below(),
                        selfDamage = local,
                        crystalId = blocking.id
                    )
                }
            }
        }
        val breaking = breakCandidate
        if (breaking == null && placement == null || !managedRotation()) releaseRotation()
        if (inhibit && breaking?.crystalId != null) {
            timing.selectRepeated(
                breaking.crystalId,
                if (attackDelay > 0.0) timing.attackInterval(attackDelay, attackFactor) else breakDelay.toDouble()
            )
        }
        val breakNow = breaking != null && attackDelay <= 0.0 &&
                timing.attackReady(timing.tickInterval(breakDelay, timeout, minTimeout, breakTimeout))
        if (breaking != null && rotatesBreak()) {
            val crystal = nn.level.getEntity(breaking.crystalId ?: -1) as? EndCrystal
            if (crystal != null) rotationAim = crystalAim(crystal.boundingBox)
        } else if (placement != null && rotatesPlace()) {
            rotationAim = placeHit(placement.pos)?.location ?: Vec3.atCenterOf(placement.pos)
        }
        if (paused()) {
            releaseRotation(); return
        }
        val aim = rotationAim
        if (managedRotation() && aim != null && (placement == null || canPlace())) {
            val target = RotationUtils.calculate(aim)
            if (yawStep == YawStepMode.Full || yawStep == YawStepMode.Semi && breakNow) {
                val previous = Mth.wrapDegrees(serverYaw ?: nn.player.yRot)
                val delta = previous - target.yaw
                // 原版先取绝对值再跨越 ±180° 修正方向，边界处可能多走一轮。
                val difference = abs(delta)
                val wrapped = if (difference > 180.0f) delta + if (delta > 0.0f) -360.0f else 360.0f else delta
                yawComplete = difference <= yawStepLimit
                if (!yawComplete) target.yaw = previous + if (wrapped > 0.0f) -yawStepLimit else yawStepLimit
            } else yawComplete = true
            if (yawComplete) rotationAim = null
            rotationRequest = target
            RotationManager.setRotationsExact(target, Priority.High)
        }
        if (!rotationReady()) return
        if (breaking != null) {
            render(breaking)
            if (breakNow) {
                attackRegular(breaking)
                timing.resetAttack()
            }
        }
        // 两个独立分支：同一实体 tick 内先攻击，再按自己的计时器放置。
        if (placement == null || !timing.placeReady(placeDelay)) return
        if (!isBase(placement.pos)) {
            val hit = placement.supportHit ?: return
            if (timing.baseTicks < baseDelay) return
            placeBase(hit)
            timing.resetBase()
        }
        render(placement)
        placeChecked(placement.pos)
        timing.resetPlace()
    }

    context(nn: NonNullContext)
    private fun submitPlacement() {
        if (placementTask?.isDone == false) return
        val work = preparePlacement(targets(), selfSnapshot())
        if (work == null) {
            placementResult.replace(); return
        }
        val service = executor ?: Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "Epsilon-AutoCrystal").apply { isDaemon = true }
        }.also { executor = it }
        val token = placementResult.token()
        placementTask = service.submit {
            try {
                placementResult.publish(token, work.calculate())
            } catch (exception: Exception) {
                if (exception is InterruptedException) Thread.currentThread().interrupt()
                Constants.LOGGER.error("Auto Crystal 异步放置计算失败", exception)
                placementResult.publish(token, null)
            }
        }
    }

    private fun cancelPlacement() {
        // 必须先失效再中断；计算可能已经完成，或者尚未响应中断。
        placementResult.replace()
        placementTask?.cancel(true)
        placementTask = null
    }

    context(nn: NonNullContext)
    private fun preparePlacement(targets: List<CrystalTarget>, self: CrystalDamageUtils.Snapshot): CrystalPlacement? {
        if (!place || !canPlace() || !canAttackWithWeakness() || targets.isEmpty()) return null
        val points = ArrayList<CrystalPlacePoint>()
        for (pos in scanPositions(nn.player.eyePosition, ceil(placeRange + 1.0).toInt())) {
            if (!placementSpace(pos, self) || !placeReachable(pos)) continue
            val base = isBase(pos)
            val hit = if (base) null else {
                if (!support || timing.baseTicks < baseDelay || findItem(Items.OBSIDIAN, baseSwapAction) == -1) continue
                supportHit(pos) ?: continue
            }
            val surroundTargets = HashSet<Int>()
            for (target in targets) {
                val entity = nn.level.getEntity(target.snapshot.id) as? Player ?: continue
                if (surroundPlacement(entity, pos)) surroundTargets.add(entity.id)
                val mining = PacketMine.targetPos
                if (antiSurround && cevBomber && PacketMine.isEnabled && mining == pos &&
                    PacketMine.mainProgressPercent >= miningProgress && adjacent(entity, pos, true, true) && crystalAt(
                        pos.above()
                    ) == null
                ) {
                    val damage = CrystalDamageUtils.damage(
                        target.snapshot,
                        crystalCenter(pos),
                        blockDestruction,
                        ignored = setOf(pos)
                    )
                    if (damage >= cevDamage) fastPlace(pos, target)
                }
            }
            points.add(CrystalPlacePoint(pos, hit, surroundTargets))
        }
        if (points.isEmpty()) return null
        var bounds = self.box
        for (target in targets) bounds = bounds.minmax(target.snapshot.box)
        for (point in points) bounds = bounds.minmax(AABB(point.pos).expandTowards(0.0, 2.0, 0.0))
        return CrystalPlacement(
            points, targets, self, CrystalDamageUtils.captureWorld(bounds, blockDestruction),
            rules(), shieldMode == ShieldMode.Bypass, maxOverride, antiSurround
        )
    }

    context(nn: NonNullContext)
    private fun bestCrystal(targets: List<CrystalTarget>, self: CrystalDamageUtils.Snapshot): CrystalCandidate? {
        val rules = rules()
        var best: CrystalCandidate? = null
        for (entity in nn.level.entitiesForRendering()) {
            if (entity !is EndCrystal || !entity.isAlive || entity.tickCount < ticksExisted ||
                inhibit && timing.inhibited(entity.id) || !crystalReachable(entity)
            ) continue
            val local = CrystalDamageUtils.damage(self, entity.position(), blockDestruction)
            val unsafe = rules.unsafe(local, self.health, self.creative)
            if (unsafe && !safetyOverride) continue
            for (target in targets) {
                if (entity.position().distanceToSqr(target.snapshot.position) > 144.0 ||
                    shieldMode == ShieldMode.Bypass && target.snapshot.blocksExplosion(entity.position())
                ) continue
                val damage = CrystalDamageUtils.damage(target.snapshot, entity.position(), blockDestruction)
                if (!rules.accepts(damage, local, unsafe, target.rules)) continue
                if (best == null || damage > best.damage) best =
                    CrystalCandidate(entity.blockPosition().below(), target, damage, local, entity.id)
            }
        }
        return best?.takeIf { it.damage >= rules.minimum(it.target.rules, it.damage) }
    }

    context(nn: NonNullContext)
    private fun onCrystalSpawn(crystal: EndCrystal) {
        val own = timing.onSpawn(BlockPos.containing(crystal.position().add(0.0, -1.0, 0.0)).asLong())
        if (!instant || !timing.attackReady(instantDelay.toDouble())) return
        if (own && (breakCandidate != null || placeCandidate != null)) {
            attackInstant(crystal)
            timing.resetAttack()
            if (sequential == SequentialMode.Normal) placeSequential()
            return
        }
        if (!instantCalc || !potentialCrystalReachable(crystal.position())) return
        val self = selfSnapshot()
        val local = CrystalDamageUtils.damage(self, crystal.position(), blockDestruction)
        val rules = rules()
        if (rules.unsafe(local, self.health, self.creative)) return
        for (target in targets()) {
            if (target.snapshot.position.distanceToSqr(crystal.position()) > 144.0) continue
            val damage = CrystalDamageUtils.damage(target.snapshot, crystal.position(), blockDestruction)
            if (damage > instantDamage || instantMax && breakCandidate?.let { damage >= it.damage } == true || rules.lethal(
                    damage,
                    target.rules
                )) {
                attackInstant(crystal)
                timing.resetAttack()
                if (sequential == SequentialMode.Normal) placeSequential()
                return
            }
        }
    }

    context(nn: NonNullContext)
    private fun attackRegular(candidate: CrystalCandidate) {
        if (!timing.swapReady(swapPenalty) || crystalHand() == InteractionHand.MAIN_HAND && paused() || !canAttackWithWeakness()) return
        val crystal = nn.level.getEntity(candidate.crystalId ?: return) as? EndCrystal ?: return
        if (!crystal.isAlive) return
        attackInstant(crystal)
        if (sequential == SequentialMode.Strict) placeSequential()
    }

    context(nn: NonNullContext)
    private fun attackInstant(crystal: EndCrystal) {
        if (packetRotation()) rotatePacket(crystalAim(crystal.boundingBox))
        val action = {
            // 只发送攻击，不调用本地 player.attack；原版不会提前将水晶标记死亡。
            nn.connection.send(ServerboundAttackPacket(crystal.id))
            breakSwing.attackSwing(crystalHand() ?: InteractionHand.MAIN_HAND)
            timing.recordAttack(crystal.id)
            true
        }
        if (antiWeakness && weaponDamage(nn.player.mainHandItem) <= 0.0) {
            val slot = (0..8).maxByOrNull { weaponDamage(nn.player.inventory.getItem(it)) } ?: return
            if (weaponDamage(nn.player.inventory.getItem(slot)) <= 0.0) return
            withSlot(slot, SwapActionMode.HotbarSwap, true, action)
        } else action()
    }

    context(nn: NonNullContext)
    private fun placeSequential() {
        val candidate = placeCandidate ?: return
        if (candidate.supportHit != null) return
        val latency = nn.connection.getPlayerInfo(nn.player.uuid)?.latency ?: 0
        if (nn.mc.currentServer?.ip?.contains("2b2t.org", true) == true && latency < 50) return
        // Normal 在生成即时攻击后、Strict 在普通攻击后调用；均不读取或重置 PlaceDelay。
        placeChecked(candidate.pos)
    }

    context(nn: NonNullContext)
    private fun placeChecked(pos: BlockPos) {
        if (rotationReady() && canAttackWithWeakness()) placeCrystal(pos)
    }

    context(nn: NonNullContext)
    private fun placeCrystal(pos: BlockPos) {
        if (paused()) return
        val hit = placeHit(pos) ?: return
        withItem(Items.END_CRYSTAL, swapAction, swap) { hand ->
            if (packetRotation()) rotatePacket(hit.location)
            sendPlace(hit, hand)
            placeSwing.interactSwing(hand)
            timing.recordPlace(pos.asLong())
            true
        }
    }

    context(nn: NonNullContext)
    private fun placeBase(hit: BlockHitResult) {
        withItem(Items.OBSIDIAN, baseSwapAction, SwapMode.Silent) { hand ->
            if (baseRotate) rotatePacket(hit.location)
            sendPlace(hit, hand)
            placeSwing.interactSwing(hand)
            true
        }
    }

    context(nn: NonNullContext)
    private fun sendPlace(hit: BlockHitResult, hand: InteractionHand) {
        nn.connection.send(
            ServerboundUseItemOnPacket(
                hand,
                hit,
                nn.level.blockStatePredictionHandler.startPredicting().currentSequence()
            )
        )
    }

    context(nn: NonNullContext)
    private fun confirmExplosion(center: Vec3) {
        for (entity in nn.level.entitiesForRendering().toList()) {
            if (entity is EndCrystal && entity.position().distanceToSqr(center) < 144.0) {
                timing.confirm(entity.id)
                nn.level.removeEntity(entity.id, Entity.RemovalReason.DISCARDED)
            }
        }
    }

    context(nn: NonNullContext)
    private fun targets(): List<CrystalTarget> {
        val result = ArrayList<CrystalTarget>()
        for (entity in nn.level.entitiesForRendering()) {
            if (entity !is LivingEntity || entity === nn.player || !entity.isAlive || !targetType(entity) ||
                FriendManager.isFriend(entity.name.string) || nn.player.distanceToSqr(entity) > enemyRange * enemyRange ||
                ignoreNakeds && CrystalDamageUtils.armorSlots.all { entity.getItemBySlot(it).isEmpty }
            ) continue
            val snapshot = CrystalDamageUtils.snapshot(entity, extrapolationTicks, assumeBestArmor)
            val nearbyShulker = shulkers && entity is Player && scanPositions(entity.position(), 3).any {
                nn.level.getBlockState(it).block is ShulkerBoxBlock
            }
            result.add(
                CrystalTarget(
                    snapshot,
                    CrystalCombatRules.Target(
                        snapshot.health, snapshot.minArmor, snapshot.maxArmor,
                        totems[entity.id]?.let { now() - it <= 500L } == true, nearbyShulker)))
        }
        return result
    }

    private fun targetType(entity: LivingEntity): Boolean {
        val neutral = entity is Enderman || entity is ZombifiedPiglin || entity is Wolf || entity is IronGolem
        val passiveNeutral = (neutral || entity is Bee) && !entity.isAggressive
        return entity is Player && players || entity is Monster && !passiveNeutral && monsters || neutral && neutrals ||
                (entity is Animal || entity is AmbientCreature || entity is Squid) && animals
    }

    private fun rules() = CrystalCombatRules(
        minDamage,
        maxLocalDamage,
        damageRatio,
        safety,
        safetyOverride,
        forcePlace,
        minHealth,
        forceDamage,
        armorBreaker,
        armorScale,
        lethalMultiplier,
        lethalDamageTick,
        lethalTotem,
        timing.attackElapsedMillis
    )

    context(nn: NonNullContext)
    private fun selfSnapshot() = CrystalDamageUtils.snapshot(nn.player, if (selfExtrapolate) extrapolationTicks else 0)

    context(nn: NonNullContext)
    private fun paused(): Boolean =
        (!whileUsing && nn.player.isUsingItem) || (!whileMining && nn.gameMode.isDestroying) || Blink.isEnabled

    context(nn: NonNullContext)
    private fun canPlace(): Boolean =
        crystalHand() != null || !paused() && swap != SwapMode.Off && findItem(Items.END_CRYSTAL, swapAction) != -1

    context(nn: NonNullContext)
    private fun managedRotation(): Boolean =
        rotate != RotateMode.None && rotate != RotateMode.Packet && (rotate != RotateMode.Grim || !nn.player.isFallFlying)

    context(nn: NonNullContext)
    private fun packetRotation(): Boolean =
        rotate == RotateMode.Packet || rotate == RotateMode.Grim && nn.player.isFallFlying

    context(nn: NonNullContext)
    private fun rotatesBreak(): Boolean =
        rotate == RotateMode.Normal || rotate == RotateMode.Break || rotate == RotateMode.Grim && managedRotation()

    context(nn: NonNullContext)
    private fun rotatesPlace(): Boolean =
        rotate == RotateMode.Normal || rotate == RotateMode.Place || rotate == RotateMode.Grim && managedRotation()

    context(nn: NonNullContext)
    private fun rotationReady(): Boolean = !managedRotation() || yawComplete &&
            (!RotationManager.isActive() || rotationRequest?.let { RotationManager.ownsRotation(it) } == true)

    context(nn: NonNullContext)
    private fun rotatePacket(aim: Vec3) {
        val rotation = RotationUtils.calculate(aim)
        nn.connection.send(
            ServerboundMovePlayerPacket.PosRot(
                nn.player.position(),
                rotation.yaw,
                rotation.pitch,
                nn.player.onGround(),
                nn.player.horizontalCollision
            )
        )
    }

    context(nn: NonNullContext)
    private fun placementSpace(pos: BlockPos, self: CrystalDamageUtils.Snapshot): Boolean {
        if (!nn.level.isInWorldBounds(pos.above(2)) || !nn.level.worldBorder.isWithinBounds(pos)) return false
        if (placements == PlacementsMode.Protocol && !nn.level.isEmptyBlock(pos.above(2))) return false
        val above = pos.above()
        val state = nn.level.getBlockState(above)
        if (!state.isAir) {
            val crystal = crystalAt(above)
            if (!state.`is`(Blocks.FIRE) || crystal == null || crystal.blockPosition() != above) return false
        }
        return spaceClear(above, self) && spaceClear(pos.above(2), self)
    }

    context(nn: NonNullContext)
    private fun spaceClear(pos: BlockPos, self: CrystalDamageUtils.Snapshot): Boolean {
        for (entity in nn.level.getEntities(null, AABB(pos))) {
            if (!entity.isAlive || entity is ItemEntity && ignoreItem && entity.tickCount <= 10) continue
            if (entity !is EndCrystal) return false
            val local = CrystalDamageUtils.damage(self, entity.position(), blockDestruction)
            if (safety && rules().unsafe(local, self.health, self.creative) || !crystalReachable(entity) ||
                timing.blocksPlacement(entity.id, attackLimit)
            ) return false
        }
        return true
    }

    context(nn: NonNullContext)
    private fun placeReachable(pos: BlockPos): Boolean {
        if (!potentialCrystalReachable(crystalCenter(pos))) return false
        val hit = placeHit(pos) ?: return false
        val range = if (visible(hit.location)) placeRange else placeWallRange
        return nn.player.eyePosition.distanceTo(hit.location) <= range
    }

    context(nn: NonNullContext)
    private fun placeHit(pos: BlockPos): BlockHitResult? {
        val eye = nn.player.eyePosition
        val face = Direction.entries.minBy { eye.distanceToSqr(Vec3.atCenterOf(pos).add(it.unitVec3.scale(0.5))) }
        val point = nearest(eye, AABB(pos)).let {
            when (face) {
                Direction.DOWN -> Vec3(it.x, pos.y.toDouble(), it.z)
                Direction.UP -> Vec3(it.x, pos.y + 1.0, it.z)
                Direction.NORTH -> Vec3(it.x, it.y, pos.z.toDouble())
                Direction.SOUTH -> Vec3(it.x, it.y, pos.z + 1.0)
                Direction.WEST -> Vec3(pos.x.toDouble(), it.y, it.z)
                Direction.EAST -> Vec3(pos.x + 1.0, it.y, it.z)
            }
        }
        return if (eye.distanceTo(point) <= placeRange) BlockHitResult(point, face, pos, false) else null
    }

    context(nn: NonNullContext)
    private fun supportHit(pos: BlockPos): BlockHitResult? {
        if (!nn.level.getBlockState(pos).canBeReplaced() || nn.level.getEntities(null, AABB(pos))
                .any { it.isAlive }
        ) return null
        for (direction in Direction.entries) {
            val neighbor = pos.relative(direction)
            val state = nn.level.getBlockState(neighbor)
            if (state.isAir || state.canBeReplaced() || state.block is EntityBlock || state.block is NoteBlock) continue
            val point = Vec3.atCenterOf(neighbor).add(direction.opposite.unitVec3.scale(0.5))
            if (nn.player.eyePosition.distanceTo(point) <= placeRange) return BlockHitResult(
                point,
                direction.opposite,
                neighbor,
                false
            )
        }
        return if (airPlace) placeHit(pos) else null
    }

    context(nn: NonNullContext)
    private fun crystalAt(pos: BlockPos): EndCrystal? = nn.level.getEntities(null, AABB(pos))
        .filterIsInstance<EndCrystal>().minByOrNull { nn.player.distanceToSqr(it) }

    context(nn: NonNullContext)
    private fun isBase(pos: BlockPos): Boolean {
        val state = nn.level.getBlockState(pos)
        return state.`is`(Blocks.OBSIDIAN) || state.`is`(Blocks.BEDROCK)
    }

    context(nn: NonNullContext)
    private fun crystalAim(box: AABB): Vec3 {
        val bottom = nearest(
            nn.player.eyePosition,
            AABB(box.minX + 0.5, box.minY, box.minZ + 0.5, box.maxX - 0.5, box.minY, box.maxZ - 0.5)
        )
        return if (nn.player.eyePosition.distanceTo(bottom) <= breakRange) bottom else nearest(
            nn.player.eyePosition,
            box
        )
    }

    context(nn: NonNullContext)
    private fun crystalReachable(crystal: EndCrystal): Boolean {
        val visible = visible(crystal.position()) && visible(crystal.eyePosition)
        return nn.player.eyePosition.distanceTo(crystalAim(crystal.boundingBox)) <= if (visible) breakRange else breakWallRange
    }

    context(nn: NonNullContext)
    private fun potentialCrystalReachable(center: Vec3): Boolean {
        val point = nearest(
            nn.player.eyePosition,
            AABB(center.x - 1.0, center.y, center.z - 1.0, center.x + 1.0, center.y + 2.0, center.z + 1.0)
        )
        return nn.player.eyePosition.distanceTo(point) <= if (visible(point)) breakRange else breakWallRange
    }

    context(nn: NonNullContext)
    private fun visible(point: Vec3): Boolean = nn.level.clip(
        ClipContext(
            nn.player.eyePosition, point,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, nn.player
        )
    ).type == HitResult.Type.MISS

    context(nn: NonNullContext)
    private fun surroundPlacement(target: Player, pos: BlockPos): Boolean {
        if (nn.level.getBlockState(target.blockPosition()).block.explosionResistance >= 600.0f) return false
        if (prePlacement && adjacent(target, pos.above(), false, false) && crystalAt(pos.above()) == null) return true
        val mining = PacketMine.targetPos ?: return false
        if (!antiSurround || !PacketMine.isEnabled || PacketMine.mainProgressPercent < miningProgress ||
            !adjacent(target, mining, false, false) || crystalAt(pos.above()) != null
        ) return false
        return Direction.entries.any { pos == mining.relative(it).below() } ||
                Direction.Plane.HORIZONTAL.any { pos == mining.below().relative(it).below() }
    }

    private fun adjacent(target: Player, pos: BlockPos, allowHead: Boolean, allSides: Boolean): Boolean {
        if (target.boundingBox.intersects(AABB(pos))) return false
        if (!allowHead && pos.y == target.blockPosition().above().y) return false
        return Direction.entries.any {
            (allSides || it != Direction.DOWN) && target.boundingBox.intersects(
                AABB(
                    pos.relative(
                        it
                    )
                )
            )
        }
    }

    context(nn: NonNullContext)
    private fun onMiningAttempt(mined: BlockPos) {
        if (findItem(Items.END_CRYSTAL, swapAction) == -1) return
        val pos = mined.below()
        val self = selfSnapshot()
        if (!isBase(pos) || !placeReachable(pos) || placements == PlacementsMode.Protocol && !nn.level.isEmptyBlock(
                mined.above()
            )
        ) return
        val local = CrystalDamageUtils.damage(self, crystalCenter(pos), blockDestruction, ignored = setOf(mined))
        if (rules().unsafe(local, self.health, self.creative)) return
        for (target in targets()) {
            val damage =
                CrystalDamageUtils.damage(target.snapshot, crystalCenter(pos), blockDestruction, ignored = setOf(mined))
            val placement = placeCandidate
            if (damage > minDamage && (placement == null || damage > placement.damage - 1.0)) {
                fastPlace(pos, target)
                return
            }
        }
    }

    context(nn: NonNullContext)
    private fun fastPlace(pos: BlockPos, target: CrystalTarget) {
        if (!isBase(pos) || !placeReachable(pos)) return
        val self = selfSnapshot()
        val local = CrystalDamageUtils.damage(self, crystalCenter(pos), blockDestruction, ignored = setOf(pos))
        if (rules().unsafe(local, self.health, self.creative)) return
        rotatePacket(placeHit(pos)?.location ?: Vec3.atCenterOf(pos))
        placeCrystal(pos)
        render(
            CrystalCandidate(
                pos,
                target,
                CrystalDamageUtils.damage(target.snapshot, crystalCenter(pos), blockDestruction, ignored = setOf(pos)),
                local
            )
        )
    }

    context(nn: NonNullContext)
    private fun crystalHand(): InteractionHand? = when {
        nn.player.offhandItem.`is`(Items.END_CRYSTAL) -> InteractionHand.OFF_HAND
        nn.player.mainHandItem.`is`(Items.END_CRYSTAL) -> InteractionHand.MAIN_HAND
        else -> null
    }

    context(nn: NonNullContext)
    private fun findItem(item: Item, action: SwapActionMode): Int {
        if (nn.player.offhandItem.`is`(item)) return 40
        return (0..if (action == SwapActionMode.HotbarSwap) 8 else 35).firstOrNull {
            nn.player.inventory.getItem(it).`is`(item)
        } ?: -1
    }

    context(nn: NonNullContext)
    private fun withItem(
        item: Item,
        action: SwapActionMode,
        mode: SwapMode,
        block: (InteractionHand) -> Boolean
    ): Boolean {
        if (nn.player.offhandItem.`is`(item)) return block(InteractionHand.OFF_HAND)
        if (nn.player.mainHandItem.`is`(item)) return block(InteractionHand.MAIN_HAND)
        if (mode == SwapMode.Off && action == SwapActionMode.HotbarSwap) return false
        val slot = findItem(item, action)
        if (slot == -1) return false
        return withSlot(slot, action, mode != SwapMode.Normal) { block(InteractionHand.MAIN_HAND) }
    }

    context(nn: NonNullContext)
    private fun withSlot(slot: Int, action: SwapActionMode, restore: Boolean, block: () -> Boolean): Boolean {
        val inventory = nn.player.inventory
        val previous = inventory.selectedSlot
        if (action == SwapActionMode.InvSwitch || slot >= 9) {
            if (nn.player.containerMenu !== nn.player.inventoryMenu) return false
            val menuSlot = if (slot < 9) slot + 36 else slot
            if (slot == previous) return block()
            ClickSlotUtils.swap(menuSlot, previous)
            if (!restore) {
                // Normal 保留交换后的主手物品；只在停用或切换模式时恢复仍匹配的槽位。
                normalInventorySwaps.add(
                    InventorySwap(
                        slot, previous,
                        inventory.getItem(slot).copy(), inventory.getItem(previous).copy()
                    )
                )
                return block()
            }
            return try {
                block()
            } finally {
                ClickSlotUtils.swap(menuSlot, previous)
            }
        }
        if (!restore && previous != slot) {
            if (originalSlot == -1 || previous != normalSlot) originalSlot = previous
            normalSlot = slot
        }
        inventory.selectedSlot = slot
        return try {
            nn.gameMode.ensureHasSentCarriedItem()
            block()
        } finally {
            if (restore) {
                inventory.selectedSlot = previous
                nn.gameMode.ensureHasSentCarriedItem()
            }
        }
    }

    context(nn: NonNullContext)
    private fun canAttackWithWeakness(): Boolean =
        weaponDamage(nn.player.mainHandItem) > 0.0 || antiWeakness && (0..8).any {
            weaponDamage(
                nn.player.inventory.getItem(it)
            ) > 0.0
        }

    context(nn: NonNullContext)
    private fun weaponDamage(stack: ItemStack): Double {
        val strength = nn.player.getEffect(MobEffects.STRENGTH)?.let { (it.amplifier + 1) * 3.0 } ?: 0.0
        val weakness = nn.player.getEffect(MobEffects.WEAKNESS)?.let { (it.amplifier + 1) * 4.0 } ?: 0.0
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
            .compute(Attributes.ATTACK_DAMAGE, 1.0 + strength - weakness, EquipmentSlot.MAINHAND)
    }

    private fun restoreSlot() {
        runSafe {
            val menu = player.containerMenu
            for (swap in normalInventorySwaps.asReversed()) {
                val source = player.inventory.getItem(swap.slot)
                val selected = player.inventory.getItem(swap.hotbar)
                // 按当前容器映射玩家槽位，打开箱子后停用也不能使用旧的背包菜单编号。
                val menuSlot =
                    menu.slots.indexOfFirst { it.container === player.inventory && it.containerSlot == swap.slot }
                // 水晶数量可以因使用减少；用户移动或替换物品后不再回滚该交换。
                if (menuSlot >= 0 && ItemStack.matches(source, swap.displaced) &&
                    (selected.isEmpty || ItemStack.isSameItemSameComponents(selected, swap.selected))
                ) {
                    ClickSlotUtils.swap(menu.containerId, menuSlot, swap.hotbar)
                }
            }
            if (originalSlot in 0..8 && player.inventory.selectedSlot == normalSlot) {
                player.inventory.selectedSlot = originalSlot
                gameMode.ensureHasSentCarriedItem()
            }
        }
        originalSlot = -1
        normalSlot = -1
        normalInventorySwaps.clear()
    }

    private fun clearState() {
        cancelPlacement()
        executor?.shutdownNow()
        executor = null
        releaseRotation()
        rotationAim = null
        yawComplete = false
        serverYaw = null
        breakCandidate = null
        rendered = null
        timing.clear()
        totems.clear()
        normalInventorySwaps.clear()
        originalSlot = -1
        normalSlot = -1
    }

    private fun releaseRotation() {
        rotationRequest?.let { RotationManager.clearRotations(it) }
        rotationRequest = null
    }

    private fun render(candidate: CrystalCandidate) {
        rendered = candidate; renderedAt = now()
    }

    private fun renderAlpha(): Double {
        if (renderMode == RenderMode.Off) return 0.0
        val age = now() - renderedAt
        if (renderMode == RenderMode.Box) return if (age < 100L) 1.0 else 0.0
        return (1.0 - age.toDouble() / maxOf(1, fadeTime)).coerceIn(0.0, 1.0)
    }

    private fun scanPositions(origin: Vec3, radius: Int): Sequence<BlockPos> = sequence {
        for (x in -radius..radius) for (y in -radius..radius) for (z in -radius..radius) {
            yield(BlockPos((origin.x + x).toInt(), (origin.y + y).toInt(), (origin.z + z).toInt()))
        }
    }

    private fun nearest(point: Vec3, box: AABB) = Vec3(
        point.x.coerceIn(box.minX, box.maxX),
        point.y.coerceIn(box.minY, box.maxY),
        point.z.coerceIn(box.minZ, box.maxZ)
    )

    private fun crystalCenter(pos: BlockPos): Vec3 = Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)

    private fun faded(color: Color, alpha: Double) =
        Color(color.red, color.green, color.blue, (color.alpha * alpha).toInt().coerceIn(0, 255))

    private fun now(): Long = System.nanoTime() / 1_000_000L

    override fun getInfo(): String? = rendered?.takeIf { now() - renderedAt < 1000L }
        ?.let { String.format(Locale.ROOT, "%.1f, %dms", it.damage, timing.averageBreakMillis) }
}
