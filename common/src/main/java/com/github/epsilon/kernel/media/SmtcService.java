package com.github.epsilon.kernel.media;

public class SmtcService {
    private static volatile Thread worker;
    private static volatile MediaSessionSnapshot snapshot;
    private static final System.Logger LOGGER = System.getLogger(SmtcService.class.getName());

    private SmtcService() {
    }

    public static MediaSessionSnapshot snapshot() {
        return snapshot;
    }

    public static synchronized void setEnabled(boolean enabled) {
        if (!enabled) {
            Thread previous = worker;
            worker = null;
            snapshot = null;
            if (previous != null) previous.interrupt();
        } else if (worker == null) {
            Thread next = new Thread(SmtcService::poll, "Epsilon-SMTC");
            next.setDaemon(true);
            worker = next;
            next.start();
        }
    }

    private static synchronized void publish(Thread owner, MediaSessionSnapshot value) {
        if (worker == owner) snapshot = value;
    }

    private static void poll() {
        Thread owner = Thread.currentThread();
        boolean reported = false;
        while (worker == owner && !owner.isInterrupted()) {
            try (WindowsSmtc smtc = WindowsSmtc.open()) {
                while (worker == owner && !owner.isInterrupted()) {
                    publish(owner, smtc.read());
                    Thread.sleep(500L);
                }
            } catch (InterruptedException interrupted) {
                owner.interrupt();
            } catch (Exception | LinkageError failure) {
                publish(owner, null);
                if (!reported) {
                    LOGGER.log(System.Logger.Level.WARNING, "SMTC unavailable; retrying in 5 seconds. Check Windows support and --enable-native-access=ALL-UNNAMED.", failure);
                    reported = true;
                }
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException interrupted) {
                    owner.interrupt();
                }
            }
        }
        publish(owner, null);
    }
}
