package com.github.epsilon.modules.impl.player;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.IntSetting;

public class JumpCooldown extends Module {

    public static final JumpCooldown INSTANCE = new JumpCooldown();

    private JumpCooldown() {
        super("Jump Cooldown", Category.PLAYER);
    }

    public final IntSetting cooldown = intSetting("Cooldown", 0, 0, 9, 1);

}
