package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class AutoSprint extends Module {

    public static final AutoSprint INSTANCE = new AutoSprint();

    private AutoSprint() {
        super("Auto Sprint", Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        if (mc.options.keySprint.isDown()) mc.options.keySprint.setDown(false);
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        mc.options.keySprint.setDown(true);
    }

}
