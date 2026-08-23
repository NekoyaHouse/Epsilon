package com.github.epsilon.managers.rotation;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.movement.MovementFix;
import com.github.epsilon.modules.impl.render.FreeCamera;
import com.github.epsilon.utils.rotation.Rot2f;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

import static com.github.epsilon.Constants.mc;

public class SilentRotationManager extends RotationManager {

    private Rot2f useItemRotation;

    @Override
    protected void handleSendPosition(SendPositionEvent event) {
        // Item use is sent before LocalPlayer ticks; keep both packets on the same server rotation.
        if (useItemRotation != null) {
            rotations = useItemRotation;
            useItemRotation = null;
        }

        float yaw = rotations.getYaw();
        float pitch = clampPitch(rotations.getPitch());
        if (!Float.isNaN(yaw) && !Float.isNaN(pitch)) {
            event.setYaw(yaw);
            event.setPitch(pitch);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMoveInput(KeyboardInputEvent event) {
        MovementFix moveFix = MovementFix.INSTANCE;
        if (moveFix.isEnabled() && hasActiveRotation() && !mc.player.isFallFlying()) {
            moveFix.fixMovement(event, rotations.getYaw());
        }
    }

    @Override
    protected boolean shouldModifyCrosshair() {
        return ClientSetting.INSTANCE.modifyCrosshair.getValue() && !FreeCamera.INSTANCE.isEnabled();
    }

    @EventHandler
    private void onItemRaytrace(UseItemRaytraceEvent event) {
        if (hasActiveRotation()) {
            event.setYaw(rotations.getYaw());
            event.setPitch(clampPitch(rotations.getPitch()));
        }
    }

    @EventHandler
    private void onStrafe(StrafeEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && hasActiveRotation() && !mc.player.isFallFlying()) {
            event.setYaw(rotations.getYaw());
        }
    }

    @EventHandler
    private void onJump(JumpEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && hasActiveRotation() && !mc.player.isFallFlying()) {
            event.setYaw(rotations.getYaw());
        }
    }

    @EventHandler
    private void onFallFlying(FallFlyingEvent event) {
        if (MovementFix.INSTANCE.isEnabled() && hasActiveRotation()) {
            event.setYaw(rotations.getYaw());
            event.setPitch(clampPitch(rotations.getPitch()));
        }
    }

    @EventHandler
    private void onUseItem(UseItemEvent event) {
        if (hasActiveRotation()) {
            event.setYaw(rotations.getYaw());
            event.setPitch(clampPitch(rotations.getPitch()));
        }
    }

    @EventHandler
    private void onFireworkUpdate(FireworkRotationEvent event) {
        if (hasActiveRotation()) {
            event.setYaw(rotations.getYaw());
            event.setPitch(clampPitch(rotations.getPitch()));
        }
    }

    @EventHandler
    private void onAttack(AttackYawEvent event) {
        if (hasActiveRotation()) {
            event.setYaw(rotations.getYaw());
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundClientTickEndPacket) {
            useItemRotation = null;
        } else if (hasActiveRotation() && event.getPacket() instanceof ServerboundUseItemPacket packet) {
            float yaw = rotations.getYaw();
            float pitch = clampPitch(rotations.getPitch());
            if (!Float.isNaN(yaw) && !Float.isNaN(pitch)) {
                useItemRotation = new Rot2f(yaw, pitch);
                if (packet.getYRot() != yaw || packet.getXRot() != pitch) {
                    event.setPacket(new ServerboundUseItemPacket(
                            packet.getHand(),
                            packet.getSequence(),
                            yaw,
                            pitch
                    ));
                }
            }
        }
    }

    @Override
    protected void resetModeState() {
        useItemRotation = null;
    }

}
