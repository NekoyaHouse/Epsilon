package com.github.epsilon.utils.player;

import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;

import static com.github.epsilon.Constants.mc;

/**
 * 挥手动画的客户端播放与服务端通知模式。
 */
public enum SwingMode {

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

    /**
     * 将布尔设置映射为客户端或服务端模式。
     *
     * @param client {@code true} 时只播放本地动画，否则只发送挥手包
     */
    public static SwingMode of(boolean client) {
        return client ? Client : Packet;
    }

    public boolean client() {
        return this == Client || this == Both;
    }

    private boolean server() {
        return this == Packet || this == Both;
    }

    /**
     * 播放攻击或挖掘挥手。
     *
     * <p>26.3 的服务端攻击包不会触发挥手，因此服务端模式需要显式发送 {@link ServerboundPunchPacket}。
     */
    public void attackSwing(InteractionHand hand) {
        if (client()) mc.player.swing(hand, mc.player.getItemInHand(hand).getAttackAnimation(), false);
        if (server()) mc.getConnection().send(ServerboundPunchPacket.INSTANCE);
    }

    /**
     * 播放交互挥手。
     *
     * <p>使用物品或交互包会由服务端自行触发挥手，这里只负责本地动画。
     */
    public void interactSwing(InteractionHand hand) {
        if (client()) mc.player.swing(hand, mc.player.getItemInHand(hand).getInteractAnimation(), false);
    }

}
