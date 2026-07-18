package com.github.epsilon.modules.impl.player;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.IntSetting;

public class UseCooldown extends Module {

    public static final UseCooldown INSTANCE = new UseCooldown();

    private UseCooldown() {
        super("Use Cooldown", Category.PLAYER);
    }

    public final IntSetting cooldown = intSetting("Cooldown", 0, 0, 4, 1);

}
