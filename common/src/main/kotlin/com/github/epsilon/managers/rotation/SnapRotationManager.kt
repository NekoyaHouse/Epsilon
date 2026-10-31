package com.github.epsilon.managers.rotation

import com.github.epsilon.Constants.mc
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.events.impl.SendPositionEvent
import com.github.epsilon.utils.rotation.Rot2f
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

class SnapRotationManager : RotationManager() {

    private var snappedRot: Rot2f? = null

    override fun onRotationsSet() {
        snapToCurrentRotation()
    }

    private fun snapToCurrentRotation() {
        val snapped = snappedRot
        if (snapped != null) {
            restoreSnappedRotation(snapped, false)
        }

        snappedRot = Rot2f(mc.player!!.yRot, mc.player!!.xRot)
        sendRotationPacket(rotations)
    }

    private fun restoreSnappedRotation(expectedSnappedRot: Rot2f?, finishRotation: Boolean) {
        if (expectedSnappedRot == null || snappedRot !== expectedSnappedRot) return

        sendRotationPacket(snappedRot)
        snappedRot = null

        if (finishRotation) {
            active = false
            priority = 0
            targetRotations = Rot2f(mc.player!!.yRot, mc.player!!.xRot)
        }
    }

    private fun sendRotationPacket(rotation: Rot2f?) {
        if (rotation == null) return

        val yaw = rotation.yaw
        val pitch = rotation.pitch
        if (yaw.isNaN() || pitch.isNaN()) return

        val player = mc.player!!
        mc.connection!!.send(
            ServerboundMovePlayerPacket.PosRot(
                player.position(), yaw, pitch,
                player.onGround(), player.horizontalCollision
            )
        )
    }

    override fun onPlayerTick(event: PlayerTickEvent.Pre) {
        val tickSnappedRot = snappedRot
        super.onPlayerTick(event)
        restoreSnappedRotation(tickSnappedRot, true)
    }

    override fun handleSendPosition(event: SendPositionEvent) {
    }

    override fun resetModeState() {
        snappedRot = null
    }

}
