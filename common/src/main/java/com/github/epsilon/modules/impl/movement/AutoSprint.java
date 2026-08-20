package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.player.InvManager;
import me.sofurry.ClInitNative;

@ClInitNative
public class AutoSprint extends Module {

    public static final AutoSprint INSTANCE = new AutoSprint();

    private AutoSprint() {
        super("Auto Sprint", Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        if (mc.options.keySprint.isDown()) {
            mc.options.keySprint.setDown(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;
        boolean sprintTransition = InvManager.INSTANCE.isEnabled() && InvManager.INSTANCE.isSprintTransitionPending();
        if (sprintTransition) return;

        boolean shouldSprint = mc.gui.screen() == null;
        mc.options.keySprint.setDown(shouldSprint);
    }

}
