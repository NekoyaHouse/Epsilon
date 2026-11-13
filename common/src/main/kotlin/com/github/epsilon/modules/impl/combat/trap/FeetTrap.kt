package com.github.epsilon.modules.impl.combat.trap

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.kernel.jvm.NonNullContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB

object FeetTrap : TrapBase("Feet Trap", EpsilonTranslations.FeetTrap.NO_BLOCK_FOUND) {

    private val extend by boolSetting("Extend", true)

    private val surroundDirections = arrayOf(
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    )

    override fun NonNullContext.getTargetPositions(): Set<BlockPos> {
        val origins = setOf(player.blockPosition(), BlockPos.containing(player.x, player.y + 0.8, player.z))
        val queue = ArrayDeque(origins.map { it to 0 })
        val visited = origins.toMutableSet()
        val playerBoxes = if (extend) level.players().map { it.boundingBox } else emptyList()
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
