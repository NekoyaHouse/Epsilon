package com.github.epsilon.utils.player

import com.github.epsilon.Constants.mc
import net.minecraft.network.protocol.game.ServerboundPunchPacket
import net.minecraft.world.InteractionHand

/**
 * 挥手动画的客户端播放与服务端通知模式。
 */
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

    fun client(): Boolean = this == Client || this == Both

    private fun server(): Boolean = this == Packet || this == Both

    /**
     * 播放攻击或挖掘挥手。
     *
     * 26.3 的服务端攻击包不会触发挥手，因此服务端模式需要显式发送 [ServerboundPunchPacket]。
     */
    fun attackSwing(hand: InteractionHand) {
        if (client()) {
            val player = mc.player ?: return
            player.swing(hand, player.getItemInHand(hand).attackAnimation, false)
        }
        if (server()) {
            mc.connection?.send(ServerboundPunchPacket.INSTANCE)
        }
    }

    /**
     * 播放交互挥手。
     *
     * 使用物品或交互包会由服务端自行触发挥手，这里只负责本地动画。
     */
    fun interactSwing(hand: InteractionHand) {
        if (client()) {
            val player = mc.player ?: return
            player.swing(hand, player.getItemInHand(hand).interactAnimation, false)
        }
    }

    companion object {

        /**
         * 将布尔设置映射为客户端或服务端模式。
         *
         * @param client `true` 时只播放本地动画，否则只发送挥手包
         */
        @JvmStatic
        fun of(client: Boolean): SwingMode = if (client) Client else Packet

    }

}
