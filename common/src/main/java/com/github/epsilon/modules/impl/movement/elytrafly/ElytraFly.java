package com.github.epsilon.modules.impl.movement.elytrafly;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.EnumMap;
import java.util.Map;

public class ElytraFly extends Module {

    /** 启动停疾跑后，压制 AutoSprint 强制疾跑的 tick 数（覆盖启动后的两三个客户端 tick 即可）。 */
    private static final int SPRINT_SUPPRESS_TICKS = 3;

    public static final ElytraFly INSTANCE = new ElytraFly();

    private ElytraFly() {
        super("Elytra Fly", Category.MOVEMENT);
        modes.put(ElytraFlightModes.Control, new ControlElytraFlightMode(this));
        modes.put(ElytraFlightModes.Pitch40, new Pitch40ElytraFlightMode(this));
        modes.put(ElytraFlightModes.NCP, new NCPElytraFlightMode(this));
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
    public final BoolSetting noEat = boolSetting("No Eat", false);
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

    public final BoolSetting ncpInstantFly = boolSetting("NCP Instant Fly", true, () -> mode.is(ElytraFlightModes.NCP));
    public final DoubleSetting ncpUpPitch = doubleSetting("NCP Up Pitch", 0.0, 0.0, 90.0, 1.0, () -> mode.is(ElytraFlightModes.NCP));
    public final DoubleSetting ncpUpFactor = doubleSetting("NCP Up Factor", 1.0, 0.0, 10.0, 0.05, () -> mode.is(ElytraFlightModes.NCP));
    public final DoubleSetting ncpDownFactor = doubleSetting("NCP Down Factor", 1.0, 0.0, 10.0, 0.05, () -> mode.is(ElytraFlightModes.NCP));
    public final DoubleSetting ncpSpeed = doubleSetting("NCP Speed", 1.0, 0.1, 10.0, 0.1, () -> mode.is(ElytraFlightModes.NCP));
    public final DoubleSetting ncpDownSpeed = doubleSetting("NCP Down Speed", 1.0, 0.1, 10.0, 0.1, () -> mode.is(ElytraFlightModes.NCP));
    public final BoolSetting ncpTimer = boolSetting("NCP Timer", true, () -> mode.is(ElytraFlightModes.NCP));
    public final BoolSetting ncpSpeedLimit = boolSetting("NCP Speed Limit", true, () -> mode.is(ElytraFlightModes.NCP));
    public final DoubleSetting ncpMaxSpeed = doubleSetting("NCP Max Speed", 2.5, 0.1, 10.0, 0.1, () -> mode.is(ElytraFlightModes.NCP) && ncpSpeedLimit.getValue());
    public final BoolSetting ncpNoDrag = boolSetting("NCP No Drag", false, () -> mode.is(ElytraFlightModes.NCP));
    public final DoubleSetting ncpTimeout = doubleSetting("NCP Timeout", 0.5, 0.1, 1.0, 0.05, () -> mode.is(ElytraFlightModes.NCP));

    private ElytraFlightModes activeModeType;
    private Float pitch40YawOverride;
    /** 启动时停疾跑后，压制 AutoSprint 强制疾跑的剩余 tick 数。 */
    private int sprintSuppressTicks;

    @Override
    protected void onEnable() {
        activeModeType = mode.getValue();
        getActiveMode().armUnbreakingTimer();
        getActiveMode().onEnable();
        stopSprintOnce();
    }

    @Override
    protected void onDisable() {
        sprintSuppressTicks = 0;
        getMode(activeModeType).onDisable();
    }

    /**
     * 启动时停一次疾跑：直接清掉疾跑状态并松开疾跑键。
     * AutoSprint 每个客户端 tick 都会把疾跑键按回去，所以同时开一个短暂的压制窗口，
     * 否则这次停止会在下一 tick 被撤销（见 {@link com.github.epsilon.modules.impl.movement.AutoSprint}）。
     */
    private void stopSprintOnce() {
        if (mc.player == null) return;

        sprintSuppressTicks = SPRINT_SUPPRESS_TICKS;
        mc.player.setSprinting(false);
        mc.options.keySprint.setDown(false);
    }

    /** AutoSprint 是否应当暂停强制疾跑。 */
    public boolean isSprintSuppressed() {
        return sprintSuppressTicks > 0;
    }

    @Override
    public String getInfo() {
        return mode.getValue().toString();
    }

    /**
     * 当前是否处于穿甲飞行状态：滑翔靠「换上鞘翅触发一次再换回胸甲」维持，鞘翅并不在身上。
     * 渲染层据此隐藏滑翔姿态，AutoArmor 据此不再把鞘翅设为最高优先级。
     */
    public boolean isArmorMode() {
        return isEnabled() && armored.getValue()
                && (mode.is(ElytraFlightModes.Control) || mode.is(ElytraFlightModes.NCP));
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
        if (sprintSuppressTicks > 0) {
            sprintSuppressTicks--;
        }
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
        if (mc.gui.screen() != null) return;
        // 只有开启 No Eat 时才拦截右键，其余情况允许正常进食/使用物品。
        if (!noEat.getValue()) return;
        if (event.getButton() == InputConstants.MOUSE_BUTTON_RIGHT && event.getAction() == InputConstants.PRESS && getActiveMode().shouldCancelRightClick()) {
            event.cancel();
        }
    }

    @EventHandler
    private void onRightClick(RightClickEvent event) {
        if (nullCheck()) return;
        getActiveMode().onRightClick();
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
