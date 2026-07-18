package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.text.SystemEmojiAtlas;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import net.minecraft.client.DeltaTracker;

import java.awt.*;

public class MTF extends HudModule {

    public static final MTF INSTANCE = new MTF();

    private MTF() {
        super("MTF", 0f, 0f, 36f, 36f);
    }

    private final DoubleSetting size = doubleSetting("Size", 32.0, 12.0, 96.0, 1.0);
    private final DoubleSetting speed = doubleSetting("Speed", 160.0, -720.0, 720.0, 5.0);
    private final ColorSetting color = colorSetting("Color", new Color(255, 255, 255, 255), true);

    @Override
    public void render(DeltaTracker deltaTracker) {
        float boxSize = size.getValue().floatValue();
        setBounds(boxSize, boxSize);

        float originX = this.x + boxSize / 2.0f;
        float originY = this.y + boxSize / 2.0f;
        float rotation = (System.currentTimeMillis() % 3_600_000L) / 1000.0f * speed.getValue().floatValue();

        final String fishcake = "\uD83C\uDF65";
        SystemEmojiAtlas.EmojiGlyph glyph = SystemEmojiAtlas.INSTANCE.get(fishcake);

        renderScope().rotatedTexture(glyph.texture(), this.x, this.y, boxSize, boxSize, glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1(), color.getValue(), originX, originY, rotation);
    }

}
