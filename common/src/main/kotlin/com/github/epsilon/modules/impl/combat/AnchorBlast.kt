package com.github.epsilon.modules.impl.combat

import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.getValue
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.client.KeybindUtils
import com.github.epsilon.utils.player.PlayerUtils
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.concurrent.ThreadLocalRandom

object AnchorBlast : Module("Anchor Blast", Category.COMBAT) {

    private val triggerKey by keybindSetting("Trigger Key", -1)
    private val detonateSlot by intSetting("Detonate Slot", 1, 1, 9, 1)
    private val placeCps by intSetting("Place CPS", 10, 1, 30, 1)
    private val chargeCps by intSetting("Charge CPS", 10, 1, 30, 1)

    private enum class Phase {
        IDLE,
        PLACE_ANCHOR,
        CHARGE,
        DETONATE,
        CLEANUP,
    }

    private var phase = Phase.IDLE
    private var originalSlot = -1
    private var wasKeyDown = false
    private var cooldown = 0

    init {
        nonNullListener<PlayerTickEvent.Pre> {
            val key = triggerKey
            if (key == -1) return@nonNullListener

            val keyDown = KeybindUtils.isPressed(key)
            val newPress = keyDown && !wasKeyDown
            wasKeyDown = keyDown

            if (newPress) {
                if (SafeAnchor.isActive() || DoubleAnchor.isActive()) {
                    return@nonNullListener
                }

                if (phase != Phase.IDLE && originalSlot >= 0) {
                    player.inventory.setSelectedSlot(originalSlot)
                }
                originalSlot = player.inventory.getSelectedSlot()
                cooldown = 0
                phase = Phase.PLACE_ANCHOR
            }

            if (phase == Phase.IDLE) return@nonNullListener

            if (cooldown > 0) {
                cooldown--
                return@nonNullListener
            }

            when (phase) {
                Phase.PLACE_ANCHOR -> doPlaceAnchor()
                Phase.CHARGE -> doCharge()
                Phase.DETONATE -> doDetonate()
                Phase.CLEANUP -> doCleanup()
                else -> {
                }
            }
        }
    }

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        resetState()
    }

    context(nn: NonNullContext)
    private fun doPlaceAnchor() {
        val hit = mc.hitResult
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) return

        // Already an anchor at crosshair? Skip placement, go straight to charge.
        if (level.getBlockState(hit.blockPos).`is`(Blocks.RESPAWN_ANCHOR)) {
            phase = Phase.CHARGE
            return
        }

        val anchorSlot = findHotbarSlot(Items.RESPAWN_ANCHOR)
        if (anchorSlot == -1) {
            doCleanup()
            return
        }

        player.inventory.setSelectedSlot(anchorSlot)
        interaction.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
        cooldown = humanizedCooldownTicks(placeCps)

        phase = Phase.CHARGE
    }

    context(nn: NonNullContext)
    private fun doCharge() {
        val hit = mc.hitResult
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) return
        if (!level.getBlockState(hit.blockPos).`is`(Blocks.RESPAWN_ANCHOR)) return

        val glowstoneSlot = findHotbarSlot(Items.GLOWSTONE)
        if (glowstoneSlot == -1) {
            doCleanup()
            return
        }

        player.inventory.setSelectedSlot(glowstoneSlot)
        interaction.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND)
        cooldown = humanizedCooldownTicks(chargeCps)

        phase = Phase.DETONATE
    }

    context(nn: NonNullContext)
    private fun doDetonate() {
        val hit = mc.hitResult
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) return
        if (!level.getBlockState(hit.blockPos).`is`(Blocks.RESPAWN_ANCHOR)) return

        val slot = detonateSlot - 1
        player.inventory.setSelectedSlot(slot)
        interaction.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        PlayerUtils.swingHand(InteractionHand.MAIN_HAND)

        phase = Phase.CLEANUP
    }

    context(nn: NonNullContext)
    private fun doCleanup() {
        if (originalSlot >= 0) {
            player.inventory.setSelectedSlot(originalSlot)
        }
        resetState()
    }

    context(nn: NonNullContext)
    private fun findHotbarSlot(item: Item): Int {
        for (i in 0 until 9) {
            if (player.inventory.getItem(i).`is`(item)) return i
        }
        return -1
    }

    /**
     * Humanized click cooldown: base interval derived from CPS plus ±25% Gaussian
     * jitter, clamped to >=50&nbsp;ms so the resulting tick count is always >=1.
     * Mirrors the pattern used by AxeBreaker so anti-cheat sees a non-uniform click
     * rhythm across our combat automation modules.
     */
    private fun humanizedCooldownTicks(cps: Int): Int {
        val baseTicks = Math.max(1, 20 / cps)
        val baseMs = baseTicks * 50.0
        val jitter = baseMs * 0.25 * ThreadLocalRandom.current().nextGaussian()
        val adjustedMs = Math.max(50.0, baseMs + jitter)
        return Math.max(1, Math.round(adjustedMs / 50.0).toInt())
    }

    private fun resetState() {
        phase = Phase.IDLE
        originalSlot = -1
        wasKeyDown = false
        cooldown = 0
    }

    /**
     * True when this module is enabled and mid-action (used by SafeAnchor/AimAssist to defer).
     */
    fun isActive(): Boolean {
        return isEnabled && phase != Phase.IDLE
    }

}
