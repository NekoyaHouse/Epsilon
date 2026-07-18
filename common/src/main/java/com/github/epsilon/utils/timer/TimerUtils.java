package com.github.epsilon.utils.timer;

public class TimerUtils {

    private long startTime = -1L;

    public TimerUtils() {
        reset();
    }

    public void reset() {
        startTime = System.currentTimeMillis();
    }

    public long getMs() {
        return System.currentTimeMillis() - startTime;
    }

    public void setMs(long ms) {
        startTime = System.currentTimeMillis() - ms;
    }

    public boolean passedSecond(double seconds) {
        return passedMillise((long) seconds * 1000L);
    }

    public boolean hasDelayed(int ticks) {
        return passedMillise((long) ticks * 50L);
    }

    public boolean every(long ms) {
        if (passedMillise(ms)) {
            reset();
            return true;
        }
        return false;
    }

    public boolean passedMillise(double ms) {
        return passedMillise((long) ms);
    }

    public boolean passedMillise(long ms) {
        return System.currentTimeMillis() - startTime >= ms;
    }

}
