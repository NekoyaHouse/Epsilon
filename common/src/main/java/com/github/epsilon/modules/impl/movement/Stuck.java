package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.utils.network.NetworkUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Stuck extends Module {

    public static final Stuck INSTANCE = new Stuck();

    private Stuck() {
        super("Stuck", Category.MOVEMENT);
    }

    private int ticks;
    private int stage;
    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();

    @Override
    protected void onEnable() {
        ticks = 20;
        stage = 0;
        packets.clear();
    }

    @Override
    protected void onDisable() {
        packets.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (stage == 0) {
            if (packet instanceof ServerboundUseItemPacket || packet instanceof ServerboundUseItemOnPacket) {
                packets.add(packet);
                event.cancel();
                stage = 1;
            }
        }
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (mc.player.onGround()) {
            setEnabled(false);
            return;
        }
        switch (stage) {
            case 0 -> {
                if (ticks > 0) {
                    event.cancel();
                    if (ticks == 10) {
                        NetworkUtils.sendPacketNoEvent(new ServerboundMovePlayerPacket.StatusOnly(mc.player.onGround(), mc.player.horizontalCollision));
                    }
                    ticks--;
                } else {
                    ticks = 20;
                }
            }
            case 1 -> stage = 2;
            case 2 -> {
                while (!packets.isEmpty()) {
                    Packet<?> poll = packets.poll();
                    NetworkUtils.sendPacketNoEvent(poll);
                }
                stage = 0;
            }
        }
    }

}
