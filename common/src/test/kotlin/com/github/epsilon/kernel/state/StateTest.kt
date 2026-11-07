package com.github.epsilon.kernel.state

import kotlin.test.*

class StateTest {
    @AfterTest fun drain() = StateNotifications.drain()

    @Test fun equalityAndConditionalDependencies() {
        val branch = mutableStateOf(true)
        val left = mutableStateOf(1)
        val right = mutableStateOf(2)
        var changes = 0
        val observer = StateObservation { changes++ }
        fun observe() = observer.observeReads { if (branch.value) left.value else right.value }
        assertEquals(1, observe())
        left.value = 1
        StateNotifications.drain()
        assertEquals(0, changes)
        left.value = 3
        StateNotifications.drain()
        assertEquals(1, changes)
        branch.value = false
        assertEquals(2, observe())
        StateNotifications.drain()
        changes = 0
        left.value = 4
        StateNotifications.drain()
        assertEquals(0, changes)
        right.value = 5
        StateNotifications.drain()
        assertEquals(1, changes)
        observer.close()
        right.value = 6
        StateNotifications.drain()
        assertEquals(1, changes)
    }

    @Test fun derivedNotifiesOnlyForChangedResultAndReadsFreshValue() {
        val input = mutableStateOf(0)
        val parity = derivedStateOf { input.value % 2 }
        var changes = 0
        val observer = StateObservation { changes++ }
        observer.observeReads { assertEquals(0, parity.value) }
        StateNotifications.drain()
        input.value = 2
        StateNotifications.drain()
        assertEquals(0, changes)
        input.value = 3
        assertEquals(1, parity.value)
        StateNotifications.drain()
        assertEquals(1, changes)
        observer.close()
        input.value = 4
        assertEquals(0, parity.value)
        (parity as AutoCloseable).close()
    }

    @Test fun nestedObservationKeepsDependenciesAtInnermostScope() {
        val outerState = mutableStateOf(0)
        val innerState = mutableStateOf(0)
        var outerChanges = 0
        var innerChanges = 0
        val outer = StateObservation { outerChanges++ }
        val inner = StateObservation { innerChanges++ }
        outer.observeReads { outerState.value; inner.observeReads { innerState.value } }
        innerState.value++
        StateNotifications.drain()
        assertEquals(0, outerChanges)
        assertEquals(1, innerChanges)
        outer.close()
        inner.close()
    }

    @Test fun failedObservationKeepsOldSubscriptions() {
        val old = mutableStateOf(0)
        val abandoned = mutableStateOf(0)
        var changes = 0
        val observer = StateObservation { changes++ }
        observer.observeReads { old.value }
        assertFailsWith<IllegalStateException> { observer.observeReads { abandoned.value; error("failed") } }
        abandoned.value++
        StateNotifications.drain()
        assertEquals(0, changes)
        old.value++
        StateNotifications.drain()
        assertEquals(1, changes)
        observer.close()
    }

    @Test fun crossThreadWriteDuringReadCannotLoseInvalidation() {
        val state = mutableStateOf(0)
        var changes = 0
        val host = Thread.currentThread()
        val observer = StateObservation { assertSame(host, Thread.currentThread()); changes++ }
        observer.observeReads {
            assertEquals(0, state.value)
            Thread { state.value = 1 }.apply { start(); join() }
        }
        assertTrue(changes > 0)
        observer.observeReads { assertEquals(1, state.value) }
        StateNotifications.drain()
        val baseline = changes
        Thread { repeat(1000) { state.value = it } }.apply { start(); join() }
        assertEquals(baseline, changes)
        StateNotifications.drain()
        assertEquals(baseline + 1, changes)
        observer.close()
    }

    @Test fun businessWritesAreRejectedDuringUiBuild() {
        val state = mutableStateOf(0)
        StateTracking.buildingUi { state.value = 0 }
        assertFailsWith<IllegalStateException> { StateTracking.buildingUi { state.value = 1 } }
        assertEquals(0, state.value)
    }

    @Test fun repeatedReadAfterConcurrentWriteKeepsTheFirstReadVersion() {
        val state = mutableStateOf(0)
        var changes = 0
        val observer = StateObservation { changes++ }
        observer.observeReads {
            assertEquals(0, state.value)
            Thread { state.value = 1 }.apply { start(); join() }
            repeat(100) { assertEquals(1, state.value) }
        }
        assertEquals(1, changes)
        observer.observeReads { repeat(100) { assertEquals(1, state.value) } }
        StateNotifications.drain()
        assertEquals(1, changes)
        observer.close()
    }
    @Test fun unusedDerivedStateDoesNotRetainAnUpstreamSubscription() {
        val input = mutableStateOf(0)
        var calculations = 0
        val derived = derivedStateOf { calculations++; input.value % 2 }
        assertEquals(0, derived.value)
        input.value = 1
        StateNotifications.drain()
        assertEquals(1, calculations)
        assertEquals(1, derived.value)
        val observer = StateObservation {}
        observer.observeReads { derived.value }
        input.value = 2
        StateNotifications.drain()
        assertEquals(3, calculations)
        observer.close()
        input.value = 3
        StateNotifications.drain()
        assertEquals(3, calculations)
        assertEquals(1, derived.value)
        (derived as AutoCloseable).close()
    }

}
