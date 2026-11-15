package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.*
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.graphics.text.StaticFontLoader
import com.github.epsilon.kernel.gameplay.combat.CrystalDamage
import com.github.epsilon.kernel.gameplay.combat.CrystalDamage.captureWorld
import com.github.epsilon.kernel.gameplay.combat.CrystalDamage.damage
import com.github.epsilon.kernel.gameplay.combat.CrystalDamage.snapshot
import com.github.epsilon.kernel.gameplay.grimac.C03Interact.placeBlock
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.swap
import com.github.epsilon.kernel.gameplay.player.SwingMode
import com.github.epsilon.kernel.gameplay.rotation.Rotation
import com.github.epsilon.kernel.gameplay.rotation.RotationPriority
import com.github.epsilon.kernel.gameplay.rotation.RotationUtils.calculate
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.render.WorldToScreen
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.Blink
import com.github.epsilon.settings.group
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.AmbientCreature
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.bee.Bee
import net.minecraft.world.entity.animal.golem.IronGolem
import net.minecraft.world.entity.animal.squid.Squid
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.monster.Enderman
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.ceil
import kotlin.math.pow

object AnchorAura : Module("Anchor Aura", Category.COMBAT) {
    private enum class SwapMode {
        Screen,
        Hotbar,
        Pick
    }

    private enum class BoxMode {
        Both,
        Lines,
        Fill
    }

    private enum class RotationMode {
        Motion,
        Silent
    }

    private val sgGeneral = settingGroup("General")
    private val sgPlace = settingGroup("Placement")
    private val sgDamage = settingGroup("Damage")
    private val sgTargets = settingGroup("Targets")
    private val sgRender = settingGroup("Rendering")

    private val multitask by boolSetting("Multi Task", false).group(sgGeneral)
    private val rotate by boolSetting("Rotate", false).group(sgGeneral)
    private val rotationMode by enumSetting(
        "Rotation Mode",
        RotationMode.Motion,
        { rotate },
        onChanged = { releaseRotation() }).group(sgGeneral)
    private val asynchronous by boolSetting("Asynchronous", true, onChanged = { cancelCalculation() }).group(sgGeneral)
    private val place by boolSetting("Place", true).group(sgPlace)
    private val airPlace by boolSetting("Air Place", false).group(sgPlace)
    private val placeSwitch by enumSetting("Place Switch", SwapMode.Hotbar).group(sgPlace)
    private val explodeSwitch by enumSetting("Explode Switch", SwapMode.Hotbar).group(sgPlace)
    private val placeRange by doubleSetting("Place Range", 4.5, 0.0..6.0, 0.1).group(sgPlace)
    private val breakCrystal by boolSetting("Break Crystal", true).group(sgPlace)
    private val breakRange by doubleSetting("Break Range", 3.0, 0.0..6.0, 0.1, { breakCrystal }).group(sgPlace)
    private val spamPlace by boolSetting("Spam Place", true, { place }).group(sgPlace)
    private val detectMining by boolSetting("Detect Mining", true, { place && spamPlace }).group(sgPlace)
    private val placeDelay by doubleSetting("Place Delay", 18.0, 0.1..1000.0, 0.1).group(sgPlace)
    private val assumeBestArmor by boolSetting("Assume Best Armor", false).group(sgDamage)
    private val minDamage by doubleSetting("Min Damage", 4.0, 1.0..10.0, 0.1).group(sgDamage)
    private val maxLocalDamage by doubleSetting("Max Local Damage", 12.0, 4.0..20.0, 0.1).group(sgDamage)
    private val damageRatio by doubleSetting("Damage Ratio", 1.2, 0.1..2.0, 0.1).group(sgDamage)
    private val safety by boolSetting("Safety", true).group(sgDamage)
    private val blockDestruction by boolSetting("Block Destruction", false).group(sgDamage)
    private val selfExtrapolate by boolSetting("Self Extrapolate", false).group(sgDamage)
    private val extrapolationTicks by intSetting("Extrapolation Ticks", 0, 0..10, 1).group(sgDamage)
    private val enemyRange by doubleSetting("Enemy Range", 10.0, 1.0..13.0, 0.1).group(sgTargets)
    private val players by boolSetting("Players", true).group(sgTargets)
    private val monsters by boolSetting("Monsters", false).group(sgTargets)
    private val neutrals by boolSetting("Neutrals", false).group(sgTargets)
    private val animals by boolSetting("Animals", false).group(sgTargets)
    private val ignoreNakeds by boolSetting("Ignore Nakeds", false).group(sgTargets)
    private val render by boolSetting("Render", true).group(sgRender)
    private val boxMode by enumSetting("Box Mode", BoxMode.Both, { render }).group(sgRender)
    private val line by colorSetting("Line", Color(255, 255, 255, 100), true, { render }).group(sgRender)
    private val side by colorSetting("Side", Color(255, 255, 255, 70), true, { render }).group(sgRender)
    private val width by doubleSetting("Width", 1.0, 0.1..5.0, 0.1, { render }).group(sgRender)
    private val shrink by boolSetting("Shrink", false, { render }).group(sgRender)
    private val sliderSpeed by doubleSetting("Slider Speed", 0.5, 0.1..1.0, 0.1, { render }).group(sgRender)
    private val startFade by doubleSetting("Start Fade", 0.2, 0.1..1.0, 0.1, { render }).group(sgRender)
    private val fadeSpeed by doubleSetting("Fade Speed", 0.3, 0.1..1.0, 0.1, { render }).group(sgRender)
    private val renderDamage by boolSetting("Render Damage", false, { render }).group(sgRender)
    private val targetDamage by boolSetting("Target Damage", true, { render && renderDamage }).group(sgRender)
    private val selfDamage by boolSetting("Self Damage", false, { render && renderDamage }).group(sgRender)

