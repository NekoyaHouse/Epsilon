package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientEntityEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.kernel.gameplay.rotation.RotationUtils
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.kernel.jvm.runSafeOrElse
import com.github.epsilon.managers.network.ServerboundPacketManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Input
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.component.SwingAnimation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

object SelfTrap : Module("Self Trap", Category.COMBAT) {
    private enum class Timing {
        Tick,
        Packet
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

    private val timing by enumSetting("Timing", Timing.Tick)
    private val explosions by boolSetting("Explosions", false, { timing == Timing.Packet })
    private val spawn by boolSetting("Spawn", false, { timing == Timing.Packet })
    private val broke by boolSetting("Broke", false, { timing == Timing.Packet })
    private val surround by boolSetting("Surround", true)
    private val trap by boolSetting("Trap", false)
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
    private val breakTime by doubleSetting("Break Time", 0.7, 0.0..2.0, 0.05, { assumeBreak })
    private val airPlace by boolSetting("Air Place", false)
    private val support by intSetting("Support", 2, 0..8, 1)
    private val calcDelay by intSetting("Calc Delay", 3, 0..20, 1, { support > 0 })
    private val multi by boolSetting("Multi", false, { support > 0 })
    private val extend by enumSetting("Extend", Extend.Disable)
    private val trapExtend by enumSetting("Trap Extend", Extend.Disable)
    private val groundOnly by boolSetting("Ground Only", false)
    private val pauseOnEat by boolSetting("Pause On Eat", false)
    private val pauseOnFlying by boolSetting("Pause On Flying", false)
    private val pauseInFluid by boolSetting("Pause In Fluid", false)
    private val disableOnYDiff by boolSetting("Disable On YDiff", false)
    private val disableOnJump by boolSetting("Disable On Jump", true)
    private val disableOnDeath by boolSetting("Disable On Death", true)

    private val remoteMining = HashMap<Int, Mining>()
    private var localMining: BlockPos? = null
    private val floorTargets = linkedSetOf<BlockPos>()
    private val wallTargets = linkedSetOf<BlockPos>()
    private val supportTargets = linkedSetOf<BlockPos>()
    private val supportPaths = arrayListOf<List<BlockPos>>()
    private val pending = HashMap<BlockPos, Long>()
    private val attacked = HashSet<Int>()
    private var placementDelay = 0
    private var calculationDelay = 0
    private var placed = 0
    private var tick = 0L
    private var previousY = Double.NaN
    private val horizontal = listOf(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)

    init {
        onEnable {
            resetRuntime()
            previousY = runSafeOrElse(Double.NaN) { player.y }
            runSafe {
                if (center == Center.OnActivate) centerPlayer()
            }
        }

        onDisable {
            resetRuntime()
        }

        safeEventHandler<ClientEntityEvent.TickPre> {
            tick++
            placed = 0
            attacked.clear()
            pending.entries.removeIf { tick - it.value >= 4 }
            remoteMining.entries.removeIf { (id, mining) ->
                val miner = level.getEntity(id)
                miner == null || miner.eyePosition.distanceToSqr(Vec3.atCenterOf(mining.pos)) > 49.0 || System.nanoTime() - mining.since > 5_000_000_000L
            }
            calculateTargets()
            val rose = !previousY.isNaN() && player.y > previousY
            previousY = player.y
            if (disableOnDeath && !player.isAlive || disableOnYDiff && rose || disableOnJump && player.input.keyPresses.jump()) {
                isEnabled = false
                return@safeEventHandler
            }
            if (placementDelay > 0) {
                placementDelay--
                return@safeEventHandler
            }
            if (!canAct()) return@safeEventHandler
            if (center == Center.Always) centerPlayer()
            placeTargets(floorTargets, true)
            placeTargets(wallTargets)
            if (supportTargets.isNotEmpty()) {
                placeTargets(supportTargets)
                placeTargets(floorTargets, true)
                placeTargets(wallTargets)
            }
        }

        safeEventHandler<PacketEvent.Receive> { event ->
            if (!event.isCancelled) {
                receive(event.packet)
            }
        }

        handler<PacketEvent.Send> { event ->
            val packet = event.packet
            if (!event.isCancelled && packet is ServerboundPlayerActionPacket) {
                when (packet.action) {
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK -> localMining = packet.pos.immutable()
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK -> localMining = null

                    else -> Unit
                }
            }
        }
    }

