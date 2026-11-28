package com.github.epsilon.modules.impl.combat.autocrystal

import net.minecraft.world.phys.AABB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlacementPredictionCacheTest {
    private val initial = AABB(0.0, 0.0, 0.0, 0.6, 1.8, 0.6)

    @Test fun reusesStepsAcrossHundredsOfBasesAndDamageChecks() {
        var calls = 0
        val cache = PlacementPredictionCache<String>(8, { initial }) { _, box ->
            calls++
            box.move(1.0, 0.0, 0.0)
        }
        repeat(729) {
            assertTrue(cache.intersects("player", initial.move(3.0, 0.0, 0.0)))
            assertEquals(initial.move(8.0, 0.0, 0.0), cache.bounds("player"))
        }
        assertEquals(8, calls)
        cache.bounds("another player")
        assertEquals(16, calls)
    }

    @Test fun blockedPathsStopAndStillCheckIntermediateSteps() {
        var calls = 0
        val cache = PlacementPredictionCache<String>(8, { initial }) { _, box ->
            calls++
            if (box.minX >= 2.0) null else box.move(1.0, 0.0, 0.0)
        }
        repeat(20) {
            assertEquals(initial.move(2.0, 0.0, 0.0), cache.bounds("player"))
            assertTrue(cache.intersects("player", initial.move(1.0, 0.0, 0.0)))
            assertFalse(cache.intersects("player", initial.move(3.0, 0.0, 0.0)))
        }
        assertEquals(3, calls)
    }

    @Test fun freshScansDoNotReuseStaleBounds() {
        var current = initial
        fun scan() = PlacementPredictionCache<String>(3, { current }) { _, _ -> null }
        val old = scan()
        assertEquals(initial, old.bounds("player"))
        current = initial.move(10.0, 0.0, 0.0)
        val fresh = scan()
        assertEquals(current, fresh.bounds("player"))
        assertTrue(fresh.intersects("player", current))
        assertFalse(fresh.intersects("player", initial))
    }

    @Test fun disabledPredictionDoesNotAdvance() {
        var calls = 0
        val cache = PlacementPredictionCache<String>(0, { initial }) { _, box ->
            calls++
            box
        }
        assertEquals(initial, cache.bounds("player"))
        assertEquals(0, calls)
    }
}