    private data class Candidate(val pos: BlockPos, val targetId: Int, val damage: Double, val selfDamage: Double)
    private data class Mining(val pos: BlockPos, val progress: Int, val tick: Int)

    private data class DamageRules(
        val minimum: Double,
        val maximumSelf: Double,
        val ratio: Double,
        val safety: Boolean
    ) {
        fun rejectsSelf(damage: Double, self: CrystalDamage.Snapshot): Boolean =
            !self.creative && (damage > maximumSelf || safety && damage >= self.health)

        fun accepts(damage: Double, selfDamage: Double): Boolean = damage >= minimum && damage >= selfDamage * ratio
    }

    private data class Calculation(
        val positions: List<BlockPos>, val targets: List<CrystalDamage.Snapshot>,
        val self: CrystalDamage.Snapshot, val world: CrystalDamage.WorldSnapshot, val rules: DamageRules
    ) {
        fun calculate(): Candidate? {
            var best: Candidate? = null
            for (pos in positions) {
                if (Thread.currentThread().isInterrupted) return null
                val center = Vec3.atCenterOf(pos)
                val ignored = setOf(pos)
                val selfDamage = CrystalDamage.calculate(self, center, world, ignored = ignored, explosionRadius = 5.0)
                if (rules.rejectsSelf(selfDamage, self)) continue
                for (target in targets) {
                    val damage =
                        CrystalDamage.calculate(target, center, world, ignored = ignored, explosionRadius = 5.0)
                    if (rules.accepts(damage, selfDamage) && damage > (best?.damage ?: 0.0)) {
                        best = Candidate(pos, target.id, damage, selfDamage)
                    }
                }
            }
            return best
        }
    }

    private var candidate: Candidate? = null
    private var calculationTask: Future<Candidate?>? = null
    private var executor: ExecutorService? = null
    private var rotationRequest: Rotation? = null
    private val mining = HashMap<Int, Mining>()
    private var lastAction = 0L
    private var rendered: Candidate? = null
    private var renderedAt = 0L
    private var renderCenter: Vec3? = null
    private var renderScale = 0.0
    private var lastRenderTime = 0L

    override fun getInfo(): String? = candidate?.let { mc.level?.getEntity(it.targetId)?.name?.string }

