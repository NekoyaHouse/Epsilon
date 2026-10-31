package com.github.epsilon.modules.impl.player

import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.impl.StartUseItemEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.math.toRadians
import com.github.epsilon.utils.player.PlayerUtils
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.safety.interaction
import com.github.epsilon.utils.safety.level
import com.github.epsilon.utils.safety.player
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object GhostHand : Module("Ghost Hand", Category.PLAYER) {

    private val posList: MutableSet<BlockPos> = ObjectOpenHashSet()

    init {
        nonNullHandler<StartUseItemEvent> { event ->
            if (!mc.options.keyUse.isDown) return@nonNullHandler

            val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
            val normalHit = player.pick(player.blockInteractionRange(), partialTick, false)
            if (level.getBlockState(BlockPos.containing(normalHit.location)).hasBlockEntity()) {
                return@nonNullHandler
            }

            val direction = Vec3(0.0, 0.0, 0.1)
                .xRot(-(player.xRot.toDouble()).toRadians().toFloat())
                .yRot(-(player.yRot.toDouble()).toRadians().toFloat())

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
                        val result = interaction.useItemOn(player, hand, BlockHitResult(Vec3.atCenterOf(pos), RotationUtils.getDirection(pos), pos, true))
                        if (result is InteractionResult.Success || result is InteractionResult.Fail) {
                            PlayerUtils.swingHand(hand)
                            event.cancel()
                            return@nonNullHandler
                        }
                    }
                }
                i++
            }
        }
    }

}
