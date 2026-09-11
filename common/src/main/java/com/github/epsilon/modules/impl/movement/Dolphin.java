package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import net.minecraft.world.phys.Vec3;

public class Dolphin extends Module {

    public static final Dolphin INSTANCE = new Dolphin();

    private Dolphin() {
        super("Dolphin", Category.MOVEMENT);
    }

    @EventHandler
    private void onTick(PlayerTickEvent.Pre event) {
        if (!mc.player.isInWater() || mc.player.isShiftKeyDown()) return;

        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(velocity.x, velocity.y + 0.04, velocity.z);
    }

}
