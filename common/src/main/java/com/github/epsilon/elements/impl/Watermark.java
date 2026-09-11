package com.github.epsilon.elements.impl;

import com.github.epsilon.Constants;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.graphics.text.TextGlitchEffect;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.settings.impl.*;
import com.github.epsilon.utils.render.ColorUtils;
import com.google.common.base.Suppliers;
import net.minecraft.client.DeltaTracker;

import java.awt.*;
import java.util.function.Supplier;

public class Watermark extends HudModule {

    public static final Watermark INSTANCE = new Watermark();

    private Watermark() {
        super("Watermark", 0f, 0f, 200f, 28f);
    }

    private enum Mode {
        Tradition,
        Glitch,
        Sakura
    }

    private enum FontMode {
        Default,
        OsakaChips
    }

    private enum ColorMode {
        Single,
        Double
    }

    private final StringSetting text = stringSetting("Text", Constants.NAME);
    private final EnumSetting<Mode> mode = enumSetting("Style", Mode.Tradition);
    private final EnumSetting<FontMode> fontMode = enumSetting("Font Mode", FontMode.OsakaChips);
    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(255, 255, 255, 235), () -> mode.is(Mode.Tradition));
    private final EnumSetting<ColorMode> colorMode = enumSetting("Color Mode", ColorMode.Single, () -> mode.is(Mode.Sakura));
    private final ColorSetting singleColor = colorSetting("Color", Color.WHITE, false, () -> mode.is(Mode.Sakura) && colorMode.is(ColorMode.Single));
    private final ColorSetting doubleColor1 = colorSetting("Color 1", Color.WHITE, false, () -> mode.is(Mode.Sakura) && colorMode.is(ColorMode.Double));
    private final ColorSetting doubleColor2 = colorSetting("Color 2", Color.RED, false, () -> mode.is(Mode.Sakura) && colorMode.is(ColorMode.Double));
    private final BoolSetting glow = boolSetting("Glow", true, () -> mode.is(Mode.Sakura));
    private final DoubleSetting glowRadius = doubleSetting("Glow Radius", 3.0, 0.1, 6.0, 0.1, () -> mode.is(Mode.Sakura) && glow.getValue());
    private final IntSetting glowIntensity = intSetting("Glow Intensity", 2, 1, 5, 1, () -> mode.is(Mode.Sakura) && glow.getValue());
    private final ColorSetting glitchColor = colorSetting("Glitch Color", Color.WHITE, false, () -> mode.is(Mode.Glitch));
    private final DoubleSetting chromaticX = doubleSetting("Chromatic X", 1.5, 0.0, 6.0, 0.1, () -> mode.is(Mode.Glitch));
    private final DoubleSetting chromaticY = doubleSetting("Chromatic Y", 0.0, 0.0, 6.0, 0.1, () -> mode.is(Mode.Glitch));
    private final DoubleSetting glitchGlowRadius = doubleSetting("Glitch Glow Radius", 2.5, 0.0, 10.0, 0.1, () -> mode.is(Mode.Glitch));
    private final DoubleSetting glitchGlowIntensity = doubleSetting("Glitch Glow Intensity", 1.6, 0.0, 3.0, 0.1, () -> mode.is(Mode.Glitch));
    private final DoubleSetting sliceHeight = doubleSetting("Slice Height", 4.0, 1.0, 32.0, 0.5, () -> mode.is(Mode.Glitch));
    private final DoubleSetting sliceAmount = doubleSetting("Slice Amount", 2.5, 0.0, 10.0, 0.1, () -> mode.is(Mode.Glitch));
    private final DoubleSetting glitchStrength = doubleSetting("Glitch Strength", 0.65, 0.0, 1.0, 0.05, () -> mode.is(Mode.Glitch));
    private final DoubleSetting scanlineStrength = doubleSetting("Scanline Strength", 0.45, 0.0, 1.0, 0.05, () -> mode.is(Mode.Glitch));
    private final DoubleSetting noiseStrength = doubleSetting("Noise Strength", 0.25, 0.0, 1.0, 0.05, () -> mode.is(Mode.Glitch));

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    public void render(DeltaTracker deltaTracker) {
        TextRenderer textRenderer = textRendererSupplier.get();
        float scaledScale = scale.getValue().floatValue() * 2f; // 这个命名给我自己整笑了
        switch (mode.getValue()) {
            case Tradition -> renderTradition(textRenderer, scaledScale);
            case Glitch -> renderGlitch(textRenderer, scaledScale);
            case Sakura -> renderSakura(textRenderer, scaledScale);
        }
    }

    private void renderTradition(TextRenderer textRenderer, float scale) {
        String text = this.text.getValue();
        TtfFontLoader font = font();
        renderScope().text(text, this.x, this.y, scale, textColor.getValue(), font);
        float totalWidth = textRenderer.getWidth(text, scale, font);
        float totalHeight = textRenderer.getHeight(scale, font);
        setBounds(totalWidth, totalHeight);
    }

    private void renderGlitch(TextRenderer textRenderer, float scale) {
        String text = this.text.getValue();
        TtfFontLoader font = font();
        TextGlitchEffect effect = new TextGlitchEffect(
                chromaticX.getValue().floatValue(),
                chromaticY.getValue().floatValue(),
                glitchGlowRadius.getValue().floatValue(),
                glitchGlowIntensity.getValue().floatValue(),
                sliceHeight.getValue().floatValue(),
                sliceAmount.getValue().floatValue(),
                glitchStrength.getValue().floatValue(),
                scanlineStrength.getValue().floatValue(),
                glitchColor.getValue(),
                noiseStrength.getValue().floatValue()
        );

        renderScope().glitchText(text, this.x, this.y, scale, Color.WHITE, effect, font);
        setBounds(textRenderer.getWidth(text, scale, font), textRenderer.getHeight(scale, font));
    }

    private void renderSakura(TextRenderer textRenderer, float scale) {
        String text = this.text.getValue();
        TtfFontLoader font = font();
        float totalWidth = textRenderer.getWidth(text, scale, font);
        float totalHeight = textRenderer.getHeight(scale, font);
        float textX = this.x;
        UiTree.Scope scope = renderScope();

        for (int index = 0; index < text.length(); index++) {
            String character = String.valueOf(text.charAt(index));
            float characterWidth = textRenderer.getWidth(character, scale, font);

            Color color = colorMode.is(ColorMode.Single) ?
                    singleColor.getValue() :
                    animateColor(
                            doubleColor1.getValue(),
                            doubleColor2.getValue(),
                            width <= 0.0f ? index * 100L : (long) ((textX - this.x) / totalWidth * 1600.0f)
                    );

            Color shadowColor = new Color(
                    (int) (color.getRed() * 0.7f),
                    (int) (color.getGreen() * 0.7f),
                    (int) (color.getBlue() * 0.7f),
                    160
            );

            scope.text(character, textX + 1.0f, this.y + 1.0f, scale, shadowColor, font); // shadow
            if (glow.getValue()) {
                scope.blurredText(character, textX, this.y, scale, glowRadius.getValue().floatValue() * scale, glowIntensity.getValue(), color);
            }
            scope.text(character, textX, this.y, scale, color, font);

            textX += characterWidth;
        }

        setBounds(totalWidth, totalHeight);
    }

    private Color animateColor(Color start, Color end, long offset) {
        double progress = ((System.currentTimeMillis() + offset) % 4000L) / 2000.0;
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0;
        }
        return ColorUtils.interpolateColor(start, end, (float) progress);
    }

    private TtfFontLoader font() {
        return switch (fontMode.getValue()) {
            case Default -> StaticFontLoader.defaultFont();
            case OsakaChips -> StaticFontLoader.OSAKA_CHIPS;
        };
    }

}
