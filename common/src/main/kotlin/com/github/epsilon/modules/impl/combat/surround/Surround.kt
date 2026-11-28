package com.github.epsilon.modules.impl.combat.surround

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.gameplay.inventory.ContainerClicks.swap
import com.github.epsilon.kernel.gameplay.player.SwingMode
import com.github.epsilon.kernel.gameplay.rotation.RotationUtils
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.Blink
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Input
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.floor

object Surround : Module("Surround", Category.COMBAT) {
    private enum class Timing {
        Vanilla,
        Sequential
    }

    private enum class Center {
        Never,
        OnActivate,
        Always
    }

    private enum class Extend {
        Always,
        Mining,
        Disable
    }

    private enum class SwapAction {
        Screen,
        Hotbar,
        Pick
    }

    private enum class RotationMode {
        BBTT,
        Grim
    }

    private val timing by enumSetting("Timing", Timing.Vanilla)
    private val explosions by boolSetting("Explosions", false, { timing == Timing.Sequential })
    private val spawn by boolSetting("Spawn", false, { timing == Timing.Sequential })
    private val broke by boolSetting("Broke", false, { timing == Timing.Sequential })
    private val surround by boolSetting("Surround", true)
    private val standHead by boolSetting("Stand Head", false)
    private val crawlHead by boolSetting("Crawl Head", false)
    private val mineDownward by boolSetting("Mine Downward", false)
    private val blocks by blockListSetting("Blocks", listOf(Blocks.OBSIDIAN, Blocks.ENDER_CHEST))
    private val delay by intSetting("Delay", 2, 0..20, 1)
    private val bpt by intSetting("BPT", 1, 1..8, 1)
    private val swapAction by enumSetting("Swap Action", SwapAction.Hotbar)
    private val center by enumSetting("Center", Center.Never)
    private val rotate by boolSetting("Rotate", true)
    private val rotationMode by enumSetting("Rotation Mode", RotationMode.Grim)
    private val placeRange by doubleSetting("Place Range", 4.5, 0.0..6.0, 0.1)
    private val protect by boolSetting("Protect", true)
    private val breakCrystal by boolSetting("Break Crystal", true)
    private val breakRange by doubleSetting("Break Range", 3.0, 0.0..6.0, 0.1)
    private val assumeBreak by boolSetting("Assume Break", true)
    private val breakTime by doubleSetting("Break Time", 0.7, 0.0..2.0, 0.1, { assumeBreak })
    private val airPlace by boolSetting("AirPlace", false)
    private val support by intSetting("Support", 2, 0..8, 1)
    private val calcDelay by intSetting("Calc Delay", 3, 0..20, 1, { support > 0 })
    private val multi by boolSetting("Multi", true, { support > 0 })
    private val extend by enumSetting("Extend", Extend.Disable)
    private val groundOnly by boolSetting("Ground Only", false)
    private val pauseOnEat by boolSetting("Pause On Eat", false)
    private val pauseOnFlying by boolSetting("Pause On Flying", false)
    private val pauseInFluid by boolSetting("Pause In Fluid", false)
    private val disableOnYDiff by boolSetting("Disable On YDiff", false)
    private val disableOnJump by boolSetting("Disable On Jump", true)
    private val disableOnDeath by boolSetting("Disable On Death", true)

    private data class Mining(val pos: BlockPos, val started: Long, val completed: Boolean)

    private val miners = mutableMapOf<Int, Mining>()
    private var localMining: BlockPos? = null
    private var localMiningStarted = 0L
    private var localMiningCompleted = false
    private val walls = linkedSetOf<BlockPos>()
    private val floors = linkedSetOf<BlockPos>()
    private var paths = emptyList<List<BlockPos>>()
    private var cachedTargets = emptySet<BlockPos>()
    private val sent = mutableMapOf<BlockPos, Long>()
    private val attacked = mutableSetOf<Int>()
    private var placementDelay = 0
    private var calculationDelay = 0
    private var placedThisTick = 0
    private var previousY: Double? = null

