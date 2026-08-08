package com.github.epsilon.modules.impl.movement.noslow;

import com.github.epsilon.events.impl.AfterSendPositionEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.events.impl.SlowdownEvent;
import com.github.epsilon.modules.impl.player.Stealer;
import com.github.epsilon.utils.network.NetworkUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GrimFullMode extends NoSlowMode {

    private int useTicks = -1;
    private int swapCount;
    private boolean using;
    private boolean onceUse = true;
    private boolean pendingNoSlow;
    private boolean pendingUseItem;

    private final TimerUtils timer = new TimerUtils();
    private final Queue<Packet<? super ClientPacketListener>> packets = new ConcurrentLinkedQueue<>();

    protected GrimFullMode(NoSlow noSlow) {
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
    public void onClientTick() {
        if (mc.player.getTicksUsingItem() >= 33 && using) {
            mc.options.keyUse.setDown(false);
            return;
        }

        if ((Stealer.INSTANCE.isWorking() || !mc.player.isUsingItem()) && using) {
            flushPackets();
            sendReleaseSequence();
            resetState();
            return;
        }

        if (mc.player.getTicksUsingItem() == 1
                && !using
                && isHoldingConsumable()) {
            using = true;
            if (onceUse) {
                pendingNoSlow = true;
                onceUse = false;
            }
        }
    }

    @Override
    public void onSendPosition(SendPositionEvent event) {
        if (pendingNoSlow) {
            sendReleaseSequence();
            pendingNoSlow = false;
            pendingUseItem = true;
        }

        if (useTicks >= 0) {
            useTicks++;
        }
    }

    @Override
    public void onAfterSendPosition(AfterSendPositionEvent event) {
        if (pendingUseItem) {
            InteractionHand hand = isConsumable(mc.player.getMainHandItem())
                    ? InteractionHand.OFF_HAND
                    : InteractionHand.MAIN_HAND;
            mc.gameMode.useItem(mc.player, hand);
            pendingUseItem = false;
        }

        if (useTicks < noSlow.getReleaseTicks() || packets.isEmpty()) return;

        flushPackets();
        if (swapCount < 1 && isHoldingConsumable()) {
            mc.getConnection().send(swapPacket());
            swapCount++;
        }
        resetState();
    }

    @Override
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.level == null) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (!packets.isEmpty()) {
                event.cancel();
                packets.add((Packet<? super ClientPacketListener>) packet);
                flushPackets();
            }
            resetState();
            return;
        }

        if (packet instanceof ClientboundContainerSetSlotPacket || packet instanceof ClientboundContainerSetContentPacket) {
            if (using || !timer.passedMillise(100L)) {
                event.cancel();
                return;
            }
        }

        if (isHoldingConsumable()
                && (packet instanceof ClientboundPingPacket || packet instanceof ClientboundSetEntityMotionPacket)
                && using
                && !event.isCancelled()) {
            if (useTicks == -1) {
                useTicks = 0;
            }
            event.cancel();
            packets.add((Packet<? super ClientPacketListener>) packet);
        }
    }

    @Override
    public void onPacketSend(PacketEvent.Send event) {
        if (!isHoldingConsumable()
                || !(event.getPacket() instanceof ServerboundPlayerActionPacket packet)
                || packet.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
                || (!using && packets.isEmpty())
        ) {
            return;
        }

        mc.options.keyUse.setDown(false);
        event.cancel();
    }

    @Override
    public void onSlowdown(SlowdownEvent event) {
        if (mc.player.getTicksUsingItem() > 2 && using && isHoldingConsumable()) {
            event.setSlowdown(false);
            mc.player.setSprinting(true);
        }
    }

    @Override
    public boolean isWorking() {
        return using;
    }

    @Override
    public void stopIfWorking() {
        if (using) {
            mc.options.keyUse.setDown(false);
            mc.gameMode.releaseUsingItem(mc.player);
        }
    }

    private boolean isHoldingConsumable() {
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        if (!isConsumable(mainHand) && !isConsumable(offHand)) return false;
        return !isRangedWeapon(mainHand) && !isRangedWeapon(offHand);
    }

    private static boolean isConsumable(ItemStack itemStack) {
        if (itemStack.isEmpty()) return false;

        Item item = itemStack.getItem();
        return itemStack.has(DataComponents.FOOD)
                || item instanceof PotionItem
                || item == Items.SHIELD
                || item instanceof BowItem
                || item instanceof CrossbowItem;
    }

    private static boolean isRangedWeapon(ItemStack itemStack) {
        return itemStack.getItem() instanceof BowItem || itemStack.getItem() instanceof CrossbowItem;
    }

    private void sendReleaseSequence() {
        mc.getConnection().send(swapPacket());
        NetworkUtils.sendPacketNoEvent(releasePacket());
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
        useTicks = -1;
        swapCount = 0;
        using = false;
        onceUse = true;
        pendingNoSlow = false;
        pendingUseItem = false;
        timer.reset();
    }

}
