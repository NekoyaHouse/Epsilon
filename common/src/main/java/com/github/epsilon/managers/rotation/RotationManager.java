package com.github.epsilon.managers.rotation;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;

import java.util.function.Function;

import static com.github.epsilon.Constants.mc;

public abstract class RotationManager {

    public static RotationManager INSTANCE;

    public enum RotationMode {
        SILENT,
        SNAP
    }

    private final Rot2f offset = new Rot2f(0, 0);
    public Rot2f rotations = new Rot2f(0, 0);
    public Rot2f lastRotations = new Rot2f(0, 0);
    public Rot2f targetRotations;
    public Rot2f animationRotation = null;
    public Rot2f lastAnimationRotation = null;

    protected boolean active;
    protected boolean smoothed;
    protected double rotationSpeed;
    protected Function<Rot2f, Boolean> raytrace;
    private float randomAngle;
    private HitResult rotationHitResult;
    private boolean calculatingHitResult;

    protected int priority;

    public void setRotations(Rot2f rotations, double rotationSpeed) {
        setRotations(rotations, rotationSpeed, null, Priority.Medium);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Priority priority) {
        setRotations(rotations, rotationSpeed, null, priority);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Function<Rot2f, Boolean> raytrace) {
        setRotations(rotations, rotationSpeed, raytrace, Priority.Medium);
    }

    public void setRotations(Rot2f rotations, double rotationSpeed, Function<Rot2f, Boolean> raytrace, Priority priority) {
        if (rotations == null) return;

        if (this.active && priority.priority < this.priority) {
            return;
        }

        this.targetRotations = rotations;
        this.rotationSpeed = rotationSpeed;
        this.raytrace = raytrace;
        this.priority = priority.priority;
        this.active = true;

        smooth();
        onRotationsSet();
    }

    protected void onRotationsSet() {
    }

    protected void resetModeState() {
    }

    protected void smooth() {
        if (!smoothed) {
            float targetYaw = targetRotations.getYaw();
            float targetPitch = targetRotations.getPitch();

            if (raytrace != null && (Math.abs(targetYaw - rotations.getYaw()) > 5 || Math.abs(targetPitch - rotations.getPitch()) > 5)) {
                final Rot2f trueTargetRotations = new Rot2f(targetRotations.getYaw(), targetRotations.getPitch());

                double speed = (Math.random() * Math.random() * Math.random()) * 20;
                randomAngle += (float) ((20 + (float) (Math.random() - 0.5) * (Math.random() * Math.random() * Math.random() * 360)) * (mc.player.tickCount / 10 % 2 == 0 ? -1 : 1));

                offset.set(
                        (float) (offset.getYaw() + -Mth.sin((float) Math.toRadians(randomAngle)) * speed),
                        (float) (offset.getPitch() + Mth.cos((float) Math.toRadians(randomAngle)) * speed)
                );

                targetYaw += offset.getYaw();
                targetPitch += offset.getPitch();

                if (!raytrace.apply(new Rot2f(targetYaw, targetPitch))) {
                    randomAngle = (float) Math.toDegrees(Math.atan2(trueTargetRotations.getYaw() - targetYaw, targetPitch - trueTargetRotations.getPitch())) - 180;

                    targetYaw -= offset.getYaw();
                    targetPitch -= offset.getPitch();

                    offset.set(
                            (float) (offset.getYaw() + -Mth.sin((float) Math.toRadians(randomAngle)) * speed),
                            (float) (offset.getPitch() + Mth.cos((float) Math.toRadians(randomAngle)) * speed)
                    );

                    targetYaw = targetYaw + offset.getYaw();
                    targetPitch = targetPitch + offset.getPitch();
                }

                if (!raytrace.apply(new Rot2f(targetYaw, targetPitch))) {
                    offset.set(0, 0);

                    targetYaw = (float) (targetRotations.getYaw() + Math.random() * 2);
                    targetPitch = (float) (targetRotations.getPitch() + Math.random() * 2);
                }
            }

            rotations = RotationUtils.smooth(new Rot2f(targetYaw, targetPitch), rotationSpeed + Math.random());
        }

        smoothed = true;

        updateHitResult();
        if (shouldModifyCrosshair()) {
            mc.pick(1.0f);
        }
    }

    private void updateHitResult() {
        if (!hasActiveRotation() || mc.player == null || mc.level == null) {
            rotationHitResult = null;
            return;
        }

        calculatingHitResult = true;
        try {
            rotationHitResult = mc.player.raycastHitResult(1.0f, mc.player);
        } finally {
            calculatingHitResult = false;
        }
    }

    protected boolean shouldModifyCrosshair() {
        return false;
    }

    protected final boolean hasActiveRotation() {
        return active && rotations != null;
    }

    protected static float clampPitch(float pitch) {
        return Mth.clamp(pitch, -90.0F, 90.0F);
    }

