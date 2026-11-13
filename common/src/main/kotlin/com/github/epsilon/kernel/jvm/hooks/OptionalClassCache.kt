package com.github.epsilon.kernel.jvm.hooks

import java.util.concurrent.ConcurrentHashMap

internal class OptionalClassCache(private val load: (String) -> Class<*> = Class<*>::forName) {
    private class Result(val type: Class<*>?)

    private val results = ConcurrentHashMap<String, Result>()

    fun find(name: String): Class<*>? = results.computeIfAbsent(name) {
        Result(
            try {
                load(it)
            } catch (_: ClassNotFoundException) {
                null
            }
        )
    }.type
}