    init {
        onEnable {
            clearState()
        }

        onDisable {
            cancelCalculation()
            executor?.shutdownNow()
            executor = null
            releaseRotation()
            mining.clear()
        }

        handler<GameLeftEvent> {
            clearState()
        }

        handler<RespawnEvent> {
            clearState()
        }

        safeEventHandler<ClientEntityEvent.TickPre>(EventPriority.HIGH) { event ->
            if (event.level === level) tickEntities()
        }

        safeEventHandler<ClientTickEvent.Post> {
            if (paused() || !itemsAvailable()) {
                cancelCalculation()
                releaseRotation()
            } else if (asynchronous && calculationTask == null) {
                val work = prepareCalculation()
                if (work == null) {
                    candidate = null
                } else {
                    val service = executor ?: Executors.newSingleThreadExecutor { runnable ->
                        Thread(runnable, "Epsilon-AutoAnchor").apply { isDaemon = true }
                    }.also { executor = it }
                    calculationTask = service.submit(Callable { work.calculate() })
                }
            }
        }

        safeEventHandler<PacketEvent.Apply> { event ->
            val packet = event.packet
            if (packet is ClientboundBlockDestructionPacket) {
                if (packet.progress in 0..9) {
                    mining[packet.id] = Mining(packet.pos, packet.progress, player.tickCount)
                } else {
                    mining.remove(packet.id)
                }
            }
        }

        safeEventHandler<Render3DEvent>(alwaysListening = true) {
            updateRender()
            val center = renderCenter ?: return@safeEventHandler
            if (!render || renderScale <= 0.0) return@safeEventHandler
            val box = AABB(center, center).inflate(if (shrink) renderScale * 0.5 else 0.5)
            if (boxMode != BoxMode.Lines) Render3DScheduler.addFilledBox(box, faded(side))
            if (boxMode != BoxMode.Fill) Render3DScheduler.addOutlineBox(box, faded(line), width.toFloat())
        }

        safeEventHandler<Render2DEvent.Level>(alwaysListening = true) { event ->
            val current = rendered ?: return@safeEventHandler
            val center = renderCenter ?: return@safeEventHandler
            if (!render || !renderDamage || renderScale <= 0.0 || now() - renderedAt >= startFade * 1000.0) return@safeEventHandler
            val labels = buildList {
                if (targetDamage) add(String.format(Locale.ROOT, "%.1f", current.damage))
                if (selfDamage) add(String.format(Locale.ROOT, "%.1f", current.selfDamage))
            }
            if (labels.isEmpty()) return@safeEventHandler
            val screen = WorldToScreen.calcWorld2Screen(center) ?: return@safeEventHandler
            val label = labels.joinToString(" / ")
            val font = StaticFontLoader.defaultFont()
            event.canvas.addText(
                label,
                screen.x - event.textMetrics.getWidth(label, 0.8f, font) * 0.5f,
                screen.y - event.textMetrics.getHeight(0.8f, font) * 0.5f,
                0.8f, faded(Color.WHITE), font
            )
        }
    }

    private fun NonNullContext.tickEntities() {
        mining.entries.removeIf { player.tickCount - it.value.tick > 400 || level.getEntity(it.key) == null }
        if (paused() || !itemsAvailable()) {
            cancelCalculation()
            releaseRotation()
            return
        }
        if (asynchronous) {
            val task = calculationTask
            if (task != null && task.isDone) {
                candidate = task.get()
                calculationTask = null
            }
        } else {
            candidate = prepareCalculation()?.calculate()
        }
        val current = candidate
        if (current == null) {
            releaseRotation()
            return
        }
        if (now() - lastAction < placeDelay) return
        val target = level.getEntity(current.targetId) as? LivingEntity
        if (target == null || !validTarget(target) || anchorWorks(current.pos)) {
            cancelCalculation()
            releaseRotation()
            return
        }
        val self = snapshot(player, if (selfExtrapolate) extrapolationTicks else 0)
        val center = Vec3.atCenterOf(current.pos)
        val ignored = setOf(current.pos)
        val localDamage = damage(self, center, blockDestruction, ignored = ignored, explosionRadius = 5.0)
        val enemyDamage = damage(
            snapshot(target, extrapolationTicks, assumeBestArmor),
            center,
            blockDestruction,
            ignored = ignored,
            explosionRadius = 5.0
        )
        val rules = damageRules()
        if (rules.rejectsSelf(localDamage, self) || !rules.accepts(enemyDamage, localDamage)) {
            cancelCalculation()
            releaseRotation()
            return
        }
        lastAction = now()
        execute(current.copy(damage = enemyDamage, selfDamage = localDamage), target)
    }

