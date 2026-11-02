package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.BlockCollisionEvent
import com.github.epsilon.events.impl.DestroyedBlockEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.movement.Phase.SwapMode.InvSwitch
import com.github.epsilon.modules.impl.movement.Phase.SwapMode.Silent
import com.github.epsilon.modules.impl.player.AutoTool
import com.github.epsilon.utils.player.*
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.Rotation
import com.github.epsilon.utils.rotation.RotationUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import kotlin.math.abs

object Phase : Module("Phase", Category.MOVEMENT) {

    private enum class Mode {
        Vanilla,
        Pearl,
        Sunrise,
        ForceMine,
        CCClip,
    }

    private enum class SwapMode {
        Silent,
        InvSwitch,
    }

    private val mode by enumSetting("Mode", Mode.Vanilla)
    private val swapMode by enumSetting("Swap Mode", SwapMode.InvSwitch, { mode == Mode.Pearl })
    private val pauseOnEat by boolSetting("Pause On Eat", true, { mode == Mode.Pearl })
    private val pauseOnPhase by boolSetting("Pause On Phase", false, { mode == Mode.Pearl })
    private val swingHand by boolSetting("Swing Hand", true, { mode == Mode.Pearl })
    private val silent by boolSetting("Silent", false, { mode == Mode.Sunrise })
    private val waitBreak by boolSetting("Wait Break", true, { mode == Mode.Sunrise })
    private val onlyOnGround by boolSetting("Only On Ground", false, { mode == Mode.Pearl })
    private val autoDisable by boolSetting("Auto Disable", false, { mode == Mode.Pearl })
    private val afterBreak by intSetting(
        "Break Timeout",
        4,
        1..20,
        1, { mode == Mode.Sunrise && waitBreak }
    )
    private val afterPearl by intSetting("Pearl Timeout", 0, 0..60, 1, { mode == Mode.Pearl })
    private val pitch by doubleSetting("Pitch", 80.0, 0.0..90.0, 1.0, { mode == Mode.Pearl })
    private val strict by boolSetting("Strict", false, { mode == Mode.ForceMine })

    var clipTimer = 0
    var afterPearlTime = 0

