package me.sofurry.smtc;

import com.github.epsilon.Constants;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SmtcService {

    public static final SmtcService INSTANCE = new SmtcService();

    private final AtomicReference<SmtcSnapshot> snapshot = new AtomicReference<>(SmtcSnapshot.UNAVAILABLE);
    private final AtomicLong generation = new AtomicLong();

    private ScheduledExecutorService executor;
    private String lastError = "";

    private SmtcService() {
    }

    public synchronized void start() {
        if (executor != null || !SmtcNativeBridge.isAvailable()) return;

        long activeGeneration = generation.incrementAndGet();
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Epsilon-SMTC");
            thread.setDaemon(true);
            return thread;
        });
        executor.execute(SmtcNativeBridge::reset);
        executor.scheduleWithFixedDelay(() -> poll(activeGeneration), 0L, 750L, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        generation.incrementAndGet();
        ScheduledExecutorService current = executor;
        executor = null;
        snapshot.set(SmtcSnapshot.UNAVAILABLE);
        lastError = "";
        if (current != null) current.shutdownNow();
    }

    public SmtcSnapshot snapshot() {
        return snapshot.get();
    }

    private void poll(long activeGeneration) {
        if (generation.get() != activeGeneration) return;

        try {
            SmtcNativeResult result = SmtcNativeBridge.poll();
            if (result == null) return;

            synchronized (this) {
                if (generation.get() != activeGeneration) return;

                String error = result.error() == null ? "" : result.error();
                if (!error.isBlank() && !error.equals(lastError)) {
                    Constants.LOGGER.warn("Windows SMTC query failed: {}", error);
                }
                lastError = error;
                snapshot.updateAndGet(previous -> SmtcSnapshot.merge(previous, result));
            }
        } catch (Throwable e) {
            synchronized (this) {
                if (generation.get() != activeGeneration) return;

                String message = e.getClass().getName() + ": " + e.getMessage();
                if (!message.equals(lastError)) {
                    Constants.LOGGER.warn("Windows SMTC polling stopped by a native bridge error", e);
                    lastError = message;
                }
                snapshot.set(SmtcSnapshot.UNAVAILABLE);
            }
        }
    }

}
