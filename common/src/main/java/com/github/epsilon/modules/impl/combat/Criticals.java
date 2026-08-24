package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.Velocity;

public class Criticals extends Module {

    public static final Criticals INSTANCE = new Criticals();

    private Criticals() {
        super("Criticals", Category.COMBAT);
    }

    public int fallTicks;
    private boolean stopSprinting;

    @Override
    protected void onDisable() {
        fallTicks = 0;
        stopSprinting = false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;

        if (mc.player.fallDistance <= 0 || mc.player.fallDistance >= 1) {
            fallTicks = 0;
        } else {
            fallTicks++;
            if (KillAura.INSTANCE.target != null && fallTicks < 3 && Velocity.INSTANCE.attackQueue == 0) {
                stopSprinting = true;
            }
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (stopSprinting) {
            stopSprinting = false;
            event.setForward(0);
        }
    }

}