    @Volatile
    private var generation = 0

    init {
        onEnable {
            clear()
            runSafe {
                previousY = player.y
                if (center == Center.OnActivate) centerPlayer()
            }
        }

        onDisable {
            clear()
        }

        safeEventHandler<PlayerTickEvent.Pre>(priority = EventPriority.HIGH) {
            placedThisTick = 0
            attacked.clear()
            val y = previousY
            previousY = player.y
            if ((disableOnDeath && !player.isAlive) ||
                (disableOnYDiff && y != null && player.y > y) ||
                (disableOnJump && player.input.keyPresses.jump())
            ) {
                isEnabled = false
                return@safeEventHandler
            }
            sent.entries.removeIf { System.nanoTime() - it.value > 250_000_000L }
            miners.entries.removeIf { System.nanoTime() - it.value.started > 10_000_000_000L }
            if (center == Center.Always && canAct()) centerPlayer()
            calculateTargets()
            if (placementDelay > 0) {
                placementDelay--
                return@safeEventHandler
            }
            if (!canAct()) return@safeEventHandler
            // Retry after helpers so newly predicted anchors can serve the same tick.
            placeAll(floors, skipMiningDown = true)
            placeAll(walls)
            val helpers = if (multi) paths.flatten() else paths.minByOrNull { it.size }.orEmpty()
            if (helpers.isNotEmpty()) {
                placeAll(helpers)
                placeAll(floors, skipMiningDown = true)
                placeAll(walls)
            }
        }

        handler<PacketEvent.Receive> { event ->
            if (event.isCancelled) return@handler
            val token = generation
            val packet = event.packet
            mc.execute {
                if (isEnabled && token == generation) runSafe { receive(packet) }
            }
        }

        handler<PacketEvent.Send> { event ->
            val packet = event.packet as? ServerboundPlayerActionPacket ?: return@handler
            if (event.isCancelled) return@handler
            val token = generation
            mc.execute {
                if (!isEnabled || token != generation) return@execute
                when (packet.action) {
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK -> {
                        localMining = packet.pos.immutable()
                        localMiningStarted = System.nanoTime()
                        localMiningCompleted = false
                    }

                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK -> localMiningCompleted = true
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK -> localMining = null
                    else -> Unit
                }
            }
        }
    }

    private fun clear() {
        generation++
        walls.clear()
        floors.clear()
        paths = emptyList()
        cachedTargets = emptySet()
        miners.clear()
        sent.clear()
        attacked.clear()
        localMining = null
        localMiningCompleted = false
        placementDelay = 0
        calculationDelay = 0
        placedThisTick = 0
        previousY = null
    }

    private fun NonNullContext.canAct(): Boolean = when {
        !player.isAlive || player.isSpectator || Blink.isEnabled -> false
        pauseOnFlying && player.isFallFlying -> false
        pauseInFluid && player.isInLiquid -> false
        pauseOnEat && player.isUsingItem -> false
        else -> !groundOnly || player.onGround() || level.getBlockState(player.blockPosition()).`is`(Blocks.LADDER)
    }

    private fun NonNullContext.centerPlayer() {
        player.setPos(floor(player.x) + 0.5, player.y, floor(player.z) + 0.5)
        connection.send(
            ServerboundMovePlayerPacket.Pos(
                player.position(),
                player.onGround(),
                player.horizontalCollision
            )
        )
    }

    private fun isMining(pos: BlockPos): Boolean = localMining == pos || miners.values.any { it.pos == pos }

    private fun predictedBreak(pos: BlockPos): Boolean {
        if (!assumeBreak) return false
        val now = System.nanoTime()
        val elapsed = (breakTime * 1_000_000_000).toLong()
        return (localMining == pos && (localMiningCompleted || now - localMiningStarted >= elapsed)) ||
                miners.values.any { it.pos == pos && (it.completed || now - it.started >= elapsed) }
    }

