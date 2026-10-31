package com.github.epsilon.kernel.threads

import cn.cutemic.clion.CLion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max

private val pool0 = Runtime.getRuntime().availableProcessors().let { cpuCount ->
    // we reduce the threads to avoid low fps
    val maxSize = max(cpuCount * 2, 8)
    ThreadPoolExecutor(
        cpuCount * 2,
        maxSize,
        5L,
        TimeUnit.SECONDS,
        SynchronousQueue(),
        CountingThreadFactory("${CLion.NAME}-Task")
    )
}

private val context0 = pool0.asCoroutineDispatcher()

internal object Coroutine : CoroutineScope by CoroutineScope(context0) {
    val pool = pool0
    val context = context0
}