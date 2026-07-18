package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;

public class GameAnimation extends Module {

    public static final GameAnimation INSTANCE = new GameAnimation();

    private static final float HOTBAR_RESET_DISTANCE = 240.0f;
    private static final long HOTBAR_ANIMATION_DURATION = 150L;

    private final Animation hotbarAnimation = new Animation(Easing.EASE_OUT_CUBIC, HOTBAR_ANIMATION_DURATION);
    private float lastHotbarTargetX = Float.NaN;

    private GameAnimation() {
        super("Game Animation", Category.RENDER);
    }

    public final BoolSetting hotbar = boolSetting("Hotbar", true);

    @Override
    protected void onDisable() {
        resetHotbarAnimation();
    }

    public int getHotbarSelectionX(int vanillaX) {
        if (!shouldAnimateHotbar()) {
            resetHotbarAnimation();
            return vanillaX;
        }

        if (!Float.isFinite(lastHotbarTargetX) || Math.abs(hotbarAnimation.getValue() - vanillaX) > HOTBAR_RESET_DISTANCE) {
            resetHotbarAnimation(vanillaX);
            return vanillaX;
        }

        lastHotbarTargetX = vanillaX;
        hotbarAnimation.run(vanillaX);
        return Math.round(hotbarAnimation.getValue());
    }

    private boolean shouldAnimateHotbar() {
        return isEnabled() && hotbar.getValue() && !nullCheck();
    }

    private void resetHotbarAnimation() {
        lastHotbarTargetX = Float.NaN;
        hotbarAnimation.setStartValue(0.0f);
        hotbarAnimation.setValue(0.0f);
        hotbarAnimation.setFinished(true);
    }

    private void resetHotbarAnimation(float x) {
        lastHotbarTargetX = x;
        hotbarAnimation.setStartValue(x);
        hotbarAnimation.setValue(x);
        hotbarAnimation.setFinished(true);
    }

}
