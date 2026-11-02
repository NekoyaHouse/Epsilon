package com.github.epsilon.modules.impl.combat.autocrystal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrystalTimingTest {
    private class Clock {
        var nanos = 0L
        val timing = CrystalTiming({ nanos }, { 1_000_000L + nanos / 1_000_000L })
        fun advance(ms: Long) { nanos += ms * 1_000_000L }
    }

    @Test
    fun tickCanBreakThenPlaceWithoutAnExtraTickOrConfirmation() {
        val clock = Clock()
        clock.advance(101)
        val actions = ArrayList<String>()
        if (clock.timing.attackReady(50.0)) {
            actions += "break"
            clock.timing.recordAttack(7)
            clock.timing.resetAttack()
        }
        if (clock.timing.placeReady(100)) {
            actions += "place"
            clock.timing.recordPlace(12L)
            clock.timing.resetPlace()
        }
        assertEquals(listOf("break", "place"), actions)
        assertTrue(clock.timing.onSpawn(12L))
        assertFalse(clock.timing.onSpawn(12L))
    }

    @Test
    fun stopwatchesUseStrictIntegerMilliseconds() {
        val clock = Clock()
        clock.advance(50)
        assertFalse(clock.timing.attackReady(50.0))
        clock.nanos += 999_999L
        assertFalse(clock.timing.attackReady(50.0))
        clock.nanos++
        assertTrue(clock.timing.attackReady(50.0))
        clock.timing.resetAttack()
        assertFalse(clock.timing.attackReady(0.0))
    }

    @Test
    fun frameAndSpawnShareAttackClockButSequentialPlacementDoesNotResetPlaceClock() {
        val clock = Clock()
        clock.advance(101)
        assertTrue(clock.timing.attackReady(clock.timing.attackInterval(1.0, 3)))
        clock.timing.resetAttack()
        clock.timing.recordPlace(10L)
        assertTrue(clock.timing.placeReady(100))
        assertFalse(clock.timing.attackReady(50.0))
        clock.advance(51)
        assertTrue(clock.timing.attackReady(50.0))
        assertEquals(50.0, clock.timing.attackInterval(1.0, 0))
        assertEquals(25.0, clock.timing.attackInterval(1.0, 2))
    }

    @Test
    fun timeoutUsesConfirmedAverageAndMinimumInTicks() {
        val clock = Clock()
        clock.timing.recordAttack(1)
        clock.advance(80)
        clock.timing.confirm(1)
        clock.timing.recordAttack(2)
        clock.advance(120)
        clock.timing.confirm(2)
        assertEquals(100, clock.timing.averageBreakMillis)
        assertEquals(250.0, clock.timing.tickInterval(50, true, 5.0, 3.0))
        assertEquals(300.0, clock.timing.tickInterval(50, true, 0.0, 4.0))
        assertEquals(50.0, clock.timing.tickInterval(50, false, 5.0, 3.0))
    }

    @Test
    fun inhibitMatchesReferenceUnitsWithoutAddingA500msRetry() {
        val clock = Clock()
        clock.timing.recordAttack(1)
        clock.advance(51)
        assertFalse(clock.timing.inhibited(1))
        clock.timing.selectRepeated(1, 50.0)
        assertFalse(clock.timing.attackReady(50.0))
        clock.nanos += 150L
        assertTrue(clock.timing.attackReady(50.0))
        clock.timing.confirm(1)
        assertEquals(0, clock.timing.averageBreakMillis)
    }

    @Test
    fun attackLimitBlocksOnlyPlacementAfterScaledCountAndClearsOnRemoval() {
        val clock = Clock()
        repeat(15) { clock.timing.recordAttack(1) }
        assertFalse(clock.timing.blocksPlacement(1, 1.5))
        clock.timing.recordAttack(1)
        assertTrue(clock.timing.blocksPlacement(1, 1.5))
        clock.advance(51)
        assertTrue(clock.timing.attackReady(50.0))
        clock.timing.confirm(1)
        assertFalse(clock.timing.blocksPlacement(1, 1.5))
        clock.timing.recordPlace(2L)
        clock.timing.clear()
        assertFalse(clock.timing.onSpawn(2L))
        assertEquals(0, clock.timing.averageBreakMillis)
    }

    @Test
    fun baseDelayCountsTicksAndSwapPenaltyUses25msUnits() {
        val clock = Clock()
        repeat(4) { clock.timing.tick() }
        assertEquals(4, clock.timing.baseTicks)
        clock.timing.resetBase()
        assertEquals(0, clock.timing.baseTicks)
        clock.advance(25)
        assertFalse(clock.timing.swapReady(1.0))
        clock.advance(1)
        assertTrue(clock.timing.swapReady(1.0))
    }
}
