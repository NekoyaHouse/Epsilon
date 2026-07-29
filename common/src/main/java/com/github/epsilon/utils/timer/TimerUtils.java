package com.github.epsilon.utils.timer;

public class TimerUtils {

    private long startTime = -1L;

    /**
     * 创建并立即复位计时器。
     */
    public TimerUtils() {
        reset();
    }

    /**
     * 将计时起点重置为当前时间。
     */
    public void reset() {
        startTime = System.currentTimeMillis();
    }

    /**
     * 获取自计时起点以来经过的毫秒数。
     *
     * @return 获取或计算得到的结果
     */
    public long getMs() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 将计时器调整为已经过指定毫秒数。
     *
     * @param ms 毫秒数或触发间隔
     */
    public void setMs(long ms) {
        startTime = System.currentTimeMillis() - ms;
    }

    /**
     * 判断是否已经过指定秒数。
     *
     * @param seconds 秒数
     * @return 判断结果
     */
    public boolean passedSecond(double seconds) {
        return passedMillise((long) seconds * 1000L);
    }

    /**
     * 判断是否已经过指定 tick 对应的时间。
     *
     * @param ticks 模拟的 tick 数
     * @return 判断结果
     */
    public boolean hasDelayed(int ticks) {
        return passedMillise((long) ticks * 50L);
    }

    /**
     * 按指定间隔触发，并在触发时复位计时器。
     *
     * @param ms 毫秒数或触发间隔
     * @return 判断结果
     */
    public boolean every(long ms) {
        if (passedMillise(ms)) {
            reset();
            return true;
        }
        return false;
    }

    /**
     * 判断是否已经过指定毫秒数。
     *
     * @param ms 毫秒数或触发间隔
     * @return 判断结果
     */
    public boolean passedMillise(double ms) {
        return passedMillise((long) ms);
    }

    /**
     * 判断是否已经过指定毫秒数。
     *
     * @param ms 毫秒数或触发间隔
     * @return 判断结果
     */
    public boolean passedMillise(long ms) {
        return System.currentTimeMillis() - startTime >= ms;
    }

}
