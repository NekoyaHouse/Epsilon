package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.EventPriority
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.ClientTickEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.ViaVersionComparison
import com.github.epsilon.settings.viaOnly
import com.github.epsilon.utils.network.NetworkUtils
import com.github.epsilon.utils.player.EnchantmentUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments

object Disabler : Module("Disabler", Category.PLAYER) {

    private enum class PositionMode {
        Disabled,
        Grim,
        GrimV3
    }

    private enum class PingMode {
        None,
        Drop,
        Spam
    }

    private val multiActionsC by boolSetting("MultiActionsC", true).viaOnly(
        ViaVersionComparison.LESS_OR_EQUAL,
        "1.20.6"
    )
    private val position by enumSetting("Position", PositionMode.Disabled)
    private val onlyPhased by boolSetting("Only Phased", false, { position != PositionMode.Disabled })
    private val limited by boolSetting("Limited", true, { position == PositionMode.GrimV3 })
    private val yawOverflow by boolSetting("Yaw Overflow", false)
    val grimTrident by boolSetting("Grim Trident", false)
    private val ping by enumSetting("Ping", PingMode.None)

    init {
        safeEventHandler<SendPositionEvent>(-1000) { event ->
            if (event.isCancelled) return@safeEventHandler
            updatePosition(event)
            if (yawOverflow && !player.isFallFlying) event.yaw += 36_000_000f
        }

        safeEventHandler<ClientTickEvent.Pre> {
            if (grimTrident && !player.isUsingItem) useTrident()
        }

        safeEventHandler<PacketEvent.Send>(EventPriority.HIGH) { event ->
            if (event.isCancelled) return@safeEventHandler
            val packet = event.packet

            if (packet is ServerboundPongPacket) {
                when (ping) {
                    PingMode.None -> Unit
                    PingMode.Drop -> if (packet.id % 2 != 0) event.cancel()
                    PingMode.Spam -> {
                        event.cancel()
                        repeat(2) { NetworkUtils.sendPacketNoEvent(ServerboundPongPacket(packet.id)) }
                    }
                }
            }

            if (multiActionsC && packet is ServerboundContainerClickPacket && player.wasSprinting) {
                event.cancel()
                withSprintStopped { NetworkUtils.sendPacketNoEvent(packet) }
                return@safeEventHandler
            }
        }
    }

    /**
     * Via 你妈真死了吧
     */
    fun handleInventoryClick(actor: Player, action: Runnable) {
        runSafe {
            if (isEnabled && multiActionsC && actor === player) {
                withSprintStopped(action)
            } else {
                action.run()
            }
        } ?: action.run()
    }

    context(nn: NonNullContext)
    private fun withSprintStopped(action: Runnable) {
        if (nn.player.wasSprinting) {
            NetworkUtils.sendPacketNoEvent(
                ServerboundPlayerCommandPacket(nn.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)
            )
            nn.player.wasSprinting = false

            action.run()

            if (nn.player.isSprinting) {
                NetworkUtils.sendPacketNoEvent(
                    ServerboundPlayerCommandPacket(nn.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING)
                )
                nn.player.wasSprinting = true
            }
        } else action.run()
    }

    context(nn: NonNullContext)
    private fun updatePosition(event: SendPositionEvent) {
        if (position == PositionMode.Disabled || nn.player.isPassenger || onlyPhased && nn.level.getBlockCollisions(
                nn.player,
                nn.player.boundingBox
            ).none { !it.isEmpty }
        ) return

        val moved = event.lastSentPosition.distanceToSqr(event.x, event.y, event.z) > 4.0E-8
        val rotated = event.yaw != event.lastSentYaw || event.pitch != event.lastSentPitch
        when (position) {
            PositionMode.Disabled -> Unit
            PositionMode.Grim -> {
                if (!rotated && (moved || event.positionReminder < 19)) return
                event.forcePosition = true
                event.forceRotation = true
            }

            PositionMode.GrimV3 -> {
                if (moved || !rotated) return
                event.forcePosition = true
                event.forceRotation = true
                if (!limited || event.positionReminder >= 19) {
                    nn.connection.send(
                        ServerboundMovePlayerPacket.Pos(
                            event.x, event.y, event.z, event.isOnGround, nn.player.horizontalCollision
                        )
                    )
                }
            }
        }
    }

    context(nn: NonNullContext)
    private fun useTrident() {
        val slot = (0..8).firstOrNull {
            val stack = nn.player.inventory.getItem(it)
            stack.`is`(Items.TRIDENT) && EnchantmentUtils.getEnchantmentLevel(stack, Enchantments.RIPTIDE) > 0
        } ?: return
        val previousSlot = nn.player.inventory.selectedSlot
        nn.player.inventory.selectedSlot = slot

        nn.gameMode.ensureHasSentCarriedItem()
        NetworkUtils.sendPacketNoEvent(
            ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND,
                nn.level.blockStatePredictionHandler.startPredicting().currentSequence(),
                nn.player.yRot,
                nn.player.xRot
            )
        )
        NetworkUtils.sendPacketNoEvent(
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN
            )
        )

        nn.player.inventory.selectedSlot = previousSlot
        nn.gameMode.ensureHasSentCarriedItem()
    }

}
