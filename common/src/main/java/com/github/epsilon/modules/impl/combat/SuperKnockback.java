package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AttackEntityEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;

public class SuperKnockback extends Module {

    public static final SuperKnockback INSTANCE = new SuperKnockback();

    private SuperKnockback() {
        super("Super Knockback", Category.COMBAT);
    }

    private enum Mode {
        StopSprinting,
        Backwards,
        NoForwards
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.StopSprinting);
    private final IntSetting delay = intSetting("Delay", 50, 0, 500, 1);
    private final IntSetting hurtTime = intSetting("Hurt Time", 9, 0, 10, 1);

    private long lastAttack;
    private boolean stopSprint;

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (System.currentTimeMillis() - lastAttack >= delay.getValue() && mc.player.hurtTime <= hurtTime.getValue()) {
            stopSprint = true;
            lastAttack = System.currentTimeMillis();
        }
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (stopSprint) {
            switch (mode.getValue()) {
                case StopSprinting -> {
                    event.setSprint(false);
                    mc.player.setSprinting(false);
                    mc.options.keySprint.setDown(false);
                }
                case Backwards -> event.setForward(-1);
                case NoForwards -> event.setForward(0);
            }
            stopSprint = false;
        }
    }

}
