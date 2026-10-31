package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.safety.*
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.item.ItemStack

object PacketEat : Module("Packet Eat", Category.PLAYER) {

    private var item: ItemStack? = null

    init {
        nonNullHandler<PlayerTickEvent.Post> {
            if (player.isUsingItem) item = player.useItem
        }

        nonNullHandler<PacketEvent.Send> { event ->
            val packet = event.packet
            if (packet !is ServerboundPlayerActionPacket || packet.action != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) return@nonNullHandler

            val food = item?.get(DataComponents.FOOD) ?: return@nonNullHandler
            if (food.canAlwaysEat()) {
                event.cancel()
            }
        }
    }

}
