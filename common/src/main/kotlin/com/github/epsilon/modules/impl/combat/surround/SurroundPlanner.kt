package com.github.epsilon.modules.impl.combat.surround

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth

internal object SurroundPlanner {
    val horizontal = listOf(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)

    fun footprint(x: Double, y: Double, z: Double): Set<BlockPos> = buildSet {
        for (bx in Mth.floor(x - 0.3)..Mth.floor(x + 0.3)) {
            for (by in Mth.floor(y)..Mth.floor(y + 0.4)) {
                for (bz in Mth.floor(z - 0.3)..Mth.floor(z + 0.3)) {
                    add(BlockPos(bx, by, bz))
                }
            }
        }
    }

    fun supportPath(
        target: BlockPos,
        limit: Int,
        canPlace: (BlockPos) -> Boolean,
        hasSide: (BlockPos) -> Boolean,
    ): List<BlockPos> {
        if (limit <= 0 || hasSide(target)) return emptyList()
        val queue = ArrayDeque<List<BlockPos>>()
        queue.add(listOf(target))
        val visited = mutableSetOf(target)
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (path.size > limit) continue
            for (side in Direction.entries) {
                val next = path.last().relative(side)
                if (!visited.add(next) || !canPlace(next)) continue
                val extended = path + next
                if (hasSide(next)) return extended.drop(1).reversed()
                queue.add(extended)
            }
        }
        return emptyList()
    }
}
