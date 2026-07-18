package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.math.MathUtils;
import net.minecraft.client.KeyMapping;

/**
 * @author Moli
 * fix: 已基本害死伪随机。
 */

public class AutoClicker extends Module {

    public static final AutoClicker INSTANCE = new AutoClicker();

    private final EnumSetting<ClickButton> button = enumSetting("Button", ClickButton.BOTH);

    private final IntSetting leftMinCPS = intSetting("Left Min CPS", 8, 1, 520, 1, this::isLeftButtonEnabled);
    private final IntSetting leftMaxCPS = intSetting("Left Max CPS", 12, 1, 520, 1, this::isLeftButtonEnabled);
    private final BoolSetting leftJitter = boolSetting("Left Jitter", false, this::isLeftButtonEnabled);

    private final IntSetting rightMinCPS = intSetting("Right Min CPS", 8, 1, 520, 1, this::isRightButtonEnabled);
    private final IntSetting rightMaxCPS = intSetting("Right Max CPS", 12, 1, 520, 1, this::isRightButtonEnabled);
    private final BoolSetting rightJitter = boolSetting("Right Jitter", false, this::isRightButtonEnabled);

    private final BoolSetting enableBurst = boolSetting("Enable Burst", true);
    private final IntSetting burstChance = intSetting("Burst Chance", 15, 1, 100, 1, enableBurst::getValue);
    private final IntSetting burstDuration = intSetting("Burst Duration", 800, 100, 3000, 100, enableBurst::getValue);
    private final DoubleSetting burstCPSMultiplier = doubleSetting("Burst CPS Multiplier", 1.8, 1.1, 3.0, 0.1, enableBurst::getValue);
    private final IntSetting decayDuration = intSetting("Decay Duration", 1200, 500, 5000, 100, enableBurst::getValue);
    private final DoubleSetting decayCurve = doubleSetting("Decay Curve", 1.5, 1.0, 3.0, 0.1, enableBurst::getValue);

    private final BoolSetting enableDoubleClick = boolSetting("Enable Double Click", true);
    private final IntSetting doubleClickChance = intSetting("Double Click Chance", 8, 1, 50, 1, enableDoubleClick::getValue);
    private final IntSetting doubleClickDelay = intSetting("Double Click Delay", 40, 10, 100, 5, enableDoubleClick::getValue);

    private final BoolSetting enableMiss = boolSetting("Enable Miss", true);
    private final IntSetting missChance = intSetting("Miss Chance", 5, 1, 30, 1, enableMiss::getValue);

    private final ButtonState leftState = new ButtonState();
    private final ButtonState rightState = new ButtonState();

    private AutoClicker() {
        super("Auto Clicker", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        leftState.reset();
        rightState.reset();
    }

    @EventHandler
    public void onTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || mc.screen != null) return;

        long currentTime = System.currentTimeMillis();

        if (isLeftButtonEnabled()) {
            handleButton(leftState, ClickButton.LEFT, currentTime);
        } else {
            leftState.reset();
        }