    private fun resetRuntime() {
        remoteMining.clear()
        localMining = null
        floorTargets.clear()
        wallTargets.clear()
        supportTargets.clear()
        supportPaths.clear()
        pending.clear()
        attacked.clear()
        placementDelay = 0
        calculationDelay = 0
        placed = 0
        tick = 0
        previousY = Double.NaN
    }

    private fun NonNullContext.centerPlayer() {
        player.setPos(floor(player.x) + 0.5, player.y, floor(player.z) + 0.5)
        player.connection.send(
            ServerboundMovePlayerPacket.Pos(
                player.position(),
                player.onGround(),
                player.horizontalCollision
            )
        )
    }

    private fun NonNullContext.canAct(): Boolean {
        if (!player.isAlive || ServerboundPacketManager.blinking) return false
        if (groundOnly && !player.onGround() && level.getBlockState(player.blockPosition()).block !is LadderBlock) return false
        if (pauseOnFlying && player.isFallFlying) return false
        if (pauseInFluid && (player.isInWater || player.isInLava)) return false
        if (pauseOnEat && player.isUsingItem) return false
        return true
    }

    private fun isMined(pos: BlockPos): Boolean = remoteMining.values.any { it.pos == pos }

    private fun NonNullContext.calculateTargets() {
        floorTargets.clear()
        wallTargets.clear()
        supportTargets.clear()
        val recalculate = calculationDelay <= 0
        if (recalculate) supportPaths.clear()
        fun add(pos: BlockPos, targets: MutableSet<BlockPos>, mode: Extend = Extend.Disable) {
            targets.add(pos)
            if (mode == Extend.Always || mode == Extend.Mining && isMined(pos)) {
                horizontal.forEach { wallTargets.add(pos.relative(it)) }
                wallTargets.add(pos.above())
            }
        }

        val feet = linkedSetOf<BlockPos>()
        for (x in floor(player.x - 0.3).toInt()..floor(player.x + 0.3).toInt()) {
            for (y in floor(player.y).toInt()..floor(player.y + 0.4).toInt()) {
                for (z in floor(player.z - 0.3).toInt()..floor(player.z + 0.3).toInt()) feet.add(BlockPos(x, y, z))
            }
        }
        if (standHead && !player.isVisuallyCrawling || crawlHead && player.isVisuallyCrawling) {
            wallTargets.add(BlockPos.containing(player.x, player.y + player.boundingBox.ysize, player.z).above())
        }
        for (pos in feet) {
            if (pos.below() !in feet) add(pos.below(), floorTargets)
            for (direction in horizontal) {
                val side = pos.relative(direction)
                if (side in feet) continue
                if (surround) add(side, wallTargets, extend)
                if (trap) add(side.above(), wallTargets, trapExtend)
            }
        }
        if (support > 0 && recalculate) {
            for (pos in floorTargets + wallTargets) {
                if (!level.getBlockState(pos).`is`(Blocks.BEDROCK) && findHit(pos) == null) {
                    findSupportPath(pos)?.let(supportPaths::add)
                }
            }
        }
        if (support > 0) {
            val paths = if (multi) supportPaths else listOfNotNull(supportPaths.minByOrNull { it.size })
            paths.forEach { supportTargets.addAll(it) }
        }
        calculationDelay = if (recalculate) calcDelay else calculationDelay - 1
    }

    private fun NonNullContext.findSupportPath(target: BlockPos): List<BlockPos>? {
        val queue = ArrayDeque<List<BlockPos>>()
        val visited = hashSetOf(target)
        queue.add(listOf(target))
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (path.size > support) continue
            for (direction in Direction.entries) {
                val next = path.last().relative(direction)
                if (!visited.add(next) || !canPlan(next)) continue
                val helpers = path.drop(1) + next
                if (findHit(next) != null) return helpers.reversed()
                if (helpers.size < support) queue.add(path + next)
            }
        }
        return null
    }

