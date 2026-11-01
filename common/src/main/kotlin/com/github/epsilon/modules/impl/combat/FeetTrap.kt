package com.github.epsilon.modules.impl.combat

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.player.*
import com.github.epsilon.utils.rotation.RaytraceUtils
import com.github.epsilon.utils.rotation.Rotation
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.timer.TimerUtils
import com.github.epsilon.utils.world.BlockUtils
import com.github.epsilon.utils.world.LegacyPlacement
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentLinkedQueue

object FeetTrap : Module("Feet Trap", Category.COMBAT) {

    private enum class RotationMode {
        Silent,
        Snap
    }

    private val toggleOnMove by boolSetting("Toggle On Move", true)
    private val toggleOnJump by boolSetting("Toggle On Jump", true)
    private val inAir by boolSetting("In Air", true)
    private val pauseOnEat by boolSetting("Pause On Eat", true)
    private val useEnderChest by boolSetting("Use Ender Chest", true)
    private val placeDelay by intSetting("Place Delay", 50, 0..500, 50)
    private val extend by boolSetting("Extend", true)
    private val support by intSetting("Support", 2, 0..8, 1)
    private val blocksPerTick by intSetting("Blocks Per Tick", 1, 1..8, 1)
    private val rotate by boolSetting("Rotate", true)
    private val rotationMode by enumSetting("Rotation Mode", RotationMode.Snap, { rotate })
    private val rotationSpeed by intSetting("Rotation Speed", 180, 18..180, 18, { rotate && rotationMode == RotationMode.Silent })
    private val inventorySwap by boolSetting("Inventory Swap", true)
    private val swingHand by boolSetting("Swing Hand", true)

    private val placeTimer = TimerUtils()
    private var startPos: Vec3? = null
    private var placing: PlaceInfo? = null

    // null 表示只有本地预测，非 null 表示已收到服务端方块状态
    private val pendingBlocks = mutableMapOf<BlockPos, BlockState?>()

    @Volatile
    private var blockUpdates: ConcurrentLinkedQueue<BlockUpdate>? = null

    private val surroundDirections = arrayOf(
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    )

    init {
        onEnable {
            clearState()
            runSafe { startPos = player.position() } ?: toggle()
        }

        onDisable {
            clearState()
        }

        safeEventHandler<PacketEvent.Receive>(EventPriority.LOWEST) { event ->
            val updates = blockUpdates ?: return@safeEventHandler
            if (event.isCancelled) return@safeEventHandler
            when (event.packet) {
                is ClientboundBlockUpdatePacket, is ClientboundSectionBlocksUpdatePacket -> {
                    updates.add(BlockUpdate(level, event))
                }
            }
        }

        safeEventHandler<PlayerTickEvent.Pre> {
            if (rotate && rotationMode == RotationMode.Silent) {
                placing?.let { RotationManager.setRotations(it.rotation, rotationSpeed.toDouble()) }
            }
        }

        safeEventHandler<ClientTickEvent.Pre> {
            updatePendingBlocks()

            if (!player.isMoving && !mc.options.keyJump.isDown) startPos = player.position()
            val origin = startPos ?: player.position().also { startPos = it }
            if ((toggleOnMove && player.distanceToSqr(origin) > 1.0) || (toggleOnJump && player.input.keyPresses.jump())) {
                toggle()
                return@safeEventHandler
            }

            if ((pauseOnEat && PlayerUtils.isEating()) || (!inAir && !player.onGround())) {
                placing = null
                return@safeEventHandler
            }
            if (!placeTimer.passedMillise(placeDelay.toLong())) return@safeEventHandler

            val blocks = findBlocks()
            if (!blocks.found()) {
                ChatUtils.sendMessage(EpsilonTranslations.FeetTrap.NO_BLOCK_FOUND.getTranslatedName())
                toggle()
                return@safeEventHandler
            }

            runPlacing(getPlacements(), blocks)
        }
    }

    private fun clearState() {
        startPos = null
        placing = null
        pendingBlocks.clear()
        // 正在入队的回调仍持有旧队列，不会污染下一批放置。
        blockUpdates = null
    }

    context(nn: NonNullContext)
    private fun updatePendingBlocks() {
        val updates = blockUpdates ?: return
        if (pendingBlocks.isEmpty()) {
            blockUpdates = null
            return
        }
        while (true) {
            val update = updates.poll() ?: break
            if (update.level !== nn.level || update.event.isCancelled) continue

            when (val packet = update.event.packet) {
                is ClientboundBlockUpdatePacket -> pendingBlocks.replace(packet.pos, packet.blockState)
                is ClientboundSectionBlocksUpdatePacket -> packet.runUpdates { pos, state ->
                    pendingBlocks.replace(pos, state)
                }
            }
        }
        pendingBlocks.entries.removeIf { (pos, confirmedState) ->
            val state = nn.level.getBlockState(pos)
            // 方块包确认支撑；被拒绝的预测由原版 Ack 回滚后重新进入候选
            state == confirmedState || state.canBeReplaced()
        }
        if (pendingBlocks.isEmpty()) blockUpdates = null
    }

