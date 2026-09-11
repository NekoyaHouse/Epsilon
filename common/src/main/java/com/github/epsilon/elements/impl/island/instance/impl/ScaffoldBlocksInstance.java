package com.github.epsilon.elements.impl.island.instance.impl;

import com.github.epsilon.elements.impl.island.IslandPalette;
import com.github.epsilon.elements.impl.island.instance.LandInstance;
import com.github.epsilon.elements.impl.island.pattern.impl.CheckPattern;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.lib.UiRect;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.awt.*;
import java.util.function.Supplier;

import static com.github.epsilon.Constants.mc;

public class ScaffoldBlocksInstance extends LandInstance {

    private static final float PADDING = 6f;
    private static final float ICON_SIZE = 26f;
    private static final float ICON_RADIUS = ICON_SIZE * 0.4f;
    private static final float GAP = 6f;
    private static final float RING_SIZE = 26f;
    private static final float RING_STROKE = 2.5f;
    private static final float TEXT_SCALE = 0.78f;
    private static final float DIGIT_ROLL_STEP = 9f;
    private static final float SPIN_DEGREES_PER_SECOND = 240f;

    private final int max;
    private final Supplier<Integer> countSupplier;
    private final Supplier<ItemStack> iconSupplier;
    private final Supplier<TextRenderer> textRendererSupplier;

    private final Animation progress = new Animation(Easing.DECELERATE, 100);
    private final Animation countAnim = new Animation(Easing.DECELERATE, 560);

    private final TimerUtils iconRefreshTimer = new TimerUtils();
    private TextureAtlasSprite iconSprite;
    private Color iconTint = Color.WHITE;

    private long lastUpdateNs = System.nanoTime();
    private float spinAngleDeg;
    private int startCount;
    private int targetCount;

    public ScaffoldBlocksInstance(Supplier<Integer> count, Supplier<ItemStack> iconSupplier, Supplier<TextRenderer> textRendererSupplier, CheckPattern pattern) {
        super(pattern, 4);
        this.max = Math.max(1, count.get());
        this.countSupplier = count;
        this.iconSupplier = iconSupplier;
        this.textRendererSupplier = textRendererSupplier;

        int initial = Math.max(0, count.get());
        this.startCount = initial;
        this.targetCount = initial;
        this.countAnim.setStartValue(initial);
    }

    @Override
    public void update() {
        targetRadius = 1.0f;
        targetWidth = PADDING * 2f + ICON_SIZE + GAP + RING_SIZE;
        targetHeight = PADDING * 2f + Math.max(ICON_SIZE, RING_SIZE);

        int count = Math.max(0, countSupplier.get());
        progress.run(Mth.clamp(count / (float) max, 0f, 1f));

        if (count != targetCount) {
            startCount = Math.round(countAnim.getValue());
            targetCount = count;
        }
        countAnim.run(count);
        if (startCount != targetCount && Math.round(countAnim.getValue()) == targetCount) {
            startCount = targetCount;
        }

        long nowNs = System.nanoTime();
        float deltaSeconds = Mth.clamp((nowNs - lastUpdateNs) / 1_000_000_000f, 0f, 0.05f);
        lastUpdateNs = nowNs;
        spinAngleDeg = (spinAngleDeg + SPIN_DEGREES_PER_SECOND * deltaSeconds) % 360f;

        if (iconSprite == null || iconRefreshTimer.every(350L)) {
            updateIconTexture(iconSupplier.get());
        }
    }

    @Override
    public void draw(UiTree.Scope scope, float translateX, float translateY, float width, float height) {
        drawBlockTexture(scope, height);

        float ringRadius = (RING_SIZE - RING_STROKE) / 2f;
        float centerX = width - PADDING - RING_SIZE / 2f;
        float centerY = height / 2f;

        scope.circle(centerX, centerY, ringRadius, RING_STROKE, fade(new Color(255, 255, 255, 60)));

        float progressValue = Mth.clamp(progress.getValue(), 0f, 1f);
        if (progressValue > 0f) {
            scope.gradientArc(
                    centerX, centerY, ringRadius, RING_STROKE,
                    -90f, progressValue * 359f, true, spinAngleDeg,
                    fade(IslandPalette.ACCENT),
                    fade(IslandPalette.ACCENT_ALT),
                    fade(IslandPalette.SUCCESS)
            );
        }

        drawRollingCount(scope, centerX, centerY);
    }

    private void updateIconTexture(ItemStack stack) {
        iconSprite = null;
        iconTint = Color.WHITE;
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return;

        BlockState state = blockItem.getBlock().defaultBlockState();
        iconSprite = mc.getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite();

        BlockTintSource tintSource = mc.getBlockColors().getTintSource(state, 0);
        if (tintSource == null) return;

        int tint = tintSource.colorAsTerrainParticle(state, mc.level, mc.player.blockPosition());
        iconTint = new Color(tint, true);
    }

