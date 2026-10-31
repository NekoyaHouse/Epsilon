package com.github.epsilon.kernel.jvm

import cn.cutemic.clion.CLion

@Suppress("UNCHECKED_CAST")
inline val <T> Class<out T>.companion: T?
    get() = try {
        getDeclaredField("Companion")[null] as? T
    } catch (e: Exception) {
        null
    }

inline val <T> Class<out T>.instance: T?
    get() {
        return this.instanceJava
    }

@Suppress("UNCHECKED_CAST")
inline val <T> Class<out T>.instanceJava: T?
    get() = this.declaredFields
        .filter { it.type == this }.firstOrNull {
            try {
                it.isAccessible = true
                it[null]
                CLion.LOGGER.info("Class ${this.name} has a valid instance, attempt to reflect.")
                true
            } catch (e: Exception) {
                CLion.LOGGER.info("Class ${this.name} has no valid instance.")
                false
            }
        }?.run {
            isAccessible = true
            this[null] as T
        }