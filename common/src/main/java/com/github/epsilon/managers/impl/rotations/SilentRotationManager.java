package com.github.epsilon.managers.impl.rotations;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.movement.MovementFix;
import com.github.epsilon.modules.impl.render.FreeCamera;

import static com.github.epsilon.Constants.mc;

public class SilentRotationManager extends RotationManager {

    @Override
    protected void handleSendPosition(SendPositionEvent event) {
        float yaw = rotations.getYaw();
        float pitch = rotations.getPitch();

        if (!Float.isNaN(yaw) && !Float.isNaN(pitch)) {
            event.setYaw(yaw);
            event.setPitch(pitch);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMoveInput(KeyboardInputEvent event) {
        MovementFix moveFix = MovementFix.INSTANCE;
        if (moveFix.isEnabled() && active && rotations != null && !mc.player.isFallFlying()) {
            moveFix.fixMovement(event, rotations.getYaw());
        }
    }

    @EventHandler
    private void onRaytrace(RaytraceEvent event) {
        if (ClientSetting.INSTANCE.modifyCrosshair.getValue() && active && rotations != null && !FreeCamera.INSTANCE.isEnabled()) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onItemRaytrace(UseItemRaytraceEvent event) {
        if (active && rotations != null) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onStrafe(StrafeEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && active && rotations != null && !mc.player.isFallFlying()) {
            event.setYaw(rotations.getYaw());
        }
    }

    @EventHandler
    private void onJump(JumpEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && active && rotations != null && !mc.player.isFallFlying()) {
            event.setYaw(rotations.getYaw());
        }
    }

    @EventHandler
    private void onFallFlying(FallFlyingEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && active && rotations != null) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onUseItem(UseItemEvent event) {
        if (active && rotations != null) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onFireworkUpdate(FireworkRotationEvent event) {
        if (active && rotations != null) {
            event.setYaw(rotations.getYaw());
            event.setPitch(rotations.getPitch());
        }
    }

    @EventHandler
    private void onAttack(AttackYawEvent event) {
        if (rotations != null) {
            event.setYaw(rotations.getYaw());
        }
    }

}
