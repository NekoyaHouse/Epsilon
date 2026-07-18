package com.github.epsilon.managers.impl.rotations;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.util.Mth;

import java.util.function.Function;

import static com.github.epsilon.Constants.mc;

public abstract class RotationManager {

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
    private boolean s08;

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

        if (s08) {
            this.rotations = this.lastRotations = this.targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
            resetModeState();
            s08 = false;
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

        mc.pick(1.0f);
    }

    protected void correctDisabledRotations() {
        Rot2f rotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        Rot2f fixedRotations = RotationUtils.resetRotation(RotationUtils.applySensitivityPatch(rotations, lastRotations));
        mc.player.setYRot(fixedRotations.getYaw());
        mc.player.setXRot(fixedRotations.getPitch());
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
        resetModeState();
        s08 = false;
    }

    @EventHandler
    protected void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket || event.getPacket() instanceof ClientboundPlayerRotationPacket) {
            s08 = true;
        }
    }

    @EventHandler(priority = -1000)
    protected void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!active || rotations == null || lastRotations == null || targetRotations == null) {
            rotations = lastRotations = targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }

        if (active) {
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
        if (active && rotations != null) {
            handleSendPosition(event);

            if (Math.abs((rotations.getYaw() - mc.player.getYRot()) % 360) < 1 && Math.abs((rotations.getPitch() - mc.player.getXRot())) < 1) {
                active = false;
                priority = 0;
                this.correctDisabledRotations();
            }

            lastRotations = rotations;
        } else {
            lastRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        }

        lastAnimationRotation = animationRotation;
        animationRotation = new Rot2f(event.getYaw(), event.getPitch());
        targetRotations = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
        raytrace = null;
        smoothed = false;
    }

    protected abstract void handleSendPosition(SendPositionEvent event);

}