    private fun NonNullContext.prepareCalculation(): Calculation? {
        val targets = level.entitiesForRendering().filterIsInstance<LivingEntity>()
            .filter { validTarget(it) }.map { snapshot(it, extrapolationTicks, assumeBestArmor) }
        if (targets.isEmpty()) return null
        val radius = ceil(placeRange + 1.0).toInt()
        val origin = player.blockPosition()
        val positions = ArrayList<BlockPos>()
        for (mutable in BlockPos.betweenClosed(
            origin.offset(-radius, -radius, -radius),
            origin.offset(radius, radius, radius)
        )) {
            val pos = mutable.immutable()
            if (!level.isInWorldBounds(pos) || !level.worldBorder.isWithinBounds(pos) || anchorWorks(pos) ||
                player.eyePosition.distanceTo(Vec3.atCenterOf(pos)) > placeRange + 0.5 || interactionHit(pos) == null
            ) continue
            val state = level.getBlockState(pos)
            if (!state.`is`(Blocks.RESPAWN_ANCHOR) &&
                (!place || !state.canBeReplaced() || !spaceClear(pos, breakCrystal) || placementHit(pos) == null)
            ) continue
            positions.add(pos)
        }
        if (positions.isEmpty()) return null
        val self = snapshot(player, if (selfExtrapolate) extrapolationTicks else 0)
        var bounds = self.box
        for (target in targets) bounds = bounds.minmax(target.box)
        for (pos in positions) bounds = bounds.minmax(AABB(pos))
        return Calculation(positions, targets, self, captureWorld(bounds, blockDestruction), damageRules())
    }

    private fun NonNullContext.execute(current: Candidate, target: LivingEntity) {
        val pos = current.pos
        val hit = interactionHit(pos) ?: return
        val state = level.getBlockState(pos)
        val existing = state.`is`(Blocks.RESPAWN_ANCHOR)
        if (!existing && !placeAnchor(pos)) return
        val charges = if (existing) state.getValue(RespawnAnchorBlock.CHARGE) else 0
        val requiredCharges = if (player.offhandItem.`is`(Items.GLOWSTONE)) RespawnAnchorBlock.MAX_CHARGES else 1
        repeat((requiredCharges - charges).coerceAtLeast(0)) {
            if (!withItem(placeSwitch, { it.`is`(Items.GLOWSTONE) }) { interact(hit) }) return
        }
        if (!withItem(explodeSwitch, { !it.`is`(Items.GLOWSTONE) }) { interact(hit) }) return
        rendered = current
        renderedAt = now()
        if (place && spamPlace && target is Player && adjacent(target, pos) && !beingMined(pos)) {
            placeAnchor(pos, afterExplosion = true)
        }
    }

    private fun NonNullContext.placeAnchor(pos: BlockPos, afterExplosion: Boolean = false): Boolean {
        if (!place || !afterExplosion && !level.getBlockState(pos).canBeReplaced()) return false
        if (!spaceClear(pos, false)) {
            if (breakCrystal && spaceClear(pos, true)) {
                for (crystal in level.getEntities(null, AABB(pos)).filterIsInstance<EndCrystal>()) {
                    val aim = nearest(player.eyePosition, crystal.boundingBox)
                    if (player.eyePosition.distanceTo(aim) <= breakRange && aimAt(aim)) {
                        gameMode.attack(player, crystal)
                        SwingMode.Both.attackSwing(InteractionHand.MAIN_HAND)
                    }
                }
            }
            return false
        }
        val hit = placementHit(pos) ?: return false
        return withItem(placeSwitch, { it.`is`(Items.RESPAWN_ANCHOR) }) {
            if (!aimAt(hit.location, blockInteraction = true)) return@withItem false
            val input = player.lastSentInput
            val sneakingInput = Input(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                true,
                input.sprint()
            )
            val localInput = player.input.keyPresses
            connection.send(ServerboundPlayerInputPacket(sneakingInput))
            if (rotate && rotationMode == RotationMode.Silent) player.input.keyPresses = sneakingInput
            try {
                sendInteraction(hit)
            } finally {
                player.input.keyPresses = localInput
                connection.send(ServerboundPlayerInputPacket(input))
            }
            true
        }
    }

    private fun NonNullContext.interact(hit: BlockHitResult): Boolean {
        if (!aimAt(hit.location, blockInteraction = true)) return false
        sendInteraction(hit)
        return true
    }

    private fun NonNullContext.sendInteraction(hit: BlockHitResult) {
        if (rotate && rotationMode == RotationMode.Silent) {
            placeBlock(InteractionHand.MAIN_HAND, hit)
        } else {
            connection.send(
                ServerboundUseItemOnPacket(
                    InteractionHand.MAIN_HAND,
                    hit,
                    level.blockStatePredictionHandler.startPredicting().currentSequence()
                )
            )
        }
        SwingMode.Both.interactSwing(InteractionHand.MAIN_HAND)
    }

