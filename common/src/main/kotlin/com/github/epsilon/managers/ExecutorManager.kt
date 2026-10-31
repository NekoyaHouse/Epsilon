package com.github.epsilon.managers

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object ExecutorManager {

    private val executor: ExecutorService

    init {
        val threadNumber = AtomicInteger(1)
        executor = Executors.newCachedThreadPool { task ->
            val thread = Thread(task)
            thread.isDaemon = true
            thread.name = "Epsilon-Executor-" + threadNumber.andIncrement
            thread
        }
    }

    fun execute(task: Runnable) {
        executor.execute(task)
    }

}
