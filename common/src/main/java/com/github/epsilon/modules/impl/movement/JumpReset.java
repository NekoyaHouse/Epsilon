package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class JumpReset extends Module {

    public static final JumpReset INSTANCE = new JumpReset();

    private JumpReset() {
        super("Jump Reset", Category.MOVEMENT);
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (mc.player.onGround() && mc.player.hurtTime == 9) {
            event.setJump(true);
        }
    }

}
