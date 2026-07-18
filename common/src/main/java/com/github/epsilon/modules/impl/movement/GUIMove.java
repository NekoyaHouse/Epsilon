package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;

public class GUIMove extends Module {

    public static final GUIMove INSTANCE = new GUIMove();

    private GUIMove() {
        super("GUI Move", Category.MOVEMENT);
    }

    private BoolSetting sneakValue = boolSetting("Sneak", false);

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mc.screen == null || mc.screen instanceof ChatScreen) return;

        boolean up = isKeyDown(mc.options.keyUp);
        boolean down = isKeyDown(mc.options.keyDown);
        boolean left = isKeyDown(mc.options.keyLeft);
        boolean right = isKeyDown(mc.options.keyRight);
        boolean jump = isKeyDown(mc.options.keyJump);
        boolean sneak = isKeyDown(mc.options.keyShift);
        boolean sprint = isKeyDown(mc.options.keySprint);

        float forward = (up == down) ? 0.0F : (up ? 1.0F : -1.0F);
        float strafe = (left == right) ? 0.0F : (left ? 1.0F : -1.0F);

        event.setForward(forward);
        event.setStrafe(strafe);
        event.setJump(jump);
        if (sneakValue.getValue()) event.setSneak(sneak);
        event.setSprint(sprint);
    }

    private boolean isKeyDown(KeyMapping mapping) {
        return InputConstants.isKeyDown(mc.getWindow(), mapping.getDefaultKey().getValue());
    }

}
