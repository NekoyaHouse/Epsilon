package com.github.epsilon.modules.impl.movement.elytrafly;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.events.impl.FallFlyingEvent;
import com.github.epsilon.events.impl.FireworkRotationEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.world.item.Items;

public class Pitch40ElytraFlightMode extends ElytraFlightMode {

    private static final float DESCEND_PITCH = 37.72F;
    private static final float ASCEND_PITCH = -54.77F;
    private static final float TAKEOFF_PITCH = ASCEND_PITCH;
    private static final double TARGET_REACH_MARGIN = 4.0;

    private boolean pitchingDown = true;
    private boolean pendingActivationCheck;
    private boolean takingOff;
    private boolean shouldJump;
    private int packetDelayTicks;
    private double completedTakeoffTarget;
    private float pitch;
    private final TimerUtils fireworkTimer = new TimerUtils();

    public Pitch40ElytraFlightMode(ElytraFly elytraFly) {
        super(elytraFly);
    }

    @Override
    public void onEnable() {
        resetState();

        if (mc.player == null || mc.level == null) {
            pendingActivationCheck = true;
            return;
        }

        initializeInWorld();
    }

    private void resetState() {
        pitchingDown = true;
        pendingActivationCheck = false;
        takingOff = false;
        shouldJump = false;
        packetDelayTicks = 0;
        completedTakeoffTarget = Double.NaN;
        pitch = DESCEND_PITCH;
        fireworkTimer.setMs(917813L);
    }

    private void initializeInWorld() {
        double targetHeight = getTakeoffTargetHeight();
        if (shouldStartTakeoff(targetHeight)) {
            startTakeoff();
            return;
        }
        if (elytraFly.pitch40AutoTakeoff.getValue()) {
            completedTakeoffTarget = targetHeight;
        }

        if (mc.player.getY() - 40.0 < elytraFly.pitch40lowerBounds.getValue()) {
            fail(EpsilonTranslations.ElytraFly.PITCH40_TOO_CLOSE_TO_LOWER_BOUNDS);
        }
    }

    @Override
    public void onDisable() {
        pendingActivationCheck = false;
        takingOff = false;
        shouldJump = false;
        packetDelayTicks = 0;
        completedTakeoffTarget = Double.NaN;
    }

    @Override
    public void onPlayerTick() {
        if (pendingActivationCheck) {
            pendingActivationCheck = false;
            initializeInWorld();
            if (!elytraFly.isEnabled()) return;
        }

        if (takingOff) {
            updateTakeoff();
            if (elytraFly.isEnabled()) {
                redirectRotation();
            }
            return;
        }

        if (mc.player.onGround()) {
            completedTakeoffTarget = Double.NaN;
        }

        if (shouldStartTakeoff(getTakeoffTargetHeight())) {
            startTakeoff();
            updateTakeoff();
            if (elytraFly.isEnabled()) {
                redirectRotation();
            }
            return;
        }

        maintainFallFlying();
        updatePitch();
        redirectRotation();
    }

