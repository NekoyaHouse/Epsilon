package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.google.common.base.Suppliers;

import java.awt.*;
import java.util.function.Supplier;

public class BetterScoreboard extends Module {

    public static final BetterScoreboard INSTANCE = new BetterScoreboard();

    private BetterScoreboard() {
        super("Better Scoreboard", Category.RENDER);
        setDefaultEnabled(true);
    }

    private final ColorSetting backgroundColor = colorSetting("Background Color", new Color(16, 17, 20, 90));
    private final DoubleSetting radius = doubleSetting("Radius", 4.5, 0.0, 12.0, 0.5);
    private final DoubleSetting xOffset = doubleSetting("X Offset", 0.0, -500.0, 500.0, 1.0);
    private final DoubleSetting yOffset = doubleSetting("Y Offset", 0.0, -500.0, 500.0, 1.0);
    private final BoolSetting backgroundBlur = boolSetting("Background Blur", true);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 8.0, 1.0, 20.0, 0.5, backgroundBlur::getValue);
    private final BoolSetting drawShadow = boolSetting("Draw Shadow", true);
    private final DoubleSetting shadowBlur = doubleSetting("Shadow Blur", 10.0, 2.0, 24.0, 1.0, drawShadow::getValue);
    private final ColorSetting shadowColor = colorSetting("Shadow Color", new Color(255, 255, 255, 110), drawShadow::getValue);

    private ScoreboardBounds pendingBounds;

    private final Supplier<Render2DScheduler> scheduler = Suppliers.memoize(Render2DScheduler::new);

    @Override
    protected void onEnable() {
        pendingBounds = null;
    }

    @Override
    protected void onDisable() {
        pendingBounds = null;
    }

    public void beginScoreboardExtraction() {
        pendingBounds = null;
    }

    public void captureVanillaBackground(int x0, int y0, int x1, int y1) {
        float left = Math.min(x0, x1);
        float top = Math.min(y0, y1);
        float right = Math.max(x0, x1);
        float bottom = Math.max(y0, y1);
        ScoreboardBounds bounds = new ScoreboardBounds(left, top, right, bottom);
        pendingBounds = pendingBounds == null ? bounds : pendingBounds.include(bounds);
    }

    @EventHandler
    private void onRender2D(Render2DEvent.HUD event) {
        ScoreboardBounds bounds = pendingBounds;
        pendingBounds = null;
        if (bounds == null) return;

        float coordinateScale = (float) (mc.getWindow().getGuiScale() / LuminRenderSystem.getGuiScale());
        float radius = this.radius.getValue().floatValue() * coordinateScale;
        float x = (bounds.left() + xOffset.getValue().floatValue()) * coordinateScale;
        float y = (bounds.top() + yOffset.getValue().floatValue()) * coordinateScale;
        float width = bounds.width() * coordinateScale;
        float height = bounds.height() * coordinateScale;

        if (backgroundBlur.getValue()) {
            BlurShader.INSTANCE.render(x, y, width, height, radius, blurStrength.getValue().floatValue());
        }

        Render2DScheduler renderScheduler = scheduler.get();
        renderScheduler.clear();
        Render2DScheduler.LayerHandle layer = renderScheduler.layer(0);

        if (drawShadow.getValue()) {
            layer.addShadow(x, y, width, height, radius, shadowBlur.getValue().floatValue() * coordinateScale, shadowColor.getValue());
        }

        layer.addRoundRect(x, y, width, height, radius, backgroundColor.getValue());
        renderScheduler.flushAndClear();
    }

    public int getRoundedXOffset() {
        return Math.round(xOffset.getValue().floatValue());
    }

    public int getRoundedYOffset() {
        return Math.round(yOffset.getValue().floatValue());
    }

    private record ScoreboardBounds(float left, float top, float right, float bottom) {
        private float width() {
            return right - left;
        }

        private float height() {
            return bottom - top;
        }

        private ScoreboardBounds include(ScoreboardBounds other) {
            return new ScoreboardBounds(
                    Math.min(left, other.left),
                    Math.min(top, other.top),
                    Math.max(right, other.right),
                    Math.max(bottom, other.bottom)
            );
        }
    }

}
