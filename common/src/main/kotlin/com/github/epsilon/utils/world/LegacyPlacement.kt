package com.github.epsilon.utils.world

import com.github.epsilon.kernel.jvm.NonNullContext
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

object LegacyPlacement {

    context(nn: NonNullContext)
    fun placeBlock(hand: InteractionHand, blockHitResult: BlockHitResult): InteractionResult {
        val angle = calculateAngle(nn.player.eyePosition, blockHitResult.location)

        // Snap
        nn.connection.send(
            ServerboundMovePlayerPacket.PosRot(
                nn.player.x,
                nn.player.y,
                nn.player.z,
                angle[0],
                angle[1],
                nn.player.onGround(),
                nn.player.horizontalCollision
            )
        )

        // Interact
        val result = nn.gameMode.useItemOn(nn.player, hand, blockHitResult)

        // Snap back
        nn.connection.send(
            ServerboundMovePlayerPacket.PosRot(
                nn.player.x,
                nn.player.y,
                nn.player.z,
                nn.player.yRot,
                nn.player.xRot,
                nn.player.onGround(),
                nn.player.horizontalCollision
            )
        )

        return result
    }

    private fun calculateAngle(from: Vec3, to: Vec3): FloatArray {
        val dx = to.x - from.x
        val dy = from.y - to.y
        val dz = to.z - from.z
        val dist = sqrt(dx * dx + dz * dz)
        val yaw = Mth.wrapDegrees(Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
        val pitch = Math.toDegrees(atan2(dy, dist)).toFloat()
        return floatArrayOf(yaw, pitch)
    }

}
