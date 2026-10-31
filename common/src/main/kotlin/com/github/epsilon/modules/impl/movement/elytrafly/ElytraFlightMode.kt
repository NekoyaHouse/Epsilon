package com.github.epsilon.modules.impl.movement.elytrafly

import com.github.epsilon.events.impl.FallFlyingEvent
import com.github.epsilon.events.impl.FireworkRotationEvent
import com.github.epsilon.events.impl.KeyboardInputEvent
import com.github.epsilon.events.impl.TravelEvent
import com.github.epsilon.kernel.MinecraftWrapper
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.kernel.jvm.runSafeOrFalse
import com.github.epsilon.utils.player.ClickSlotUtils
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.player.SwingMode
import com.github.epsilon.utils.timer.TimerUtils
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items

abstract class ElytraFlightMode(protected val elytraFly: ElytraFly) {

    protected val mc: Minecraft get() = MinecraftWrapper.mc
    private val unbreakingTimer = TimerUtils()

    open fun onEnable() {
    }

    open fun onDisable() {
    }

    open fun onPlayerTick() {
    }

    open fun onTravel(event: TravelEvent) {
    }

    open fun onKeyboardInput(event: KeyboardInputEvent) {
    }

    open fun onFallFlying(event: FallFlyingEvent) {
    }

    open fun onFireworkUpdate(event: FireworkRotationEvent) {
    }

    open fun shouldCancelRightClick(): Boolean {
        return true
    }

    /**
     * 原版处理右键（Minecraft#handleKeybinds）之前触发，用于在续吃判定之前调整右键状态。
     */
    open fun onRightClick() {
    }

    open fun armUnbreakingTimer() {
        unbreakingTimer.setMs(917813L)
    }

    open fun handleUnbreaking() {
        runSafe {
            if (!elytraFly.unbreaking) return@runSafe
            if (mc.gui.screen() != null) return@runSafe
            if (!player.isFallFlying || player.onGround()) return@runSafe
            if (!unbreakingTimer.passedMillise(elytraFly.unbreakingDelay.toLong())) return@runSafe

            val chestStack = player.getItemBySlot(EquipmentSlot.CHEST)
            if (!LivingEntity.canGlideUsing(chestStack, EquipmentSlot.CHEST)) return@runSafe

            val containerId = player.containerMenu.containerId
            ClickSlotUtils.click(containerId, 6)
            ClickSlotUtils.click(containerId, 6)

            connection.send(
                ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            )
            player.startFallFlying()
            unbreakingTimer.reset()
        }
    }

    protected fun canStartFallFlying(): Boolean = runSafeOrFalse {
        !player.isFallFlying && !player.isInWater
    }

    protected fun startFallFlying(): Boolean = runSafeOrFalse {
        if (player.tryToStartFallFlying()) {
            connection.send(
                ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            )
            true
        } else {
            false
        }
    }

    protected fun restartFallFlying() {
        runSafe {
            connection.send(
                ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING)
            )
            player.startFallFlying()
        }
    }

    protected fun canGlide(hasElytraInInventory: Boolean): Boolean = runSafeOrFalse {
        elytraFly.armored && hasElytraInInventory || EquipmentSlot.VALUES.any {
            LivingEntity.canGlideUsing(
                player.getItemBySlot(
                    it
                ), it
            )
        }
    }

    protected fun useFirework(): Boolean = runSafeOrFalse {
        val rocket = if (elytraFly.swapMode == ElytraFly.SwapMode.Silent) {
            InvUtils.findInHotbar(Items.FIREWORK_ROCKET)
        } else {
            InvUtils.find(Items.FIREWORK_ROCKET)
        }
        if (!rocket.found()) return@runSafeOrFalse false

        val hand = rocket.getHand()

        if (elytraFly.swapMode == ElytraFly.SwapMode.Silent) {
            InvUtils.swap(rocket.slot, true)
        } else {
            InvUtils.invSwap(rocket.slot)
        }

        val result = gameMode.useItem(player, hand)
        if (result.consumesAction()) {
            SwingMode.Both.interactSwing(hand)
        }

        if (elytraFly.swapMode == ElytraFly.SwapMode.Silent) {
            InvUtils.swapBack()
        } else {
            InvUtils.invSwapBack()
        }

        result.consumesAction()
    }

    protected fun swapArmor(containerSlot: Int) {
        runSafe {
            val containerId = player.containerMenu.containerId
            ClickSlotUtils.click(containerId, containerSlot)
            ClickSlotUtils.click(containerId, 6)
            ClickSlotUtils.click(containerId, containerSlot)
        }
    }


}