    private fun NonNullContext.aimAt(point: Vec3, blockInteraction: Boolean = false): Boolean {
        if (!rotate || blockInteraction && rotationMode == RotationMode.Silent) {
            releaseRotation()
            return true
        }
        val request = calculate(point)
        if (!RotationManager.setRotationsExact(request, RotationPriority.High)) return false
        rotationRequest = request
        connection.send(
            ServerboundMovePlayerPacket.PosRot(
                player.position(),
                request.yaw,
                request.pitch,
                player.onGround(),
                player.horizontalCollision
            )
        )
        return true
    }

    private fun NonNullContext.findSlot(mode: SwapMode, predicate: (ItemStack) -> Boolean): Int = when (mode) {
        SwapMode.Screen -> player.containerMenu.slots.indexOfFirst {
            predicate(it.item) && it.mayPickup(player) && it.mayPlace(player.mainHandItem)
        }

        SwapMode.Hotbar -> (0..8).firstOrNull { predicate(player.inventory.getItem(it)) } ?: -1
        SwapMode.Pick -> (0..35).firstOrNull { predicate(player.inventory.getItem(it)) } ?: -1
    }

    private fun NonNullContext.withItem(
        mode: SwapMode,
        predicate: (ItemStack) -> Boolean,
        action: () -> Boolean
    ): Boolean {
        val slot = findSlot(mode, predicate)
        if (slot == -1) return false
        val previous = player.inventory.selectedSlot
        gameMode.ensureHasSentCarriedItem()
        if (mode == SwapMode.Screen || slot >= 9) {
            val menu = player.containerMenu
            val menuSlot = if (mode == SwapMode.Screen) slot else {
                menu.slots.indexOfFirst { it.container === player.inventory && it.containerSlot == slot }
            }
            if (menuSlot == -1 || !menu.getSlot(menuSlot).mayPickup(player)) return false
            if (menu.getSlot(menuSlot).container === player.inventory && menu.getSlot(menuSlot).containerSlot == previous) return action()
            swap(menu.containerId, menuSlot, previous)
            return try {
                action()
            } finally {
                swap(menu.containerId, menuSlot, previous)
            }
        }
        player.inventory.selectedSlot = slot
        return try {
            gameMode.ensureHasSentCarriedItem()
            action()
        } finally {
            player.inventory.selectedSlot = previous
            gameMode.ensureHasSentCarriedItem()
        }
    }

    private fun NonNullContext.itemsAvailable(): Boolean =
        (!place || findSlot(placeSwitch) { it.`is`(Items.RESPAWN_ANCHOR) } != -1) &&
                findSlot(placeSwitch) { it.`is`(Items.GLOWSTONE) } != -1 &&
                findSlot(explodeSwitch) { !it.`is`(Items.GLOWSTONE) } != -1

    private fun NonNullContext.interactionHit(pos: BlockPos): BlockHitResult? {
        val eye = player.eyePosition
        return Direction.entries.map { face ->
            val nearest = nearest(eye, AABB(pos))
            val point = when (face) {
                Direction.DOWN -> Vec3(nearest.x, pos.y.toDouble(), nearest.z)
                Direction.UP -> Vec3(nearest.x, pos.y + 1.0, nearest.z)
                Direction.NORTH -> Vec3(nearest.x, nearest.y, pos.z.toDouble())
                Direction.SOUTH -> Vec3(nearest.x, nearest.y, pos.z + 1.0)
                Direction.WEST -> Vec3(pos.x.toDouble(), nearest.y, nearest.z)
                Direction.EAST -> Vec3(pos.x + 1.0, nearest.y, nearest.z)
            }
            BlockHitResult(point, face, pos, false)
        }.filter { eye.distanceTo(it.location) <= placeRange }.minByOrNull { eye.distanceToSqr(it.location) }
    }

    private fun NonNullContext.placementHit(pos: BlockPos): BlockHitResult? {
        for (direction in Direction.entries) {
            val neighbor = pos.relative(direction)
            val state = level.getBlockState(neighbor)
            if (state.isAir || state.canBeReplaced() || !state.fluidState.isEmpty) continue
            val point = Vec3.atCenterOf(neighbor).add(direction.opposite.unitVec3.scale(0.5))
            if (player.eyePosition.distanceTo(point) <= placeRange) return BlockHitResult(
                point,
                direction.opposite,
                neighbor,
                false
            )
        }
        return if (airPlace) interactionHit(pos) else null
    }

