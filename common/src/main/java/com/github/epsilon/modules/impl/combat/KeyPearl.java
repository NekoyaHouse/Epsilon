package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyPressEvent;
import com.github.epsilon.events.impl.MousePressEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.NotificationManager;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.NoSlowdown;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.settings.impl.KeybindSetting;
import com.github.epsilon.utils.client.KeybindUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.mojang.blaze3d.platform.InputConstants;
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
    private final BoolSetting pauseOnEat = boolSetting("Pause On Eat", true);
    private final IntSetting delay = intSetting("Delay", 0, 0, 20, 1);
    private final BoolSetting switchBack = boolSetting("Switch Back", true);
    private final IntSetting switchDelay = intSetting("Switch Delay", 0, 0, 20, 1);
    private final BoolSetting swingHand = boolSetting("Swing Hand", true);

    private boolean pressed;
    private boolean hasActivated;
    private int clock, switchClock;

    @Override
    protected void onEnable() {
        resetState();
        pressed = false;
    }

    @Override
    protected void onDisable() {
        resetState();
        pressed = false;
    }

    @EventHandler
    private void onKeyPress(KeyPressEvent event) {
        if (event.getKey() == activateKey.getValue()) {
            if (event.getAction() == InputConstants.RELEASE) {
                pressed = false;
            } else if (mc.gui.screen() == null) {
                pressed = true;
            }
        }
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (KeybindUtils.encodeMouseButton(event.getButton()) == activateKey.getValue()) {
            if (event.getAction() == InputConstants.RELEASE) {
                pressed = false;
            } else if (mc.gui.screen() == null) {
                pressed = true;
            }
        }
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (mc.gui.screen() != null) {
            pressed = false;
            return;
        }

        if (hasActivated) {
            if (switchBack.getValue()) {
                handleSwitchBack();
            } else {
                resetState();
            }
        }

        if (pressed) {
            FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
            if (!pearl.found()) {
                resetState();
                return;
            }

            if (mc.player.getCooldowns().isOnCooldown(Items.ENDER_PEARL.getDefaultInstance())) {
                return;
            }

            if (pauseOnEat.getValue() && (PlayerUtils.isEating() || NoSlowdown.INSTANCE.isWorking())) {
                return;
            } else {
                NoSlowdown noSlowdown = NoSlowdown.INSTANCE;
                if (noSlowdown.isWorking()) {
                    NotificationManager.INSTANCE.warning(noSlowdown.getTranslatedName(), EpsilonTranslations.Notifications.NO_SLOWDOWN_DISABLED_PEARL.getTranslatedName());
                    noSlowdown.stop();
                }
            }

            // 强制给你的转头归位
            RotationManager.INSTANCE.setRotations(new Rot2f(mc.player.getYRot(), mc.player.getXRot()), 180.0f, Priority.Highest);

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
