package com.github.epsilon.modules.impl.movement.follower;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFlightModes;
import com.github.epsilon.modules.impl.movement.elytrafly.ElytraFly;
import com.github.epsilon.settings.impl.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class Follower extends Module {

    public static final Follower INSTANCE = new Follower();

    public enum Mode {
        Straight,
        AStar
    }

    private final Map<Mode, FollowerNavigator> navigators = new EnumMap<>(Mode.class);

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Straight);
    private final DoubleSetting range = doubleSetting("Range", 96.0, 8.0, 256.0, 1.0);
    private final DoubleSetting stopDistance = doubleSetting("Stop Distance", 6.0, 1.0, 32.0, 0.5);
    private final BoolSetting ignoreInvisible = boolSetting("Ignore Invisible", true);
    private final IntSetting predictTicks = intSetting("Predict Ticks", 4, 0, 20, 1);
    private final DoubleSetting verticalDeadzone = doubleSetting("Vertical Deadzone", 1.5, 0.0, 12.0, 0.5);
    private final IntSetting searchRadius = intSetting("Search Radius", 24, 6, 64, 1, () -> mode.is(Mode.AStar));
    private final IntSetting maxNodes = intSetting("Max Nodes", 1200, 100, 6000, 100, () -> mode.is(Mode.AStar));
    private final BoolSetting renderPath = boolSetting("Render Path", true);
    private final ColorSetting pathColor = colorSetting("Path Color", new Color(80, 220, 255, 210), () -> renderPath.getValue());
    private final DoubleSetting pathLineWidth = doubleSetting("Path Line Width", 2.5, 0.5, 8.0, 0.5, () -> renderPath.getValue());

    private FollowerInput controlInput;
    private LivingEntity target;
    private List<Vec3> pathPoints = List.of();

    private Follower() {
        super("Follower", Category.MOVEMENT);
        navigators.put(Mode.Straight, new StraightFollowerNavigator());
        navigators.put(Mode.AStar, new AStarFollowerNavigator());
    }

    @Override
    protected void onDisable() {
        clearControl();
    }

    @Override
    public String getInfo() {
        if (target != null && target.isAlive()) {
            return target.getName().getString();
        }
        return mode.getValue().toString();
    }

    public FollowerInput getControlInput() {
        return controlInput;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (nullCheck() || !canControlElytraFly()) {
            clearControl();
            return;
        }

        target = Managers.TARGET.acquirePrimary(TargetRequest.of(
                range.getValue(),
                360.0f,
                true,
                false,
                false,
                false,
                !ignoreInvisible.getValue(),
                living -> living instanceof Player,
                1
        ));

        if (target == null
                || (mode.is(Mode.Straight) && mc.player.distanceTo(target) <= stopDistance.getValue())) {
            controlInput = null;
            pathPoints = List.of();
            return;
        }

        Vec3 targetPos = predictedTargetPos(target);
        FollowerConfig config = new FollowerConfig(
                stopDistance.getValue(),
                verticalDeadzone.getValue(),
                searchRadius.getValue(),
                maxNodes.getValue()
        );
        FollowerPath path = navigators.get(mode.getValue()).getPath(mc.player, target, targetPos, config);
        pathPoints = path.points();
        controlInput = createInput(mc.player, path.nextPoint());
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onKeyboardInput(KeyboardInputEvent event) {
        if (controlInput == null || !canControlElytraFly()) return;

        event.setForward(controlInput.forwardImpulse());
        event.setStrafe(controlInput.strafeImpulse());
        event.setJump(controlInput.jump());
        event.setSneak(controlInput.sneak());
        event.setSprint(false);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderPath.getValue() || pathPoints.size() < 2 || !canControlElytraFly()) return;

        for (int i = 1; i < pathPoints.size(); i++) {
            Render3DScheduler.INSTANCE.addLine(pathPoints.get(i - 1), pathPoints.get(i), pathColor.getValue(), pathLineWidth.getValue().floatValue());
        }
    }

    private boolean canControlElytraFly() {
        return ElytraFly.INSTANCE.isEnabled() && ElytraFly.INSTANCE.mode.is(ElytraFlightModes.Control);
    }

    private Vec3 predictedTargetPos(LivingEntity target) {
        return target.position().add(target.getDeltaMovement().scale(predictTicks.getValue()));
    }

    private FollowerInput createInput(LocalPlayer player, Vec3 point) {
        Vec3 delta = point.subtract(player.position());
        if (delta.horizontalDistanceSqr() < 0.01 && Math.abs(delta.y) <= verticalDeadzone.getValue()) {
            return null;
        }

        float yaw = yawTo(delta);
        float pitch = pitchTo(delta);
        DirectionInput direction = directionInput(Mth.wrapDegrees(yaw - player.getYRot()));
        boolean jump = delta.y > verticalDeadzone.getValue();
        boolean sneak = delta.y < -verticalDeadzone.getValue();

        return new FollowerInput(
                direction.forward(),
                direction.back(),
                direction.left(),
                direction.right(),
                jump,
                sneak,
                yaw,
                pitch
        );
    }

    private float yawTo(Vec3 delta) {
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f);
    }

    private float pitchTo(Vec3 delta) {
        double horizontal = Math.max(0.001, delta.horizontalDistance());
        return Mth.clamp((float) -Math.toDegrees(Math.atan2(delta.y, horizontal)), -90.0f, 90.0f);
    }

    private DirectionInput directionInput(float yawDelta) {
        int sector = Math.floorMod(Math.round(yawDelta / 45.0f), 8);
        return switch (sector) {
            case 0 -> new DirectionInput(true, false, false, false);
            case 1 -> new DirectionInput(true, false, false, true);
            case 2 -> new DirectionInput(false, false, false, true);
            case 3 -> new DirectionInput(false, true, false, true);
            case 4 -> new DirectionInput(false, true, false, false);
            case 5 -> new DirectionInput(false, true, true, false);
            case 6 -> new DirectionInput(false, false, true, false);
            default -> new DirectionInput(true, false, true, false);
        };
    }

    private void clearControl() {
        controlInput = null;
        target = null;
        pathPoints = List.of();
    }

    private record DirectionInput(boolean forward, boolean back, boolean left, boolean right) {
    }

}
