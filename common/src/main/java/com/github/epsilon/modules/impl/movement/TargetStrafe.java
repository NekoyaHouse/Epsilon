package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.combat.KillAura;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

public class TargetStrafe extends Module {

    public static final TargetStrafe INSTANCE = new TargetStrafe();

    private TargetStrafe() {
        super("Target Strafe", Category.MOVEMENT);
    }

    private final DoubleSetting range = doubleSetting("Range", 1.0, 0.1, 8.0, 0.1);
    private final DoubleSetting followRange = doubleSetting("Follow Range", 1.5, 0.1, 10.0, 0.1);
    private final BoolSetting controlDirection = boolSetting("Control Direction", true);
    private final BoolSetting edgeCheck = boolSetting("Edge Check", true);
    private final BoolSetting voidCheck = boolSetting("Void Check", true);
    private final BoolSetting jumpOnly = boolSetting("Jump Only", true);
    private final BoolSetting moveOnly = boolSetting("Move Only", true);
    private final BoolSetting drawRadius = boolSetting("Draw Radius", true);
    private final ColorSetting circleColor = colorSetting("Color", new Color(255, 255, 255));
    private final IntSetting circlePoints = intSetting("Points", 32, 3, 120, 1);

    private int direction = 1;

    @EventHandler
    public void onMoveInput(KeyboardInputEvent event) {
        if (nullCheck()) return;

        KillAura killAura = KillAura.INSTANCE;
        if (!killAura.isEnabled()) return;
        LivingEntity target = killAura.target;

        if (target == null) return;

        if (moveOnly.getValue() && !mc.player.isMoving()) return;
        if (jumpOnly.getValue() && !mc.options.keyJump.isDown()) return;

        double distance = mc.player.distanceTo(target);
        if (distance > followRange.getValue()) return;

        if (mc.player.horizontalCollision) {
            direction = -direction;
        }

        if (controlDirection.getValue()) {
            if (mc.options.keyLeft.isDown()) direction = 1;
            if (mc.options.keyRight.isDown()) direction = -1;
        }

        double speed = Math.sqrt(mc.player.getDeltaMovement().x * mc.player.getDeltaMovement().x + mc.player.getDeltaMovement().z * mc.player.getDeltaMovement().z);
        double strafeYaw = Math.atan2(target.getZ() - mc.player.getZ(), target.getX() - mc.player.getX());

        Vec3 strafeVec = computeDirectionVec(strafeYaw, distance, speed, range.getValue().floatValue(), direction);
        Vec3 pointCoords = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()).add(strafeVec);

        if (!validatePoint(pointCoords)) {
            direction = -direction;
            strafeVec = computeDirectionVec(strafeYaw, distance, speed, range.getValue().floatValue(), direction);
        }

        double targetYaw = Math.toDegrees(Math.atan2(-strafeVec.x, strafeVec.z));
        MovementFix.INSTANCE.fixMovement(event, (float) targetYaw);

        if (event.getForward() < 0.8f) {
            mc.player.setSprinting(false);
        }
    }

    private Vec3 computeDirectionVec(double strafeYaw, double distance, double speed, float range, int direction) {
        double yaw = strafeYaw - (0.5f * Math.PI);
        double encirclement = distance - range;

        if (encirclement < -speed) encirclement = -speed;

        double encirclementX = -Math.sin(yaw) * encirclement;
        double encirclementZ = Math.cos(yaw) * encirclement;

        double strafeX = -Math.sin(strafeYaw) * speed * direction;
        double strafeZ = Math.cos(strafeYaw) * speed * direction;

        return new Vec3(encirclementX + strafeX, 0.0, encirclementZ + strafeZ);
    }

    private boolean validatePoint(Vec3 point) {
        AABB box = mc.player.getDimensions(mc.player.getPose()).makeBoundingBox(point);
        if (mc.level.getBlockCollisions(mc.player, box).iterator().hasNext()) return false;

        if (edgeCheck.getValue() && isCloseToFall(point)) return false;

        if (voidCheck.getValue() && wouldFallIntoVoid(point)) return false;

        return true;
    }

    private boolean isCloseToFall(Vec3 point) {
        point = new Vec3(point.x, Math.floor(point.y), point.z);
        AABB box = mc.player.getDimensions(mc.player.getPose()).makeBoundingBox(point)
                .inflate(-0.05, 0.0, -0.05) // 稍微收缩
                .move(0.0, -1.2, 0.0); // 检查下方 1.2 格

        return !mc.level.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    private boolean wouldFallIntoVoid(Vec3 point) {
        for (int i = 0; i < 5; i++) {
            BlockPos pos = BlockPos.containing(point.x, point.y - i, point.z);
            if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!drawRadius.getValue()) return;

        KillAura killAura = KillAura.INSTANCE;
        if (!killAura.isEnabled()) return;
        LivingEntity target = killAura.target;
        if (target == null) return;

        drawCircle(target, range.getValue(), circleColor.getValue());
    }

    private void drawCircle(LivingEntity entity, double radius, Color color) {
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());

        int points = circlePoints.getValue();
        double anglePerPoint = 360.0 / points;

        for (int i = 0; i < points; i++) {
            double rad = Math.toRadians(i * anglePerPoint);
            double radNext = Math.toRadians((i + 1) * anglePerPoint);

            double x1 = Math.sin(rad) * radius;
            double z1 = Math.cos(rad) * radius;
            double x2 = Math.sin(radNext) * radius;
            double z2 = Math.cos(radNext) * radius;

            Render3DScheduler.INSTANCE.addLine(new Vec3(x + x1, y, z + z1), new Vec3(x + x2, y, z + z2), color, 2.0f);
        }
    }

}
