package com.github.epsilon.modules.impl.combat.surround

import net.minecraft.core.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurroundPlannerTest {
    @Test fun centeredFootprintAndNegativeCoordinates() {
        assertEquals(setOf(BlockPos(0, 64, 0)), SurroundPlanner.footprint(0.5, 64.0, 0.5))
        assertEquals(setOf(BlockPos(-1, 64, -1)), SurroundPlanner.footprint(-0.5, 64.0, -0.5))
    }

    @Test fun coversEdgesCornersAndFractionalHeight() {
        assertEquals(2, SurroundPlanner.footprint(1.0, 64.0, 0.5).size)
        assertEquals(4, SurroundPlanner.footprint(1.0, 64.0, 1.0).size)
        assertEquals(setOf(BlockPos(0, 64, 0), BlockPos(0, 65, 0)), SurroundPlanner.footprint(0.5, 64.8, 0.5))
    }

    @Test fun supportsAreAnchorFirstAndBounded() {
        val target = BlockPos(0, 64, 0)
        val near = target.below()
        val anchor = near.below()
        val allowed = setOf(near, anchor)
        assertTrue(SurroundPlanner.supportPath(target, 1, { it in allowed }, { it == anchor }).isEmpty())
        assertEquals(listOf(anchor, near), SurroundPlanner.supportPath(target, 2, { it in allowed }, { it == anchor }))
        assertTrue(SurroundPlanner.supportPath(target, 0, { true }, { true }).isEmpty())
        assertTrue(SurroundPlanner.supportPath(target, 8, { false }, { false }).isEmpty())
        assertTrue(SurroundPlanner.supportPath(target, 8, { true }, { it == target }).isEmpty())
    }

    @Test fun findsShortestPathAndAvoidsBlockedHelpers() {
        val target = BlockPos(0, 64, 0)
        val short = target.east()
        val longer = target.below().below()
        val allowed = setOf(short, target.below(), longer)
        assertEquals(listOf(short), SurroundPlanner.supportPath(target, 8, { it in allowed }, { it == short || it == longer }))
        assertTrue(SurroundPlanner.supportPath(target, 8, { it == target.below() }, { it == longer }).isEmpty())
    }
}
