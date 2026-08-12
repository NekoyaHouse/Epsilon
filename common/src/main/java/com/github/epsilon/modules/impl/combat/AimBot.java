package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.events.impl.UseItemEvent;
import com.github.epsilon.managers.FriendManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
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
    private final BoolSetting ignoreWalls = boolSetting("Ignore Walls", true, () -> mode.is(Mode.AimAssist));
    private final IntSetting reactionTime = intSetting("Reaction Time", 80, 1, 500, 1, () -> mode.is(Mode.AimAssist) && !ignoreWalls.getValue());
    private final BoolSetting ignoreInvisible = boolSetting("Ignore Invis", false, () -> mode.is(Mode.AimAssist));
    private final IntSetting predictTicks = intSetting("Predict Ticks", 2, 0, 20, 1, () -> mode.is(Mode.BowAim));

    private Entity target;
    private float rotationYaw, rotationPitch, assistAcceleration;
    private int aimTicks;
    private final TimerUtils visibleTime = new TimerUtils();

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        target = null;
        rotationYaw = mc.player.getYRot();
        rotationPitch = mc.player.getXRot();
        assistAcceleration = 0.0f;
        aimTicks = 0;
        visibleTime.reset();
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        switch (mode.getValue()) {
            case AimAssist -> updateAimAssist();
            case BowAim -> updateBowAim();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onSendPosition(SendPositionEvent event) {
        if (mode.is(Mode.BowAim) && isUsingBow() && !Float.isNaN(rotationYaw) && !Float.isNaN(rotationPitch)) {
            event.setYaw(rotationYaw);
            event.setPitch(rotationPitch);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onUseItem(UseItemEvent event) {
        if (mode.is(Mode.BowAim) && isUsingBow() && !Float.isNaN(rotationYaw) && !Float.isNaN(rotationPitch)) {
            event.setYaw(rotationYaw);
            event.setPitch(rotationPitch);
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

        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (isUsingBow() && target != null && (mc.player.hasLineOfSight(target) || ignoreWalls.getValue())) {
            if (rotation.is(Rotation.Client)) {
                mc.player.setYRot(Mth.lerp(tickDelta, mc.player.yRotO, rotationYaw));
                mc.player.setXRot(Mth.lerp(tickDelta, mc.player.xRotO, rotationPitch));
            }
        }

        if (rotation.is(Rotation.Client) && mode.is(Mode.BowAim) && isUsingBow()) {
            mc.player.setYRot(Mth.lerp(tickDelta, mc.player.yRotO, rotationYaw));
            mc.player.setXRot(Mth.lerp(tickDelta, mc.player.xRotO, rotationPitch));
        }
    }

    private void updateBowAim() {
        if (!isUsingBow()) return;

        Player nearestTarget = getTargetByFOV(128.0f);
        target = nearestTarget;
        if (nearestTarget == null) return;

        float currentDuration = BowItem.getPowerForTime(mc.player.getTicksUsingItem());
        float pitch = (float) -Math.toDegrees(calculateArc(nearestTarget, currentDuration * 3.0f));
        if (Float.isNaN(pitch)) return;

        Vec3 predicted = predictPosition(nearestTarget, predictTicks.getValue());
        double iX = predicted.x - nearestTarget.xOld;
        double iZ = predicted.z - nearestTarget.zOld;
        double distance = mc.player.distanceTo(nearestTarget);
        distance -= distance % 2.0;
        iX = distance / 2.0 * iX * (mc.player.isSprinting() ? 1.3 : 1.1);
        iZ = distance / 2.0 * iZ * (mc.player.isSprinting() ? 1.3 : 1.1);
        rotationYaw = (float) Math.toDegrees(Math.atan2(predicted.z + iZ - mc.player.getZ(), predicted.x + iX - mc.player.getX())) - 90.0f;
        rotationPitch = pitch;
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

    private float calculateArc(Player target, double duration) {
        double yArc = target.getY() + target.getEyeHeight(target.getPose()) - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dX = target.getX() - mc.player.getX();
        double dZ = target.getZ() - mc.player.getZ();
        double dirRoot = Math.sqrt(dX * dX + dZ * dZ);
        return calculateArc(duration, dirRoot, yArc);
    }

    private float calculateArc(double d, double dr, double y) {
        y = 2.0 * y * d * d;
        y = 0.05000000074505806 * (0.05000000074505806 * dr * dr + y);
        y = Math.sqrt(d * d * d * d - y);
        d = d * d - y;
        y = Math.atan2(d * d + y, 0.05000000074505806 * dr);
        d = Math.atan2(d, 0.05000000074505806 * dr);
        return (float) Math.min(y, d);
    }

    private Player getTargetByFOV(float maxFov) {
        Player best = null;
        float bestFov = maxFov;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || shouldSkipPlayer(player)) continue;
            float yawDiff = Math.abs(Mth.wrapDegrees(getYawBetween(mc.player.getYRot(), mc.player.getX(), mc.player.getZ(), player.getX(), player.getZ()) - mc.player.getYRot()));
            if (yawDiff < bestFov) {
                best = player;
                bestFov = yawDiff;
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
