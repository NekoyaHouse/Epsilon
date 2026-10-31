package com.github.epsilon.utils.render.animation

class Animation(val easing: Easing, var duration: Long) {

    var millis: Long = 0L

    var startValue: Float = 0.0f
        set(value) {
            field = value
            this.value = value
        }

    private var startTime: Long = System.currentTimeMillis()

    private var destinationValue: Float = 0.0f

    var value: Float = 0.0f

    var isFinished: Boolean = false

    /**
     * 推进动画并更新到目标值。
     *
     * @param destinationValue 目标动画值
     */
    fun run(destinationValue: Float) {
        this.millis = System.currentTimeMillis()
        if (this.destinationValue != destinationValue) {
            this.destinationValue = destinationValue
            reset()
        } else {
            this.isFinished = this.millis - this.duration > this.startTime || this.value == destinationValue
            if (this.isFinished) {
                this.value = destinationValue
                return
            }
        }

        val result = this.easing.function.apply(getProgress())
        if (this.duration == 0L) {
            this.value = destinationValue
        } else if (this.value > destinationValue) {
            this.value = this.startValue - (this.startValue - destinationValue) * result
        } else {
            this.value = this.startValue + (destinationValue - this.startValue) * result
        }

        if (value.isNaN() || !value.isFinite()) {
            this.value = destinationValue
        }
    }

    /**
     * 获取应用缓动函数后的动画进度。
     *
     * @return 获取或计算得到的结果
     */
    fun getProgress(): Float {
        return (System.currentTimeMillis() - this.startTime).toFloat() / this.duration.toFloat()
    }

    /**
     * 将动画计时和状态重置到起点。
     */
    fun reset() {
        this.startTime = System.currentTimeMillis()
        this.startValue = value
        this.isFinished = false
    }

}