    context(nn: NonNullContext)
    private fun getSurroundPositions(): Set<BlockPos> {
        val origins = setOf(nn.player.blockPosition(), BlockPos.containing(nn.player.x, nn.player.y + 0.8, nn.player.z))
        val queue = ArrayDeque(origins.map { it to 0 })
        val visited = origins.toMutableSet()
        val playerBoxes = if (extend) nn.level.players().map { it.boundingBox } else emptyList()
        val positions = linkedSetOf<BlockPos>()

        while (queue.isNotEmpty()) {
            val (origin, depth) = queue.removeFirst()
            for (direction in surroundDirections) {
                val pos = origin.relative(direction)
                positions.add(pos)
                if (extend && depth < 2 && visited.add(pos) && playerBoxes.any { it.intersects(AABB(pos)) }) {
                    queue.addLast(pos to depth + 1)
                }
            }
        }
        return positions
    }

    context(nn: NonNullContext)
    private fun getPlacements(): MutableList<PlaceInfo> {
        val unsupported = mutableListOf<BlockPos>()
        val placements = linkedMapOf<BlockPos, PlaceInfo>()
        val rotation = RotationManager.getRotation()

        for (pos in getSurroundPositions()) {
            if (!canPlace(pos)) continue
            val placement = findPlacement(pos, rotation)
            if (placement != null) {
                placements[pos] = placement
            } else if (support > 0) {
                unsupported.add(pos)
            }
        }

        for (pos in unsupported) {
            val helper = findSupportPlacement(pos, rotation, placements.keys) ?: continue
            placements.putIfAbsent(helper.pos, helper)
        }
        return placements.values.toMutableList().also { result ->
            if (rotate) result.sortBy { rotationDistanceSq(rotation, it.rotation) }
        }
    }

    context(nn: NonNullContext)
    private fun findSupportPlacement(pos: BlockPos, rotation: Rotation, planned: Set<BlockPos>): PlaceInfo? {
        val visited = mutableSetOf(pos)
        var frontier = listOf(pos)
        val priority = compareBy<PlaceInfo> { it.pos !in planned }
            .thenBy { if (rotate) rotationDistanceSq(rotation, it.rotation) else 0f }

        repeat(support) {
            val next = mutableListOf<BlockPos>()
            var best: PlaceInfo? = null
            for (target in frontier) {
                for (side in Direction.entries) {
                    val helper = target.relative(side)
                    if (helper in pendingBlocks) {
                        if (isClickable(nn.level.getBlockState(helper), helper) && canUseSupportFace(
                                helper,
                                side.opposite
                            )
                        ) {
                            // 已有同样短的路径在等待服务端确认，不再另铺一条辅助路径。
                            return null
                        }
                        continue
                    }
                    if (helper in visited || !canPlace(helper) || !canUseSupportFace(helper, side.opposite)) continue
                    visited.add(helper)
                    val placement = findPlacement(helper, rotation)
                    if (placement == null) {
                        next.add(helper)
                    } else if (best == null || priority.compare(placement, best) < 0) {
                        best = placement
                    }
                }
            }
            if (best != null) return best
            if (next.isEmpty()) return null
            frontier = next
        }
        return null
    }

    context(nn: NonNullContext)
    private fun canUseSupportFace(pos: BlockPos, face: Direction): Boolean {
        val hit = Vec3.atCenterOf(pos).relative(face, 0.5)
        val eye = nn.player.eyePosition
        val range = nn.player.blockInteractionRange()
        if (eye.distanceToSqr(hit) > range * range) return false
        // 辅助块尚未放下，射线不会撞到它；额外排除放下后背向玩家的面。
        val facing = (eye.x - hit.x) * face.stepX + (eye.y - hit.y) * face.stepY + (eye.z - hit.z) * face.stepZ
        return facing > 0.0 && RotationUtils.canSee(pos, face)
    }

    context(nn: NonNullContext)
    private fun canPlace(pos: BlockPos): Boolean = pos !in pendingBlocks &&
            nn.level.isInWorldBounds(pos) && nn.level.worldBorder.isWithinBounds(pos) &&
            nn.player.isWithinBlockInteractionRange(pos, 0.0) && BlockUtils.canPlaceAt(pos)

