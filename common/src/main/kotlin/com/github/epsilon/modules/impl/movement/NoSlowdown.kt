package com.github.epsilon.modules.impl.movement

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.events.impl.SlowdownEvent
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.managers.NotificationManager
import com.github.epsilon.managers.RotationManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.network.NetworkUtils
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
        onDisable { resetState() }

        safeEventHandler<SendPositionEvent> {
            if (mode != Mode.GrimBlink) {
                return@safeEventHandler
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
                    resetState()
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

        safeEventHandler<PacketEvent.Receive> { event ->
            if (player.tickCount < 30 || !eating || mode != Mode.GrimBlink) return@safeEventHandler

            val packet = event.packet

            if (packet is ClientboundPlayerPositionPacket) {
                flush()
                return@safeEventHandler
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
                return@safeEventHandler
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

        safeEventHandler<SlowdownEvent> { event ->
            if (!food && player.useItem.has(DataComponents.FOOD)) return@safeEventHandler
            if ((!bow || mode == Mode.GrimBlink) && player.useItem.`is`(Items.BOW)) return@safeEventHandler
            if ((!crossbow || mode == Mode.GrimBlink) && player.useItem.`is`(Items.CROSSBOW)) return@safeEventHandler

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
            gameMode.releaseUsingItem(player)
        }
    }

    private fun resetState() {
        flush()
        eating = false
        ticks = 0
        useDuration = 32
    }

    context(nn: NonNullContext)
    private fun cancel(event: SlowdownEvent) {
        event.isSlowdown = false
        if (!nn.player.horizontalCollision && nn.player.input.hasForwardImpulse()) nn.player.isSprinting = true
    }

    context(nn: NonNullContext)
    private fun grimBlink(event: SlowdownEvent) {
        if (nn.player.isUsingItem && nn.player.useItemRemainingTicks < 30 &&
            !(isFoodOrDrink(nn.player.mainHandItem) && isFoodOrDrink(nn.player.offhandItem)) &&
            isFoodOrDrink(nn.player.useItem)
        ) {
            event.isSlowdown = false
            nn.player.isSprinting = true
        }
    }

    context(nn: NonNullContext)
    private fun grim50(event: SlowdownEvent) {
        if (nn.player.useItemRemainingTicks % 2 == 0 && nn.player.useItemRemainingTicks <= 30) {
            event.isSlowdown = false
        }
    }

    context(nn: NonNullContext)
    private fun grim33(event: SlowdownEvent) {
        if (nn.player.useItemRemainingTicks % 3 == 0 && nn.player.useItemRemainingTicks <= 30) {
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
            return
        }
        // 收到位置包时 flush 会在 Netty 线程执行；此时直接 handle 会让 ClientPacketListener 判定跨线程并抛出
        // RunningOnDifferentThreadException，该异常在事件监听器里会被当作监听失败并取消订阅。
        // 因此离线线程上按原顺序把包交给 Minecraft 的包处理器，由主线程在下一个 tick 按序执行。
        val onMainThread = mc.isSameThread
        while (true) {
            val queued = packets.poll() ?: break
            val packet = queued as Packet<ClientPacketListener>
            if (onMainThread) {
                packet.handle(connection)
            } else {
                mc.packetProcessor().scheduleIfPossible(connection, packet)
            }
        }
    }

    fun noWeb(): Boolean {
        return isEnabled && mode == Mode.Vanilla && cobweb
    }

}
