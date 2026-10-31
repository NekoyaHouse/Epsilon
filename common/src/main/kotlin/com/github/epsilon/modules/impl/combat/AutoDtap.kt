package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.MousePressEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.gameMode
import com.github.epsilon.kernel.jvm.level
import com.github.epsilon.kernel.jvm.player
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.SwingMode
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.*

object AutoDtap : Module("Auto Dtap", Category.COMBAT) {

    private val swapBack by boolSetting("SwapBack", true)

    private val random = Random()

    private var stepDelay = 0
    private var step = 0
    private var originalSlot = -1
    private var rightClicked = false

    init {
        handler<MousePressEvent> { event ->
            if (event.button == InputConstants.MOUSE_BUTTON_RIGHT && event.action == InputConstants.PRESS) {
                rightClicked = true
            }
        }

        safeEventHandler<PlayerTickEvent.Pre> {
            if (step != 0) {
                while (mc.options.keyUse.consumeClick()) {
                }
            }

            // 处理延迟
            if (stepDelay > 0) {
                stepDelay--
                return@safeEventHandler
            }

            // 状态机处理
            handleState()
        }
    }

    override fun onEnable() {
        stepDelay = 0
        step = 0
        originalSlot = -1
        rightClicked = false
    }

    override fun onDisable() {
        resetState()
    }

    context(nn: NonNullContext)
    private fun handleState() {
        when (step) {
            0 -> {
                if (rightClicked) {
                    rightClicked = false

                    val main: ItemStack = player.mainHandItem
                    if (!main.`is`(ItemTags.SWORDS)) return

                    val hit = mc.hitResult
                    if (hit !is BlockHitResult) return
                    if (hit.type != HitResult.Type.BLOCK) return

                    val pos: BlockPos = hit.blockPos
                    val state: BlockState = level.getBlockState(pos)
                    if (state.isAir) return

                    val isObsidian = state.`is`(Blocks.OBSIDIAN) || state.`is`(Blocks.BEDROCK)

                    // 寻找水晶
                    val endCrystalSlot = InvUtils.findInHotbar(Items.END_CRYSTAL).slot
                    if (endCrystalSlot == -1) return

                    originalSlot = player.inventory.selectedSlot

                    if (isObsidian) {
                        // 如果已经是黑曜石，直接切水晶并放置
                        InvUtils.swap(endCrystalSlot, false)

                        val topHit = BlockHitResult(
                            hit.location,
                            Direction.UP,
                            pos,
                            false
                        )
                        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, topHit)
                        SwingMode.Both.interactSwing(InteractionHand.MAIN_HAND)

                        stepDelay = 1 + random.nextInt(2)
                        step = 2 // 跳到恢复阶段
                    } else {
                        val obsidianSlot = InvUtils.findInHotbar(Items.OBSIDIAN).slot
                        if (obsidianSlot == -1) return

                        InvUtils.swap(obsidianSlot, false)
                        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
                        SwingMode.Both.interactSwing(InteractionHand.MAIN_HAND)

                        InvUtils.swap(endCrystalSlot, false)

                        stepDelay = 1 + random.nextInt(2)
                        step = 1
                    }
                }
            }

            1 -> {
                val hit1 = mc.hitResult
                if (hit1 !is BlockHitResult) {
                    resetState()
                    return
                }

                val base = hit1.blockPos
                val topHit = BlockHitResult(
                    hit1.location,
                    Direction.UP,
                    base,
                    false
                )
                gameMode.useItemOn(player, InteractionHand.MAIN_HAND, topHit)
                SwingMode.Both.interactSwing(InteractionHand.MAIN_HAND)

                stepDelay = 1 + random.nextInt(2)
                step = 2
            }

            2 -> {
                if (swapBack && originalSlot != -1) {
                    InvUtils.swap(originalSlot, false)
                }
                resetState()
            }
        }
    }

    private fun resetState() {
        step = 0
        stepDelay = 0
        originalSlot = -1
        rightClicked = false
    }

}