    context(nn: NonNullContext)
    private fun findPlacement(pos: BlockPos, rotation: Rotation): PlaceInfo? {
        var best: PlaceInfo? = null
        var bestDistance = Float.POSITIVE_INFINITY
        val current = placing?.takeIf { it.pos == pos }
        val eye = nn.player.eyePosition
        val range = nn.player.blockInteractionRange()
        for (side in Direction.entries) {
            val neighbour = pos.relative(side)
            if (neighbour in pendingBlocks) continue
            val state = nn.level.getBlockState(neighbour)
            if (state.canBeReplaced() || !isClickable(state, neighbour)) continue
            val hitVec = Vec3.atCenterOf(neighbour).relative(side.opposite, 0.5)
            if (eye.distanceToSqr(hitVec) > range * range || !RotationUtils.canSee(neighbour, side.opposite)) continue
            val hit = BlockHitResult(hitVec, side.opposite, neighbour, false)
            val info = PlaceInfo(pos, hit, RotationUtils.calculate(hitVec))
            // 等待旋转时保留仍有效的点击面，避免支撑方块变化导致目标来回切换。
            if (!rotate || current != null && current.hit.blockPos == neighbour && current.hit.direction == hit.direction) {
                return info
            }
            val distance = rotationDistanceSq(rotation, info.rotation)
            if (distance < bestDistance) {
                best = info
                bestDistance = distance
            }
        }
        return best
    }

    private fun rotationDistanceSq(from: Rotation, to: Rotation): Float {
        val yaw = Mth.wrapDegrees(to.yaw - from.yaw)
        val pitch = to.pitch - from.pitch
        return yaw * yaw + pitch * pitch
    }

    context(nn: NonNullContext)
    private fun runPlacing(placements: MutableList<PlaceInfo>, blocks: FindItemResult) {
        // 先完成正在等待旋转的目标，其余候选按当前视角的转角排序。
        val currentIndex = placements.indexOfFirst { it.pos == placing?.pos }
        if (currentIndex > 0) placements.add(0, placements.removeAt(currentIndex))
        placing = placements.firstOrNull()

        for (index in 0 until minOf(blocksPerTick, placements.size)) {
            val info = placements[index]
            if (rotate && rotationMode == RotationMode.Silent && !RaytraceUtils.overBlock(
                    RotationManager.getRotation(),
                    info.hit.blockPos,
                    info.hit.direction
                )
            ) break
            if (!placeBlock(info, blocks)) break
            // 达到本 tick 放置上限后，也能在随后的 PlayerTick 中开始转向下一处。
            placing = placements.getOrNull(index + 1)
        }
    }

    context(nn: NonNullContext)
    private fun placeBlock(info: PlaceInfo, blocks: FindItemResult): Boolean {
        val hand = blocks.getHand()
        val swap = !blocks.isOffhand()

        if (swap) {
            if (inventorySwap) InvUtils.invSwap(blocks.slot) else InvUtils.swap(blocks.slot, true)
        }

        // 先发布接收队列，再发送放置包，避免快速回包早于本地预测登记。
        if (blockUpdates == null) blockUpdates = ConcurrentLinkedQueue()

        val success = try {
            val result = if (rotate && rotationMode == RotationMode.Snap) {
                LegacyPlacement.placeBlock(hand, info.hit)
            } else {
                nn.gameMode.useItemOn(nn.player, hand, info.hit)
            }
            SwingMode.of(swingHand).interactSwing(hand)
            result.consumesAction()
        } finally {
            if (swap) {
                if (inventorySwap) InvUtils.invSwapBack() else InvUtils.swapBack()
            }
        }

        if (success && !nn.level.getBlockState(info.pos).canBeReplaced()) pendingBlocks[info.pos] = null
        if (pendingBlocks.isEmpty()) blockUpdates = null
        if (!success) return false
        placeTimer.reset()
        return true
    }

    context(nn: NonNullContext)
    private fun isClickable(state: BlockState, pos: BlockPos): Boolean {
        if (nn.player.isSecondaryUseActive) return true
        val block = state.block
        return state.getMenuProvider(nn.level, pos) == null && block !is EntityBlock &&
                block !is NoteBlock && block !is LeverBlock &&
                !state.`is`(BlockTags.BUTTONS) && !state.`is`(BlockTags.BEDS) &&
                !state.`is`(BlockTags.FENCE_GATES) && !state.`is`(BlockTags.DOORS) && !state.`is`(BlockTags.TRAPDOORS)
    }

    context(nn: NonNullContext)
    private fun findBlocks(): FindItemResult {
        val obsidian = if (inventorySwap) InvUtils.find(Items.OBSIDIAN) else InvUtils.findInHotbar(Items.OBSIDIAN)
        if (obsidian.found() || !useEnderChest) return obsidian
        return if (inventorySwap) InvUtils.find(Items.ENDER_CHEST) else InvUtils.findInHotbar(Items.ENDER_CHEST)
    }

    private class PlaceInfo(val pos: BlockPos, val hit: BlockHitResult, val rotation: Rotation)

    private class BlockUpdate(val level: ClientLevel, val event: PacketEvent.Receive)

}
