package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.network.PacketUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.player.Input;

public class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private Disabler() {
        super("Disabler", Category.PLAYER);
    }

    private final BoolSetting badPacketsA = boolSetting("BadPacketsA", true);

    private final BoolSetting sprinting = boolSetting("Sprinting", true);
    private final BoolSetting input = boolSetting("Input", true);

    private int lastSendSlot = -1;
    private boolean hasOldInput;
    private Input oldInput;
    private boolean shouldRestore;

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();

        if (badPacketsA.getValue()) {
            if (packet instanceof ServerboundSetCarriedItemPacket setCarriedItemPacket) {
                int slot = setCarriedItemPacket.getSlot();
                if (slot == lastSendSlot && slot != -1) {
                    event.cancel();
                }
                lastSendSlot = setCarriedItemPacket.getSlot();
            }
        }

        if (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerClosePacket) {
            event.cancel();

            boolean sprinted = false;

            if (input.getValue()) {
                spoofInput();
                hasOldInput = true;
            }

            if (sprinting.getValue() && mc.player.isSprinting()) {
                raoGuoSprinting(false);
                sprinted = true;
            }

            PacketUtils.sendSilently(packet);

            if (sprinted) {
                raoGuoSprinting(true);
            }

            if (input.getValue() && hasOldInput) {
                restoreInput();
                hasOldInput = false;
            }
        }
    }

    private void raoGuoSprinting(boolean sprintState) {
        mc.player.setSprinting(sprintState);
        mc.player.wasSprinting = sprintState; // BadPacketsF
        mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, sprintState ? ServerboundPlayerCommandPacket.Action.START_SPRINTING : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
    }

    private void spoofInput() {
        if (shouldRestore) return;
        oldInput = mc.player.input.keyPresses;
        mc.player.input.keyPresses = Input.EMPTY;
        mc.getConnection().send(new ServerboundPlayerInputPacket(Input.EMPTY));
        mc.player.lastSentInput = Input.EMPTY;
        shouldRestore = true;
    }

    private void restoreInput() {
        if (!shouldRestore) return;
        mc.player.input.keyPresses = oldInput;
        oldInput = null;
        shouldRestore = false;
    }

}
