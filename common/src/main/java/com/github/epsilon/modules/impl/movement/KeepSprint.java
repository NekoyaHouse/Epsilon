package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AfterSendPositionEvent;
import com.github.epsilon.events.impl.AttackSlowdownEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class KeepSprint extends Module {

    public static final KeepSprint INSTANCE = new KeepSprint();

    private KeepSprint() {
        super("Keep Sprint", Category.MOVEMENT);
    }

    private boolean shouldReSprint = false;

    @Override
    protected void onDisable() {
        shouldReSprint = false;
    }

    @EventHandler
    private void onAttackSlowdown(AttackSlowdownEvent event) {
        if (mc.player.isMoving()) shouldReSprint = true;
    }

    @EventHandler
    private void onMotion(AfterSendPositionEvent event) {
        if (shouldReSprint && mc.player.isMoving() && mc.player.getFoodData().getFoodLevel() > 6 && !mc.player.isBlocking()) {
            shouldReSprint = false;
            mc.player.setSprinting(true);
        }
    }

}
