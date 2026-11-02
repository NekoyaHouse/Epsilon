package com.github.epsilon.modules.impl.combat.trap

import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.*
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.player.*
import com.github.epsilon.utils.render.animation.Easing
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
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.*
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedQueue

abstract class TrapBase(
    name: String,
    private val noBlockFound: TranslateComponent,
    private val anchorToPlayer: Boolean = true,
) : Module(name, Category.COMBAT) {

    private enum class SwitchMode {
        Silent,
        InvSwitch
    }

    private enum class RotationMode {
        Motion,
        Silent
    }

    private val toggleOnDeath by boolSetting("Toggle On Death", true)
    private val toggleOnMove by boolSetting("Toggle On Move", anchorToPlayer, { anchorToPlayer })
    private val toggleOnJump by boolSetting("Toggle On Jump", anchorToPlayer, { anchorToPlayer })
    private val inAir by boolSetting("In Air", true)
    private val pauseOnEat by boolSetting("Pause On Eat", anchorToPlayer)
    private val switchMode by enumSetting(
        "Switch Mode",
        if (anchorToPlayer) SwitchMode.InvSwitch else SwitchMode.Silent
    )
    private val useEnderChest by boolSetting("Use Ender Chest", true, { anchorToPlayer })
    private val placeDelay by intSetting("Place Delay", if (anchorToPlayer) 50 else 100, 0..500, 50)
    private val support by intSetting("Support", 2, 0..8, 1)
    private val blocksPerTick by intSetting("Blocks Per Tick", 1, 1..8, 1)
    private val rotate by boolSetting("Rotate", anchorToPlayer)
    private val rotationMode by enumSetting("Rotation Mode", RotationMode.Silent, { rotate })
    private val rotationSpeed by intSetting(
        "Rotation Speed",
        180,
        18..180,
        18,
        { breakCrystal || rotate && rotationMode == RotationMode.Motion }
    )
    protected val breakCrystal by boolSetting("Break Crystal", !anchorToPlayer)
    private val breakDelay by intSetting("Break Delay", 100, 1..1000, 1, { breakCrystal })
    private val swingHand by boolSetting("Swing Hand", true)
    private val render by boolSetting("Render", true)
    private val fade by boolSetting("Fade", true, { render })
    private val fadeTime by intSetting("Fade Time", 500, 0..3000, 50, { render && fade })
    private val shrink by boolSetting("Shrink", false, { render })
    private val sideColor by colorSetting("Side Color", Color(255, 183, 197, 100), true, { render })
    private val lineColor by colorSetting("Line Color", Color(255, 105, 180), true, { render })

    private val placeTimer = TimerUtils()
    private val breakTimer = TimerUtils()
    private var startPos: Vec3? = null
    private var placing: PlaceInfo? = null
    private var breakingCrystal: EndCrystal? = null
    private var rotationRequest: Rotation? = null

    // null 表示只有本地预测，非 null 表示已收到服务端方块状态
    private val pendingBlocks = mutableMapOf<BlockPos, BlockState?>()

    @Volatile
    private var blockUpdates: ConcurrentLinkedQueue<BlockUpdate>? = null

    private val renderBoxes: MutableList<RenderInfo> = ArrayList()

    init {
        onEnable {
            clearState()
            renderBoxes.clear()
            runSafe { startPos = player.position() } ?: toggle()
        }

        onDisable {
            clearState()
        }

        handler<LevelUpdateEvent>(alwaysListening = true) {
            clearState()
            renderBoxes.clear()
        }

        handler<RespawnEvent>(alwaysListening = true) {
            clearState()
            renderBoxes.clear()
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
            val target = breakingCrystal
            val pos = placing?.pos
            if (breakCrystal && target != null && pos != null && canAttackCrystal(target) &&
                target.boundingBox.intersects(AABB(pos))
            ) {
                requestRotation(RotationUtils.calculate(target.boundingBox.center))
            } else if (rotate && rotationMode == RotationMode.Motion) {
                breakingCrystal = null
                val info = placing
                if (info != null) requestRotation(info.rotation) else clearRotation()
            } else {
                breakingCrystal = null
                clearRotation()
            }
        }

        safeEventHandler<ClientTickEvent.Pre> {
            updatePendingBlocks()
            if (toggleOnDeath && !player.isAlive) {
                toggle()
                return@safeEventHandler
            }

            if (anchorToPlayer) {
                if (!player.isMoving && !mc.options.keyJump.isDown) startPos = player.position()
                val origin = startPos ?: player.position().also { startPos = it }
                if ((toggleOnMove && player.distanceToSqr(origin) > 1.0) || (toggleOnJump && player.input.keyPresses.jump())) {
                    toggle()
                    return@safeEventHandler
                }
            }

            if (player.isSpectator || (pauseOnEat && PlayerUtils.isEating()) || (!inAir && !player.onGround()) ||
                (switchMode == SwitchMode.InvSwitch && player.containerMenu !== player.inventoryMenu)
            ) {
                clearPlacement()
                return@safeEventHandler
            }
            val positions = getTargetPositions()
            if (positions.isEmpty()) {
                clearPlacement()
                return@safeEventHandler
            }
            if (!placeTimer.passedMillise(placeDelay.toLong())) return@safeEventHandler

            val blocks = findBlocks()
            if (!blocks.found()) {
                ChatUtils.sendMessage(noBlockFound.getTranslatedName())
                toggle()
                return@safeEventHandler
            }

            runPlacing(getPlacements(positions), blocks)
        }

        handler<Render3DEvent>(alwaysListening = true) {
            if (!render || mc.level == null) {
                renderBoxes.clear()
                return@handler
            }

            if (renderBoxes.isEmpty()) return@handler

            val time = System.currentTimeMillis()
            val fadeMillis = fadeTime.toLong().coerceAtLeast(1L)

            renderBoxes.removeAll { box -> time - box.startTime >= fadeMillis }

            for (box in renderBoxes) {
                val progress = ((time - box.startTime).toFloat() / fadeMillis).coerceIn(0.0f, 1.0f)

                var scale = 1.0
                if (box.shrink) {
                    scale = 1.0 - Easing.EASE_IN_OUT_EXPO.function.apply(progress)
                    if (scale < 0) scale = 0.0
                }

                val alphaFactor = if (box.fade) (1.0f - progress).coerceIn(0.0f, 1.0f) else 1.0f

                val boxSideColor = box.sideColor
                val boxLineColor = box.lineColor

                val side = Color(
                    boxSideColor.red, boxSideColor.green, boxSideColor.blue,
                    (boxSideColor.alpha * alphaFactor).toInt()
                )
                val line = Color(
                    boxLineColor.red, boxLineColor.green, boxLineColor.blue,
                    (boxLineColor.alpha * alphaFactor).toInt()
                )

                var renderBox = box.aabb
                if (box.shrink) {
                    renderBox = AABB.ofSize(
                        renderBox.center,
                        renderBox.xsize * scale,
                        renderBox.ysize * scale,
                        renderBox.zsize * scale
                    )
                }

                Render3DScheduler.addFilledBox(renderBox, side)
                Render3DScheduler.addOutlineBox(renderBox, line)
            }
        }
    }

    private fun clearState() {
        placeTimer.setMs(placeDelay.toLong())
        breakTimer.reset()
        startPos = null
        clearPlacement()
        pendingBlocks.clear()
        // 正在入队的回调仍持有旧队列，不会污染下一批放置。
        blockUpdates = null
    }

    protected fun clearPlacement() {
        placing = null
        breakingCrystal = null
        clearRotation()
    }

    private fun requestRotation(rotation: Rotation) {
        rotationRequest = rotation
        RotationManager.setRotations(rotation, rotationSpeed.toDouble())
    }

    private fun clearRotation() {
        rotationRequest?.let { RotationManager.clearRotations(it) }
        rotationRequest = null
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
    protected abstract fun getTargetPositions(): Set<BlockPos>

    context(nn: NonNullContext)
    protected open fun getPlaceRange(): Double = nn.player.blockInteractionRange()

    context(nn: NonNullContext)
    protected open fun getBreakRange(): Double = nn.player.entityInteractionRange()

    protected open fun getBlockItems(): List<Item> =
        if (useEnderChest) listOf(Items.OBSIDIAN, Items.ENDER_CHEST) else listOf(Items.OBSIDIAN)

    context(nn: NonNullContext)
    private fun getPlacements(positions: Set<BlockPos>): MutableList<PlaceInfo> {
        val unsupported = mutableListOf<BlockPos>()
        val placements = linkedMapOf<BlockPos, PlaceInfo>()
        val rotation = RotationManager.getRotation()

        for (pos in positions) {
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
        val range = getPlaceRange()
        if (eye.distanceToSqr(hit) > range * range) return false
        // 辅助块尚未放下，射线不会撞到它；额外排除放下后背向玩家的面。
        val facing = (eye.x - hit.x) * face.stepX + (eye.y - hit.y) * face.stepY + (eye.z - hit.z) * face.stepZ
        return facing > 0.0 && RotationUtils.canSee(pos, face)
    }

    context(nn: NonNullContext)
    private fun canPlace(pos: BlockPos): Boolean {
        val range = getPlaceRange()
        return pos !in pendingBlocks && nn.level.isInWorldBounds(pos) && nn.level.worldBorder.isWithinBounds(pos) &&
                AABB(pos).distanceToSqr(nn.player.eyePosition) < range * range && BlockUtils.canPlaceAt(
            pos,
            breakCrystal
        )
    }

    context(nn: NonNullContext)
    private fun findPlacement(pos: BlockPos, rotation: Rotation): PlaceInfo? {
        var best: PlaceInfo? = null
        var bestDistance = Float.POSITIVE_INFINITY
        val current = placing?.takeIf { it.pos == pos }
        val eye = nn.player.eyePosition
        val range = getPlaceRange()
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

        var placed = 0
        for ((index, info) in placements.withIndex()) {
            placing = info
            if (breakCrystal && clearBlockingCrystal(info.pos)) {
                // 水晶旋转与方块旋转互斥，等待命中期间保持当前目标。
                if (breakingCrystal != null) return
                placing = placements.getOrNull(index + 1)
                continue
            }
            breakingCrystal = null
            if (!BlockUtils.canPlaceAt(info.pos)) {
                placing = placements.getOrNull(index + 1)
                continue
            }
            if (rotate && rotationMode == RotationMode.Motion && !isFacingPlacement(info)) break
            if (!placeBlock(info, blocks)) break
            // 达到本 tick 放置上限后，也能在随后的 PlayerTick 中开始转向下一处。
            placing = placements.getOrNull(index + 1)
            if (++placed >= blocksPerTick) break
        }
        breakingCrystal = null
    }

    context(nn: NonNullContext)
    private fun isFacingPlacement(info: PlaceInfo): Boolean {
        val rotation = RotationManager.getRotation()
        val eye = nn.player.eyePosition
        val end = eye.add(Vec3.directionFromRotation(rotation.pitch, rotation.yaw).scale(getPlaceRange()))
        val hit = nn.level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, nn.player))
        return hit.type == HitResult.Type.BLOCK && hit.blockPos == info.hit.blockPos && hit.direction == info.hit.direction
    }

    context(nn: NonNullContext)
    private fun clearBlockingCrystal(pos: BlockPos): Boolean {
        val crystals = nn.level.getEntitiesOfClass(EndCrystal::class.java, AABB(pos)) { it.isAlive }
        val previous = breakingCrystal
        breakingCrystal = null
        if (crystals.isEmpty()) return false

        if (breakTimer.passedMillise(breakDelay.toLong())) {
            val target = previous?.takeIf { it in crystals && canAttackCrystal(it) }
                ?: crystals.filter { canAttackCrystal(it) }.minByOrNull { nn.player.distanceToSqr(it) }
            if (target != null) {
                breakingCrystal = target
                val hit = RaytraceUtils.raytrace(RotationManager.getRotation(), getBreakRange())
                if (hit !is EntityHitResult || hit.entity !== target) return true
                nn.gameMode.attack(nn.player, target)
                SwingMode.of(swingHand).attackSwing(InteractionHand.MAIN_HAND)
                breakTimer.reset()
            }
        }
        // 攻击不代表服务端已移除实体；本轮跳过该位置，避免重复放置预测。
        return true
    }

    context(nn: NonNullContext)
    private fun canAttackCrystal(target: EndCrystal): Boolean {
        val range = getBreakRange()
        return target.isAlive && nn.level.getEntity(target.id) === target &&
                nn.level.worldBorder.isWithinBounds(target.blockPosition()) &&
                target.boundingBox.distanceToSqr(nn.player.eyePosition) < range * range &&
                nn.player.isWithinAttackRange(nn.player.mainHandItem, target.boundingBox, 0.0)
    }

    context(nn: NonNullContext)
    private fun placeBlock(info: PlaceInfo, blocks: FindItemResult): Boolean {
        val hand = blocks.getHand()
        val swap = !blocks.isOffhand()
        val selectedSlot = nn.player.inventory.selectedSlot
        val containerSlot = if (blocks.slot < 9) blocks.slot + InventoryMenu.USE_ROW_SLOT_START else blocks.slot

        if (swap) when (switchMode) {
            SwitchMode.Silent -> nn.player.inventory.selectedSlot = blocks.slot
            SwitchMode.InvSwitch -> ClickSlotUtils.swap(containerSlot, selectedSlot)
        }

        // 先发布接收队列，再发送放置包，避免快速回包早于本地预测登记。
        if (blockUpdates == null) blockUpdates = ConcurrentLinkedQueue()

        val result = try {
            val result = if (rotate && rotationMode == RotationMode.Silent) {
                LegacyPlacement.placeBlock(hand, info.hit)
            } else {
                nn.gameMode.useItemOn(nn.player, hand, info.hit)
            }
            if (result.consumesAction()) SwingMode.of(swingHand).interactSwing(hand)
            result
        } finally {
            if (swap) when (switchMode) {
                SwitchMode.Silent -> {
                    nn.player.inventory.selectedSlot = selectedSlot
                    nn.gameMode.ensureHasSentCarriedItem()
                }

                SwitchMode.InvSwitch -> ClickSlotUtils.swap(containerSlot, selectedSlot)
            }
        }

        if (result.consumesAction() && !nn.level.getBlockState(info.pos).canBeReplaced()) pendingBlocks[info.pos] = null
        if (pendingBlocks.isEmpty()) blockUpdates = null
        if (!result.consumesAction()) return false
        if (render) {
            renderBoxes.add(RenderInfo(AABB(info.pos), lineColor, sideColor, System.currentTimeMillis(), fade, shrink))
        }
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
        for (item in getBlockItems()) {
            if (item === Items.AIR) continue
            val result = when (switchMode) {
                SwitchMode.Silent -> InvUtils.findInHotbar(item)
                SwitchMode.InvSwitch -> InvUtils.find(item)
            }
            if (result.found()) return result
        }
        return FindItemResult(-1, 0, 0)
    }

    private class PlaceInfo(val pos: BlockPos, val hit: BlockHitResult, val rotation: Rotation)

    private class BlockUpdate(val level: ClientLevel, val event: PacketEvent.Receive)

    private data class RenderInfo(
        val aabb: AABB,
        val lineColor: Color,
        val sideColor: Color,
        val startTime: Long,
        val fade: Boolean,
        val shrink: Boolean,
    )

}