    private fun NonNullContext.calculateTargets() {
        walls.clear()
        floors.clear()
        val footprint = SurroundPlanner.footprint(player.x, player.y, player.z)
        for (pos in footprint) {
            floors.add(pos.below())
            if (!surround) continue
            for (side in SurroundPlanner.horizontal) {
                val wall = pos.relative(side)
                if (wall in footprint) continue
                walls.add(wall)
                if (extend == Extend.Always || (extend == Extend.Mining && isMining(wall))) {
                    SurroundPlanner.horizontal.mapTo(walls) { wall.relative(it) }
                    walls.add(wall.above())
                }
            }
        }
        if ((standHead && !player.isVisuallyCrawling) || (crawlHead && player.isVisuallyCrawling)) {
            walls.add(BlockPos.containing(player.x, player.boundingBox.maxY, player.z).above())
        }
        val targets = (floors + walls).toSet()
        if (support == 0 || airPlace) {
            paths = emptyList()
        } else if (calculationDelay <= 0 || targets != cachedTargets) {
            paths = targets.filter { !level.getBlockState(it).`is`(Blocks.BEDROCK) }.mapNotNull { target ->
                SurroundPlanner.supportPath(target, support, { canSupport(it) }, { hit(it) != null })
                    .takeIf { it.isNotEmpty() }
            }
            calculationDelay = calcDelay
        } else calculationDelay--
        cachedTargets = targets
    }

    private fun NonNullContext.canSupport(pos: BlockPos): Boolean =
        level.getBlockState(pos).canBeReplaced() && validPosition(pos) &&
                level.getEntities(null, AABB(pos)).none { !it.isRemoved && it.blocksBuilding }

    private fun NonNullContext.validPosition(pos: BlockPos): Boolean =
        !level.isOutsideBuildHeight(pos) && level.worldBorder.isWithinBounds(pos) &&
                player.eyePosition.distanceTo(Vec3.atCenterOf(pos)) <= placeRange + 0.5

    private fun NonNullContext.hit(pos: BlockPos): BlockHitResult? = Direction.entries.mapNotNull { side ->
        val neighbor = pos.relative(side)
        val state = level.getBlockState(neighbor)
        if (state.canBeReplaced() || !state.fluidState.isEmpty) return@mapNotNull null
        val face = side.opposite
        if (!RotationUtils.isGrimDirection(neighbor, face)) return@mapNotNull null
        hit(
            state.getShape(level, neighbor, CollisionContext.of(player)),
            neighbor,
            face,
            player.eyePosition,
            placeRange
        )
    }.minByOrNull { player.eyePosition.distanceToSqr(it.location) }

    private fun NonNullContext.placeAll(positions: Collection<BlockPos>, skipMiningDown: Boolean = false) {
        for (pos in positions) {
            if (skipMiningDown && mineDownward && pos == player.blockPosition().below() && localMining == pos) continue
            attempt(pos)
        }
    }

    private fun NonNullContext.attempt(pos: BlockPos, force: Boolean = false): Boolean {
        if (placedThisTick >= bpt || pos in sent || !validPosition(pos)) return false
        val success = place(pos, force || predictedBreak(pos))
        if (success) {
            placedThisTick++
            placementDelay = delay
            sent[pos] = System.nanoTime()
        }
        return success
    }

