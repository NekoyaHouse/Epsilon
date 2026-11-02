package com.github.epsilon.utils.world

import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.utils.rotation.RotationUtils
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult

object LegacyPlacement {

    context(nn: NonNullContext)
    fun placeBlock(hand: InteractionHand, blockHitResult: BlockHitResult): InteractionResult {
        val rotation = RotationUtils.calculate(blockHitResult.location)

        // Snap
        nn.connection.send(
            ServerboundMovePlayerPacket.PosRot(
                nn.player.x,
                nn.player.y,
                nn.player.z,
                rotation.yaw,
                rotation.pitch,
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

}
