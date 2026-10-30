package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.player
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.item.ItemStack

object PacketEat : Module("Packet Eat", Category.PLAYER) {

    private var item: ItemStack? = null

    init {
        nonNullListener<PlayerTickEvent.Post> {
            if (player.isUsingItem) item = player.useItem
        }

        nonNullListener<PacketEvent.Send> { event ->
            val packet = event.packet
            if (packet !is ServerboundPlayerActionPacket || packet.action != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) return@nonNullListener

            val food = item?.get(DataComponents.FOOD) ?: return@nonNullListener
            if (food.canAlwaysEat()) {
                event.cancel()
            }
        }
    }

}
