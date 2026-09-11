package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;


public class ItemPhysics extends Module {

    public static final ItemPhysics INSTANCE = new ItemPhysics();

    private ItemPhysics() {
        super("Item Physics", Category.RENDER);
    }

}
