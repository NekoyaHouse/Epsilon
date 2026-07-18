package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ButtonSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public class AutoMap extends Module {

    public static final AutoMap INSTANCE = new AutoMap();

    private static final int STATE_VERSION = 1;
    private static final double TWO_PI = Math.PI * 2.0;
    private final BoolSetting autoLaunch = boolSetting("Auto Launch", true);
    private final DoubleSetting spiralSpacing = doubleSetting("Spiral Spacing", 192.0, 16.0, 1024.0, 1.0);
    private final IntSetting pointsPerLap = intSetting("Points Per Lap", 16, 4, 96, 1);
    private final DoubleSetting arrivalDistance = doubleSetting("Arrival Distance", 8.0, 2.0, 64.0, 0.5);
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 180, 10, 180, 10);
    private final DoubleSetting takeoffHeight = doubleSetting("Takeoff Height", 192.0, 16.0, 1024.0, 1.0);
    private final BoolSetting takeoffFirework = boolSetting("Takeoff Firework", true, autoLaunch::getValue);

    private final IntSetting saveInterval = intSetting("Save Interval", 100, 20, 1200, 20);
    private final ButtonSetting writeRecord = buttonSetting("Write Record", () -> writeRecord(true));
    private final ButtonSetting resetRecord = buttonSetting("Reset Record", () -> resetRecord(true));

    private boolean initializedInWorld;
    private boolean routeLoaded;
    private boolean routeDirty;
    private int saveTicks;

    private double centerX;
    private double centerZ;
    private double spiralAngle;
    private double spiralRadius;
    private String dimensionId = "";

    private ElytraFly.Pitch40ControlState restorePitch40State;
    private double activeTakeoffTargetHeight = Double.NaN;
    private double takeoffBaseY = Double.NaN;

    private AutoMap() {
        super("Auto Map", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        saveTicks = 0;
        if (!nullCheck()) {
            initializeInWorld();
        }
    }

    @Override
    protected void onDisable() {
        initializedInWorld = false;
        restoreFlightControl();
    }

    @Override
    public String getInfo() {
        return routeLoaded ? "R" + Math.round(spiralRadius) : null;
    }

    @Override
    protected void resetCustomState() {
        initializedInWorld = false;
        routeLoaded = false;
        routeDirty = false;
        saveTicks = 0;
        centerX = 0.0;
        centerZ = 0.0;
        spiralAngle = 0.0;
        spiralRadius = 0.0;
        dimensionId = "";
    }

    @Override
    public JsonObject saveCustomState() {
        if (!routeLoaded) return null;

        JsonObject state = new JsonObject();
        state.addProperty("version", STATE_VERSION);
        state.addProperty("dimension", dimensionId);
        state.addProperty("centerX", centerX);
        state.addProperty("centerZ", centerZ);
        state.addProperty("spiralAngle", spiralAngle);
        state.addProperty("spiralRadius", spiralRadius);
        return state;
    }

    @Override
    public void loadCustomState(JsonObject state) {
        if (state == null) {
            resetCustomState();
            return;
        }

        String loadedDimension = readString(state, "dimension", "");
        double loadedCenterX = readDouble(state, "centerX", Double.NaN);
        double loadedCenterZ = readDouble(state, "centerZ", Double.NaN);
        double loadedAngle = readDouble(state, "spiralAngle", Double.NaN);
        double loadedRadius = readDouble(state, "spiralRadius", Double.NaN);

        if (!isFinite(loadedCenterX) || !isFinite(loadedCenterZ) || !isFinite(loadedAngle) || !isFinite(loadedRadius) || loadedRadius < 0.0) {
            resetCustomState();
            return;
        }

        dimensionId = loadedDimension;
        centerX = loadedCenterX;
        centerZ = loadedCenterZ;
        spiralAngle = normalizeAngle(loadedAngle);
        spiralRadius = loadedRadius;
        routeLoaded = true;
        routeDirty = false;
        saveTicks = 0;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck()) {
            initializedInWorld = false;
            return;
        }

        if (!initializedInWorld) {
            initializeInWorld();
        }

        syncFlightControl();
        updateNavigation();
        autoSaveRecord();
    }

    @EventHandler
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (nullCheck() || !initializedInWorld) return;

        event.setForward(1.0F);
        event.setStrafe(0.0F);
        if (autoLaunch.getValue() && !mc.player.isFallFlying() && !mc.player.isInWater()) {
            event.setJump(true);
        }
    }

    private void initializeInWorld() {
        initializedInWorld = true;

        if (!routeLoaded || !Objects.equals(dimensionId, currentDimension())) {
            resetRouteToPlayer(false);
        }

        updateTakeoffTarget(true);
        syncFlightControl();
    }

    private void syncFlightControl() {
        captureFlightState();
        updateTakeoffTarget(false);
        ElytraFly.INSTANCE.applyPitch40Control(autoLaunch.getValue(), autoLaunch.getValue() && takeoffFirework.getValue(), -128.0, activeTakeoffTargetHeight, yawTo(currentTarget()));
    }

    private void captureFlightState() {
        if (restorePitch40State == null) {
            restorePitch40State = ElytraFly.INSTANCE.capturePitch40ControlState();
        }
    }

    private void restoreFlightControl() {
        if (restorePitch40State != null) {
            ElytraFly.INSTANCE.restorePitch40Control(restorePitch40State);
            restorePitch40State = null;
        }
        activeTakeoffTargetHeight = Double.NaN;
        takeoffBaseY = Double.NaN;
    }

    private void updateNavigation() {
        if (!routeLoaded) return;

        Vec3 target = currentTarget();
        int advances = 0;
        while (horizontalDistanceSqr(target) <= Mth.square(arrivalDistance.getValue().doubleValue()) && advances++ < 8) {
            advanceTarget();
            target = currentTarget();
        }

        float yaw = yawTo(target);
        float pitch = Managers.ROTATION.isActive() ? Managers.ROTATION.getPitch() : mc.player.getXRot();
        Managers.ROTATION.setRotations(new Rot2f(yaw, pitch), rotationSpeed.getValue(), Priority.Highest);
    }

    private Vec3 currentTarget() {
        double x = centerX + Math.cos(spiralAngle) * spiralRadius;
        double z = centerZ + Math.sin(spiralAngle) * spiralRadius;
        return new Vec3(x, mc.player.getY(), z);
    }

    private void advanceTarget() {
        spiralAngle = normalizeAngle(spiralAngle + angleStep());
        spiralRadius += radiusStep();
        routeDirty = true;
    }

    private double angleStep() {
        return TWO_PI / Math.max(1, pointsPerLap.getValue());
    }

    private double radiusStep() {
        return spiralSpacing.getValue() / Math.max(1, pointsPerLap.getValue());
    }

    private double horizontalDistanceSqr(Vec3 target) {
        double dx = target.x - mc.player.getX();
        double dz = target.z - mc.player.getZ();
        return dx * dx + dz * dz;
    }

    private float yawTo(Vec3 target) {
        double dx = target.x - mc.player.getX();
        double dz = target.z - mc.player.getZ();
        if (dx * dx + dz * dz < 1.0E-6) {
            return mc.player.getYRot();
        }
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
    }

    private void resetRecord(boolean notify) {
        if (nullCheck()) {
            if (notify) sendMessage("当前没有可用的玩家位置");
            return;
        }

        resetRouteToPlayer(true);
        writeRecord(false);
        if (notify) sendMessage("已重置记录");
    }

    private void resetRouteToPlayer(boolean dirty) {
        centerX = mc.player.getX();
        centerZ = mc.player.getZ();
        dimensionId = currentDimension();
        spiralAngle = normalizeAngle(angleStep());
        spiralRadius = radiusStep();
        routeLoaded = true;
        routeDirty = dirty;
        saveTicks = 0;
    }

    private void updateTakeoffTarget(boolean forceReset) {
        if (!forceReset && !needsTakeoffRetarget()) {
            return;
        }

        takeoffBaseY = mc.player.getY();
        activeTakeoffTargetHeight = takeoffBaseY + takeoffHeight.getValue();
    }

    private boolean needsTakeoffRetarget() {
        if (Double.isNaN(activeTakeoffTargetHeight) || Double.isNaN(takeoffBaseY)) {
            return true;
        }

        return mc.player.onGround() && !mc.player.isFallFlying() && Math.abs(mc.player.getY() - takeoffBaseY) > 0.5;
    }

    private void writeRecord(boolean notify) {
        if (!routeLoaded) {
            if (!nullCheck()) {
                resetRouteToPlayer(true);
            } else {
                if (notify) sendMessage("暂无可写入的记录");
                return;
            }
        }

        routeDirty = false;
        saveTicks = 0;
        if (notify) sendMessage("已写入记录");
    }

    private void autoSaveRecord() {
        if (!routeDirty) return;

        saveTicks++;
        if (saveTicks >= saveInterval.getValue()) {
            writeRecord(false);
        }
    }

    private String currentDimension() {
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    private void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[自动扫图] " + message));
        }
    }

    private static double normalizeAngle(double angle) {
        double normalized = angle % TWO_PI;
        return normalized < 0.0 ? normalized + TWO_PI : normalized;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String readString(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double readDouble(JsonObject object, String key, double fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsDouble() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

}
