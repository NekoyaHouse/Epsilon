package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.SlowdownEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NoSlow extends Module {

    public static final NoSlow INSTANCE = new NoSlow();

    private NoSlow() {
        super("No Slow", Category.MOVEMENT);
    }

    private enum Mode {
        Vanilla,
        Jump,
        GrimC0F,
        Grim1_2,
        Grim1_3
    }

    private enum Step {
        NONE,
        CANCEL_C0F,
        SWAP_HANDS,
        EATING
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Vanilla);
    private final BoolSetting food = boolSetting("Food", true);
    private final BoolSetting bow = boolSetting("Bow", true);
    private final BoolSetting crossbow = boolSetting("Crossbow", true);

    private int onGroundTick = 0;

    private Step step = Step.NONE;
    private int noUsingItemTicks = 0;
    private final Queue<Packet<?>> packets = new ConcurrentLinkedQueue<>();

    @Override
    protected void onEnable() {
        onGroundTick = 0;
    }

    @Override
    protected void onDisable() {
        step = Step.NONE;
        noUsingItemTicks = 0;
        releasePackets();
    }

    @EventHandler
    private void onSlowdown(SlowdownEvent event) {
        if (nullCheck()) return;

        if (mc.player.onGround()) {
            onGroundTick++;
        } else {
            onGroundTick = 0;
        }

        if (!food.getValue() && mc.player.getUseItem().has(DataComponents.FOOD)) return;
        if (!bow.getValue() && mc.player.getUseItem().is(Items.BOW)) return;
        if (!crossbow.getValue() && mc.player.getUseItem().is(Items.CROSSBOW)) return;

        switch (mode.getValue()) {
            case Vanilla -> cancel(event);
            case Jump -> jump(event);
            case Grim1_2 -> grim50(event);
            case Grim1_3 -> grim33(event);
            case GrimC0F -> grimC0F(event);
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mode.is(Mode.Jump) && mc.player.onGround() && mc.player.isUsingItem() && (event.getForward() != 0 || event.getStrafe() != 0)) {
            event.setJump(true);
        }
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || !mode.is(Mode.GrimC0F)) return;

        if (step != Step.EATING) {
            noUsingItemTicks = 0;
            return;
        }

        if (mc.player.isUsingItem()) {
            noUsingItemTicks = 0;
        } else {
            noUsingItemTicks++;
            if (noUsingItemTicks >= 5) {
                releasePackets();
                swap();
            }
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!mode.is(Mode.GrimC0F)) return;

        Packet<?> packet = event.getPacket();

        if (packet instanceof ServerboundPongPacket && step != Step.NONE) {
            event.cancel();
            packets.add(packet);

            if (step == Step.CANCEL_C0F) {
                step = Step.SWAP_HANDS;
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ZERO,
                        Direction.DOWN
                ));
            }
        }

        if (packet instanceof ServerboundPlayerActionPacket actionPacket) {
            if (actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM && step == Step.EATING) {
                releasePackets();
                swap();
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!mode.is(Mode.GrimC0F)) return;
        if (event.getPacket() instanceof ClientboundContainerSetSlotPacket && step == Step.SWAP_HANDS) {
            mc.options.keyUse.setDown(true);
            step = Step.EATING;
        }
    }

    private void cancel(SlowdownEvent event) {
        event.setSlowdown(false);
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

    private void grimC0F(SlowdownEvent event) {
        ItemUseAnimation activeUseAnim = mc.player.getUseItem().getUseAnimation();
        if (!isUsable(activeUseAnim) || mc.player.getUseItemRemainingTicks() <= 0) {
            return;
        }

        InteractionHand oppositeHand = mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        if (isUsable(mc.player.getItemInHand(oppositeHand).getUseAnimation())) {
            return;
        }

        if (step != Step.EATING) {
            mc.options.keyUse.setDown(false);
        }

        if (step == Step.NONE) {
            step = Step.CANCEL_C0F;

            boolean isInventoryOpenServerSide = mc.player.containerMenu != mc.player.inventoryMenu;
            if (isInventoryOpenServerSide) {
                mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            }
        } else if (step == Step.EATING) {
            mc.player.setSprinting(true);
            event.setSlowdown(false);
        }
    }

    private void releasePackets() {
        step = Step.NONE;

        while (!packets.isEmpty()) {
            Packet<?> p = packets.poll();
            if (p != null && mc.getConnection() != null) {
                mc.getConnection().send(p);
            }
        }
    }

    private void swap() {
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO,
                    Direction.DOWN
            ));
        }
    }

    private boolean isUsable(ItemUseAnimation useAnim) {
        return useAnim == ItemUseAnimation.EAT || useAnim == ItemUseAnimation.DRINK;
    }

}
