package com.github.epsilon.modules.impl;

import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;

import java.awt.*;

public class GuiSetting extends Module {

    public static final GuiSetting INSTANCE = new GuiSetting();

    private GuiSetting() {
        super("Gui Setting", null);
    }

    public final BoolSetting background = boolSetting("Background", false, () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));
    public final BoolSetting backgroundBlur = boolSetting("Background Blur", false, () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));
    public final IntSetting blurStrength = intSetting("Blur Strength", 12, 1, 20, 1, () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));
    public final BoolSetting windowOutline = boolSetting("Window Outline", true, () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));
    public final BoolSetting titleBar = boolSetting("Title Bar", false, () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));
    public final DoubleSetting xMargin = doubleSetting("X Margin", 2.0, 0.0, 10.0, 0.5, () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));
    public final DoubleSetting yMargin = doubleSetting("Y Margin", 1.5, 0.0, 10.0, 0.5, () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));
    public final ColorSetting primaryColor = colorSetting("Primary Color", new Color(255, 140, 180, 220), () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));
    public final ColorSetting backgroundColor = colorSetting("Background Color", new Color(0, 0, 0, 160), () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));
    public final ColorSetting textColor = colorSetting("Text Color", new Color(255, 250, 253, 255), () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));
    public final IntSetting hoverAlpha = intSetting("Hover Alpha", 32, 0, 255, 1, () -> ClientSetting.INSTANCE.guiMode.is(ClientSetting.GuiMode.TrollHack));

}