    private fun NonNullContext.place(pos: BlockPos, forced: Boolean): Boolean {
        val replaceable = level.getBlockState(pos).canBeReplaced()
        if (protect && (replaceable || isMining(pos))) {
            level.getEntities(null, AABB(pos).inflate(1.0)).filterIsInstance<EndCrystal>().forEach { attackCrystal(it) }
        }
        if ((!replaceable && !forced) || level.getBlockState(pos).`is`(Blocks.BEDROCK)) return false
        val collisions = level.getEntities(null, AABB(pos)).filter { !it.isRemoved && it.blocksBuilding }
        if (collisions.any { it !is EndCrystal }) return false
        if (collisions.isNotEmpty() && (!breakCrystal || collisions.any { !attackCrystal(it as EndCrystal) })) return false
        val normalHit = hit(pos)
        val blockHit = normalHit ?: if (airPlace) {
            airHit(pos, player.eyePosition, placeRange) { RotationUtils.isGrimDirection(pos, it) } ?: return false
        } else return false
        if (player.eyePosition.distanceTo(blockHit.location) > placeRange) return false

        var slot = -1
        for (block in blocks) {
            val candidates = if (swapAction == SwapAction.Hotbar) (listOf(40, player.inventory.selectedSlot) + (0..8))
            else (listOf(40, player.inventory.selectedSlot) + (0..35))
            slot = candidates.firstOrNull { index ->
                val stack = player.inventory.getItem(index)
                !stack.isEmpty && (stack.item as? BlockItem)?.block == block
            } ?: -1
            if (slot != -1) break
        }
        if (slot == -1 || (swapAction != SwapAction.Hotbar && player.containerMenu !== player.inventoryMenu)) return false
        val selected = player.inventory.selectedSlot
        val inventorySwap = slot in 9..35 || (swapAction == SwapAction.Screen && slot in 0..8 && slot != selected)
        val hand = if (slot == 40) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val wasSneaking = player.isShiftKeyDown
        val originalInput = player.input.keyPresses
        try {
            if (inventorySwap) swap(if (slot < 9) slot + 36 else slot, selected)
            else if (slot in 0..8) player.inventory.selectedSlot = slot
            gameMode.ensureHasSentCarriedItem()
            // Suppress interactive anchors (notably ender chests) without opening their GUI.
            player.isShiftKeyDown = true
            if (!wasSneaking) connection.send(
                ServerboundPlayerInputPacket(
                    Input(
                        originalInput.forward(),
                        originalInput.backward(),
                        originalInput.left(),
                        originalInput.right(),
                        originalInput.jump(),
                        true,
                        originalInput.sprint()
                    )
                )
            )
            withRotation(blockHit.location) {
                if (forced || normalHit == null || collisions.isNotEmpty()) {
                    level.blockStatePredictionHandler.startPredicting().use { prediction ->
                        connection.send(ServerboundUseItemOnPacket(hand, blockHit, prediction.currentSequence()))
                    }
                } else {
                    if (!gameMode.useItemOn(player, hand, blockHit).consumesAction()) return@withRotation false
                }
                SwingMode.Both.interactSwing(hand)
                true
            }.let { return it }
        } finally {
            player.isShiftKeyDown = wasSneaking
            if (!wasSneaking) connection.send(ServerboundPlayerInputPacket(originalInput))
            if (inventorySwap) swap(if (slot < 9) slot + 36 else slot, selected)
            player.inventory.selectedSlot = selected
            gameMode.ensureHasSentCarriedItem()
        }
    }

    private fun NonNullContext.attackCrystal(crystal: EndCrystal): Boolean {
        if (player.eyePosition.distanceTo(crystal.boundingBox.center) > breakRange) return false
        if (crystal.id in attacked) return true
        withRotation(crystal.boundingBox.center) {
            gameMode.attack(player, crystal)
            SwingMode.Both.attackSwing(InteractionHand.MAIN_HAND)
        }
        attacked.add(crystal.id)
        return true
    }

