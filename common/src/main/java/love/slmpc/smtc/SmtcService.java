package love.slmpc.smtc;

import com.github.epsilon.Constants;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 在独立线程轮询 Windows SMTC，避免 WinRT 异步等待阻塞 Minecraft 渲染线程。
 */
public class SmtcService {

    public static final SmtcService INSTANCE = new SmtcService();

    private final AtomicReference<SmtcSnapshot> snapshot = new AtomicReference<>(SmtcSnapshot.UNAVAILABLE);

    private ScheduledExecutorService executor;
    private String lastError = "";

    private SmtcService() {
    }

    public synchronized void start() {
        if (executor != null || !SmtcNativeBridge.isAvailable()) return;

        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Epsilon-SMTC");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::poll, 0L, 750L, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (executor == null) {
            snapshot.set(SmtcSnapshot.UNAVAILABLE);
        } else {
            executor.shutdownNow();
            executor = null;
            SmtcNativeBridge.reset();
            snapshot.set(SmtcSnapshot.UNAVAILABLE);
            lastError = "";
        }
    }

    public SmtcSnapshot snapshot() {
        return snapshot.get();
    }

    private void poll() {
        try {
            SmtcNativeResult result = SmtcNativeBridge.poll();
            if (result == null) return;

            String error = result.error() == null ? "" : result.error();
            if (!error.isBlank() && !error.equals(lastError)) {
                Constants.LOGGER.warn("Windows SMTC query failed: {}", error);
            }
            lastError = error;
            snapshot.updateAndGet(previous -> SmtcSnapshot.merge(previous, result));
        } catch (Throwable e) {
            String message = e.getClass().getName() + ": " + e.getMessage();
            if (!message.equals(lastError)) {
                Constants.LOGGER.warn("Windows SMTC polling stopped by a native bridge error", e);
                lastError = message;
            }
            snapshot.set(SmtcSnapshot.UNAVAILABLE);
        }
    }

}
