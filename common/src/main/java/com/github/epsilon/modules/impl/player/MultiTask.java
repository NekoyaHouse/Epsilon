package com.github.epsilon.modules.impl.player;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class MultiTask extends Module {

    public static final MultiTask INSTANCE = new MultiTask();

    private MultiTask() {
        super("Multi Task", Category.PLAYER);
    }

}
