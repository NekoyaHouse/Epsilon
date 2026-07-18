package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.world.phys.HitResult;

public class KeepSprint extends Module {

    public static final KeepSprint INSTANCE = new KeepSprint();

    private KeepSprint() {
        super("Keep Sprint", Category.MOVEMENT);
    }

    public final IntSetting slowdown = intSetting("Slowdown", 0, 0, 100, 1);
    private final BoolSetting groundOnly = boolSetting("Ground Only", false);
    private final BoolSetting prediction = boolSetting("Prediction", false);
    private final BoolSetting reachOnly = boolSetting("Reach Only", false);

    private boolean can;

    @Override
    public String getInfo() {
        return slowdown.getValue() + "%";
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        can = false;
    }

    @EventHandler
    private void onPostTick(PlayerTickEvent.Post event) {
        can = true;
    }

    public boolean shouldKeepSprint() {
        if (prediction.getValue() && !can) return false;
        if (groundOnly.getValue() && !mc.player.onGround()) return false;
        if (reachOnly.getValue()) {
            HitResult hitResult = mc.hitResult;
            if (hitResult == null || hitResult.getType() == HitResult.Type.MISS) return false;
            return hitResult.getLocation().distanceTo(mc.player.getEyePosition()) > 3.0;
        }
        return true;
    }

}
