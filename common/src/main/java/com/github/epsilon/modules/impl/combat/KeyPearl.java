package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public class KeyPearl extends Module {

    public static final KeyPearl INSTANCE = new KeyPearl();

    private KeyPearl() {
        super("Key Pearl", Category.COMBAT);
    }

    private final KeybindSetting activateKey = keybindSetting("Activate Key", GLFW.GLFW_KEY_UNKNOWN);
    private final IntSetting delay = intSetting("Delay", 0, 0, 20, 1);
    private final BoolSetting switchBack = boolSetting("Switch Back", true);
    private final IntSetting switchDelay = intSetting("Switch Delay", 0, 0, 20, 1);
    private final BoolSetting swingHand = boolSetting("Swing Hand", true);

    private boolean active, hasActivated;
    private int clock, previousSlot, switchClock;
    private boolean isKeyDown;

    @Override
    protected void onEnable() {
        resetState();
        isKeyDown = false;
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || mc.screen != null) return;

        boolean pressed = KeybindUtils.isPressed(activateKey.getValue());
        if (!pressed) {
            isKeyDown = false;
        }

        if (pressed) {
            if (isKeyDown && !active) return;
            isKeyDown = true;
            active = true;
        }

        if (active) {
            if (previousSlot == -1)
                previousSlot = mc.player.getInventory().getSelectedSlot();

            FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
            if (!pearl.found()) {
                resetState();
                return;
            }

            InvUtils.swap(pearl.slot(), false);

            if (clock < delay.getValue()) {
                clock++;
                return;
            }

            if (!hasActivated) {
                InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                if (result.consumesAction()) {
                    if (swingHand.getValue()) {
                        mc.player.swing(InteractionHand.MAIN_HAND);
                    } else {
                        mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
                    }
                }
                hasActivated = true;
            }

            if (switchBack.getValue()) {
                handleSwitchBack();
            } else {
                resetState();
            }
        }
    }

    private void handleSwitchBack() {
        if (switchClock < switchDelay.getValue()) {
            switchClock++;
            return;
        }
        if (previousSlot != -1) {
            InvUtils.swap(previousSlot, false);
        }
        resetState();
    }

    private void resetState() {
        previousSlot = -1;
        clock = 0;
        switchClock = 0;
        active = false;
        hasActivated = false;
    }

}
