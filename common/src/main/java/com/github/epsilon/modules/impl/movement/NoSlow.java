package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.events.impl.SlowdownEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.network.NetworkUtils;
import love.slmpc.ClInitNative;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

import java.util.concurrent.LinkedBlockingQueue;

@ClInitNative
public class NoSlow extends Module {

    public static final NoSlow INSTANCE = new NoSlow();

    private NoSlow() {
        super("No Slow", Category.MOVEMENT);
    }

    private enum Mode {
        Vanilla,
        Jump,
        GrimFull,
        Grim1_2,
        Grim1_3
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Vanilla);
    private final BoolSetting food = boolSetting("Food", true);
    private final BoolSetting bow = boolSetting("Bow", true);
    private final BoolSetting crossbow = boolSetting("Crossbow", true);

    private int ticks;
    private int useDuration = 32;
    private int onGroundTick = 0;

    private boolean eating;

    private final LinkedBlockingQueue<Packet<ClientGamePacketListener>> packets = new LinkedBlockingQueue<>();

    public boolean isGrimFullWorking() {
        return isEnabled() && mode.is(Mode.GrimFull) && eating;
    }

    @Override
    protected void onDisable() {
        flush();
        eating = false;
        ticks = 0;
        useDuration = 32;
    }

    @EventHandler
    private void onSendPosition(SendPositionEvent event) {
        if (mc.player.onGround()) {
            onGroundTick++;
        } else {
            onGroundTick = 0;
        }

        if (!mode.is(Mode.GrimFull)) {
            return;
        }

        if (mc.player.tickCount < 30) {
            return;
        }

        if (eating) {
            ticks++;
        }

        ItemUseAnimation anim = mc.player.getUseItem().getUseAnimation();
        if (mc.player.isUsingItem() && (anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK || anim == ItemUseAnimation.CROSSBOW) && !eating) {
            eating = true;
            ticks = 0;
            ItemStack useItem = mc.player.getUseItem();
            useDuration = useItem.getItem().getUseDuration(useItem, mc.player);
        }

        if (eating) {
            if (ticks == 1) {
                NetworkUtils.sendPacketNoEvent(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                NetworkUtils.sendPacketNoEvent(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
            }
            if (ticks == 2) {
                InteractionHand hand = mc.player.getUsedItemHand() == InteractionHand.OFF_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                mc.getConnection().send(new ServerboundUseItemPacket(hand, mc.level.getBlockStatePredictionHandler().startPredicting().currentSequence(), mc.player.getYRot(), mc.player.getXRot()));
            }
            if (ticks > useDuration + 3) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
                mc.options.keyUse.setDown(false);
                ticks = 0;
                eating = false;
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck() || mc.player.tickCount < 30 || !eating || !mode.is(Mode.GrimFull)) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundPlayerPositionPacket) {
            flush();
            return;
        }

        if (
                packet instanceof ClientboundSetHealthPacket
                        || packet instanceof ClientboundSystemChatPacket
                        || packet instanceof ClientboundMoveEntityPacket
                        || packet instanceof ClientboundTeleportEntityPacket
                        || packet instanceof ClientboundEntityEventPacket
                        || packet instanceof ClientboundAddEntityPacket
                        || packet instanceof ClientboundBlockUpdatePacket
                        || packet instanceof ClientboundBlockEventPacket
        ) {
            return;
        }

        if (packet.type().flow() == PacketFlow.CLIENTBOUND) {
            event.cancel();
            packets.add((Packet<ClientGamePacketListener>) packet);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mode.is(Mode.GrimFull) && eating && event.getPacket() instanceof ServerboundPlayerActionPacket packet && packet.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            eating = false;
            ticks = 0;
            flush();
            NetworkUtils.sendPacketNoEvent(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        }
    }

    @EventHandler
    private void onSlowdown(SlowdownEvent event) {
        if (mc.player.tickCount < 30) return;

        if (!food.getValue() && mc.player.getUseItem().has(DataComponents.FOOD)) return;
        if (!bow.getValue() && mc.player.getUseItem().is(Items.BOW)) return;
        if (!crossbow.getValue() && mc.player.getUseItem().is(Items.CROSSBOW)) return;

        switch (mode.getValue()) {
            case Vanilla -> cancel(event);
            case GrimFull -> grimFull(event);
            case Jump -> jump(event);
            case Grim1_2 -> grim50(event);
            case Grim1_3 -> grim33(event);
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mode.is(Mode.Jump) && mc.player.onGround() && mc.player.isUsingItem() && (event.getForward() != 0 || event.getStrafe() != 0)) {
            event.setJump(true);
        }
    }

    private void cancel(SlowdownEvent event) {
        event.setSlowdown(false);
    }

    private void grimFull(SlowdownEvent event) {
        ItemUseAnimation anim = mc.player.getUseItem().getUseAnimation();
        if (mc.player.isUsingItem() && mc.player.getUseItemRemainingTicks() < 30 && (anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK)) {
            event.setSlowdown(false);
            mc.player.setSprinting(true);
        }
    }

    private void jump(SlowdownEvent event) {
        if (onGroundTick == 1 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setSlowdown(false);
        }
    }

    private void grim50(SlowdownEvent event) {
        if (mc.player.getUseItemRemainingTicks() % 2 == 0 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setSlowdown(false);
        }
    }

    private void grim33(SlowdownEvent event) {
        if (mc.player.getUseItemRemainingTicks() % 3 == 0 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setSlowdown(false);
        }
    }

    private void flush() {
        Packet<ClientGamePacketListener> packet;
        while ((packet = packets.poll()) != null) {
            packet.handle(mc.getConnection());
        }
    }

}
