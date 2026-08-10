package com.github.epsilon.elements.impl.island.instance.impl;

import com.github.epsilon.elements.impl.island.IslandPalette;
import com.github.epsilon.elements.impl.island.instance.LandInstance;
import com.github.epsilon.elements.impl.island.pattern.LCPattern;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

public class BalanceInstance extends LandInstance {

    private static final float PADDING = 6f;
    private static final float BAR_HEIGHT = 4f;
    private static final float TEXT_SCALE = 1.0f;
    private static final float GAP = 5f;

    private final Supplier<Long> balanceSupplier;
    private final Supplier<Long> maxBalanceSupplier;
    private final Supplier<TextRenderer> textRendererSupplier;

    private final Animation progressAnim = new Animation(Easing.DECELERATE, 200);

    public BalanceInstance(Supplier<Long> balanceSupplier, Supplier<Long> maxBalanceSupplier, Supplier<TextRenderer> textRendererSupplier, LCPattern pattern) {
        super(pattern, 2);
        this.balanceSupplier = balanceSupplier;
        this.maxBalanceSupplier = maxBalanceSupplier;
        this.textRendererSupplier = textRendererSupplier;
    }

    @Override
    public void update() {
        targetRadius = 0.5f;

        long balance = Math.max(0L, balanceSupplier.get());
        long maxBalance = Math.max(1L, maxBalanceSupplier.get());

        TextRenderer textRenderer = textRendererSupplier.get();
        float titleWidth = textRenderer.getWidth(TITLE, TEXT_SCALE);
        float valueWidth = textRenderer.getWidth(balance + "ms", TEXT_SCALE);
        float textHeight = textRenderer.getHeight(TEXT_SCALE);

        targetWidth = Math.max(100f, PADDING * 2f + titleWidth + GAP + valueWidth);
        targetHeight = PADDING * 2f + textHeight + GAP + BAR_HEIGHT;

        progressAnim.run(Mth.clamp((float) balance / maxBalance, 0.0f, 1.0f));
    }

    private static final String TITLE = "Timer Balance";

    @Override
    public void draw(UiTree.Scope scope, float translateX, float translateY, float width, float height) {
        if (width < 20f || height < 10f) return;

        TextRenderer textRenderer = textRendererSupplier.get();
        long balance = Math.max(0L, balanceSupplier.get());
        String valueText = balance + "ms";

        float titleWidth = textRenderer.getWidth(TITLE, TEXT_SCALE);
        float valueWidth = textRenderer.getWidth(valueText, TEXT_SCALE);
        float textHeight = textRenderer.getHeight(TEXT_SCALE);

        float barWidth = Math.max(1f, width - PADDING * 2f);
        float barY = height - PADDING - BAR_HEIGHT;
        float textX = Math.round((width - (titleWidth + GAP + valueWidth)) / 2f);
        float textY = Math.max(PADDING, (barY - PADDING - textHeight) / 2f + PADDING * 0.5f);

        scope.text(TITLE, textX, textY, TEXT_SCALE, fade(IslandPalette.TEXT_PRIMARY));
        scope.text(valueText, textX + titleWidth + GAP, textY, TEXT_SCALE, fade(IslandPalette.ACCENT));

        float barRadius = BAR_HEIGHT * 0.5f;
        scope.roundRect(PADDING, barY, barWidth, BAR_HEIGHT, barRadius, fade(IslandPalette.TRACK));

        float progress = Mth.clamp(progressAnim.getValue(), 0f, 1f);
        if (progress > 0f) {
            scope.roundRectHorizontalGradient(PADDING, barY, barWidth * progress, BAR_HEIGHT, barRadius, fade(IslandPalette.ACCENT), fade(IslandPalette.ACCENT_ALT));
            scope.circle(PADDING + barWidth * progress, barY + barRadius, 1.4f, 1.2f, fade(IslandPalette.ACCENT));
        }
    }

}