    private fun NonNullContext.canPlan(pos: BlockPos): Boolean {
        if (!level.getBlockState(pos).canBeReplaced() || !level.isInWorldBounds(pos)) return false
        if (player.eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) > placeRange * placeRange) return false
        return level.getEntities(
            null as Entity?,
            AABB(pos)
        ) { it.isAlive && !it.isSpectator && it.blocksBuilding && it !is EndCrystal }.isEmpty()
    }

    private fun NonNullContext.findHit(pos: BlockPos, allowAir: Boolean = false): BlockHitResult? {
        val hits = Direction.entries.mapNotNull { direction ->
            val neighbor = pos.relative(direction)
            val state = level.getBlockState(neighbor)
            if (state.canBeReplaced() && neighbor !in pending) return@mapNotNull null
            val face = direction.opposite
            val point = Vec3.atCenterOf(neighbor).add(face.stepX * 0.5, face.stepY * 0.5, face.stepZ * 0.5)
            if (player.eyePosition.distanceToSqr(point) > placeRange * placeRange) return@mapNotNull null
            BlockHitResult(point, face, neighbor, false)
        }
        return hits.minByOrNull { player.eyePosition.distanceToSqr(it.location) }
            ?: if (allowAir && player.eyePosition.distanceToSqr(Vec3.atCenterOf(pos)) <= placeRange * placeRange)
                BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false) else null
    }

    private fun NonNullContext.placeTargets(targets: Set<BlockPos>, isFloor: Boolean = false) {
        val underfoot = BlockPos.containing(player.x, Math.round(player.y).toDouble(), player.z).below()
        for (pos in targets) {
            if (placed >= bpt) break
            if (isFloor && mineDownward && pos == underfoot && localMining == pos) continue
            placeCounted(pos)
        }
    }

    private fun NonNullContext.placeCounted(pos: BlockPos, speculative: Boolean = false): Boolean {
        if (placed >= bpt || !place(pos, speculative)) return false
        placed++
        placementDelay = delay
        return true
    }

    private fun NonNullContext.face(point: Vec3) {
        if (!rotate) return
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
    }

    private fun NonNullContext.attackCrystal(crystal: EndCrystal): Boolean {
        if (player.eyePosition.distanceToSqr(crystal.boundingBox.getCenter()) > breakRange * breakRange) return false
        if (attacked.add(crystal.id)) {
            face(crystal.boundingBox.getCenter())
            gameMode.attack(player, crystal)
            player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true)
            if (rotate) {
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
        return true
    }

    private fun NonNullContext.place(pos: BlockPos, forced: Boolean = false): Boolean {
        if (pos in pending || !level.isInWorldBounds(pos)) return false
        val mining = remoteMining.values.firstOrNull { it.pos == pos }
        val speculative = forced || assumeBreak && mining != null &&
                (mining.finished || System.nanoTime() - mining.since >= (breakTime * 1_000_000_000).toLong())
        if (!speculative && !level.getBlockState(pos).canBeReplaced()) return false
        if (protect && (level.getBlockState(pos).canBeReplaced() || mining != null)) {
            level.getEntities(
                null as Entity?,
                AABB(pos.x - 1.0, pos.y - 1.0, pos.z - 1.0, pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)
            )
                .filterIsInstance<EndCrystal>().forEach { attackCrystal(it) }
        }
        val hit = findHit(pos, airPlace) ?: return false
        val inventory = player.inventory
        val slots = if (swapAction == SwapAction.Hotbar) 0..8 else 0..35
        val slot = blocks.firstNotNullOfOrNull { block ->
            slots.firstOrNull { index ->
                val stack = inventory.getItem(index)
                !stack.isEmpty && (stack.item as? BlockItem)?.block == block
            }
        } ?: return false
        // Use the selected block's actual collision shape, not always a full obsidian cube.
        val block = (inventory.getItem(slot).item as BlockItem).block
        val shape = block.defaultBlockState().getCollisionShape(level, pos)
            .move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        for (box in shape.toAabbs()) {
            for (entity in level.getEntities(
                null as Entity?,
                box
            ) { it.isAlive && !it.isSpectator && it.blocksBuilding }) {
                if (entity !is EndCrystal || !breakCrystal || !attackCrystal(entity)) return false
            }
        }
        val selected = inventory.selectedSlot
        val containerSwap = swapAction == SwapAction.Screen || slot >= 9
        val screenSlot = if (slot < 9) slot + 36 else slot
        if (containerSwap && player.containerMenu !== player.inventoryMenu) return false
        fun swapInventory() = gameMode.handleContainerInput(
            player.inventoryMenu.containerId,
            screenSlot,
            selected,
            ContainerInput.SWAP,
            player
        )
        if (containerSwap) swapInventory() else inventory.setSelectedSlot(slot)
        gameMode.ensureHasSentCarriedItem()
        val input = player.input.keyPresses
        try {
            if (!input.shift()) {
                val sneaking = Input(
                    input.forward(),
                    input.backward(),
                    input.left(),
                    input.right(),
                    input.jump(),
                    true,
                    input.sprint()
                )
                player.connection.send(ServerboundPlayerInputPacket(sneaking))
            }
            face(hit.location)
            player.connection.send(
                ServerboundUseItemOnPacket(
                    InteractionHand.MAIN_HAND,
                    hit,
                    level.blockStatePredictionHandler.startPredicting().currentSequence()
                )
            )
            player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true)
            pending[pos] = tick
            return true
        } finally {
            if (!input.shift()) player.connection.send(ServerboundPlayerInputPacket(input))
            if (containerSwap) swapInventory() else inventory.setSelectedSlot(selected)
            gameMode.ensureHasSentCarriedItem()
            if (rotate) player.connection.send(
                ServerboundMovePlayerPacket.Rot(
                    player.yRot,
                    player.xRot,
                    player.onGround(),
                    player.horizontalCollision
                )
            )
        }
    }

    private fun NonNullContext.receive(packet: Packet<*>) {
        when (packet) {
            is ClientboundBundlePacket -> packet.subPackets().forEach { receive(it) }
            is ClientboundBlockDestructionPacket -> {
                if (packet.progress in 0..9) {
                    val old = remoteMining[packet.id]
                    remoteMining[packet.id] = Mining(
                        packet.pos.immutable(),
                        if (old?.pos == packet.pos) old.since else System.nanoTime(), packet.progress == 9
                    )
                } else remoteMining.remove(packet.id)
            }

            is ClientboundPlayerCombatKillPacket -> if (disableOnDeath && packet.playerId() == player.id) isEnabled =
                false

            is ClientboundBlockUpdatePacket -> {
                pending.remove(packet.pos)
                if (!packet.blockState.canBeReplaced()) remoteMining.entries.removeIf { it.value.pos == packet.pos && it.value.finished }
                if (timing == Timing.Packet && broke && packet.pos in wallTargets && packet.blockState.canBeReplaced() && canAct()) {
                    // Upstream Broke bypasses Delay/BPT; Spawn/Explosions share the tick budget.
                    place(packet.pos, true)
                }
            }

            is ClientboundExplodePacket -> if (timing == Timing.Packet && explosions && canAct()) {
                val pos = BlockPos.containing(packet.center())
                if (pos in wallTargets) placeCounted(pos)
            }

            is ClientboundAddEntityPacket -> if (timing == Timing.Packet && spawn && packet.type == EntityTypes.END_CRYSTAL && canAct()) {
                val pos = BlockPos.containing(packet.x, packet.y, packet.z)
                if (pos in wallTargets) placeCounted(pos)
            }
        }
    }

    private data class Mining(val pos: BlockPos, val since: Long, val finished: Boolean)
}
