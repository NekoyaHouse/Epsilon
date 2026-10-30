package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.managers.rotation.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AimBot extends Module {

    public static final AimBot INSTANCE = new AimBot();

    private AimBot() {
        super("Aim Bot", Category.COMBAT);
    }

    private enum Mode {
        AimAssist,
        BowAim
    }

    private enum Rotation {
        Client,
        Silent
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.AimAssist);
    private final EnumSetting<Rotation> rotation = enumSetting("Rotation", Rotation.Silent, () -> !mode.is(Mode.AimAssist));
    private final IntSetting aimStrength = intSetting("Aim Strength", 30, 1, 100, 1, () -> mode.is(Mode.AimAssist));
    private final IntSetting aimSmooth = intSetting("Aim Smooth", 45, 1, 180, 1, () -> mode.is(Mode.AimAssist));
    private final IntSetting aimTime = intSetting("Aim Time", 2, 1, 10, 1, () -> mode.is(Mode.AimAssist));
    private final BoolSetting onlyWeapon = boolSetting("Only Weapon", false, () -> mode.is(Mode.AimAssist));
    private final BoolSetting lmbActivation = boolSetting("LMB Activation", false, () -> mode.is(Mode.AimAssist));
    private final BoolSetting ignoreWalls = boolSetting("Ignore Walls", true);
    private final IntSetting reactionTime = intSetting("Reaction Time", 80, 1, 500, 1, () -> mode.is(Mode.AimAssist) && !ignoreWalls.getValue());
    private final BoolSetting ignoreInvisible = boolSetting("Ignore Invis", false, () -> mode.is(Mode.AimAssist));

    private final IntSetting predictTicks = intSetting("Predict Ticks", 2, 0, 20, 1, () -> mode.is(Mode.BowAim));
    private final IntSetting bowFov = intSetting("Bow FOV", 90, 10, 360, 1, () -> mode.is(Mode.BowAim));
    private final IntSetting bowRange = intSetting("Bow Range", 64, 8, 128, 1, () -> mode.is(Mode.BowAim));
    private final IntSetting bowSmooth = intSetting("Bow Smooth", 30, 5, 100, 1, () -> mode.is(Mode.BowAim));

    private Entity target;
    private float rotationYaw, assistAcceleration;
    private float bowTargetYaw = Float.NaN, bowTargetPitch = Float.NaN;
    private int aimTicks;
    private final TimerUtils visibleTime = new TimerUtils();

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        target = null;
        assistAcceleration = 0.0f;
        aimTicks = 0;
        bowTargetYaw = Float.NaN;
        bowTargetPitch = Float.NaN;
        visibleTime.reset();
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        switch (mode.getValue()) {
            case AimAssist -> updateAimAssist();
            case BowAim -> updateBowAim();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mode.is(Mode.AimAssist)) {
            if (!Float.isNaN(rotationYaw)) {
                mc.player.setYRot(Mth.lerp(assistAcceleration, mc.player.getYRot(), rotationYaw));
            }
            return;
        }

        if (mode.is(Mode.BowAim) && rotation.is(Rotation.Client) && !Float.isNaN(bowTargetYaw)) {
            float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            float factor = Mth.clamp(bowSmooth.getValue() / 100.0f * tickDelta, 0.0f, 1.0f);
            float currentYaw = mc.player.getYRot();
            mc.player.setYRot(currentYaw + Mth.wrapDegrees(bowTargetYaw - currentYaw) * factor);
            mc.player.setXRot(Mth.lerp(factor, mc.player.getXRot(), bowTargetPitch));
        }
    }

    private void updateBowAim() {
        if (!isUsingBow()) {
            cancelBowAim();
            return;
        }

        Player bestTarget = getBestBowTarget();
        target = bestTarget;
        if (bestTarget == null) {
            cancelBowAim();
            return;
        }

        float charge = BowItem.getPowerForTime(mc.player.getTicksUsingItem());
        double velocity = charge * 3.0;

        Vec3 predicted = predictPosition(bestTarget, predictTicks.getValue());

        double dx = predicted.x - mc.player.getX();
        double dz = predicted.z - mc.player.getZ();
        double dy = (predicted.y + bestTarget.getEyeHeight(bestTarget.getPose()))
                - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));

        float targetPitch = computeProjectilePitch(dx, dz, dy, velocity);
        if (Float.isNaN(targetPitch)) {
            cancelBowAim();
            return;
        }

        float targetYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);

        bowTargetYaw = targetYaw;
        bowTargetPitch = targetPitch;

        RotationManager.INSTANCE.setRotations(
                new Rot2f(targetYaw, targetPitch),
                bowSmooth.getValue(),
                Priority.Medium
        );
    }

    private void cancelBowAim() {
        bowTargetYaw = Float.NaN;
        bowTargetPitch = Float.NaN;
    }

    private void updateAimAssist() {
        if (lmbActivation.getValue() && !mc.options.keyAttack.isDown()) {
            resetAimAssist();
            return;
        }

        if (onlyWeapon.getValue() && !mc.player.getMainHandItem().has(DataComponents.WEAPON)) {
            resetAimAssist();
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            aimTicks++;
        } else {
            aimTicks = 0;
        }

        if (aimTicks >= aimTime.getValue()) {
            assistAcceleration = 0.0f;
            return;
        }

        Player nearestTarget = getNearestTarget(5.0f);
        assistAcceleration = Mth.clamp(assistAcceleration + aimStrength.getValue() / 10000.0f, 0.0f, 1.0f);

        if (nearestTarget != null) {
            if (!mc.player.hasLineOfSight(nearestTarget) && !ignoreWalls.getValue()) {
                visibleTime.reset();
            }

            if (!visibleTime.passedMillise(reactionTime.getValue())) {
                rotationYaw = Float.NaN;
                return;
            }

            if (Float.isNaN(rotationYaw)) {
                rotationYaw = mc.player.getYRot();
            }

            float deltaYaw = Mth.wrapDegrees((float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(nearestTarget.getEyePosition().z - mc.player.getZ(), nearestTarget.getEyePosition().x - mc.player.getX())) - 90.0) - rotationYaw);
            if (deltaYaw > 180.0f) {
                deltaYaw -= 180.0f;
            }
            float yawStep = Mth.clamp(Mth.abs(deltaYaw), -aimSmooth.getValue(), aimSmooth.getValue());
            float newYaw = rotationYaw + (deltaYaw > 0.0f ? yawStep : -yawStep);
            double gcdFix = Math.pow(mc.options.sensitivity().get() * 0.6 + 0.2, 3.0) * 1.2;
            rotationYaw = (float) (newYaw - (newYaw - rotationYaw) % gcdFix);
        } else {
            resetAimAssist();
        }
    }

    private void resetAimAssist() {
        rotationYaw = Float.NaN;
        assistAcceleration = 0.0f;
        aimTicks = 0;
    }

    private float computeProjectilePitch(double dx, double dz, double dy, double velocity) {
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double v2 = velocity * velocity;
        double v4 = v2 * v2;
        double g = 0.05;

        double discriminant = v4 - g * (g * horizontalDist * horizontalDist + 2.0 * dy * v2);
        if (discriminant < 0.0) return Float.NaN;

        double tanTheta = (v2 - Math.sqrt(discriminant)) / (g * horizontalDist);
        return (float) -Math.toDegrees(Math.atan(tanTheta));
    }

    private Player getBestBowTarget() {
        Player best = null;
        double bestScore = Double.MAX_VALUE;
        float halfFov = bowFov.getValue() / 2.0f;
        double rangeSq = (double) bowRange.getValue() * bowRange.getValue();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || shouldSkipPlayer(player)) continue;

            double distSq = mc.player.distanceToSqr(player);
            if (distSq > rangeSq) continue;

            float yawDiff = Math.abs(Mth.wrapDegrees(getYawBetween(mc.player.getYRot(), mc.player.getX(), mc.player.getZ(), player.getX(), player.getZ()) - mc.player.getYRot()));
            if (yawDiff > halfFov) continue;

            if (!ignoreWalls.getValue() && !mc.player.hasLineOfSight(player)) continue;

            double score = yawDiff * Math.sqrt(distSq);
            if (score < bestScore) {
                best = player;
                bestScore = score;
            }
        }
        return best;
    }

    private Player getNearestTarget(float range) {
        Player nearest = null;
        double bestDistance = range * range;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || shouldSkipPlayer(player)) continue;
            if (entity.isInvisible() && ignoreInvisible.getValue()) continue;
            if (!ignoreWalls.getValue() && !mc.player.hasLineOfSight(player)) continue;
            double distance = mc.player.distanceToSqr(player);
            if (distance < bestDistance) {
                nearest = player;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean shouldSkipPlayer(Player player) {
        if (player == mc.player || !player.isAlive() || player.isDeadOrDying()) return true;
        if (AntiBot.INSTANCE.isBot(player)) return true;
        if (FriendManager.INSTANCE.isFriend(player)) return true;
        return false;
    }

    private float getYawBetween(float yaw, double srcX, double srcZ, double destX, double destZ) {
        double xDist = destX - srcX;
        double zDist = destZ - srcZ;
        float yaw1 = (float) (StrictMath.atan2(zDist, xDist) * 180.0 / Math.PI) - 90.0f;
        return yaw + Mth.wrapDegrees(yaw1 - yaw);
    }

    private Vec3 predictPosition(Entity entity, int ticks) {
        double motionX = entity.getX() - entity.xo;
        double motionY = entity.getY() - entity.yo;
        double motionZ = entity.getZ() - entity.zo;
        return entity.position().add(motionX * ticks, motionY * ticks, motionZ * ticks);
    }

    private boolean isUsingBow() {
        return mc.player.getUseItem().getItem() instanceof BowItem;
    }

}
