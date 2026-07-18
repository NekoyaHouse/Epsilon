package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;

public class SneakTweak extends Module {

    public static final SneakTweak INSTANCE = new SneakTweak();

    private SneakTweak() {
        super("Sneak Tweak", Category.RENDER);
    }

    private enum SneakingEyeHeight {
        Default,
        Custom,
        Pre_1_14,
        Pre_1_9
    }

    private final BoolSetting smoothing = boolSetting("Smoothing", true);
    private final IntSetting speedPercentage = intSetting("Speed Percentage", 100, 25, 300, 1, () -> smoothing.getValue());
    private final EnumSetting<SneakingEyeHeight> sneakingEyeHeight = enumSetting("Sneaking Eye Height", SneakingEyeHeight.Default, ignored -> refreshPlayerDimensions());
    private final DoubleSetting customSneakingEyeHeight = doubleSetting("Custom Sneaking Eye Height", 1.27, 0.0, 1.8, 0.01, () -> sneakingEyeHeight.is(SneakingEyeHeight.Custom), ignored -> refreshPlayerDimensions());
    private final BoolSetting thirdPersonEyeHeight = boolSetting("Third Person Eye Height", false, ignored -> refreshPlayerDimensions());

    @Override
    protected void onEnable() {
        refreshPlayerDimensions();
    }

    @Override
    protected void onDisable() {
        refreshPlayerDimensions();
    }

    public float getCameraSmoothingModifier(float defaultModifier) {
        if (isEnabled() && smoothing.getValue()) {
            return defaultModifier * (speedPercentage.getValue() / 100.0f);
        }
        return defaultModifier;
    }

    public boolean shouldSnapCameraEyeHeight() {
        return isEnabled() && !smoothing.getValue();
    }

    public float modifySneakingEyeHeight(float height) {
        if (mc.options.getCameraType().isFirstPerson() || thirdPersonEyeHeight.getValue()) {
            return switch (sneakingEyeHeight.getValue()) {
                case Default -> height;
                case Pre_1_14 -> 1.42f;
                case Pre_1_9 -> 1.54f;
                case Custom -> customSneakingEyeHeight.getValue().floatValue();
            };
        }
        return height;
    }

    private void refreshPlayerDimensions() {
        if (mc.player != null) mc.player.refreshDimensions();
    }

}
