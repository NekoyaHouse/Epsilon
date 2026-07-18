package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.AfterRender3DEvent;
import com.github.epsilon.graphics.shaders.FXAAShader;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;

public class AntiAlias extends Module {

    public static final AntiAlias INSTANCE = new AntiAlias();

    private AntiAlias() {
        super("Anti Alias", Category.RENDER);
    }

    @EventHandler
    private void onAfterRender3D(AfterRender3DEvent event) {
        FXAAShader.INSTANCE.renderMainTarget();
    }

}
