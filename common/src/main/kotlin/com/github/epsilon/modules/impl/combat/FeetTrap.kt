package com.github.epsilon.modules.impl.combat

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.kernel.jvm.NonNullContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB

object FeetTrap : BlockTrapModule("Feet Trap", EpsilonTranslations.FeetTrap.NO_BLOCK_FOUND) {

    private val extend by boolSetting("Extend", true)
    private val surroundDirections = arrayOf(
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    )

    context(nn: NonNullContext)
    override fun getTargetPositions(): Set<BlockPos> {
        val origins = setOf(nn.player.blockPosition(), BlockPos.containing(nn.player.x, nn.player.y + 0.8, nn.player.z))
        val queue = ArrayDeque(origins.map { it to 0 })
        val visited = origins.toMutableSet()
        val playerBoxes = if (extend) nn.level.players().map { it.boundingBox } else emptyList()
        val positions = linkedSetOf<BlockPos>()

        while (queue.isNotEmpty()) {
            val (origin, depth) = queue.removeFirst()
            for (direction in surroundDirections) {
                val pos = origin.relative(direction)
                positions.add(pos)
                if (extend && depth < 2 && visited.add(pos) && playerBoxes.any { it.intersects(AABB(pos)) }) {
                    queue.addLast(pos to depth + 1)
                }
            }
        }
        return positions
    }

}
