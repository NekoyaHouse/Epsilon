package com.github.epsilon.managers.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorManager {

    private final ExecutorService executor;

    public ExecutorManager() {
        AtomicInteger threadNumber = new AtomicInteger(1);

        executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.setName("Meteor-Executor-" + threadNumber.getAndIncrement());
            return thread;
        });
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

}
