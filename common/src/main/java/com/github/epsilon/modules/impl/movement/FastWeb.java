package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.player.MoveUtils;
import com.github.epsilon.utils.player.PlayerUtils;

public class FastWeb extends Module {

    public static final FastWeb INSTANCE = new FastWeb();

    private FastWeb() {
        super("Fast Web", Category.MOVEMENT);
    }

    private enum Mode {
        Vanilla,
        GrimMotion
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.GrimMotion);
    private final BoolSetting onlyOnGround = boolSetting("Only On Ground", false, () -> mode.is(Mode.GrimMotion));

    @EventHandler
    private void onSendPosition(SendPositionEvent event) {
        if (nullCheck() || mode.is(Mode.Vanilla)) return;

        if (!PlayerUtils.isInWeb()) {
            return;
        }

        if (!mc.player.isMoving()) {
            return;
        }

        if (mc.player.onGround() || !onlyOnGround.getValue()) {
            double[] forward = MoveUtils.forward(0.63);
            mc.player.setDeltaMovement(forward[0], mc.player.getDeltaMovement().y, forward[1]);
        }
    }

    public boolean isVanillaMode() {
        return isEnabled() && mode.is(Mode.Vanilla);
    }

}
