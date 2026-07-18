package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;

import java.awt.*;
import java.util.function.Supplier;

public class Watermark extends HudModule {

    public static final Watermark INSTANCE = new Watermark();

    private Watermark() {
        super("Watermark", 0f, 0f, 200f, 28f);
    }

    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(255, 255, 255, 235));

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    public void render(DeltaTracker deltaTracker) {
        TextRenderer textRenderer = textRendererSupplier.get();

        String traditionText = "EPSILON";
        float scaledScale = scale.getValue().floatValue() * 2f; // 这个命名给我自己整笑了

        renderScope().text(traditionText, this.x, this.y, scaledScale, textColor.getValue(), StaticFontLoader.OSAKA_CHIPS);

        float totalWidth = textRenderer.getWidth(traditionText, scaledScale, StaticFontLoader.OSAKA_CHIPS) + 3f * scaledScale;
        float totalHeight = textRenderer.getHeight(scaledScale, StaticFontLoader.OSAKA_CHIPS) + 3f * scaledScale;

        setBounds(totalWidth, totalHeight);
    }

}
