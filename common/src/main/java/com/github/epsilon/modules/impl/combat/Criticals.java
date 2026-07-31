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
    private boolean stopSprint;

    @Override
    public void onDisable() {
        fallTicks = 0;
        stopSprint = false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;

        if (mc.player.fallDistance <= 0 || mc.player.fallDistance >= 1) {
            fallTicks = 0;
        } else {
            fallTicks++;
            KillAura killAura = KillAura.INSTANCE;
            if (killAura.target != null && Velocity.INSTANCE.attackQueue == 0) {
                stopSprint = true;
            }
        }
    }

    @EventHandler
    private void onMoveInput(KeyboardInputEvent event) {
        if (!stopSprint) return;
        stopSprint = false;
        event.setForward(0);
    }

}