    init {
        onEnable {
            clipTimer = 0
            afterPearlTime = 0

            runSafe {
                if (player.onGround() && mode == Mode.CCClip) {
                    val diagonalOffset = MoveUtils.forwardWithoutStrafe(0.44)
                    val yaw = Mth.positiveModulo(RotationManager.getYaw(), 90.0f)
                    val diagonal = yaw > 35 && yaw < 55

                    if (!player.wasSprinting) {
                        connection.send(
                            ServerboundPlayerCommandPacket(
                                player,
                                ServerboundPlayerCommandPacket.Action.START_SPRINTING
                            )
                        )
                        player.wasSprinting = true
                    }

                    if (diagonal) {
                        val directionVec = MoveUtils.forwardWithoutStrafe(0.51)

                        var height = if (level.clip(
                                ClipContext(
                                    player.eyePosition,
                                    player.eyePosition.add(diagonalOffset[0], 0.0, diagonalOffset[1]),
                                    ClipContext.Block.COLLIDER,
                                    ClipContext.Fluid.NONE,
                                    player
                                )
                            ).type == HitResult.Type.MISS
                        ) {
                            1
                        } else {
                            2
                        }

                        player.setPos(player.x + directionVec[0], player.y + height, player.z + directionVec[1])
                        connection.send(ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, true, false))

                        height = if (level.getBlockState(
                                BlockPos.containing(player.position().add(diagonalOffset[0], -2.0, diagonalOffset[1]))
                            ).isAir
                        ) {
                            2
                        } else {
                            1
                        }

                        player.setPos(player.x + directionVec[0], player.y - height, player.z + directionVec[1])
                        connection.send(ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, true, false))
                    } else {
                        val directionVec = MoveUtils.forwardWithoutStrafe(0.57)

                        var height = if (level.clip(
                                ClipContext(
                                    player.eyePosition,
                                    player.eyePosition.add(diagonalOffset[0], 0.0, diagonalOffset[1]),
                                    ClipContext.Block.COLLIDER,
                                    ClipContext.Fluid.NONE,
                                    player
                                )
                            ).type == HitResult.Type.MISS
                        ) {
                            1
                        } else {
                            2
                        }

                        player.setPos(player.x + directionVec[0], player.y + height, player.z + directionVec[1])
                        connection.send(ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, true, false))

                        player.setPos(player.x + directionVec[0], player.y, player.z + directionVec[1])
                        connection.send(ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, true, false))

                        height = if (level.getBlockState(
                                BlockPos.containing(player.position().add(diagonalOffset[0], -2.0, diagonalOffset[1]))
                            ).isAir
                        ) {
                            2
                        } else {
                            1
                        }

                        player.setPos(player.x + directionVec[0], player.y - height, player.z + directionVec[1])
                        connection.send(ServerboundMovePlayerPacket.Pos(player.x, player.y, player.z, true, false))
                    }
                    if (!player.isSprinting) {
                        connection.send(
                            ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)
                        )
                        player.wasSprinting = false
                    }
                    isEnabled = false
                }
            }
        }

        safeEventHandler<DestroyedBlockEvent> { event ->
            if (mode == Mode.Sunrise && level.getBlockState(event.blockPos).isAir) {
                clipTimer = afterBreak
            }
        }

        safeEventHandler<SendPositionEvent>(-999) { event ->
            if (event.isCancelled || !canUsePearl()) return@safeEventHandler
            val slot = findPearlSlot()
            if (slot != -1) doUsePearl(slot)
        }

        safeEventHandler<BlockCollisionEvent> { event ->
            val playerPos = BlockPos.containing(player.position())

            val canNoClip = mode == Mode.Vanilla || !waitBreak || clipTimer != 0
            if ((mode != Mode.CCClip && mode != Mode.Pearl && mode != Mode.ForceMine && canNoClip) || afterPearlTime > 0) {
                if (event.pos != playerPos.below() || mc.options.keyShift.isDown) {
                    event.state = Blocks.AIR.defaultBlockState()
                }
            }

            if (mode == Mode.ForceMine) {
                val xDelta = abs(playerPos.x - event.pos.x).toFloat()
                val zDelta = abs(playerPos.z - event.pos.z).toFloat()

                if (xDelta != 0.0f && zDelta != 0.0f && strict) {
                    return@safeEventHandler
                }

                if (event.pos != playerPos.below() || mc.options.keyShift.isDown) {
                    event.state = Blocks.AIR.defaultBlockState()
                }
            }
        }

        safeEventHandler<PlayerTickEvent.Pre> {
            if (clipTimer > 0) clipTimer--
            if (afterPearlTime > 0) afterPearlTime--

            if (mode == Mode.Sunrise && (player.horizontalCollision || isInsideBlock()) && !player.isInWater && !player.isInLava && clipTimer <= 0) {
                val dir = MoveUtils.forward(0.5)

                var blockToBreak: BlockPos? = null

                if (mc.options.keyJump.isDown) {
                    blockToBreak = BlockPos.containing(player.x + dir[0], player.y + 2, player.z + dir[1])
                } else if (mc.options.keyShift.isDown) {
                    blockToBreak = BlockPos.containing(player.x + dir[0], player.y - 1, player.z + dir[1])
                } else if (player.isMoving) {
                    blockToBreak = BlockPos.containing(player.x + dir[0], player.y, player.z + dir[1])
                }

                if (blockToBreak == null) return@safeEventHandler
                val bestTool = AutoTool.getBestTool(blockToBreak)
                if (bestTool == -1) return@safeEventHandler

                val previousSlot = player.inventory.selectedSlot
                player.inventory.selectedSlot = bestTool
                try {
                    gameMode.continueDestroyBlock(blockToBreak, player.direction)
                    SwingMode.Both.attackSwing(InteractionHand.MAIN_HAND)
                } finally {
                    if (silent) {
                        player.inventory.selectedSlot = previousSlot
                    }
                }
            }

            if (mode == Mode.ForceMine && (player.horizontalCollision || isInsideBlock()) && !player.isInWater && !player.isInLava) {
                for (x in -2 until 2) {
                    for (y in -1 until 3) {
                        for (z in -2 until 2) {
                            if (((x == 0 && y == 0 && z == 0) || (x == 0 && y == 1 && z == 0)) && !mc.options.keyShift.isDown) {
                                continue
                            }
                            val bp = BlockPos.containing(player.position()).offset(x, y, z)
                            if (player.boundingBox.intersects(AABB(bp)) && !level.getBlockState(bp).isAir) {
                                connection.send(
                                    ServerboundPlayerActionPacket(
                                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                                        bp,
                                        Direction.UP
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (canUsePearl() && findPearlSlot() != -1) {
                val dir = MoveUtils.forward(0.5)
                val block = BlockPos.containing(player.x + dir[0], player.y, player.z + dir[1])
                val angle = RotationUtils.calculate(Vec3.atCenterOf(block))
                val rotation = Rotation(angle.yaw, pitch.toFloat())
                RotationManager.setRotations(rotation, 360.0, Priority.Highest)
            }
        }
    }

    context(nn: NonNullContext)
    private fun canUsePearl(): Boolean {
        return mode == Mode.Pearl && nn.player.isAlive && !nn.player.isPassenger &&
                nn.mc.cameraEntity === nn.player && nn.player.horizontalCollision && clipTimer <= 0 &&
                nn.player.tickCount > 60 && (nn.player.onGround() || !onlyOnGround) &&
                !nn.mc.options.keyShift.isDown && (!pauseOnEat || !PlayerUtils.isEating()) &&
                (!pauseOnPhase || !isInsideBlock())
    }

    context(nn: NonNullContext)
    private fun findPearlSlot(): Int {
        return when (swapMode) {
            Silent -> InvUtils.findInHotbar(Items.ENDER_PEARL).slot
            InvSwitch -> InvUtils.find(Items.ENDER_PEARL).slot
        }
    }

    context(nn: NonNullContext)
    private fun doUsePearl(slot: Int) {
        val inventory = nn.player.inventory
        val previousSlot = inventory.selectedSlot
        val hand = if (slot == Inventory.SLOT_OFFHAND) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val containerSlot = if (slot < Inventory.SELECTION_SIZE) slot + 36 else slot

        if (hand == InteractionHand.MAIN_HAND && slot != previousSlot) when (swapMode) {
            Silent -> inventory.selectedSlot = slot
            InvSwitch -> ClickSlotUtils.swap(containerSlot, previousSlot)
        }

        val stack = nn.player.getItemInHand(hand)
        val used = if (!stack.`is`(Items.ENDER_PEARL) || nn.player.cooldowns.isOnCooldown(stack)) {
            false
        } else {
            val result = nn.gameMode.useItem(nn.player, hand)
            if (result.consumesAction()) SwingMode.of(swingHand).interactSwing(hand)
            result.consumesAction()
        }

        if (hand == InteractionHand.MAIN_HAND && slot != previousSlot) when (swapMode) {
            Silent -> inventory.selectedSlot = previousSlot
            InvSwitch -> ClickSlotUtils.swap(containerSlot, previousSlot)
        }

        if (!used) return

        clipTimer = 20
        afterPearlTime = afterPearl

        if (autoDisable) toggle()
    }

    context(nn: NonNullContext)
    private fun isInsideBlock(): Boolean {
        val box = nn.player.boundingBox.deflate(1.0E-7)
        val context = CollisionContext.of(nn.player)
        for (pos in BlockPos.betweenClosed(
            BlockPos.containing(box.minX, box.minY, box.minZ),
            BlockPos.containing(box.maxX, box.maxY, box.maxZ)
        )) {
            val shape = nn.level.getBlockState(pos).getCollisionShape(nn.level, pos, context)
            if (shape.toAabbs().any { it.move(pos).intersects(box) }) return true
        }
        return false
    }

}
