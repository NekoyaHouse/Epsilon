package com.github.epsilon.modules.impl

import com.github.epsilon.modules.Module
import java.awt.Color

object GuiSetting : Module("Gui Setting", null) {

    val background by boolSetting(
        "Background",
        false,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

    val backgroundBlur by boolSetting(
        "Background Blur",
        false,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

    val blurStrength by intSetting(
        "Blur Strength",
        12,
        1..20,
        1,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

    val windowOutline by boolSetting(
        "Window Outline",
        true,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

    val titleBar by boolSetting(
        "Title Bar",
        false,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

    val xMargin by doubleSetting(
        "X Margin",
        2.0,
        0.0..10.0,
        0.5,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

    val yMargin by doubleSetting(
        "Y Margin",
        1.5,
        0.0..10.0,
        0.5,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

    val primaryColor by colorSetting(
        "Primary Color",
        Color(255, 140, 180, 220),
        true,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

    val backgroundColor by colorSetting(
        "Background Color",
        Color(0, 0, 0, 160),
        true,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

    val textColor by colorSetting(
        "Text Color",
        Color(255, 250, 253, 255),
        true,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

    val hoverAlpha by intSetting(
        "Hover Alpha",
        32,
        0..255,
        1,
        { ClientSetting.guiMode.`is`(ClientSetting.GuiMode.TrollHack) }
    )

}