    private inline fun <T> NonNullContext.withRotation(point: Vec3, action: () -> T): T {
        if (!rotate) return action()
        val rotation = RotationUtils.calculate(point)
        when (rotationMode) {
            RotationMode.BBTT -> connection.send(
                ServerboundMovePlayerPacket.Rot(
                    rotation.yaw,
                    rotation.pitch,
                    player.onGround(),
                    player.horizontalCollision
                )
            )

            RotationMode.Grim -> connection.send(
                ServerboundMovePlayerPacket.PosRot(
                    player.x,
                    player.y,
                    player.z,
                    rotation.yaw,
                    rotation.pitch,
                    player.onGround(),
                    player.horizontalCollision
                )
            )
        }
        return try {
            action()
        } finally {
            when (rotationMode) {
                RotationMode.BBTT -> connection.send(
                    ServerboundMovePlayerPacket.Rot(
                        player.yRot,
                        player.xRot,
                        player.onGround(),
                        player.horizontalCollision
                    )
                )

                RotationMode.Grim -> connection.send(
                    ServerboundMovePlayerPacket.PosRot(
                        player.x,
                        player.y,
                        player.z,
                        player.yRot,
                        player.xRot,
                        player.onGround(),
                        player.horizontalCollision
                    )
                )
            }
        }
    }

    private fun NonNullContext.receive(packet: Packet<*>) {
        when (packet) {
            is ClientboundPlayerCombatKillPacket -> if (disableOnDeath && packet.playerId() == player.id) isEnabled =
                false

            is ClientboundBlockDestructionPacket -> {
                if (packet.progress in 0..9) {
                    val old = miners[packet.id]
                    miners[packet.id] = Mining(
                        packet.pos.immutable(),
                        if (old?.pos == packet.pos) old.started else System.nanoTime(), packet.progress == 9
                    )
                } else miners.remove(packet.id)
            }

            is ClientboundBlockUpdatePacket -> {
                sent.remove(packet.pos)
                if (packet.blockState.canBeReplaced()) {
                    miners.entries.removeIf { it.value.pos == packet.pos }
                    if (localMining == packet.pos) localMining = null
                    if (timing == Timing.Sequential && broke && packet.pos in walls && canAct()) attempt(
                        packet.pos,
                        true
                    )
                }
            }

            is ClientboundExplodePacket -> if (timing == Timing.Sequential && explosions && canAct()) {
                val pos = BlockPos.containing(packet.center())
                if (pos in walls) attempt(pos)
            }

            is ClientboundAddEntityPacket -> if (timing == Timing.Sequential && spawn && packet.type == EntityTypes.END_CRYSTAL && canAct()) {
                val pos = BlockPos.containing(packet.x, packet.y, packet.z)
                if (pos in walls) attempt(pos)
            }
        }
    }

    private fun faceCenter(bounds: AABB, face: Direction): Vec3 {
        val center = bounds.center
        return when (face) {
            Direction.DOWN -> Vec3(center.x, bounds.minY, center.z)
            Direction.UP -> Vec3(center.x, bounds.maxY, center.z)
            Direction.NORTH -> Vec3(center.x, center.y, bounds.minZ)
            Direction.SOUTH -> Vec3(center.x, center.y, bounds.maxZ)
            Direction.WEST -> Vec3(bounds.minX, center.y, center.z)
            Direction.EAST -> Vec3(bounds.maxX, center.y, center.z)
        }
    }

    private fun hit(shape: VoxelShape, pos: BlockPos, face: Direction, eye: Vec3, range: Double): BlockHitResult? =
        shape.toAabbs().mapNotNull { localBounds ->
            val bounds = localBounds.move(pos)
            val point = faceCenter(bounds, face)
            val clipped = shape.clip(eye, point.subtract(face.unitVec3.scale(1.0E-4)), pos)
                ?: return@mapNotNull null
            clipped.takeIf { it.direction == face && eye.distanceTo(it.location) <= range }
        }.minByOrNull { eye.distanceToSqr(it.location) }

    private fun airHit(
        pos: BlockPos,
        eye: Vec3,
        range: Double,
        canUseFace: (Direction) -> Boolean,
    ): BlockHitResult? = Direction.entries.filter(canUseFace).mapNotNull { face ->
        hit(Shapes.block(), pos, face, eye, range)
    }.minByOrNull { eye.distanceToSqr(it.location) }
}
