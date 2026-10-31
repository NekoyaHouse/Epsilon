package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.events.impl.RightClickEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.gameMode
import com.github.epsilon.kernel.jvm.player
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.math.MathUtils
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.SwingMode
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.core.BlockPos
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.awt.Color

object AutoHitCrystal : Module("Auto Hit Crystal", Category.COMBAT) {

    private val activateKey by keybindSetting("Activate Key", InputConstants.UNKNOWN.value)
    private val checkPlace by boolSetting("Check Place", false)
    private val switchDelay by doubleSetting("Switch Delay", 0.0, 0.0..20.0, 1.0)
    private val switchChance by doubleSetting("Switch Chance", 100.0, 0.0..100.0, 1.0)
    private val placeDelay by doubleSetting("Place Delay", 0.0, 0.0..20.0, 1.0)
    private val placeChance by doubleSetting("Place Chance", 100.0, 0.0..100.0, 1.0)
    private val workWithTotem by boolSetting("Work With Totem", false)
    private val workWithCrystal by boolSetting("Work With Crystal", false)
    private val workWithPickaxe by boolSetting("Work With Pickaxe", false)
    private val swordSwap by boolSetting("Sword Swap", true)
    private val swingHand by boolSetting("Swing Hand", true)

    private val render by boolSetting("Render", true)
    private val fadeTime by intSetting("Fade Time", 500, 0..3000, 50, { render })
    private val sideColor by colorSetting("Side Color", Color(255, 183, 197, 100), true, { render })
    private val lineColor by colorSetting("Line Color", Color(255, 105, 180), true, { render })

    private var placeClock = 0
    private var switchClock = 0
    private var active = false
    private var crystalling = false

    private val renderBoxes: MutableList<RenderBox> = ArrayList()