        if (isRightButtonEnabled()) {
            handleButton(rightState, ClickButton.RIGHT, currentTime);
        } else {
            rightState.reset();
        }
    }

    private boolean isLeftButtonEnabled() {
        ClickButton btn = button.getValue();
        return btn == ClickButton.LEFT || btn == ClickButton.BOTH;
    }

    private boolean isRightButtonEnabled() {
        ClickButton btn = button.getValue();
        return btn == ClickButton.RIGHT || btn == ClickButton.BOTH;
    }

    private void handleButton(ButtonState state, ClickButton clickButton, long currentTime) {
        if (!isButtonPressed(clickButton)) {
            state.reset();
            return;
        }

        state.updateBurstState(currentTime);

        if (currentTime - state.lastClickTime < state.nextDelay) {
            handlePendingDoubleClick(state, clickButton, currentTime);
            return;
        }

        if (shouldMiss()) {
            state.lastClickTime = currentTime;
            updateNextDelay(state, clickButton, currentTime);
            return;
        }

        performClick(clickButton);
        applyJitter(clickButton);
        scheduleDoubleClick(state, currentTime);
        handlePendingDoubleClick(state, clickButton, currentTime);

        state.lastClickTime = currentTime;
        updateNextDelay(state, clickButton, currentTime);
    }

    private void updateNextDelay(ButtonState state, ClickButton clickButton, long currentTime) {
        IntSetting minCPS = clickButton == ClickButton.LEFT ? leftMinCPS : rightMinCPS;
        IntSetting maxCPS = clickButton == ClickButton.LEFT ? leftMaxCPS : rightMaxCPS;

        double baseCPS = MathUtils.getRandom(minCPS.getValue(), maxCPS.getValue());
        double adjustedCPS = applyBurstAndDecay(state, baseCPS, currentTime);

        state.nextDelay = (long) (1000.0 / adjustedCPS);
    }

    private void applyJitter(ClickButton clickButton) {
        BoolSetting jitterSetting = clickButton == ClickButton.LEFT ? leftJitter : rightJitter;
        if (!jitterSetting.getValue()) return;

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        float yawRandom = (float) ((MathUtils.getRandom(-0.5f, 0.5f)) * 0.5);
        float pitchRandom = (float) ((MathUtils.getRandom(-0.5f, 0.5f)) * 0.5);
        mc.player.setYRot(yaw + yawRandom);
        mc.player.setXRot(pitch + pitchRandom);
    }

    private boolean isButtonPressed(ClickButton clickButton) {
        return clickButton == ClickButton.LEFT ? mc.mouseHandler.isLeftPressed() : mc.mouseHandler.isRightPressed();
    }

    private void performClick(ClickButton clickButton) {
        KeyMapping key = clickButton == ClickButton.LEFT ? mc.options.keyAttack : mc.options.keyUse;
        KeyMapping.click(key.getDefaultKey());
    }

    private boolean shouldMiss() {
        if (!enableMiss.getValue()) return false;
        return MathUtils.getRandom(1, 100) <= missChance.getValue();
    }

    private void scheduleDoubleClick(ButtonState state, long currentTime) {
        if (!enableDoubleClick.getValue()) return;
        if (state.pendingDoubleClick) return;

        if (MathUtils.getRandom(1, 100) <= doubleClickChance.getValue()) {
            state.pendingDoubleClick = true;
            state.doubleClickTime = currentTime + doubleClickDelay.getValue();
        }
    }

    private void handlePendingDoubleClick(ButtonState state, ClickButton clickButton, long currentTime) {
        if (!state.pendingDoubleClick) return;
        if (currentTime < state.doubleClickTime) return;

        KeyMapping key = clickButton == ClickButton.LEFT ? mc.options.keyAttack : mc.options.keyUse;
        KeyMapping.click(key.getDefaultKey());

        state.pendingDoubleClick = false;
    }

    private double applyBurstAndDecay(ButtonState state, double value, long currentTime) {
        if (!enableBurst.getValue()) return value;

        if (state.isInBurst) {
            return value * burstCPSMultiplier.getValue();
        } else if (state.isInDecay) {
            double decayProgress = (double) (currentTime - state.decayStartTime) / decayDuration.getValue();
            decayProgress = Math.min(1.0, Math.max(0.0, decayProgress));

            double curveFactor = Math.pow(1.0 - decayProgress, decayCurve.getValue());
            double multiplier = 1.0 + (burstCPSMultiplier.getValue() - 1.0) * curveFactor;

            return value * multiplier;
        }

        return value;
    }

    private enum ClickButton {
        LEFT("Left"),
        RIGHT("Right"),
        BOTH("Both");

        public final String name;

        ClickButton(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private class ButtonState {
        long lastClickTime = 0;
        long nextDelay = 0;

        boolean isInBurst = false;
        boolean isInDecay = false;
        long burstStartTime = 0;
        long burstEndTime = 0;
        long decayStartTime = 0;
        long decayEndTime = 0;

        boolean pendingDoubleClick = false;
        long doubleClickTime = 0;

        void updateBurstState(long currentTime) {
            if (!enableBurst.getValue()) return;

            if (isInBurst) {
                if (currentTime >= burstEndTime) {
                    isInBurst = false;
                    isInDecay = true;
                    decayStartTime = currentTime;
                }
            } else if (isInDecay) {
                if (currentTime >= decayEndTime) {
                    isInDecay = false;
                }
            } else {
                if (MathUtils.getRandom(1, 100) <= burstChance.getValue()) {
                    isInBurst = true;
                    burstStartTime = currentTime;
                    burstEndTime = currentTime + burstDuration.getValue();
                    decayEndTime = burstEndTime + decayDuration.getValue();
                }
            }
        }

        void reset() {
            lastClickTime = 0;
            nextDelay = 0;
            isInBurst = false;
            isInDecay = false;
            burstStartTime = 0;
            burstEndTime = 0;
            decayStartTime = 0;
            decayEndTime = 0;
            pendingDoubleClick = false;
            doubleClickTime = 0;
        }
    }

}
