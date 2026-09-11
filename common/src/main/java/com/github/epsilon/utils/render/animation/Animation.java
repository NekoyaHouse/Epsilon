package com.github.epsilon.utils.render.animation;

public class Animation {

    private final Easing easing;
    private long duration;
    private long millis;
    private long startTime;

    private float startValue;
    private float destinationValue;
    private float value;
    private boolean finished;

    /**
     * 创建使用指定缓动函数和时长的动画。
     *
     * @param easing   缓动类型
     * @param duration 持续时间，单位为毫秒
     */
    public Animation(Easing easing, long duration) {
        this.easing = easing;
        this.startTime = System.currentTimeMillis();
        this.duration = duration;
    }

    /**
     * 推进动画并更新到目标值。
     *
     * @param destinationValue 目标动画值
     */
    public void run(float destinationValue) {
        this.millis = System.currentTimeMillis();
        if (this.destinationValue != destinationValue) {
            this.destinationValue = destinationValue;
            this.reset();
        } else {
            this.finished = this.millis - this.duration > this.startTime || this.value == destinationValue;
            if (this.finished) {
                this.value = destinationValue;
                return;
            }
        }

        float result = this.easing.getFunction().apply(this.getProgress());
        if (this.duration == 0L) {
            this.value = destinationValue;
        } else if (this.value > destinationValue) {
            this.value = this.startValue - (this.startValue - destinationValue) * result;
        } else {
            this.value = this.startValue + (destinationValue - this.startValue) * result;
        }

        if (Float.isNaN(value) || !Float.isFinite(value)) {
            this.value = destinationValue;
        }
    }

    /**
     * 获取应用缓动函数后的动画进度。
     *
     * @return 获取或计算得到的结果
     */
    public float getProgress() {
        return (float) (System.currentTimeMillis() - this.startTime) / (float) this.duration;
    }

    /**
     * 将动画计时和状态重置到起点。
     */
    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.startValue = value;
        this.finished = false;
    }

    /**
     * 获取动画持续时间。
     *
     * @return 获取或计算得到的结果
     */
    public long getDuration() {
        return duration;
    }

    /**
     * 设置动画持续时间。
     *
     * @param duration 持续时间，单位为毫秒
     */
    public void setDuration(long duration) {
        this.duration = duration;
    }

    /**
     * 设置动画已经过的时间。
     *
     * @param millis 已经过的毫秒数
     */
    public void setMillis(long millis) {
        this.millis = millis;
    }

    /**
     * 获取动画已经过的时间。
     *
     * @return 获取或计算得到的结果
     */
    public long getMillis() {
        return millis;
    }

    /**
     * 获取当前动画值。
     *
     * @return 获取或计算得到的结果
     */
    public float getValue() {
        return value;
    }

    /**
     * 直接设置当前动画值。
     *
     * @param value 要设置的值
     */
    public void setValue(float value) {
        this.value = value;
    }

    /**
     * 设置动画起始值。
     *
     * @param startValue 动画起始值
     */
    public void setStartValue(float startValue) {
        this.startValue = startValue;
        this.value = startValue;
    }

    /**
     * 获取动画起始值。
     *
     * @return 获取或计算得到的结果
     */
    public float getStartValue() {
        return startValue;
    }

    /**
     * 判断动画是否已经完成。
     *
     * @return 判断结果
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * 设置动画完成状态。
     *
     * @param finished 是否标记为完成
     */
    public void setFinished(boolean finished) {
        this.finished = finished;
    }

}
