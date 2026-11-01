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
import com.github.epsilon.utils.player.ChatUtils
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.RaytraceUtils
import com.github.epsilon.utils.rotation.Rotation
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.timer.TimerUtils
import com.github.epsilon.utils.world.BlockUtils
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
    private val toggleOnMove by boolSetting("Toggle On Move", true)
    private val toggleOnJump by boolSetting("Toggle On Jump", true)
    private val inAir by boolSetting("In Air", true)
    private val pauseOnEat by boolSetting("Pause On Eat", true)
    private val placeDelay by intSetting("Place Delay", 50, 0..500, 50)
    private val extend by boolSetting("Extend", true)
    private val blocksPerTick by intSetting("Blocks Per Tick", 1, 1..8, 1)
    private val rotate by boolSetting("Rotate", true)
    private val rotationSpeed by intSetting("Rotation Speed", 180, 18..180, 18, { rotate })
    private val enderChest by boolSetting("Ender Chest", true)
    private val inventorySwap by boolSetting("Inventory Swap", true)

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
            if (rotate) placing?.let { RotationManager.setRotations(it.rotation, rotationSpeed.toDouble()) }
        }

        safeEventHandler<ClientTickEvent.Pre> {
            updatePendingBlocks()

            if (!player.isMoving && !mc.options.keyJump.isDown) startPos = player.position()
            val origin = startPos ?: player.position().also { startPos = it }
            if (toggleOnMove && player.distanceToSqr(origin) > 1.0 || toggleOnJump && player.input.keyPresses.jump()) {
                toggle()
                return@safeEventHandler
            }

            if (pauseOnEat && PlayerUtils.isEating() || !inAir && !player.onGround()) {
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
        val playerBoxes = nn.level.players().map { it.boundingBox }
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
    private fun getPlacements(): List<PlaceInfo> {
        val positions = getSurroundPositions()
        val helpers = linkedSetOf<BlockPos>()
        val placements = mutableListOf<PlaceInfo>()
        val rotation = RotationManager.getRotation()

        for (pos in positions) {
            if (!canPlace(pos)) continue
            val neighbour = getNeighbour(pos, rotation)
            if (neighbour != null) {
                placements.add(neighbour)
            } else {
                for (side in Direction.entries) {
                    val helper = pos.relative(side)
                    if (RotationUtils.canSee(helper, side.opposite)) helpers.add(helper)
                }
            }
        }

        for (pos in helpers - positions) {
            if (canPlace(pos)) getNeighbour(pos, rotation)?.let(placements::add)
        }
        return if (rotate) placements.sortedBy { rotationDistanceSq(rotation, it.rotation) } else placements
    }

    context(nn: NonNullContext)
    private fun canPlace(pos: BlockPos): Boolean = pos !in pendingBlocks && BlockUtils.canPlaceAt(pos)

    context(nn: NonNullContext)
    private fun getNeighbour(pos: BlockPos, rotation: Rotation): PlaceInfo? {
        var best: PlaceInfo? = null
        var bestDistance = Float.POSITIVE_INFINITY
        val current = placing?.takeIf { it.pos == pos }
        for (side in Direction.entries) {
            val neighbour = pos.relative(side)
            if (neighbour in pendingBlocks) continue
            val state = nn.level.getBlockState(neighbour)
            if (state.canBeReplaced() || !isClickable(state, neighbour)) continue
            if (!RotationUtils.canSee(neighbour, side.opposite)) continue

            val hitVec = Vec3.atCenterOf(neighbour).relative(side.opposite, 0.5)
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
    private fun runPlacing(placements: List<PlaceInfo>, blocks: FindItemResult) {
        if (placements.isEmpty()) {
            placing = null
            return
        }

        // 先完成正在等待旋转的目标，其余候选按当前视角的转角排序。
        val current = placements.firstOrNull { it.pos == placing?.pos }
        val ordered = if (current == null) placements else listOf(current) + (placements - current)
        for ((index, info) in ordered.take(blocksPerTick).withIndex()) {
            placing = info
            if (rotate && !RaytraceUtils.overBlock(
                    RotationManager.getRotation(),
                    info.hit.blockPos,
                    info.hit.direction
                )
            ) break
            if (!placeBlock(info, blocks)) break
            // 达到本 tick 放置上限后，也能在随后的 PlayerTick 中开始转向下一处。
            placing = ordered.getOrNull(index + 1)
        }
    }

    context(nn: NonNullContext)
    private fun placeBlock(info: PlaceInfo, blocks: FindItemResult): Boolean {
        val hand = blocks.getHand()

        if (!blocks.isOffhand()) {
            if (inventorySwap) InvUtils.invSwap(blocks.slot) else InvUtils.swap(blocks.slot, true)
        }

        // 先发布接收队列，再发送放置包，避免快速回包早于本地预测登记。
        if (blockUpdates == null) blockUpdates = ConcurrentLinkedQueue()
        val result = nn.gameMode.useItemOn(nn.player, hand, info.hit)

        if (!blocks.isOffhand()) {
            if (inventorySwap) InvUtils.invSwapBack() else InvUtils.swapBack()
        }

        if (result.consumesAction() && !nn.level.getBlockState(info.pos).canBeReplaced()) pendingBlocks[info.pos] = null
        if (pendingBlocks.isEmpty()) blockUpdates = null
        if (!result.consumesAction()) return false
        placeTimer.reset()
        return true
    }

    context(nn: NonNullContext)
    private fun isClickable(state: BlockState, pos: BlockPos): Boolean {
        if (nn.player.isSecondaryUseActive) return true
        return state.getMenuProvider(nn.level, pos) == null && state.block !is EntityBlock &&
                state.block !is NoteBlock && state.block !is LeverBlock &&
                !state.`is`(BlockTags.BUTTONS) && !state.`is`(BlockTags.BEDS) &&
                !state.`is`(BlockTags.FENCE_GATES) && !state.`is`(BlockTags.DOORS) && !state.`is`(BlockTags.TRAPDOORS)
    }

    context(nn: NonNullContext)
    private fun findBlocks(): FindItemResult {
        val obsidian = if (inventorySwap) InvUtils.find(Items.OBSIDIAN) else InvUtils.findInHotbar(Items.OBSIDIAN)
        if (obsidian.found() || !enderChest) return obsidian
        return if (inventorySwap) InvUtils.find(Items.ENDER_CHEST) else InvUtils.findInHotbar(Items.ENDER_CHEST)
    }

    private class PlaceInfo(val pos: BlockPos, val hit: BlockHitResult, val rotation: Rotation)

    private class BlockUpdate(val level: ClientLevel, val event: PacketEvent.Receive)

}
