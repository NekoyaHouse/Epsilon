package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.DoubleSetting;

public class AspectRatio extends Module {

    public static final AspectRatio INSTANCE = new AspectRatio();

    private AspectRatio() {
        super("Aspect Ratio", Category.RENDER);
    }

    public final DoubleSetting ratio = doubleSetting("Ratio", 1.78, 0.1, 8.0, 0.1);

}