    protected void correctDisabledRotations(LocalPlayer player) {
        Rot2f playerRotation = new Rot2f(player.getYRot(), player.getXRot());
        Rot2f fixedRotations = RotationUtils.applySensitivityPatch(playerRotation, lastRotations);
        float fixedYaw = fixedRotations.getYaw() + Mth.wrapDegrees(player.getYRot() - fixedRotations.getYaw());
        player.setYRot(fixedYaw);
    }

    public float getYaw() {
        return getRotation().getYaw();
    }

    public float getPitch() {
        return getRotation().getPitch();
    }

    public Rot2f getRotation() {
        return active ? rotations : new Rot2f(mc.player.getYRot(), mc.player.getXRot());
    }

    public Rot2f getLastRotation() {
        return lastRotations != null ? lastRotations : new Rot2f(mc.player.yRotO, mc.player.xRotO);
    }

    /**
     * 获取按当前托管旋转计算的逻辑命中结果。未启用托管旋转时返回原版准星结果。
     *
     * @return 当前逻辑命中结果
     */
    public HitResult getHitResult() {
        return hasActiveRotation() && rotationHitResult != null ? rotationHitResult : mc.hitResult;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isSmoothed() {
        return smoothed;
    }

    public void setSmoothed(boolean smoothed) {
        this.smoothed = smoothed;
    }

    public void copyStateFrom(RotationManager manager) {
        this.rotations = manager.rotations;
        this.lastRotations = manager.lastRotations;
        this.targetRotations = manager.targetRotations;
        this.animationRotation = manager.animationRotation;
        this.lastAnimationRotation = manager.lastAnimationRotation;
        this.active = manager.active;
        this.smoothed = manager.smoothed;
        this.rotationSpeed = manager.rotationSpeed;
        this.raytrace = manager.raytrace;
        this.priority = manager.priority;
        this.rotationHitResult = manager.rotationHitResult;
    }

    @EventHandler
    protected void onRespawn(RespawnEvent event) {
        offset.set(0, 0);
        rotations = new Rot2f(0, 0);
        lastRotations = new Rot2f(0, 0);
        targetRotations = null;
        animationRotation = null;
        lastAnimationRotation = null;
        active = false;
        priority = 0;
        smoothed = false;
        raytrace = null;
        randomAngle = 0;
        rotationHitResult = null;
        calculatingHitResult = false;
        resetModeState();
    }

    @EventHandler
    private void onRaytrace(RaytraceEvent event) {
        if (!hasActiveRotation() || (!calculatingHitResult && !shouldModifyCrosshair())) return;

        event.setYaw(rotations.getYaw());
        event.setPitch(clampPitch(rotations.getPitch()));
    }

    @EventHandler(priority = -1000)
    protected void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!active || rotations == null || lastRotations == null || targetRotations == null) {
            rotations = lastRotations = targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }

        if (hasActiveRotation()) {
            smooth();
            afterPlayerTick();
        }
    }

    protected void afterPlayerTick() {
    }

    @EventHandler
    protected void onAnimation(RotationAnimationEvent event) {
        if (active && animationRotation != null && lastAnimationRotation != null) {
            event.setYaw(animationRotation.getYaw());
            event.setLastYaw(lastAnimationRotation.getYaw());
            event.setPitch(animationRotation.getPitch());
            event.setLastPitch(lastAnimationRotation.getPitch());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onSendPosition(SendPositionEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) {
            active = false;
            priority = 0;
            targetRotations = null;
            raytrace = null;
            smoothed = false;
            rotationHitResult = null;
            calculatingHitResult = false;
            resetModeState();
            return;
        }

        if (active && rotations != null) {
            handleSendPosition(event);

            if (Math.abs((rotations.getYaw() - player.getYRot()) % 360) < 1 && Math.abs((rotations.getPitch() - player.getXRot())) < 1) {
                active = false;
                priority = 0;
                this.correctDisabledRotations(player);
            }

            lastRotations = rotations;
        } else {
            lastRotations = new Rot2f(player.getYRot(), player.getXRot());
        }

        lastAnimationRotation = animationRotation;
        animationRotation = new Rot2f(event.getYaw(), event.getPitch());
        targetRotations = new Rot2f(player.getYRot(), player.getXRot());
        raytrace = null;
        smoothed = false;
    }

    protected abstract void handleSendPosition(SendPositionEvent event);

    public static void switchRotationManager(RotationManager.RotationMode mode) {
        RotationManager previous = INSTANCE;
        RotationManager next = switch (mode) {
            case SNAP -> new SnapRotationManager();
            case SILENT -> new SilentRotationManager();
        };
        if (previous != null) {
            next.copyStateFrom(previous);
            EventBus.INSTANCE.unsubscribe(previous);
        }
        INSTANCE = next;
        EventBus.INSTANCE.subscribe(next);
    }

}
