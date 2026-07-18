package com.github.epsilon.modules.impl.player;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.DoubleSetting;

public class Timer extends Module {

    public static final Timer INSTANCE = new Timer();

    private Timer() {
        super("Timer", Category.PLAYER);
    }

    public final DoubleSetting multiplier = doubleSetting("Multiplier", 1.0, 0.1, 5.0, 0.1);

    @Override
    protected void onEnable() {
        Managers.TIMER.reset();
    }

    @Override
    protected void onDisable() {
        Managers.TIMER.reset();
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        Managers.TIMER.tryReset();
    }

}
