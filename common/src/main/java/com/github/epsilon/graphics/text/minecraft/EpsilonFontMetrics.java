package com.github.epsilon.graphics.text.minecraft;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.jspecify.annotations.Nullable;

public final class EpsilonFontMetrics {

    private static final float VANILLA_LINE_HEIGHT = 9.0f;
    private static final Identifier FONT_ID = ResourceLocationUtils.getIdentifier("fonts/font.ttf");

    public static final float LETTER_SPACING = 0f;
    public static final float SPACE_WIDTH = 3.0f;

    private static @Nullable TtfFontLoader font;

    private EpsilonFontMetrics() {
    }

    public static @Nullable TtfFontLoader font() {
        if (font != null) {
            return font;
        }

        try {
            font = new TtfFontLoader(FONT_ID);
            return font;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static float minecraftScale(TtfFontLoader font) {
        return VANILLA_LINE_HEIGHT / font.fontFile.fontHeight;
    }

    public static float advance(int codepoint, Style style, @Nullable TtfFontLoader font) {
        if (Character.isWhitespace(codepoint)) {
            return SPACE_WIDTH + (style.isBold() ? 1.0f : 0.0f);
        }
        if (font == null) {
            return 0.0f;
        }
        if (codepoint < Character.MIN_CODE_POINT || codepoint > Character.MAX_CODE_POINT) {
            return 0.0f;
        }

        return font.getAdvance(codepoint) * minecraftScale(font) + LETTER_SPACING + (style.isBold() ? 1.0f : 0.0f);
    }

    public static @Nullable Float width(String text) {
        TtfFontLoader font = font();
        if (font == null) {
            return null;
        }

        float[] width = new float[]{0.0f};
        StringDecomposer.iterateFormatted(text, Style.EMPTY, (position, style, codepoint) -> {
            width[0] += advance(codepoint, style, font);
            return true;
        });
        return width[0];
    }

    public static @Nullable Float width(FormattedCharSequence text) {
        TtfFontLoader font = font();
        if (font == null) {
            return null;
        }

        float[] width = new float[]{0.0f};
        text.accept((position, style, codepoint) -> {
            width[0] += advance(codepoint, style, font);
            return true;
        });
        return width[0];
    }

    public static @Nullable Float width(FormattedText text) {
        TtfFontLoader font = font();
        if (font == null) {
            return null;
        }

        float[] width = new float[]{0.0f};
        StringDecomposer.iterateFormatted(text, Style.EMPTY, (position, style, codepoint) -> {
            width[0] += advance(codepoint, style, font);
            return true;
        });
        return width[0];
    }
}