    init {
        // 渲染事件不受模块启用状态过滤：残留方块需要继续淡出。
        handler<Render3DEvent>(alwaysListening = true) {
            if (!render || renderBoxes.isEmpty()) return@handler

            val time = System.currentTimeMillis()
            val fade = fadeTime.toLong()

            renderBoxes.removeAll { box -> time - box.startTime > fade }

            for (box in renderBoxes) {
                val age = time - box.startTime
                val progress = (age.toFloat() / fade).coerceIn(0.0f, 1.0f)
                val alphaFactor = (1.0f - progress).coerceIn(0.0f, 1.0f)

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

                Render3DScheduler.addFilledBox(box.aabb, side)
                Render3DScheduler.addOutlineBox(box.aabb, line)
            }
        }

        safeEventHandler<PlayerTickEvent.Pre> {
            if (mc.gui.screen() != null) return@safeEventHandler

            if (switchClock > 0) --switchClock
            if (placeClock > 0) --placeClock

            val pressed = KeybindUtils.isPressed(activateKey)
            if (pressed) {
                val hitResult = mc.hitResult
                if (hitResult is BlockHitResult) {
                    if (hitResult.type == HitResult.Type.BLOCK && !active &&
                        !level.getBlockState(hitResult.blockPos).canBeReplaced() && checkPlace
                    ) {
                        return@safeEventHandler
                    }
                }

                val mainHandStack = player.mainHandItem
                if (!(mainHandStack.`is`(ItemTags.SWORDS) ||
                            (workWithTotem && mainHandStack.`is`(Items.TOTEM_OF_UNDYING)) ||
                            (workWithCrystal && mainHandStack.`is`(Items.END_CRYSTAL)) ||
                            (workWithPickaxe && mainHandStack.`is`(ItemTags.PICKAXES)) ||
                            active)
                ) {
                    return@safeEventHandler
                }

                active = true

                if (!crystalling && hitResult is BlockHitResult) {
                    if (hitResult.type == HitResult.Type.MISS) {
                        return@safeEventHandler
                    }

                    val renderPos = hitResult.blockPos.relative(hitResult.direction)
                    val hitState = level.getBlockState(hitResult.blockPos)

                    if (hitState.`is`(Blocks.BEDROCK) || hitState.`is`(Blocks.OBSIDIAN)) {
                        active = true
                        crystalling = true
                    } else if (!level.getBlockState(renderPos).`is`(Blocks.OBSIDIAN)) {
                        val state = level.getBlockState(hitResult.blockPos)
                        if (state.`is`(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
                            return@safeEventHandler
                        }

                        mc.options.keyUse.isDown = false

                        if (!player.isHolding(Items.OBSIDIAN)) {
                            if (switchClock > 0) {
                                return@safeEventHandler
                            }
                            if (MathUtils.getRandom(1, 100) <= switchChance.toInt()) {
                                switchClock = switchDelay.toInt()
                                selectItemFromHotbar(Items.OBSIDIAN)
                            }
                        }

                        if (player.isHolding(Items.OBSIDIAN)) {
                            if (placeClock > 0) {
                                return@safeEventHandler
                            }

                            if (MathUtils.getRandom(1, 100) <= placeChance.toInt()) {
                                placeBlock(hitResult, renderPos)
                                placeClock = placeDelay.toInt()
                                crystalling = true
                            }
                        }
                    }
                }

                if (crystalling) {
                    if (!player.isHolding(Items.END_CRYSTAL)) {
                        if (switchClock > 0) {
                            return@safeEventHandler
                        }
                        if (MathUtils.getRandom(1, 100) <= switchChance.toInt()) {
                            if (selectItemFromHotbar(Items.END_CRYSTAL)) {
                                switchClock = switchDelay.toInt()
                            }
                        }
                    }

                    if (player.isHolding(Items.END_CRYSTAL) && !CrystalAura.isEnabled) {
                        CrystalAura.onTick(null)
                    }
                }
            } else {
                resetState()
            }
        }

        safeEventHandler<RightClickEvent> { event ->
            if (!active) return@safeEventHandler

            val mainHandStack = player.mainHandItem
            val crosshairTarget = mc.hitResult

            if (swordSwap && mainHandStack.`is`(ItemTags.SWORDS) && crosshairTarget is BlockHitResult) {
                if (level.getBlockState(crosshairTarget.blockPos).`is`(Blocks.OBSIDIAN) &&
                    !player.isHolding(Items.END_CRYSTAL)
                ) {
                    if (selectItemFromHotbar(Items.END_CRYSTAL)) {
                        crystalling = true
                    }
                }
            }

            if (active && KeybindUtils.isPressed(activateKey)) {
                if (mainHandStack.`is`(Items.END_CRYSTAL) || mainHandStack.`is`(Items.OBSIDIAN)) {
                    event.cancel()
                }
            }

            if (mainHandStack.`is`(Items.END_CRYSTAL) && !mc.mouseHandler.isLeftPressed) {
                event.cancel()
            }
        }
    }

    override fun onEnable() {
        resetState()
    }

    context(nn: NonNullContext)
    private fun selectItemFromHotbar(item: Item): Boolean {
        val result: FindItemResult = InvUtils.findInHotbar(item)
        if (result.found()) {
            InvUtils.swap(result.slot, false)
            return true
        }
        return false
    }

    context(nn: NonNullContext)
    private fun placeBlock(hit: BlockHitResult, renderPos: BlockPos) {
        val hand =
            if (player.offhandItem.`is`(Items.OBSIDIAN)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val result: InteractionResult = gameMode.useItemOn(player, hand, hit)
        if (result.consumesAction()) {
            SwingMode.of(swingHand).interactSwing(hand)
            renderBoxes.add(RenderBox(AABB(renderPos), lineColor, sideColor, System.currentTimeMillis()))
        }
    }

    private fun resetState() {
// this.placeClock = this.placeDelay.intValue();
// this.switchClock = this.switchDelay.intValue();
        switchClock = 0
        placeClock = 0
        active = false
        crystalling = false
    }

    private data class RenderBox(val aabb: AABB, val lineColor: Color, val sideColor: Color, val startTime: Long)

}
