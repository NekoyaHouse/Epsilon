package com.github.epsilon.gui.screen.accounts;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.graphics.text.IconChars;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

import java.awt.*;

public class AccountSortButton {

    private static final float LABEL_SCALE = 0.75f;
    private static final float INSET = 8.0f;
    private static final float GAP = 5.0f;

    private final Animation hoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);

    public AccountSortButton() {
        hoverAnimation.setStartValue(0.0f);
    }

    public float measureWidth(TextRenderer textRenderer, AccountSortMode mode) {
        float iconWidth = textRenderer.getWidth(IconChars.SWAP_VERT, 1.0f, StaticFontLoader.ICONS);
        float labelWidth = textRenderer.getWidth(mode.getLabel(), LABEL_SCALE);
        return INSET + iconWidth + GAP + labelWidth + INSET;
    }

    public void draw(UiTree.Scope scope, UiRect bounds, double mouseX, double mouseY, TextRenderer textRenderer, AccountSortMode mode) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        float hoverProgress = scope.animate(hoverAnimation, hovered);

        scope.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CONTROL_RADIUS, MD3Theme.SECONDARY_CONTAINER);
        if (hoverProgress > 0.01f) {
            scope.roundRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), MD3Theme.CONTROL_RADIUS, MD3Theme.stateLayer(MD3Theme.PRIMARY, hoverProgress, 34));
        }

        Color fg = MD3Theme.ON_SECONDARY_CONTAINER;
        float iconWidth = textRenderer.getWidth(IconChars.SWAP_VERT, 1.0f, StaticFontLoader.ICONS);
        float iconHeight = textRenderer.getHeight(1.0f, StaticFontLoader.ICONS);
        scope.text(IconChars.SWAP_VERT, bounds.x() + INSET, bounds.y() + (bounds.height() - iconHeight) / 2.0f, 1.0f, fg, StaticFontLoader.ICONS);

        String label = mode.getLabel();
        float labelHeight = textRenderer.getHeight(LABEL_SCALE);
        scope.text(label, bounds.x() + INSET + iconWidth + GAP, bounds.y() + (bounds.height() - labelHeight) / 2.0f, LABEL_SCALE, fg);
    }

    public boolean hasActiveAnimations() {
        return !hoverAnimation.isFinished();
    }

}
