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

    private boolean hasActivated;
    private int clock, switchClock;

    @Override
    protected void onEnable() {
        resetState();
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (mc.gui.screen() != null) return;

        if (hasActivated) {
            if (switchBack.getValue()) {
                handleSwitchBack();
            } else {
                resetState();
            }
        }

        boolean pressed = KeybindUtils.isPressed(activateKey.getValue());
        if (pressed) {
            FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
            if (!pearl.found()) {
                resetState();
                return;
            }

            if (mc.player.getCooldowns().isOnCooldown(Items.ENDER_PEARL.getDefaultInstance())) {
                return;
            }

            InvUtils.swap(pearl.slot(), switchBack.getValue());

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
        }
    }

    private void handleSwitchBack() {
        if (switchClock < switchDelay.getValue()) {
            switchClock++;
            return;
        }
        InvUtils.swapBack();
        resetState();
    }

    private void resetState() {
        clock = 0;
        switchClock = 0;
        hasActivated = false;
    }

}
