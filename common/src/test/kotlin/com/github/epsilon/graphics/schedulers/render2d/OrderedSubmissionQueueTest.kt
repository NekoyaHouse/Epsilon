package com.github.epsilon.graphics.schedulers.render2d

import kotlin.test.*

class OrderedSubmissionQueueTest {
    private data class Draw(val layer: Int, val order: Long, val resource: String)
    private val queue = OrderedSubmissionQueue<Draw>({ it.layer }, { it.order })

    @Test fun flushConsumesAndLaterSubmissionsCannotChangeTheSnapshot() {
        val first = Draw(0, 0, "first")
        queue.add(first)
        val submitted = queue.consume()
        assertTrue(queue.consume().isEmpty())
        queue.add(Draw(0, 1, "second"))
        assertEquals(listOf(first), submitted)
        assertEquals("second", queue.consume().single().resource)
        assertTrue(queue.consume().isEmpty())
    }

    @Test fun selectedLayerPreservesOtherLayersAndResourceInterleaving() {
        queue.addAll(listOf(Draw(2, 0, "background"), Draw(1, 1, "A"), Draw(1, 2, "B"), Draw(1, 3, "A")))
        assertEquals(listOf("A", "B", "A"), queue.consume(1).map { it.resource })
        assertEquals("background", queue.consume().single().resource)
    }

    @Test fun layerOrderIsExplicitAndClearOnlyDiscardsPendingRecords() {
        queue.addAll(listOf(Draw(5, 0, "top"), Draw(0, 1, "base")))
        assertEquals(listOf("base", "top"), queue.consume().map { it.resource })
        queue.add(Draw(-1, 2, "discard"))
        queue.clear()
        assertTrue(queue.consume().isEmpty())
        queue.add(Draw(0, 3, "after-clear"))
        assertEquals("after-clear", queue.consume().single().resource)
    }
}
