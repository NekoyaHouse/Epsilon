package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.GameLeftEvent
import com.github.epsilon.events.impl.LevelUpdateEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.RespawnEvent
import com.github.epsilon.kernel.jvm.*
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
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object FeetTrap : Module("Feet Trap", Category.COMBAT) {

    private enum class RotationMode {
        Silent,
        Snap
    }

    private val toggleOnMove by boolSetting("Toggle On Move", true)
    private val toggleOnJump by boolSetting("Toggle On Jump", true)
    private val inAir by boolSetting("In Air", true)
    private val pauseOnEat by boolSetting("Pause On Eat", true)
    private val placeDelay by intSetting("Place Delay", 50, 0..500, 50)
    private val extend by boolSetting("Extend", true)
    private val blocksPerTick by intSetting("Blocks Per Tick", 1, 1..8, 1)
    private val rotate by boolSetting("Rotate", true)
    private val rotationMode by enumSetting("Rotate Mode", RotationMode.Snap)
    private val rotationSpeed by intSetting("Rotation Speed", 180, 18..180, 18, { rotate })
    private val enderChest by boolSetting("Ender Chest", true)
    private val inventorySwap by boolSetting("Inventory Swap", true)

    private var startX = 0.0
    private var startY = 0.0
    private var startZ = 0.0
    private var rotation: Rotation? = null
    private var pendingPlacement: PlaceData? = null

    private val timer = TimerUtils()

    private val DIRECTIONS = arrayOf(
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    )

    init {
        onEnable {
            resetPlacementState()
            runSafe {
                startX = player.x
                startY = player.y
                startZ = player.z
            } ?: toggle()
        }

        onDisable {
            resetPlacementState()
        }

        safeEventHandler<PlayerTickEvent.Pre> {
            val currentRotation = rotation
            if (currentRotation != null && rotate) {
                RotationManager.setRotations(currentRotation, rotationSpeed.toDouble())
            }
        }

        safeEventHandler<ClientTickEvent.Pre> {
            if (!rotate) resetPlacementState()

            if (pauseOnEat && PlayerUtils.isEating() || !inAir && !player.onGround()) {
                resetPlacementState()
                return@safeEventHandler
            }

            if (!timer.passedMillise(placeDelay.toLong())) return@safeEventHandler

            if (!player.isMoving && !mc.options.keyJump.isDown) {
                startX = player.x
                startY = player.y
                startZ = player.z
            }

            val result = findBlocks()
            if (!result.found()) {
                ChatUtils.sendMessage("[FeetTrap] No block found")
                toggle()
                return@safeEventHandler
            }

            if (toggleOnMove && player.distanceToSqr(startX, startY, startZ) > 1.0 ||
                toggleOnJump && player.input.keyPresses.jump()
            ) {
                toggle()
                return@safeEventHandler
            }

            placeSurround(result.slot)
        }
    }

    private fun resetPlacementState() {
        rotation = null
        pendingPlacement = null
    }

    context(nn: NonNullContext)
    private fun placeSurround(slot: Int) {
        val positions = linkedSetOf<BlockPos>()
        collectSurround(BlockPos.containing(nn.player.x, nn.player.y, nn.player.z), positions)
        collectSurround(BlockPos.containing(nn.player.x, nn.player.y + 0.8, nn.player.z), positions)
        val attempted = mutableSetOf<BlockPos>()

        repeat(blocksPerTick) {
            // 每次放置后重新检查支撑面，让后续位置能使用刚预测放下的方块。
            val targets = getPlacementTargets(positions, attempted)
            val currentRotation = RotationManager.getRotation()
            val pending = pendingPlacement
            // 等待对准期间固定位置和支撑面，避免新候选或角度微变导致换点。
            val target = targets.firstOrNull { pending != null && it.pos == pending.pos && it.side == pending.side }
                ?: targets.minWithOrNull(
                    compareBy<PlaceData> { rotationDistance(currentRotation, it.rotation) }
                        .thenBy { nn.player.eyePosition.distanceToSqr(hitVec(it.pos, it.side)) }
                )

            if (target == null) {
                pendingPlacement = null
                // 本轮已经发出交互时保留朝向，下一轮仍无目标再回正。
                if (attempted.isEmpty()) rotation = null
                return
            }

            pendingPlacement = target
            if (rotate) {
                rotation = target.rotation
                if (!RaytraceUtils.overBlock(currentRotation, target.pos.relative(target.side))) return
            }

            tryPlaceBlock(target, slot)
            attempted.add(target.pos)
            pendingPlacement = null
        }
    }

    private fun rotationDistance(from: Rotation, to: Rotation): Float {
        val yaw = Mth.wrapDegrees(to.yaw - from.yaw)
        val pitch = to.pitch - from.pitch
        return yaw * yaw + pitch * pitch
    }

    context(nn: NonNullContext)
    private fun collectSurround(pos: BlockPos, positions: MutableSet<BlockPos>) {
        for (i in DIRECTIONS) {
            val offsetPos = pos.relative(i)
            positions.add(offsetPos)
            if ((selfIntersectPos(offsetPos) || otherIntersectPos(offsetPos)) && extend) {
                for (i2 in DIRECTIONS) {
                    val offsetPos2 = offsetPos.relative(i2)
                    if (selfIntersectPos(offsetPos2) || otherIntersectPos(offsetPos2)) {
                        for (i3 in DIRECTIONS) {
                            positions.add(offsetPos2)
                            val offsetPos3 = offsetPos2.relative(i3)
                            positions.add(offsetPos3)
                        }
                    }
                    positions.add(offsetPos2)
                }
            }
        }
    }

    context(nn: NonNullContext)
    private fun getPlacementTargets(positions: Set<BlockPos>, attempted: Set<BlockPos>): List<PlaceData> {
        val placementPositions = linkedSetOf<BlockPos>()
        for (pos in positions) {
            if (!BlockUtils.canPlaceAt(pos)) continue
            if (getPlaceSides(pos).isNotEmpty()) {
                placementPositions.add(pos)
            } else {
                for (side in Direction.entries) {
                    val helper = pos.relative(side)
                    if (RotationUtils.canSee(helper, side.opposite)) placementPositions.add(helper)
                }
            }
        }

        return buildList {
            for (pos in placementPositions) {
                if (pos in attempted || !BlockUtils.canPlaceAt(pos)) continue
                for (side in getPlaceSides(pos)) {
                    add(PlaceData(pos, side, RotationUtils.calculate(hitVec(pos, side))))
                }
            }
        }
    }

    context(nn: NonNullContext)
    private fun getPlaceSides(pos: BlockPos): List<Direction> {
        return Direction.entries.filter { i ->
            val neighbourPos = pos.relative(i)
            val neighbourState = nn.level.getBlockState(neighbourPos)
            !neighbourState.canBeReplaced() && isClickable(neighbourState, neighbourPos) && RotationUtils.canSee(neighbourPos, i.opposite)
        }
    }

    context(nn: NonNullContext)
    private fun isClickable(state: BlockState, pos: BlockPos): Boolean {
        return nn.player.isSecondaryUseActive || !(
                state.getMenuProvider(nn.level, pos) != null ||
                        state.block is EntityBlock ||
                        state.`is`(BlockTags.BUTTONS) ||
                        state.`is`(BlockTags.PRESSURE_PLATES) ||
                        state.`is`(BlockTags.BEDS) ||
                        state.`is`(BlockTags.FENCE_GATES) ||
                        state.`is`(BlockTags.DOORS) ||
                        state.`is`(BlockTags.TRAPDOORS) ||
                        state.block is NoteBlock
                )
    }

    context(nn: NonNullContext)
    private fun tryPlaceBlock(target: PlaceData, slot: Int) {
        if (inventorySwap) InvUtils.invSwap(slot) else InvUtils.swap(slot, true)
            placeBlock(target.pos.relative(target.side), target.side.opposite, InteractionHand.MAIN_HAND)
            timer.reset()
            if (inventorySwap) InvUtils.invSwapBack() else InvUtils.swapBack()
    }

    context(nn: NonNullContext)
    private fun placeBlock(pos: BlockPos, side: Direction, hand: InteractionHand) {
        val result = BlockHitResult(hitVec(pos, side), side, pos, false)
        nn.gameMode.useItemOn(nn.player, hand, result)
    }

    private fun hitVec(pos: BlockPos, side: Direction): Vec3 {
        return Vec3.atCenterOf(pos).relative(side, 0.5)
    }

    context(nn: NonNullContext)
    private fun selfIntersectPos(pos: BlockPos): Boolean {
        return nn.player.boundingBox.intersects(AABB(pos))
    }

    context(nn: NonNullContext)
    private fun otherIntersectPos(pos: BlockPos): Boolean {
        for (otherPlayer in nn.level.players()) {
            if (otherPlayer.boundingBox.intersects(AABB(pos))) {
                return true
            }
        }
        return false
    }

    context(nn: NonNullContext)
    private fun findBlocks(): FindItemResult {
        if (inventorySwap) {
            val result = InvUtils.find(Items.OBSIDIAN)
            if (result.found() || !enderChest) {
                return result
            }
            return InvUtils.find(Items.ENDER_CHEST)
        } else {
            val result = InvUtils.findInHotbar(Items.OBSIDIAN)
            if (result.found() || !enderChest) {
                return result
            }
            return InvUtils.findInHotbar(Items.ENDER_CHEST)
        }
    }

    private data class PlaceData(val pos: BlockPos, val side: Direction, val rotation: Rotation)

}
