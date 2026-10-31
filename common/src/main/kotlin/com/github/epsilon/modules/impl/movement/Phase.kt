package com.github.epsilon.modules.impl.movement

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.BlockCollisionEvent
import com.github.epsilon.events.impl.DestroyedBlockEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.player.AutoTool
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.MoveUtils
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.RotationUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

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
    private val swapMode by enumSetting("Swap Mode", SwapMode.InvSwitch)
    private val pauseOnPhase by boolSetting("Pause On Phase", false, dependency = { mode == Mode.Pearl })
    private val swingHand by boolSetting("Swing Hand", true, dependency = { mode == Mode.Pearl })
    private val silent by boolSetting("Silent", false, dependency = { mode == Mode.Sunrise })
    private val waitBreak by boolSetting("Wait Break", true, dependency = { mode == Mode.Sunrise })
    private val onlyOnGround by boolSetting("Only On Ground", false, dependency = { mode == Mode.Pearl })
    private val autoDisable by boolSetting("Auto Disable", false, dependency = { mode == Mode.Pearl })
    private val afterBreak by intSetting("Break Timeout", 4, 1, 20, 1, dependency = { mode == Mode.Sunrise && waitBreak })
    private val afterPearl by intSetting("Pearl Timeout", 0, 0, 60, 1, dependency = { mode == Mode.Pearl })
    private val pitch by doubleSetting("Pitch", 80.0, 0.0, 90.0, 1.0, dependency = { mode == Mode.Pearl })
    private val strict by boolSetting("Strict", false, dependency = { mode == Mode.ForceMine })

    var clipTimer = 0
    var afterPearlTime = 0

    init {
        handler<DestroyedBlockEvent> {
            clipTimer = afterBreak
        }

        nonNullHandler<BlockCollisionEvent> { event ->
            val playerPos = BlockPos.containing(player.position())

            if ((mode != Mode.CCClip && mode != Mode.Pearl && mode != Mode.ForceMine && canNoClip()) || afterPearlTime > 0) {
                if (event.pos != playerPos.below() || mc.options.keyShift.isDown) {
                    event.state = Blocks.AIR.defaultBlockState()
                }
            }

            if (mode == Mode.ForceMine) {
                val xDelta = Math.abs(playerPos.x - event.pos.x).toFloat()
                val zDelta = Math.abs(playerPos.z - event.pos.z).toFloat()

                if (xDelta != 0.0f && zDelta != 0.0f && strict) {
                    return@nonNullHandler
                }

                if (event.pos != playerPos.below() || mc.options.keyShift.isDown) {
                    event.state = Blocks.AIR.defaultBlockState()
                }
            }
        }

        nonNullHandler<PlayerTickEvent.Pre> { event ->
            if (clipTimer > 0) clipTimer--
            if (afterPearlTime > 0) afterPearlTime--

            if (mode == Mode.Sunrise && (player.horizontalCollision || isPlayerInBlock()) &&
                !player.isInWater && !player.isInLava && clipTimer <= 0
            ) {
                val dir = MoveUtils.forward(0.5)

                var blockToBreak: BlockPos? = null

                if (mc.options.keyJump.isDown) {
                    blockToBreak = BlockPos.containing(player.x + dir[0], player.y + 2, player.z + dir[1])
                } else if (mc.options.keyShift.isDown) {
                    blockToBreak = BlockPos.containing(player.x + dir[0], player.y - 1, player.z + dir[1])
                } else if (player.isMoving) {
                    blockToBreak = BlockPos.containing(player.x + dir[0], player.y, player.z + dir[1])
                }

                if (blockToBreak == null) return@nonNullHandler
                val bestTool = AutoTool.getBestTool(blockToBreak)
                if (bestTool == -1) return@nonNullHandler

                InvUtils.swap(bestTool, true)
                interaction.continueDestroyBlock(blockToBreak, player.direction)
                PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
                if (silent) {
                    InvUtils.swapBack()
                }
            }

            if (mode == Mode.ForceMine && (player.horizontalCollision || isPlayerInBlock()) &&
                !player.isInWater && !player.isInLava
            ) {
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

            if (mode == Mode.Pearl && (player.onGround() || !onlyOnGround)) {
                if (player.horizontalCollision && clipTimer <= 0 && player.tickCount > 60) {
                    if (pauseOnPhase && isPlayerInBlock()) {
                        return@nonNullHandler
                    }

                    val dir = MoveUtils.forward(0.5)
                    val block = BlockPos.containing(player.x + dir[0], player.y, player.z + dir[1])

                    if (mc.options.keyShift.isDown) {
                        return@nonNullHandler
                    }

                    val angle = RotationUtils.calculate(Vec3.atCenterOf(block))
                    val result = if (swapMode == SwapMode.Silent) {
                        InvUtils.findInHotbar(Items.ENDER_PEARL)
                    } else {
                        InvUtils.find(Items.ENDER_PEARL)
                    }
                    if (result.found()) {
                        val prevYaw = player.yRot
                        val prevPitch = player.xRot

                        player.yRot = angle.yaw
                        player.xRot = pitch.toFloat()

                        doUsePearl(result.slot)

                        player.yRot = prevYaw
                        player.xRot = prevPitch
                    }
                }
            }
        }
    }

    override fun onEnable() {
        afterPearlTime = 0
        clipTimer = 0

        runSafe {
            if (player.onGround() && mode == Mode.CCClip) {
                val diagonalOffset = MoveUtils.forwardWithoutStrafe(0.44)
                val diagonal = player.yRot % 90 > 35 && player.yRot % 90 < 55

                connection.send(
                    ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING)
                )

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
                    toggle()
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
                    toggle()
                }
            }
        }
    }

    context(nn: NonNullContext)
    private fun doUsePearl(slot: Int) {
        if (swapMode == SwapMode.Silent) {
            InvUtils.swap(slot, true)
        } else {
            InvUtils.invSwap(slot)
        }

        interaction.useItem(player, InteractionHand.MAIN_HAND)

        if (swingHand) {
            PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
        }

        if (swapMode == SwapMode.Silent) {
            InvUtils.swapBack()
        } else {
            InvUtils.invSwapBack()
        }

        if (autoDisable) {
            toggle()
        }

        clipTimer = 20
        afterPearlTime = afterPearl
    }

    private fun canNoClip(): Boolean {
        if (mode == Mode.Vanilla) return true
        if (!waitBreak) return true
        return clipTimer != 0
    }

    context(nn: NonNullContext)
    private fun isPlayerInBlock(): Boolean {
        return !level.getBlockState(BlockPos.containing(player.position())).isAir
    }

}