    private void drawBlockTexture(UiTree.Scope scope, float height) {
        TextureAtlasSprite sprite = iconSprite;
        if (sprite == null) return;

        float iconY = (height - ICON_SIZE) / 2f;
        scope.roundedTexture(
                sprite.atlasLocation(),
                PADDING, iconY, ICON_SIZE, ICON_SIZE, ICON_RADIUS,
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(),
                fade(iconTint)
        );
    }

    private void drawRollingCount(UiTree.Scope scope, float centerX, float centerY) {
        TextRenderer textRenderer = textRendererSupplier.get();
        float textHeight = textRenderer.getHeight(TEXT_SCALE);
        float baseY = centerY - textHeight / 2f;

        if (startCount == targetCount) {
            drawCentered(scope, String.valueOf(Math.max(0, targetCount)), centerX, baseY, fade(Color.WHITE));
            return;
        }

        int direction = targetCount > startCount ? 1 : -1;
        int distance = Math.abs(targetCount - startCount);
        if (distance <= 0) {
            drawCentered(scope, String.valueOf(Math.max(0, targetCount)), centerX, baseY, fade(Color.WHITE));
            return;
        }

        float progressed = Math.abs(countAnim.getValue() - startCount);
        int step = Math.min((int) Math.floor(progressed), distance);
        if (step >= distance) {
            drawCentered(scope, String.valueOf(Math.max(0, targetCount)), centerX, baseY, fade(Color.WHITE));
            return;
        }

        float localProgress = Mth.clamp(progressed - step, 0f, 1f);
        int current = startCount + direction * Mth.clamp(distance - 1, 0, step);
        drawDigitRoll(scope, textRenderer, centerX, baseY, current, current + direction, direction, localProgress);
    }

    private void drawDigitRoll(UiTree.Scope scope, TextRenderer textRenderer, float centerX, float baseY, int current, int next, int direction, float localProgress) {
        String currentText = String.valueOf(Math.max(0, current));
        String nextText = String.valueOf(Math.max(0, next));
        int length = Math.max(currentText.length(), nextText.length());

        float[] cellWidths = new float[length];
        float totalWidth = 0f;
        float zeroWidth = textRenderer.getWidth("0", TEXT_SCALE);
        for (int i = 0; i < length; i++) {
            char a = paddedChar(currentText, length, i);
            char b = paddedChar(nextText, length, i);
            float cell = Math.max(Math.max(charWidth(textRenderer, a), charWidth(textRenderer, b)), zeroWidth);
            cellWidths[i] = cell;
            totalWidth += cell;
        }

        float textHeight = textRenderer.getHeight(TEXT_SCALE);
        float eased = Easing.EASE_IN_OUT_CUBIC.getFunction().apply(localProgress);
        UiRect clip = new UiRect(centerX - totalWidth / 2f - 2f, baseY - textHeight * 0.6f, totalWidth + 4f, textHeight * 2.2f);

        float finalTotalWidth = totalWidth;
        scope.scissor(clip, inner -> {
            float cellX = centerX - finalTotalWidth / 2f;
            for (int i = 0; i < length; i++) {
                float cell = cellWidths[i];
                float cellCenterX = cellX + cell / 2f;

                char currentChar = paddedChar(currentText, length, i);
                char nextChar = paddedChar(nextText, length, i);

                if (currentChar == nextChar) {
                    if (currentChar != ' ') {
                        drawCentered(inner, String.valueOf(currentChar), cellCenterX, baseY, fade(Color.WHITE));
                    }
                } else {
                    float currentY = baseY + (direction > 0 ? -eased : eased) * DIGIT_ROLL_STEP;
                    float nextY = baseY + (direction > 0 ? (1f - eased) : -(1f - eased)) * DIGIT_ROLL_STEP;

                    if (currentChar != ' ') {
                        drawCentered(inner, String.valueOf(currentChar), cellCenterX, currentY, fade(withAlpha(Color.WHITE, 1f - eased)));
                    }
                    if (nextChar != ' ') {
                        drawCentered(inner, String.valueOf(nextChar), cellCenterX, nextY, fade(withAlpha(Color.WHITE, eased)));
                    }
                }

                cellX += cell;
            }
        });
    }

    private void drawCentered(UiTree.Scope scope, String text, float centerX, float y, Color color) {
        float width = textRendererSupplier.get().getWidth(text, TEXT_SCALE);
        scope.text(text, centerX - width / 2f, y, TEXT_SCALE, color);
    }

    private float charWidth(TextRenderer textRenderer, char c) {
        if (c == ' ') return 0f;
        return textRenderer.getWidth(String.valueOf(c), TEXT_SCALE);
    }

    private static char paddedChar(String text, int length, int index) {
        int pad = length - text.length();
        int source = index - pad;
        if (source < 0 || source >= text.length()) return ' ';
        return text.charAt(source);
    }

    private static Color withAlpha(Color color, float alphaMul) {
        int alpha = Mth.clamp((int) (color.getAlpha() * alphaMul), 0, 255);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

}
