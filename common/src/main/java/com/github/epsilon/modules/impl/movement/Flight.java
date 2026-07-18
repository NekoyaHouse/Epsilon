package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.utils.player.MoveUtils;

public class Flight extends Module {

    public static final Flight INSTANCE = new Flight();

    private Flight() {
        super("Flight", Category.MOVEMENT);
    }

    private final DoubleSetting horizontalSpeed = doubleSetting("Horizontal Speed", 3.5, 0.1, 10.0, 0.1);
    private final DoubleSetting verticalSpeed = doubleSetting("Vertical Speed", 1.0, 0.1, 5.0, 0.1);

    @Override
    protected void onDisable() {
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);
    }

    @EventHandler
    private void onSendPosition(SendPositionEvent event) {
        double y = 0.0;

        if (mc.options.keyJump.isDown()) {
            y = verticalSpeed.getValue();
        } else if (mc.options.keyShift.isDown()) {
            y = -verticalSpeed.getValue();
        }

        double[] strafe = MoveUtils.forward(horizontalSpeed.getValue());
        mc.player.setDeltaMovement(strafe[0], y, strafe[1]);
    }

}
