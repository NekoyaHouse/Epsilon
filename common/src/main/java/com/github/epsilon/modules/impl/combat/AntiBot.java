package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import net.minecraft.world.entity.Entity;

public class AntiBot extends Module {

    public static final AntiBot INSTANCE = new AntiBot();

    private AntiBot() {
        super("Anti Bot", Category.COMBAT);
    }

    public boolean isBot(Entity entity) {
        return isEnabled() && !mc.getConnection().getOnlinePlayerIds().contains(entity.getUUID());
    }

}
