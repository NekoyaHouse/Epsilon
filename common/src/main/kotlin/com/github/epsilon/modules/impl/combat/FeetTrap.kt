package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.PlayerTickEvent
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
import com.github.epsilon.utils.safety.NonNullContext
import com.github.epsilon.utils.safety.interaction
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.player
import com.github.epsilon.utils.timer.TimerUtils
import com.github.epsilon.utils.world.BlockUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.NoteBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object FeetTrap : Module("Feet Trap", Category.COMBAT) {

    private val toggleOnMove by boolSetting("Toggle On Move", true)
    private val toggleOnJump by boolSetting("Toggle On Jump", true)
    private val inAir by boolSetting("In Air", true)
    private val pauseOnEat by boolSetting("Pause On Eat", true)
    private val placeDelay by intSetting("Place Delay", 50, 0..500, 50)
    private val extend by boolSetting("Extend", true)
    private val blocksPerTick by intSetting("Blocks Per Tick", 1, 1..8, 1)
    private val rotate by boolSetting("Rotate", true)
    private val rotationSpeed by intSetting(
        "Rotation Speed", 180, 18..180, 18, { rotate })
    private val enderChest by boolSetting("Ender Chest", true)
    private val inventorySwap by boolSetting("Inventory Swap", true)

    private var startX = 0.0
    private var startY = 0.0
    private var startZ = 0.0
    private var progress = 0
    private var rotation: Rotation? = null

    private val timer = TimerUtils()

    private val DIRECTIONS = arrayOf(
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    )

    init {
        nonNullHandler<PlayerTickEvent.Pre> {
            val currentRotation = rotation
            if (currentRotation != null && rotate) {
                RotationManager.setRotations(currentRotation, rotationSpeed.toDouble())
            }
        }

        nonNullHandler<ClientTickEvent.Pre> {
            if (!timer.passedMillise(placeDelay.toLong())) return@nonNullHandler

            progress = 0

            if (!player.isMoving && !mc.options.keyJump.isDown) {
                startX = player.x
                startY = player.y
                startZ = player.z
            }

            val result = findBlocks()
            if (!result.found()) {
                ChatUtils.addChatMessage("[FeetTrap] No block found")
                toggle()
                return@nonNullHandler
            }

            if (toggleOnMove && player.distanceToSqr(startX, startY, startZ) > 1.0 ||
                toggleOnJump && player.input.keyPresses.jump()
            ) {
                toggle()
                return@nonNullHandler
            }

            if (pauseOnEat && PlayerUtils.isEating()) {
                return@nonNullHandler
            }

            if (!inAir && !player.onGround()) {
                return@nonNullHandler
            }

            doSurround(BlockPos.containing(player.x, player.y, player.z), result.slot)
            doSurround(BlockPos.containing(player.x, player.y + 0.8, player.z), result.slot)
        }
    }

    private fun contextVisibility(): com.github.epsilon.settings.Setting.Visibility =
        { rotate }

    override fun onEnable() {
        val nn = NonNullContext.current
        if (nn == null) {
            toggle()
            return
        }
        context(nn) {
            startX = player.x
            startY = player.y
            startZ = player.z
        }
    }

    context(nn: NonNullContext)
    private fun doSurround(pos: BlockPos, slot: Int) {
        for (i in DIRECTIONS) {
            val offsetPos = pos.relative(i)
            tryPlaceBlockOrHelper(offsetPos, slot)
            if ((selfIntersectPos(offsetPos) || otherIntersectPos(offsetPos)) && extend) {
                for (i2 in DIRECTIONS) {
                    val offsetPos2 = offsetPos.relative(i2)
                    if (selfIntersectPos(offsetPos2) || otherIntersectPos(offsetPos2)) {
                        for (i3 in DIRECTIONS) {
                            tryPlaceBlock(offsetPos2, slot)
                            val offsetPos3 = offsetPos2.relative(i3)
                            tryPlaceBlockOrHelper(offsetPos3, slot)
                        }
                    }
                    tryPlaceBlockOrHelper(offsetPos2, slot)
                }
            }
        }
    }

    context(nn: NonNullContext)
    private fun tryPlaceBlockOrHelper(pos: BlockPos, slot: Int) {
        if (getPlaceSide(pos) != null || !level.getBlockState(pos).canBeReplaced()) {
            tryPlaceBlock(pos, slot)
        } else {
            val helper = getHelperPos(pos) ?: return
            tryPlaceBlock(helper, slot)
        }
    }

    context(nn: NonNullContext)
    private fun getPlaceSide(pos: BlockPos): Direction? {
        var minDistance = Double.MAX_VALUE
        var side: Direction? = null
        for (i in Direction.values()) {
            val neighbourPos = pos.relative(i)
            val neighbourState = level.getBlockState(neighbourPos)
            if (neighbourState.canBeReplaced() || !isClickable(neighbourState, neighbourPos)) continue
            if (!RotationUtils.canSee(neighbourPos, i.opposite)) continue
            val vecDis = player.eyePosition.distanceToSqr(hitVec(pos, i))
            if (vecDis > minDistance) {
                continue
            }
            side = i
            minDistance = vecDis
        }
        return side
    }

    context(nn: NonNullContext)
    private fun isClickable(state: BlockState, pos: BlockPos): Boolean {
        return player.isSecondaryUseActive || !(
                state.getMenuProvider(level, pos) != null ||
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
    private fun tryPlaceBlock(pos: BlockPos, slot: Int) {
        if (!(progress < blocksPerTick)) return
        val side = getPlaceSide(pos) ?: return
        if (!BlockUtils.canPlaceAt(pos)) return

        if (rotate) {
            rotation = RotationUtils.calculate(hitVec(pos, side))
            if (RaytraceUtils.overBlock(RotationManager.getRotation(), pos.relative(side))) {
                // 在这个 tick 转头已经到达，无需继续设置转头
                rotation = null
            } else {
                // 没到，给劳资转！
                return
            }
        }

        if (inventorySwap) InvUtils.invSwap(slot) else InvUtils.swap(slot, true)

        placeBlock(pos.relative(side), side.opposite, InteractionHand.MAIN_HAND)

        timer.reset()

        if (inventorySwap) InvUtils.invSwapBack() else InvUtils.swapBack()

        progress++
    }

    context(nn: NonNullContext)
    private fun placeBlock(pos: BlockPos, side: Direction, hand: InteractionHand) {
        val result = BlockHitResult(hitVec(pos, side), side, pos, false)
        interaction.useItemOn(player, hand, result)
    }

    private fun hitVec(pos: BlockPos, side: Direction): Vec3 {
        return Vec3.atCenterOf(pos).relative(side, 0.5)
    }

    context(nn: NonNullContext)
    private fun getHelperPos(pos: BlockPos): BlockPos? {
        for (i in Direction.values()) {
            if (!RotationUtils.canSee(pos.relative(i), i.opposite)) continue
            if (BlockUtils.canPlaceAt(pos.relative(i))) return pos.relative(i)
        }
        return null
    }

    context(nn: NonNullContext)
    private fun selfIntersectPos(pos: BlockPos): Boolean {
        return player.boundingBox.intersects(AABB(pos))
    }

    context(nn: NonNullContext)
    private fun otherIntersectPos(pos: BlockPos): Boolean {
        for (otherPlayer in level.players()) {
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

}
