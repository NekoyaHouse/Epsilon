package com.github.epsilon.utils.timer

class TimerUtils {

    private var startTime = -1L

    /**
     * 创建并立即复位计时器。
     */
    init {
        reset()
    }

    /**
     * 将计时起点重置为当前时间。
     */
    fun reset() {
        startTime = System.currentTimeMillis()
    }

    /**
     * 获取自计时起点以来经过的毫秒数。
     *
     * @return 获取或计算得到的结果
     */
    fun getMs(): Long {
        return System.currentTimeMillis() - startTime
    }

    /**
     * 将计时器调整为已经过指定毫秒数。
     *
     * @param ms 毫秒数或触发间隔
     */
    fun setMs(ms: Long) {
        startTime = System.currentTimeMillis() - ms
    }

    /**
     * 判断是否已经过指定秒数。
     *
     * @param seconds 秒数
     * @return 判断结果
     */
    fun passedSecond(seconds: Double): Boolean {
        return passedMillise(seconds.toLong() * 1000L)
    }

    /**
     * 判断是否已经过指定 tick 对应的时间。
     *
     * @param ticks 模拟的 tick 数
     * @return 判断结果
     */
    fun hasDelayed(ticks: Int): Boolean {
        return passedMillise(ticks.toLong() * 50L)
    }

    /**
     * 按指定间隔触发，并在触发时复位计时器。
     *
     * @param ms 毫秒数或触发间隔
     * @return 判断结果
     */
    fun every(ms: Long): Boolean {
        if (passedMillise(ms)) {
            reset()
            return true
        }
        return false
    }

    /**
     * 判断是否已经过指定毫秒数。
     *
     * @param ms 毫秒数或触发间隔
     * @return 判断结果
     */
    fun passedMillise(ms: Double): Boolean {
        return passedMillise(ms.toLong())
    }

    /**
     * 判断是否已经过指定毫秒数。
     *
     * @param ms 毫秒数或触发间隔
     * @return 判断结果
     */
    fun passedMillise(ms: Long): Boolean {
        return System.currentTimeMillis() - startTime >= ms
    }

}
