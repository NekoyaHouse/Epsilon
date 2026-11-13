package com.github.epsilon.kernel.gameplay.player

import com.github.epsilon.kernel.jvm.NonNullContext
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.world.InteractionHand

enum class SwingMode {

    /**
     * 播放本地动画，并发送服务端挥手包。
     */
    Both,

    /**
     * 只发送服务端挥手包。
     */
    Packet,

    /**
     * 只播放本地动画。
     */
    Client,

    /**
     * 不播放动画，也不发送挥手包。
     */
    None;

    private fun client(): Boolean = this == Client || this == Both

    private fun server(): Boolean = this == Packet || this == Both

    context(nn: NonNullContext)
    fun attackSwing(hand: InteractionHand) {
        if (client()) nn.player.swing(hand, nn.player.getItemInHand(hand).attackAnimation, false)
        if (server()) nn.connection.send(ServerboundPunchPacket.INSTANCE)
    }

    context(nn: NonNullContext)
    fun interactSwing(hand: InteractionHand) {
        if (client()) nn.player.swing(hand, nn.player.getItemInHand(hand).interactAnimation, false)
    }

    companion object {

        @JvmStatic
        fun of(client: Boolean): SwingMode = if (client) Client else Packet

    }

}
