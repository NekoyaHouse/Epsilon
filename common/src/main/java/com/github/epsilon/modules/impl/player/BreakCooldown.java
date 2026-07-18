package com.github.epsilon.modules.impl.player;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.IntSetting;

public class BreakCooldown extends Module {

    public static final BreakCooldown INSTANCE = new BreakCooldown();

    private BreakCooldown() {
        super("Break Cooldown", Category.PLAYER);
    }

    public final IntSetting cooldown = intSetting("Cooldown", 0, 0, 5, 1);

}