    private fun NonNullContext.spaceClear(pos: BlockPos, ignoreCrystals: Boolean): Boolean =
        level.getEntities(null, AABB(pos)).none {
            !it.isRemoved && !it.isSpectator && it.blocksBuilding && !(ignoreCrystals && it is EndCrystal)
        }

    private fun NonNullContext.validTarget(entity: LivingEntity): Boolean =
        entity !== player && entity.isAlive && !entity.isSpectator && targetType(entity) &&
                !FriendManager.isFriend(entity.name.string) && player.distanceToSqr(entity) <= enemyRange * enemyRange &&
                !(ignoreNakeds && entity is Player && CrystalDamage.armorSlots.all { entity.getItemBySlot(it).isEmpty })

    private fun targetType(entity: LivingEntity): Boolean {
        val neutral = entity is Enderman || entity is ZombifiedPiglin || entity is Wolf || entity is IronGolem
        val passiveNeutral = (neutral || entity is Bee) && !entity.isAggressive
        return entity is Player && players || entity is Monster && !passiveNeutral && monsters || neutral && neutrals ||
                (entity is Animal || entity is AmbientCreature || entity is Squid) && animals
    }

    private fun adjacent(target: Player, pos: BlockPos): Boolean = !target.boundingBox.intersects(AABB(pos)) &&
            Direction.entries.any { target.boundingBox.intersects(AABB(pos.relative(it))) }

    private fun NonNullContext.beingMined(pos: BlockPos): Boolean {
        if (!detectMining) return false
        if (PacketMine.isEnabled && (PacketMine.targetPos == pos && PacketMine.mainProgressPercent > 60.0 ||
                    PacketMine.secondPos == pos && PacketMine.secondProgressPercent > 60.0)
        ) return true
        return mining.any { (id, entry) ->
            entry.pos == pos && entry.progress >= 6 &&
                    level.getEntity(id)?.eyePosition?.distanceTo(Vec3.atCenterOf(pos))?.let { it <= 7.0 } == true
        }
    }

    private fun NonNullContext.anchorWorks(pos: BlockPos): Boolean =
        level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos)

    private fun NonNullContext.paused(): Boolean =
        !player.isAlive || player.isSpectator || player.isShiftKeyDown || Blink.isEnabled || !multitask && player.isUsingItem

    private fun damageRules() = DamageRules(minDamage, maxLocalDamage, damageRatio, safety)

    private fun cancelCalculation() {
        calculationTask?.cancel(true)
        calculationTask = null
        candidate = null
    }

    private fun releaseRotation() {
        rotationRequest?.let { RotationManager.clearRotations(it) }
        rotationRequest = null
    }

    private fun clearState() {
        cancelCalculation()
        releaseRotation()
        mining.clear()
        lastAction = now()
        rendered = null
        renderedAt = 0L
        renderCenter = null
        renderScale = 0.0
        lastRenderTime = 0L
    }

    private fun updateRender() {
        val current = rendered ?: return
        val time = now()
        val frames = if (lastRenderTime == 0L) 1.0 else (time - lastRenderTime) / (1000.0 / 60.0)
        lastRenderTime = time
        val targetScale = if (time - renderedAt < startFade * 1000.0) 1.0 else 0.0
        val fade = if (fadeSpeed >= 1.0) 1.0 else 1.0 - (1.0 - fadeSpeed / 10.0).pow(frames)
        renderScale += (targetScale - renderScale) * fade
        if (targetScale == 0.0 && renderScale < 0.001) {
            renderScale = 0.0
            renderCenter = null
            return
        }
        val center = Vec3.atCenterOf(current.pos)
        val slide = if (sliderSpeed >= 1.0) 1.0 else 1.0 - (1.0 - sliderSpeed / 10.0).pow(frames)
        renderCenter = renderCenter?.lerp(center, slide) ?: center
    }

    private fun faded(color: Color) =
        Color(color.red, color.green, color.blue, (color.alpha * renderScale).toInt().coerceIn(0, 255))

    private fun nearest(point: Vec3, box: AABB) = Vec3(
        point.x.coerceIn(box.minX, box.maxX), point.y.coerceIn(box.minY, box.maxY), point.z.coerceIn(box.minZ, box.maxZ)
    )

    private fun now(): Long = System.nanoTime() / 1_000_000L
}
