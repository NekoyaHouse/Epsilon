package com.github.epsilon.modules.impl.movement.noslow;

import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.events.impl.SlowdownEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.utils.network.NetworkUtils;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GrimBlinkMode extends NoSlowMode {

    private final Queue<Packet<? super ClientPacketListener>> packets = new ConcurrentLinkedQueue<>();

    private int ticks;
    private int useDuration = 32;
    private boolean eating;

    protected GrimBlinkMode(NoSlow noSlow) {
        super(noSlow);
    }

    @Override
    public void onEnable() {
        packets.clear();
        resetState();
    }

    @Override
    public void onDisable() {
        flushPackets();
        resetState();
    }

    @Override
    public void onGameLeft() {
        packets.clear();
        resetState();
    }

    @Override
    public void onSendPosition(SendPositionEvent event) {
        if (eating) {
            ticks++;
            if (Math.toIntExact(packets.stream().filter(packet -> packet instanceof ClientboundPingPacket).count()) > 150 && ticks > 1) {
                Managers.NOTIFICATION.error(noSlow.getTranslatedName(), "Transaction count too high!");
                mc.options.keyUse.setDown(false);
                resetState();
            }
        }

        ItemUseAnimation animation = mc.player.getUseItem().getUseAnimation();
        if (mc.player.isUsingItem()
                && (animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK)
                && !eating
        ) {
            eating = true;
            ticks = 0;
            ItemStack useItem = mc.player.getUseItem();
            useDuration = useItem.getItem().getUseDuration(useItem, mc.player);
        }

        if (!eating) return;

        if (ticks == 1) {
            NetworkUtils.sendPacketNoEvent(swapPacket());
            NetworkUtils.sendPacketNoEvent(releasePacket());
        }

        if (ticks == 2) {
            InteractionHand hand = mc.player.getUsedItemHand() == InteractionHand.OFF_HAND
                    ? InteractionHand.MAIN_HAND
                    : InteractionHand.OFF_HAND;
            mc.getConnection().send(new ServerboundUseItemPacket(
                    hand,
                    mc.level.getBlockStatePredictionHandler().startPredicting().currentSequence(),
                    Managers.ROTATION.getYaw(),
                    Managers.ROTATION.getPitch()
            ));
        }

        if (ticks > useDuration + 3) {
            mc.getConnection().send(releasePacket());
            mc.options.keyUse.setDown(false);
            resetState();
        }
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player.tickCount < 30 || !eating) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (!packets.isEmpty()) {
                event.cancel();
                queuePacket(packet);
                mc.execute(this::flushPackets);
            }
            return;
        }

        if (isImmediatePacket(packet)) return;

        if (packet.type().flow() == PacketFlow.CLIENTBOUND) {
            event.cancel();
            queuePacket(packet);
        }
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        if (!eating || !(event.getPacket() instanceof ServerboundPlayerActionPacket packet) || packet.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM)
            return;
        resetState();
        flushPackets();
        NetworkUtils.sendPacketNoEvent(swapPacket());
    }

    @Override
    public void onSlowdown(SlowdownEvent event) {
        ItemUseAnimation animation = mc.player.getUseItem().getUseAnimation();
        if (mc.player.isUsingItem() && mc.player.getUseItemRemainingTicks() < 30 && (animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK)) {
            event.setSlowdown(false);
            mc.player.setSprinting(true);
        }
    }

    @Override
    public boolean isWorking() {
        return eating;
    }

    @Override
    public void stopIfWorking() {
        if (eating) {
            mc.options.keyUse.setDown(false);
            mc.gameMode.releaseUsingItem(mc.player);
        }
    }

    private boolean isImmediatePacket(Packet<?> packet) {
        return packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundTeleportEntityPacket
                || packet instanceof ClientboundEntityEventPacket
                || packet instanceof ClientboundAddEntityPacket
                || packet instanceof ClientboundBlockUpdatePacket
                || packet instanceof ClientboundBlockEventPacket;
    }

    @SuppressWarnings("unchecked")
    private void queuePacket(Packet<?> packet) {
        packets.add((Packet<? super ClientPacketListener>) packet);
    }

    private void flushPackets() {
        if (mc.getConnection() == null) {
            packets.clear();
            return;
        }

        Packet<? super ClientPacketListener> packet;
        while ((packet = packets.poll()) != null) {
            packet.handle(mc.getConnection());
        }
    }

    private void resetState() {
        ticks = 0;
        useDuration = 32;
        eating = false;
    }

    private static ServerboundPlayerActionPacket swapPacket() {
        return new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN
        );
    }

    private static ServerboundPlayerActionPacket releasePacket() {
        return new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN
        );
    }

}
