package com.github.epsilon.elements.impl;

import com.github.epsilon.elements.HudModule;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.utils.render.ColorUtils;
import com.google.common.base.Suppliers;
import love.slmpc.ClInitNative;
import net.minecraft.client.DeltaTracker;

import java.awt.*;
import java.util.function.Supplier;

@ClInitNative
public class Watermark extends HudModule {

    public static final Watermark INSTANCE = new Watermark();

    private Watermark() {
        super("Watermark", 0f, 0f, 200f, 28f);
    }

    private enum Mode {
        Traditional,
        Sakura
    }

    private enum ColorMode {
        Single,
        Double
    }

    private final EnumSetting<Mode> mode = enumSetting("Style", Mode.Traditional);
    private final DoubleSetting scale = doubleSetting("Scale", 1.0, 0.5, 2.0, 0.1);
    private final ColorSetting textColor = colorSetting("Text Color", new Color(255, 255, 255, 235), () -> mode.is(Mode.Traditional));
    private final EnumSetting<ColorMode> colorMode = enumSetting("Color Mode", ColorMode.Single, () -> mode.is(Mode.Sakura));
    private final ColorSetting singleColor = colorSetting("Color", Color.WHITE, false, () -> mode.is(Mode.Sakura) && colorMode.is(ColorMode.Single));
    private final ColorSetting doubleColor1 = colorSetting("Color 1", Color.WHITE, false, () -> mode.is(Mode.Sakura) && colorMode.is(ColorMode.Double));
    private final ColorSetting doubleColor2 = colorSetting("Color 2", Color.RED, false, () -> mode.is(Mode.Sakura) && colorMode.is(ColorMode.Double));
    private final BoolSetting glow = boolSetting("Glow", true, () -> mode.is(Mode.Sakura));
    private final DoubleSetting glowRadius = doubleSetting("Glow Radius", 3.0, 0.1, 6.0, 0.1, () -> mode.is(Mode.Sakura) && glow.getValue());
    private final IntSetting glowIntensity = intSetting("Glow Intensity", 2, 1, 5, 1, () -> mode.is(Mode.Sakura) && glow.getValue());

    private final Supplier<TextRenderer> textRendererSupplier = Suppliers.memoize(TextRenderer::create);

    @Override
    public void render(DeltaTracker deltaTracker) {
        TextRenderer textRenderer = textRendererSupplier.get();

        float scaledScale = scale.getValue().floatValue() * 2f; // 这个命名给我自己整笑了

        if (mode.is(Mode.Sakura)) {
            renderSakura(textRenderer, scaledScale);
            return;
        }

        String traditionText = "EPSILON";
        renderScope().text(traditionText, this.x, this.y, scaledScale, textColor.getValue(), StaticFontLoader.OSAKA_CHIPS);

        float totalWidth = textRenderer.getWidth(traditionText, scaledScale, StaticFontLoader.OSAKA_CHIPS);
        float totalHeight = textRenderer.getHeight(scaledScale, StaticFontLoader.OSAKA_CHIPS);

        setBounds(totalWidth, totalHeight);
    }

    private void renderSakura(TextRenderer textRenderer, float scale) {
        String text = "Epsilon";
        TtfFontLoader font = StaticFontLoader.defaultFont();
        float totalWidth = textRenderer.getWidth(text, scale, font);
        float totalHeight = textRenderer.getHeight(scale, font);
        float textX = this.x;
        UiTree.Scope scope = renderScope();

        for (int index = 0; index < text.length(); index++) {
            String character = String.valueOf(text.charAt(index));
            float characterWidth = textRenderer.getWidth(character, scale, font);
            Color color = resolveSakuraColor(textX - this.x, totalWidth, index);

            Color shadowColor = new Color(
                    (int) (color.getRed() * 0.7f),
                    (int) (color.getGreen() * 0.7f),
                    (int) (color.getBlue() * 0.7f),
                    160
            );
            scope.text(character, textX + 1.0f, this.y + 1.0f, scale, shadowColor, font);
            if (glow.getValue()) {
                scope.blurredText(character, textX, this.y, scale,
                        glowRadius.getValue().floatValue() * scale, glowIntensity.getValue(), color);
            }
            scope.text(character, textX, this.y, scale, color, font);
            textX += characterWidth;
        }

        setBounds(totalWidth, totalHeight);
    }

    private Color resolveSakuraColor(float x, float width, int index) {
        if (colorMode.is(ColorMode.Single)) {
            return singleColor.getValue();
        }

        long offset = width <= 0.0f ? index * 100L : (long) (x / width * 1600.0f);
        return animateColor(doubleColor1.getValue(), doubleColor2.getValue(), offset);
    }

    private Color animateColor(Color start, Color end, long offset) {
        double progress = ((System.currentTimeMillis() + offset) % 4000L) / 2000.0;
        if (progress > 1.0) {
            progress = 1.0 - progress % 1.0;
        }
        return ColorUtils.interpolateColor(start, end, (float) progress);
    }

}
