package com.github.epsilon.managers.rotation

import com.github.epsilon.Constants.mc
import com.github.epsilon.events.bus.EventHandler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.*
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.modules.impl.movement.MovementFix
import com.github.epsilon.modules.impl.render.FreeCamera
import com.github.epsilon.utils.rotation.Rot2f
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket

class SilentRotationManager : RotationManager() {

    private var useItemRotation: Rot2f? = null

    override fun handleSendPosition(event: SendPositionEvent) {
        // Item use is sent before LocalPlayer ticks; keep both packets on the same server rotation.
        val useItemRotation = useItemRotation
        if (useItemRotation != null) {
            rotations = useItemRotation
            this.useItemRotation = null
        }

        val yaw = rotations.yaw
        val pitch = clampPitch(rotations.pitch)
        if (!yaw.isNaN() && !pitch.isNaN()) {
            event.yaw = yaw
            event.pitch = pitch
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    private fun onMoveInput(event: KeyboardInputEvent) {
        if (MovementFix.isEnabled && hasActiveRotation() && !mc.player!!.isFallFlying) {
            MovementFix.fixMovement(event, rotations.yaw)
        }
    }

    override fun shouldModifyCrosshair(): Boolean {
        return ClientSetting.modifyCrosshair && !FreeCamera.isEnabled
    }

    @EventHandler
    private fun onItemRaytrace(event: UseItemRaytraceEvent) {
        if (hasActiveRotation()) {
            event.yaw = rotations.yaw
            event.pitch = clampPitch(rotations.pitch)
        }
    }

    @EventHandler
    private fun onStrafe(event: StrafeEvent) {
        if (MovementFix.isEnabled && hasActiveRotation() && !mc.player!!.isFallFlying) {
            event.yaw = rotations.yaw
        }
    }

    @EventHandler
    private fun onJump(event: JumpEvent) {
        if (MovementFix.isEnabled && hasActiveRotation() && !mc.player!!.isFallFlying) {
            event.yaw = rotations.yaw
        }
    }

    @EventHandler
    private fun onFallFlying(event: FallFlyingEvent) {
        if (MovementFix.isEnabled && hasActiveRotation()) {
            event.yaw = rotations.yaw
            event.pitch = clampPitch(rotations.pitch)
        }
    }

    @EventHandler
    private fun onUseItem(event: UseItemEvent) {
        if (hasActiveRotation()) {
            event.yaw = rotations.yaw
            event.pitch = clampPitch(rotations.pitch)
        }
    }

    @EventHandler
    private fun onFireworkUpdate(event: FireworkRotationEvent) {
        if (hasActiveRotation()) {
            event.yaw = rotations.yaw
            event.pitch = clampPitch(rotations.pitch)
        }
    }

    @EventHandler
    private fun onAttack(event: AttackYawEvent) {
        if (hasActiveRotation()) {
            event.yaw = rotations.yaw
        }
    }

    @EventHandler
    private fun onPacketSend(event: PacketEvent.Send) {
        val packet = event.packet
        if (packet is ServerboundClientTickEndPacket) {
            useItemRotation = null
        } else if (hasActiveRotation() && packet is ServerboundUseItemPacket) {
            val yaw = rotations.yaw
            val pitch = clampPitch(rotations.pitch)
            if (!yaw.isNaN() && !pitch.isNaN()) {
                useItemRotation = Rot2f(yaw, pitch)
                if (packet.yRot() != yaw || packet.xRot() != pitch) {
                    event.packet = ServerboundUseItemPacket(
                        packet.hand(),
                        packet.sequence(),
                        yaw,
                        pitch
                    )
                }
            }
        }
    }

    override fun resetModeState() {
        useItemRotation = null
    }

}
