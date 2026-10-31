package com.github.epsilon.modules.impl.player

import com.github.epsilon.utils.*
import com.github.epsilon.events.impl.StartUseItemEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.interaction
import com.github.epsilon.utils.player
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.level
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object GhostHand : Module("Ghost Hand", Category.PLAYER) {

    private val posList: MutableSet<BlockPos> = ObjectOpenHashSet()

    init {
        nonNullListener<StartUseItemEvent> { event ->
            if (!mc.options.keyUse.isDown) return@nonNullListener

            val partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)
            val normalHit = player.pick(player.blockInteractionRange(), partialTick, false)
            if (level.getBlockState(BlockPos.containing(normalHit.location)).hasBlockEntity()) {
                return@nonNullListener
            }

            val direction = Vec3(0.0, 0.0, 0.1)
                .xRot(-Math.toRadians(player.xRot.toDouble()).toFloat())
                .yRot(-Math.toRadians(player.yRot.toDouble()).toFloat())

            posList.clear()

            var i = 1
            while (i < player.blockInteractionRange() * 10) {
                val pos = BlockPos.containing(player.getEyePosition(partialTick).add(direction.scale(i.toDouble())))

                if (posList.contains(pos)) {
                    i++
                    continue
                }
                posList.add(pos)

                if (level.getBlockState(pos).hasBlockEntity()) {
                    for (hand in InteractionHand.values()) {
                        val result = interaction.useItemOn(player, hand, BlockHitResult(Vec3.atCenterOf(pos), getDirection(pos), pos, true))
                        if (result is InteractionResult.Success || result is InteractionResult.Fail) {
                            swingHand(hand)
                            event.cancel()
                            return@nonNullListener
                        }
                    }
                }
                i++
            }
        }
    }

}
