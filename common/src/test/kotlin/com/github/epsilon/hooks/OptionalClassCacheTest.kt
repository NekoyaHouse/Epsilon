package com.github.epsilon.hooks

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class OptionalClassCacheTest {
    @Test fun presentAndMissingClassesAreResolvedOnceAcrossThreads() {
        val presentLoads = AtomicInteger()
        val missingLoads = AtomicInteger()
        val cache = OptionalClassCache { name ->
            if (name == "present") {
                presentLoads.incrementAndGet()
                String::class.java
            } else {
                missingLoads.incrementAndGet()
                throw ClassNotFoundException(name)
            }
        }
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val threads = List(8) {
            Thread {
                try {
                    repeat(1000) {
                        assertSame(String::class.java, cache.find("present"))
                        assertNull(cache.find("missing"))
                    }
                } catch (error: Throwable) { failures.add(error) }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        failures.firstOrNull()?.let { throw it }
        assertEquals(1, presentLoads.get())
        assertEquals(1, missingLoads.get())
    }

    @Test fun linkageFailuresAreNotMisreportedAsMissingClasses() {
        var loads = 0
        val cache = OptionalClassCache {
            if (++loads == 1) throw NoClassDefFoundError("dependency")
            String::class.java
        }
        assertFailsWith<NoClassDefFoundError> { cache.find("present") }
        assertSame(String::class.java, cache.find("present"))
        assertEquals(2, loads)
    }
}
