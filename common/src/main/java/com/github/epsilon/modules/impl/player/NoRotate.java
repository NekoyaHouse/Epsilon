package com.github.epsilon.modules.impl.player;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class NoRotate extends Module {

    public static final NoRotate INSTANCE = new NoRotate();

    private NoRotate() {
        super("No Rotate", Category.PLAYER);
    }

}
