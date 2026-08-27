package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.events.impl.SlowdownEvent;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.network.NetworkUtils;
import me.sofurry.ClInitNative;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

@ClInitNative
public class NoSlowdown extends Module {

    public static final NoSlowdown INSTANCE = new NoSlowdown();

    private NoSlowdown() {
        super("No Slowdown", Category.MOVEMENT);
    }

    private enum Mode {
        Vanilla,
        GrimBlink,
        Grim1_2,
        Grim1_3
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Vanilla);
    private final BoolSetting food = boolSetting("Food", true);
    private final BoolSetting bow = boolSetting("Bow", true, () -> !mode.is(Mode.GrimBlink));
    private final BoolSetting crossbow = boolSetting("Crossbow", true, () -> !mode.is(Mode.GrimBlink));
    private final BoolSetting cobweb = boolSetting("Cobweb", true, () -> mode.is(Mode.Vanilla));

    private int ticks;
    private boolean eating;
    private int useDuration = 32;

    private final Queue<Packet<?>> packets = new LinkedBlockingQueue<>();

    public boolean isWorking() {
        return isEnabled() && mode.is(Mode.GrimBlink) && eating;
    }

    public void stop() {
        mc.options.keyUse.setDown(false);
        mc.gameMode.releaseUsingItem(mc.player);
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
        if (!mode.is(Mode.GrimBlink)) {
            return;
        }

        if (eating) {
            ticks++;
            if (Math.toIntExact(packets.stream().filter(packet -> packet instanceof ClientboundPingPacket).count()) > 150 && ticks > 1) {
                NotificationManager.INSTANCE.error(this.getTranslatedName(), EpsilonTranslations.Notifications.TRANSACTION_COUNT_TOO_HIGH.getTranslatedName());
                mc.options.keyUse.setDown(false);
                onDisable();
            }
        }

        if (mc.player.isUsingItem() && isFoodOrDrink(mc.player.getUseItem()) && !eating) {
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
                mc.getConnection().send(new ServerboundUseItemPacket(hand, mc.level.getBlockStatePredictionHandler().startPredicting().currentSequence(), RotationManager.INSTANCE.getYaw(), RotationManager.INSTANCE.getPitch()));
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
        if (nullCheck() || mc.player.tickCount < 30 || !eating || !mode.is(Mode.GrimBlink)) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ClientboundPlayerPositionPacket) {
            flush();
            return;
        }

        if (packet instanceof ClientboundSetHealthPacket
                || packet instanceof ClientboundSystemChatPacket
                || packet instanceof ClientboundMoveEntityPacket
                || packet instanceof ClientboundTeleportEntityPacket
                || packet instanceof ClientboundEntityEventPacket
                || packet instanceof ClientboundAddEntityPacket
                || packet instanceof ClientboundBlockUpdatePacket
                || packet instanceof ClientboundBlockEventPacket) {
            return;
        }

        if (packet.type().flow() == PacketFlow.CLIENTBOUND) {
            event.cancel();
            packets.add(packet);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mode.is(Mode.GrimBlink) && eating && event.getPacket() instanceof ServerboundPlayerActionPacket packet && packet.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            eating = false;
            ticks = 0;
            flush();
            NetworkUtils.sendPacketNoEvent(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        }
    }

    @EventHandler
    private void onSlowdown(SlowdownEvent event) {
        if (!food.getValue() && mc.player.getUseItem().has(DataComponents.FOOD)) return;
        if ((!bow.getValue() || mode.is(Mode.GrimBlink)) && mc.player.getUseItem().is(Items.BOW)) return;
        if ((!crossbow.getValue() || mode.is(Mode.GrimBlink)) && mc.player.getUseItem().is(Items.CROSSBOW)) return;

        switch (mode.getValue()) {
            case Vanilla -> cancel(event);
            case GrimBlink -> grimBlink(event);
            case Grim1_2 -> grim50(event);
            case Grim1_3 -> grim33(event);
        }
    }

    private void cancel(SlowdownEvent event) {
        event.setSlowdown(false);
    }

    private void grimBlink(SlowdownEvent event) {
        if (mc.player.isUsingItem() && mc.player.getUseItemRemainingTicks() < 30 && !(isFoodOrDrink(mc.player.getMainHandItem()) && isFoodOrDrink(mc.player.getOffhandItem())) && isFoodOrDrink(mc.player.getUseItem())) {
            event.setSlowdown(false);
            mc.player.setSprinting(true);
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

    private boolean isFoodOrDrink(ItemStack stack) {
        ItemUseAnimation anim = stack.getUseAnimation();
        return anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
    }

    private void flush() {
        if (mc.getConnection() == null) {
            packets.clear();
        } else {
            Packet packet;
            while ((packet = packets.poll()) != null) {
                packet.handle(mc.getConnection());
            }
        }
    }

    public boolean noWeb() {
        return isEnabled() && mode.is(Mode.Vanilla) && cobweb.getValue();
    }

}
