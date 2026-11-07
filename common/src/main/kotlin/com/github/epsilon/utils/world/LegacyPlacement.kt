package com.github.epsilon.utils.world

import com.github.epsilon.kernel.jvm.*
import com.github.epsilon.utils.rotation.RotationUtils.calculate
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult

object LegacyPlacement {

    fun NonNullContext.placeBlock(hand: InteractionHand, blockHitResult: BlockHitResult): InteractionResult {
        val rotation = calculate(blockHitResult.location)

        // Snap
        connection.send(
            ServerboundMovePlayerPacket.PosRot(
                player.x,
                player.y,
                player.z,
                rotation.yaw,
                rotation.pitch,
                player.onGround(),
                player.horizontalCollision
            )
        )

        // Interact
        val result = gameMode.useItemOn(player, hand, blockHitResult)

        // Snap back
        connection.send(
            ServerboundMovePlayerPacket.PosRot(
                player.x,
                player.y,
                player.z,
                player.yRot,
                player.xRot,
                player.onGround(),
                player.horizontalCollision
            )
        )

        return result
    }

}
