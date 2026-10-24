package com.github.epsilon.gui.troll;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTextMetrics;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.modules.impl.ClientSetting;

import java.awt.*;

public class TrollTheme {

    public static final float TEXT_SCALE = 0.65f;
    public static final float VALUE_SCALE = 0.49f;

    private TrollTheme() {
    }

    public static Color primary() {
        return ClientSetting.INSTANCE.trollPrimaryColor.getValue();
    }

    public static Color text() {
        return ClientSetting.INSTANCE.trollTextColor.getValue();
    }

    public static float xMargin() {
        return ClientSetting.INSTANCE.trollXMargin.getValue().floatValue();
    }

    public static float yMargin() {
        return ClientSetting.INSTANCE.trollYMargin.getValue().floatValue();
    }

    public static float rowHeight(TextRenderer metrics) {
        return metrics.getHeight(TEXT_SCALE) + 3.0f;
    }

    public static float headerHeight(TextRenderer metrics) {
        return metrics.getHeight(TEXT_SCALE) + 6.0f;
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static void window(UiTree.Scope scope, UiRect bounds, float headerHeight, String title, TextRenderer metrics) {
        boolean titleBar = ClientSetting.INSTANCE.trollTitleBar.getValue();
        Color background = ClientSetting.INSTANCE.trollBackgroundColor.getValue();
        scope.rect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), background);
        if (titleBar) {
            scope.rect(bounds.x(), bounds.y(), bounds.width(), headerHeight, primary());
        }
        scope.text(3, trim(title, metrics, TEXT_SCALE, bounds.width() - 6.0f), bounds.x() + 3.0f, bounds.y() + 3.0f, TEXT_SCALE, text());
        if (ClientSetting.INSTANCE.trollWindowOutline.getValue()) {
            scope.rectOutline(4, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 0.5f, alpha(primary(), 255));
        }
    }

    public static String trim(String value, TextRenderer metrics, float scale, float width) {
        if (value == null || width <= 0.0f) return "";
        if (metrics.getWidth(value, scale) <= width) return value;
        String suffix = "...";
        if (metrics.getWidth(suffix, scale) > width) return "";
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String result = value.substring(0, end) + suffix;
            if (metrics.getWidth(result, scale) <= width) return result;
        }
        return suffix;
    }

    public static UiTextMetrics metrics(TextRenderer renderer) {
        return new UiTextMetrics() {
            @Override
            public float textWidth(String text, float scale) {
                return renderer.getWidth(text, scale);
            }

            @Override
            public float textHeight(float scale) {
                return renderer.getHeight(scale);
            }

            @Override
            public float textWidth(String text, float scale, TtfFontLoader fontLoader) {
                return renderer.getWidth(text, scale, fontLoader);
            }

            @Override
            public float textHeight(float scale, TtfFontLoader fontLoader) {
                return renderer.getHeight(scale, fontLoader);
            }
        };
    }

}
