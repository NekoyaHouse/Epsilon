package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.network.NetworkUtils;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;

public class Disabler extends Module {

    public static final Disabler INSTANCE = new Disabler();

    private Disabler() {
        super("Disabler", Category.PLAYER);
    }

    private final BoolSetting duplicateSlot = boolSetting("ACA Duplicate Slot", true);
    private final BoolSetting fastSwitch = boolSetting("ACA Fast Switch", true);

    private int lastSlot = -1;

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (duplicateSlot.getValue() && event.getPacket() instanceof ServerboundSetCarriedItemPacket packet) {
            int nextSlot = packet.getSlot();
            if (nextSlot < 0 || nextSlot > 8 || nextSlot == lastSlot && !fastSwitch.getValue()) {
                event.cancel();
                return;
            }
            if (fastSwitch.getValue() && lastSlot >= 0 && nextSlot != lastSlot && !((lastSlot == 0 && nextSlot == 8) || (lastSlot == 8 && nextSlot == 0))) {
                event.cancel();
                int step = lastSlot < nextSlot ? 1 : -1;
                for (int slot = lastSlot + step; slot != nextSlot; slot += step) {
                    NetworkUtils.sendPacketNoEvent(new ServerboundSetCarriedItemPacket(slot));
                }
                NetworkUtils.sendPacketNoEvent(packet);
            }
            lastSlot = nextSlot;
        }
    }

}
