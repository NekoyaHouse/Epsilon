package com.github.epsilon.modules.impl.movement.elytrafly;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

public class ElytraFly extends Module {

    public static final ElytraFly INSTANCE = new ElytraFly();

    private ElytraFly() {
        super("Elytra Fly", Category.MOVEMENT);
        modes.put(ElytraFlightModes.Control, new ControlElytraFlightMode(this));
        modes.put(ElytraFlightModes.Pitch40, new Pitch40ElytraFlightMode(this));
    }

    public enum SwapMode {
        Silent,
        InvSwitch
    }

    public record Pitch40ControlState(
            boolean enabled,
            ElytraFlightModes mode,
            double lowerBounds,
            boolean autoTakeoff,
            double takeoffTargetHeight,
            boolean autoFirework,
            Float yawOverride
    ) {
    }

    private final Map<ElytraFlightModes, ElytraFlightMode> modes = new EnumMap<>(ElytraFlightModes.class);

    public final EnumSetting<ElytraFlightModes> mode = enumSetting("Mode", ElytraFlightModes.Control, this::onModeChanged);
    public final EnumSetting<SwapMode> swapMode = enumSetting("Swap Mode", SwapMode.InvSwitch);

    public final BoolSetting armored = boolSetting("Armored", false);
    public final BoolSetting unbreaking = boolSetting("Unbreaking", true);
    public final IntSetting unbreakingDelay = intSetting("Unbreaking Delay", 800, 100, 2000, 50, () -> unbreaking.getValue());
    public final BoolSetting noSprint = boolSetting("No Sprint", true, () -> mode.is(ElytraFlightModes.Control) && armored.getValue());
    public final BoolSetting useFireworks = boolSetting("Use Fireworks", true, () -> mode.is(ElytraFlightModes.Control));
    public final IntSetting boostDelay = intSetting("Boost Delay", 20, 2, 50, 1, () -> mode.is(ElytraFlightModes.Control) && useFireworks.getValue());

    public final DoubleSetting pitch40lowerBounds = doubleSetting("Pitch40 Lower Bounds", 180.0, -128.0, 1024.0, 1.0, () -> mode.is(ElytraFlightModes.Pitch40));
    public final DoubleSetting pitch40rotationSpeedUp = doubleSetting("Pitch40 Rotate Speed Up", 5.45, 1.0, 20.0, 0.05, () -> mode.is(ElytraFlightModes.Pitch40));
    public final DoubleSetting pitch40rotationSpeedDown = doubleSetting("Pitch40 Rotate Speed Down", 0.90, 0.5, 2.0, 0.05, () -> mode.is(ElytraFlightModes.Pitch40));
    public final IntSetting pitch40PacketDelay = intSetting("Pitch40 Packet Delay", 3, 1, 20, 1, () -> mode.is(ElytraFlightModes.Pitch40) && armored.getValue());
    public final BoolSetting pitch40AutoTakeoff = boolSetting("Pitch40 Auto Takeoff", true, () -> mode.is(ElytraFlightModes.Pitch40));
    public final DoubleSetting pitch40TakeoffTargetHeight = doubleSetting("Pitch40 Takeoff Target Height", 300.0, -128.0, 1024.0, 1.0, () -> mode.is(ElytraFlightModes.Pitch40) && pitch40AutoTakeoff.getValue());
    public final BoolSetting pitch40AutoFirework = boolSetting("Pitch40 Auto Firework", true, () -> mode.is(ElytraFlightModes.Pitch40) && pitch40AutoTakeoff.getValue());
    public final IntSetting pitch40FireworkCooldown = intSetting("Pitch40 Firework Cooldown", 10, 0, 100, 1, () -> mode.is(ElytraFlightModes.Pitch40) && pitch40AutoTakeoff.getValue() && pitch40AutoFirework.getValue());

    private ElytraFlightModes activeModeType;
    private Float pitch40YawOverride;

