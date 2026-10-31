package com.github.epsilon.modules.impl.movement

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.events.impl.SlowdownEvent
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.network.NetworkUtils
import com.github.epsilon.utils.safety.*
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.*
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.Items
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

object NoSlowdown : Module("No Slowdown", Category.MOVEMENT) {

    private enum class Mode {
        Vanilla,
        GrimBlink,
        Grim1_2,
        Grim1_3,
    }

    private val mode by enumSetting("Mode", Mode.Vanilla)
    private val food by boolSetting("Food", true)
    private val bow by boolSetting("Bow", true, { mode != Mode.GrimBlink })
    private val crossbow by boolSetting("Crossbow", true, { mode != Mode.GrimBlink })
    private val cobweb by boolSetting("Cobweb", true, { mode == Mode.Vanilla })

    private var ticks = 0
    private var eating = false
    private var useDuration = 32

    private val packets: Queue<Packet<*>> = LinkedBlockingQueue()

    init {
        nonNullHandler<SendPositionEvent> {
            if (mode != Mode.GrimBlink) {
                return@nonNullHandler
            }

            if (eating) {
                ticks++
                if (Math.toIntExact(packets.stream().filter { it is ClientboundPingPacket }
                        .count()) > 150 && ticks > 1) {
                    NotificationManager.error(
                        translatedName,
                        EpsilonTranslations.Notifications.TRANSACTION_COUNT_TOO_HIGH.getTranslatedName()
                    )
                    mc.options.keyUse.isDown = false
                    onDisable()
                }
            }

            if (player.isUsingItem && isFoodOrDrink(player.useItem) && !eating) {
                eating = true
                ticks = 0
                val useItem = player.useItem
                useDuration = useItem.item.getUseDuration(useItem, player)
            }

            if (eating) {
                if (ticks == 1) {
                    NetworkUtils.sendPacketNoEvent(
                        ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            BlockPos.ZERO,
                            Direction.DOWN
                        )
                    )
                    NetworkUtils.sendPacketNoEvent(
                        ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                            BlockPos.ZERO,
                            Direction.DOWN
                        )
                    )
                }
                if (ticks == 2) {
                    val hand =
                        if (player.usedItemHand == InteractionHand.OFF_HAND) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
                    connection.send(
                        ServerboundUseItemPacket(
                            hand,
                            level.blockStatePredictionHandler.startPredicting().currentSequence(),
                            RotationManager.getYaw(),
                            RotationManager.getPitch()
                        )
                    )
                }
                if (ticks > useDuration + 3) {
                    connection.send(
                        ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                            BlockPos.ZERO,
                            Direction.DOWN
                        )
                    )
                    mc.options.keyUse.isDown = false
                    ticks = 0
                    eating = false
                }
            }
        }

        nonNullHandler<PacketEvent.Receive> { event ->
            if (player.tickCount < 30 || !eating || mode != Mode.GrimBlink) return@nonNullHandler

            val packet = event.packet

            if (packet is ClientboundPlayerPositionPacket) {
                flush()
                return@nonNullHandler
            }

            if (packet is ClientboundSetHealthPacket ||
                packet is ClientboundSystemChatPacket ||
                packet is ClientboundMoveEntityPacket ||
                packet is ClientboundTeleportEntityPacket ||
                packet is ClientboundEntityEventPacket ||
                packet is ClientboundAddEntityPacket ||
                packet is ClientboundBlockUpdatePacket ||
                packet is ClientboundBlockEventPacket
            ) {
                return@nonNullHandler
            }

            if (packet.type().flow() == PacketFlow.CLIENTBOUND) {
                event.cancel()
                packets.add(packet)
            }
        }

        handler<PacketEvent.Send> { event ->
            val packet = event.packet
            if (mode == Mode.GrimBlink && eating && packet is ServerboundPlayerActionPacket &&
                packet.action == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
            ) {
                eating = false
                ticks = 0
                flush()
                NetworkUtils.sendPacketNoEvent(
                    ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ZERO,
                        Direction.DOWN
                    )
                )
            }
        }

        nonNullHandler<SlowdownEvent> { event ->
            if (!food && player.useItem.has(DataComponents.FOOD)) return@nonNullHandler
            if ((!bow || mode == Mode.GrimBlink) && player.useItem.`is`(Items.BOW)) return@nonNullHandler
            if ((!crossbow || mode == Mode.GrimBlink) && player.useItem.`is`(Items.CROSSBOW)) return@nonNullHandler

            when (mode) {
                Mode.Vanilla -> cancel(event)
                Mode.GrimBlink -> grimBlink(event)
                Mode.Grim1_2 -> grim50(event)
                Mode.Grim1_3 -> grim33(event)
            }
        }
    }

    fun isWorking(): Boolean {
        return isEnabled && mode == Mode.GrimBlink && eating
    }

    fun stop() {
        runSafe {
            mc.options.keyUse.isDown = false
            interaction.releaseUsingItem(player)
        }
    }

    override fun onDisable() {
        flush()
        eating = false
        ticks = 0
        useDuration = 32
    }

    context(nn: NonNullContext)
    private fun cancel(event: SlowdownEvent) {
        event.isSlowdown = false
        if (!player.horizontalCollision && player.input.hasForwardImpulse()) player.isSprinting = true
    }

    context(nn: NonNullContext)
    private fun grimBlink(event: SlowdownEvent) {
        if (player.isUsingItem && player.useItemRemainingTicks < 30 &&
            !(isFoodOrDrink(player.mainHandItem) && isFoodOrDrink(player.offhandItem)) &&
            isFoodOrDrink(player.useItem)
        ) {
            event.isSlowdown = false
            player.isSprinting = true
        }
    }

    context(nn: NonNullContext)
    private fun grim50(event: SlowdownEvent) {
        if (player.useItemRemainingTicks % 2 == 0 && player.useItemRemainingTicks <= 30) {
            event.isSlowdown = false
        }
    }

    context(nn: NonNullContext)
    private fun grim33(event: SlowdownEvent) {
        if (player.useItemRemainingTicks % 3 == 0 && player.useItemRemainingTicks <= 30) {
            event.isSlowdown = false
        }
    }

    private fun isFoodOrDrink(stack: ItemStack): Boolean {
        val anim = stack.useAnimation
        return anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK
    }

    @Suppress("UNCHECKED_CAST")
    private fun flush() {
        val connection = mc.connection
        if (connection == null) {
            packets.clear()
        } else {
            while (true) {
                val packet = packets.poll() ?: break
                (packet as Packet<ClientPacketListener>).handle(connection)
            }
        }
    }

    fun noWeb(): Boolean {
        return isEnabled && mode == Mode.Vanilla && cobweb
    }

}
