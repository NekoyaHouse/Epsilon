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

    public Animation(Easing easing, long duration) {
        this.easing = easing;
        this.startTime = System.currentTimeMillis();
        this.duration = duration;
    }

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

    public float getProgress() {
        return (float) (System.currentTimeMillis() - this.startTime) / (float) this.duration;
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.startValue = value;
        this.finished = false;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setMillis(long millis) {
        this.millis = millis;
    }

    public long getMillis() {
        return millis;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    public void setStartValue(float startValue) {
        this.startValue = startValue;
        this.value = startValue;
    }

    public float getStartValue() {
        return startValue;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

}
