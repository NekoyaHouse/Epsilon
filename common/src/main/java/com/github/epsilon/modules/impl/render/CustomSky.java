package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;

import java.awt.*;

/**
 * 在世界渲染的天空阶段结束后应用指定的天空着色器。
 */
public class CustomSky extends Module {

    public static final CustomSky INSTANCE = new CustomSky();

    public final EnumSetting<ShaderMode> shader = enumSetting("Shader", ShaderMode.Cine);
    public final DoubleSetting speed = doubleSetting("Speed", 4.0, 0.0, 20.0, 0.1);
    public final ColorSetting color = colorSetting("Color", new Color(255, 255, 255, 200));
    public final ColorSetting backgroundColor = colorSetting("Background Color (Local)", new Color(0, 0, 0, 255), () -> shader.is(ShaderMode.Local));

    private CustomSky() {
        super("CustomSky", Category.RENDER);
    }

    public enum ShaderMode {
        Cine,
        Euabe,
        Local
    }
}
