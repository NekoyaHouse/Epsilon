package com.github.epsilon.graphics.schedulers.render2d

import java.awt.Color
import kotlin.test.*

class AdjacentBatchPlannerTest {
    private val bounds = Render2DBounds.of(0f, 0f, 10f, 10f)
    private fun rect(sequence: Long) = Render2DCommand.Rect(0, sequence, bounds, null,
        Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE)

    @Test fun fillsOutlinesAndChevronsShareOneGeometryBatchInDeclarationOrder() {
        val outline = Render2DCommand.RoundRectOutline(0, 1, bounds, null, 2f, 2f, 2f, 2f, 1f, Color.WHITE)
        val triangle = Render2DCommand.Triangle(0, 2, bounds, null, 5f, 5f, 2f, 0f, Color.WHITE)
        val groups = AdjacentBatchPlanner.plan(listOf(rect(0), outline, triangle, rect(3)))
        assertEquals(listOf(listOf(0L, 1L, 2L, 3L)), groups.map { it.commands.map { c -> c.sequence } })
        assertEquals(listOf(rect(0), outline, triangle, rect(3)), groups.single().commands)
    }

    @Test fun clipsSplitAdjacentRuns() {
        val clipped = rect(1).copy(scissor = Render2DScissor(0, 0, 5, 5))
        assertEquals(3, AdjacentBatchPlanner.plan(listOf(rect(0), clipped, rect(2))).size)
    }

    @Test fun resourceGenerationAdvancesWithoutGpuAccess() {
        val previous = Render2DResources.generation
        Render2DResources.invalidate()
        assertTrue(Render2DResources.generation > previous)
    }
}
