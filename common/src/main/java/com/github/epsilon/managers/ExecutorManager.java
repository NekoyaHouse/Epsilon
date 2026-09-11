package com.github.epsilon.managers;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorManager {

    public static final ExecutorManager INSTANCE = new ExecutorManager();

    private final ExecutorService executor;

    private ExecutorManager() {
        AtomicInteger threadNumber = new AtomicInteger(1);
        executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.setName("Epsilon-Executor-" + threadNumber.getAndIncrement());
            return thread;
        });
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

}
