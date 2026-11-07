package com.github.epsilon.hooks

import java.util.concurrent.ConcurrentHashMap

/** 加载器的类路径在启动后固定；缺失的可选类也要缓存，避免逐帧扫描 Jar 并创建异常。 */
internal class OptionalClassCache(private val load: (String) -> Class<*> = Class<*>::forName) {
    private class Result(val type: Class<*>?)
    private val results = ConcurrentHashMap<String, Result>()

    fun find(name: String): Class<*>? = results.computeIfAbsent(name) {
        Result(try { load(it) } catch (_: ClassNotFoundException) { null })
    }.type
}
