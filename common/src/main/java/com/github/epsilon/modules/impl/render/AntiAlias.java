package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.GameLeftEvent;
import com.github.epsilon.graphics.shaders.OverlayAntiAlias;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.EnumSetting;

public class AntiAlias extends Module {

    public static final AntiAlias INSTANCE = new AntiAlias();

    private final EnumSetting<Samples> samples = enumSetting("Samples", Samples.X4);

    private AntiAlias() {
        super("Anti Alias", Category.RENDER);
    }

    public enum Samples {
        X4(2), X16(4);

        private final int scale;

        Samples(int scale) {
            this.scale = scale;
        }
    }

    public void render(Runnable drawAction) {
        if (isEnabled()) {
            OverlayAntiAlias.INSTANCE.render(samples.getValue().scale, drawAction);
        } else {
            drawAction.run();
        }
    }

    @Override
    protected void onDisable() {
        mc.execute(() -> OverlayAntiAlias.INSTANCE.close());
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        mc.execute(() -> OverlayAntiAlias.INSTANCE.close());
    }

}
