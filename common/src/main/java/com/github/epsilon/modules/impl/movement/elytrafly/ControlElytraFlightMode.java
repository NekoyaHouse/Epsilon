package com.github.epsilon.modules.impl.movement.elytrafly;

import com.github.epsilon.events.impl.FallFlyingEvent;
import com.github.epsilon.events.impl.FireworkRotationEvent;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.TravelEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.impl.movement.follower.Follower;
import com.github.epsilon.modules.impl.movement.follower.FollowerInput;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public class ControlElytraFlightMode extends ElytraFlightMode {

    private static final double CEILING_PROBE_DISTANCE = 0.75;
    private static final double CEILING_PROBE_EPSILON = 1.0E-4;
    private static final float CEILING_ESCAPE_PITCH = 5.0f;

    private boolean hasFirstFirework;
    private boolean shouldJump;
    private final TimerUtils timer = new TimerUtils();

    public ControlElytraFlightMode(ElytraFly elytraFly) {
        super(elytraFly);
    }

    @Override
    public void onEnable() {
        hasFirstFirework = false;
        shouldJump = false;
        timer.setMs(917813L);
    }

    @Override
    public void onDisable() {
        shouldJump = false;
    }

    @Override
    public void onPlayerTick() {
        redirectRotation(); // 让你转你就受着
        updateControl();
    }

    @Override
    public void onTravel(TravelEvent event) {
        boolean avoidCeilingLift = shouldAvoidCeilingLift();

        if (avoidCeilingLift && mc.player.getDeltaMovement().y > 0.0) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }

        if (!avoidCeilingLift && !hasMoveInput() && (!elytraFly.useFireworks.getValue() || hasFirstFirework)) {
            mc.player.setDeltaMovement(0, 0.02, 0);
        }
    }

    @Override
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (elytraFly.noSprint.getValue()) event.setSprint(false);
        if (shouldJump) {
            event.setJump(true);
            shouldJump = false;
        }
    }

    @Override
    public void onFallFlying(FallFlyingEvent event) {
        event.setYaw(calcYaw());
        event.setPitch(calcPitch());
    }

    @Override
    public void onFireworkUpdate(FireworkRotationEvent event) {
        event.setYaw(calcYaw());
        event.setPitch(calcPitch());
    }

    private void updateControl() {
        if (elytraFly.noSprint.getValue() && mc.player.isSprinting()) return;

        FindItemResult elytra = InvUtils.find(Items.ELYTRA);

        if (!canGlide(elytra.found()) || mc.player.onGround()) {
            shouldJump = true;
            hasFirstFirework = false;
            return;
        }

        if (elytraFly.armored.getValue()) {
            if (canStartFallFlying()) {
                jiaFei(elytra.slot());
            }
        } else {
            if (canStartFallFlying() && startFallFlying()) {
                shouldJump = true;
            }
            useTimedFirework();
        }
    }

    private void useTimedFirework() {
        if (!elytraFly.useFireworks.getValue() || !timer.hasDelayed(elytraFly.boostDelay.getValue())) return;
        if (useFirework()) {
            hasFirstFirework = true;
            timer.reset();
        }
    }

    private void redirectRotation() {
        Managers.ROTATION.setRotations(new Rot2f(calcYaw(), calcPitch()), 360, Priority.Highest);
    }

    private float calcYaw() {
        FollowerInput followerInput = Follower.INSTANCE.getControlInput();
        if (followerInput != null) {
            return followerInput.yaw();
        }

        float yaw = mc.player.getYRot();

        boolean forward = mc.options.keyUp.isDown();
        boolean back = mc.options.keyDown.isDown();
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();

        if (forward && !back) {
            if (left && !right) {
                yaw -= 45f;
            } else if (right && !left) {
                yaw += 45f;
            }
        } else if (back && !forward) {
            yaw += 180f;
            if (left && !right) {
                yaw += 45f;
            } else if (right && !left) {
                yaw -= 45f;
            }
        } else if (left && !right) {
            yaw -= 90f;
        } else if (right && !left) {
            yaw += 90f;
        }
        return Mth.wrapDegrees(yaw);
    }

    private float calcPitch() {
        FollowerInput followerInput = Follower.INSTANCE.getControlInput();
        if (followerInput != null) {
            return applyCeilingPitchGuard(followerInput.pitch());
        }

        float pitch = mc.player.getXRot();

        boolean jump = mc.options.keyJump.isDown();
        boolean sneak = mc.options.keyShift.isDown();
        boolean moving = mc.player.isMoving();

        if (sneak && jump) {
            pitch = -3f;
        } else if (jump) {
            pitch = moving ? -45f : -90f;
        } else if (sneak) {
            pitch = moving ? 45f : 90f;
        } else if (moving) {
            pitch = -1.9f;
        }
        return applyCeilingPitchGuard(pitch);
    }

    private float applyCeilingPitchGuard(float pitch) {
        if (shouldAvoidCeilingLift()) {
            return Math.max(pitch, CEILING_ESCAPE_PITCH);
        }
        return Mth.clamp(pitch, -90f, 90f);
    }

    private boolean shouldAvoidCeilingLift() {
        if (!mc.player.isFallFlying()) return false;

        AABB box = mc.player.getBoundingBox();
        double probeDistance = CEILING_PROBE_DISTANCE + Math.max(0.0, mc.player.getDeltaMovement().y);
        AABB ceilingProbe = new AABB(
                box.minX + CEILING_PROBE_EPSILON,
                box.maxY - CEILING_PROBE_EPSILON,
                box.minZ + CEILING_PROBE_EPSILON,
                box.maxX - CEILING_PROBE_EPSILON,
                box.maxY + probeDistance,
                box.maxZ - CEILING_PROBE_EPSILON
        );
        return !mc.level.noBlockCollision(mc.player, ceilingProbe);
    }

    private boolean hasMoveInput() {
        FollowerInput followerInput = Follower.INSTANCE.getControlInput();
        if (followerInput != null) {
            return followerInput.hasMoveInput();
        }

        return mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown()
                || mc.options.keyShift.isDown();
    }

    private void jiaFei(int elytraSlot) {
        int elytra = elytraSlot < 9 ? elytraSlot + 36 : elytraSlot;

        swapArmor(elytra);
        if (startFallFlying()) {
            shouldJump = true;
        }
        useTimedFirework();
        swapArmor(elytra);
    }

}
