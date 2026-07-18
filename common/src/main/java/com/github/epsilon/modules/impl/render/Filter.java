package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.AfterRender3DEvent;
import com.github.epsilon.graphics.shaders.FilterShader;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.EnumSetting;

import java.awt.*;

public class Filter extends Module {

    public static final Filter INSTANCE = new Filter();

    private enum Mode {
        Shader,
        LightMap
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Shader);
    private final ColorSetting baseColor = colorSetting("Base Color", new Color(70, 70, 150, 50), true);

    private Filter() {
        super("Filter", Category.RENDER);
    }

    public boolean isLightMapMode() {
        return isEnabled() && mode.is(Mode.LightMap);
    }

    public Color getLightMapColor() {
        return new Color(baseColor.getValue().getRed(), baseColor.getValue().getGreen(), baseColor.getValue().getBlue(), 255);
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onAfterRender3D(AfterRender3DEvent event) {
        if (mode.is(Mode.Shader)) {
            Color color = this.baseColor.getValue();
            if (color.getAlpha() <= 0) return;
            FilterShader.INSTANCE.renderToMainTarget(color);
        }
    }

}