    @Override
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (shouldJump) {
            event.setJump(true);
            shouldJump = false;
        }
    }

    @Override
    public boolean shouldCancelRightClick() {
        return false;
    }

    @Override
    public void onFallFlying(FallFlyingEvent event) {
        event.setYaw(elytraFly.getPitch40Yaw(event.getYaw()));
        event.setPitch(pitch);
    }

    @Override
    public void onFireworkUpdate(FireworkRotationEvent event) {
        event.setYaw(elytraFly.getPitch40Yaw(mc.player.getYRot()));
        event.setPitch(pitch);
    }

    private void updateTakeoff() {
        pitch = TAKEOFF_PITCH;

        if (mc.player.getY() >= getTakeoffTargetHeight()) {
            finishTakeoff();
            return;
        }

        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (!canGlide(elytra.found())) {
            fail(EpsilonTranslations.ElytraFly.PITCH40_NO_USABLE_ELYTRA);
            return;
        }

        redirectRotation();

        if (mc.player.onGround()) {
            shouldJump = true;
            return;
        }

        if (elytraFly.armored.getValue()) {
            refreshArmoredFallFlying(elytra.slot(), false);
        } else if (canStartFallFlying() && startFallFlying()) {
            shouldJump = true;
        }

        if (elytraFly.pitch40AutoFirework.getValue() && mc.player.isFallFlying() && fireworkTimer.hasDelayed(elytraFly.pitch40FireworkCooldown.getValue()) && useFirework()) {
            fireworkTimer.reset();
        }
    }

    private void maintainFallFlying() {
        if (mc.player.onGround() || mc.player.isInWater()) return;

        FindItemResult elytra = InvUtils.find(Items.ELYTRA);
        if (!canGlide(elytra.found())) return;

        if (elytraFly.armored.getValue()) {
            refreshArmoredFallFlying(elytra.slot(), true);
        } else if (startFallFlying()) {
            shouldJump = true;
        }
    }

    private void redirectRotation() {
        Managers.ROTATION.setRotations(new Rot2f(elytraFly.getPitch40Yaw(mc.player.getYRot()), pitch), 180, Priority.Highest);
    }

    private void finishTakeoff() {
        takingOff = false;
        pitchingDown = true;
        packetDelayTicks = 0;
        completedTakeoffTarget = getTakeoffTargetHeight();
        pitch = DESCEND_PITCH;
        maintainFallFlying();
        Managers.NOTIFICATION.success(elytraFly.getTranslatedName(), EpsilonTranslations.ElytraFly.PITCH40_TAKEOFF_COMPLETE.getTranslatedName());
    }

    private void refreshArmoredFallFlying(int elytraSlot, boolean respectDelay) {
        if (respectDelay && ++packetDelayTicks <= elytraFly.pitch40PacketDelay.getValue()) {
            return;
        }
        packetDelayTicks = 0;

        int elytra = elytraSlot < 9 ? elytraSlot + 36 : elytraSlot;
        swapArmor(elytra);
        if (!mc.player.isFallFlying() && startFallFlying()) {
            shouldJump = true;
        } else if (mc.player.isFallFlying()) {
            restartFallFlying();
        }
        swapArmor(elytra);
    }

    private void updatePitch() {
        /*
         * 下降时看向 37.72 度；上升时先快速抬头到 -54.77 度，再缓慢压回 37.72 度。
         */
        if (pitchingDown && mc.player.getY() <= elytraFly.pitch40lowerBounds.getValue()) {
            pitchingDown = false;
        }

        if (!pitchingDown) {
            pitch -= randPitch(elytraFly.pitch40rotationSpeedUp.getValue().floatValue(), 1.0F);

            if (pitch < ASCEND_PITCH) {
                pitch = ASCEND_PITCH;
                pitchingDown = true;
            }
        } else if (pitch < DESCEND_PITCH) {
            pitch += randPitch(elytraFly.pitch40rotationSpeedDown.getValue().floatValue(), 0.50F);
        }
    }

    private double getTakeoffTargetHeight() {
        return elytraFly.pitch40TakeoffTargetHeight.getValue();
    }

    private void startTakeoff() {
        takingOff = true;
        pitch = TAKEOFF_PITCH;
    }

    private boolean shouldStartTakeoff(double targetHeight) {
        return elytraFly.pitch40AutoTakeoff.getValue()
                && mc.player.getY() < targetHeight - TARGET_REACH_MARGIN
                && (Double.isNaN(completedTakeoffTarget) || targetHeight > completedTakeoffTarget + TARGET_REACH_MARGIN);
    }

    private void fail(TranslateComponent message) {
        Managers.NOTIFICATION.error(elytraFly.getTranslatedName(), message.getTranslatedName());
        elytraFly.toggle();
    }

    private float randPitch(float pitch, float bound) {
        return (float) (pitch + bound * (Math.random() - 0.5));
    }

}
