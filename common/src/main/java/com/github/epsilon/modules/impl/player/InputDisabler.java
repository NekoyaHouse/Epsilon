package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.utils.network.NetworkUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

public class InputDisabler extends Module {

    public static final InputDisabler INSTANCE = new InputDisabler();

    private InputDisabler() {
        super("Input Disabler", Category.PLAYER);
    }

    private Input oldInput;
    private boolean shouldRestore;

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundContainerClickPacket || packet instanceof ServerboundContainerClosePacket) {
            event.cancel();
            spoofInput();
            NetworkUtils.sendPacketNoEvent(packet);
            restoreInput();
        }
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