    @Override
    protected void onEnable() {
        activeModeType = mode.getValue();
        getActiveMode().armUnbreakingTimer();
        getActiveMode().onEnable();
    }

    @Override
    protected void onDisable() {
        getMode(activeModeType).onDisable();
    }

    @Override
    public String getInfo() {
        return mode.getValue().toString();
    }

    public boolean isArmorMode() {
        return isEnabled() && mode.is(ElytraFlightModes.Control) && armored.getValue();
    }

    public Pitch40ControlState capturePitch40ControlState() {
        return new Pitch40ControlState(
                isEnabled(),
                mode.getValue(),
                pitch40lowerBounds.getValue(),
                pitch40AutoTakeoff.getValue(),
                pitch40TakeoffTargetHeight.getValue(),
                pitch40AutoFirework.getValue(),
                pitch40YawOverride
        );
    }

    public void applyPitch40Control(boolean autoTakeoff, boolean autoFirework, double lowerBounds, double takeoffTargetHeight) {
        applyPitch40Control(autoTakeoff, autoFirework, lowerBounds, takeoffTargetHeight, null);
    }

    public void applyPitch40Control(boolean autoTakeoff, boolean autoFirework, double lowerBounds, double takeoffTargetHeight, Float yawOverride) {
        pitch40AutoTakeoff.setValue(autoTakeoff);
        pitch40AutoFirework.setValue(autoFirework);
        pitch40lowerBounds.setValue(lowerBounds);
        pitch40TakeoffTargetHeight.setValue(takeoffTargetHeight);
        pitch40YawOverride = yawOverride;

        if (!mode.is(ElytraFlightModes.Pitch40)) {
            mode.setMode(ElytraFlightModes.Pitch40);
        }
        if (!isEnabled()) {
            setEnabled(true);
        }
    }

    public void restorePitch40Control(Pitch40ControlState state) {
        if (state == null) return;

        pitch40lowerBounds.setValue(state.lowerBounds());
        pitch40AutoTakeoff.setValue(state.autoTakeoff());
        pitch40TakeoffTargetHeight.setValue(state.takeoffTargetHeight());
        pitch40AutoFirework.setValue(state.autoFirework());
        pitch40YawOverride = state.yawOverride();
        mode.setMode(state.mode());

        if (!state.enabled() && isEnabled()) {
            setEnabled(false);
        }
    }

    public float getPitch40Yaw(float fallback) {
        return pitch40YawOverride != null ? pitch40YawOverride : fallback;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) return;
        getActiveMode().onPlayerTick();
        if (isEnabled()) {
            getActiveMode().handleUnbreaking();
        }
    }

    @EventHandler
    private void onTravel(TravelEvent event) {
        if (nullCheck()) return;
        getActiveMode().onTravel(event);
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (nullCheck()) return;
        getActiveMode().onKeyboardInput(event);
    }

    @EventHandler
    private void onFallFlying(FallFlyingEvent event) {
        if (nullCheck()) return;
        getActiveMode().onFallFlying(event);
    }

    @EventHandler
    private void onFireworkRotationUpdate(FireworkRotationEvent event) {
        if (nullCheck()) return;
        getActiveMode().onFireworkUpdate(event);
    }

    @EventHandler
    private void onMousePress(MousePressEvent event) {
        if (mc.screen != null) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS && getActiveMode().shouldCancelRightClick()) {
            event.cancel();
        }
    }

    public ElytraFlightMode getActiveMode() {
        return getMode(mode.getValue());
    }

    private ElytraFlightMode getMode(ElytraFlightModes mode) {
        return modes.getOrDefault(mode, modes.get(ElytraFlightModes.Control));
    }

    private void onModeChanged(ElytraFlightModes newMode) {
        if (!isEnabled()) {
            activeModeType = newMode;
            return;
        }

        getMode(activeModeType).onDisable();
        activeModeType = newMode;
        getActiveMode().onEnable();
    }

}
