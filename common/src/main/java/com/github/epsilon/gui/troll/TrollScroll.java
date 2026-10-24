package com.github.epsilon.gui.troll;

public class TrollScroll {

    private float position;
    private float velocity;
    private long lastUpdate;

    public float update(float maximum) {
        long now = System.nanoTime();
        float elapsed = lastUpdate == 0L ? 0.0f : Math.min((now - lastUpdate) / 100_000_000.0f, 1.0f);
        lastUpdate = now;
        float decay = (float) Math.pow(0.25, elapsed);
        position += velocity * (decay - 1.0f) / (float) Math.log(0.25);
        velocity *= decay;
        if (position < 0.0f || position > maximum) {
            float target = Math.clamp(position, 0.0f, maximum);
            position += (target - position) * (1.0f - (float) Math.exp(-elapsed * 2.5f));
            velocity += (target - position) * elapsed;
        }
        position = Math.clamp(position, -24.0f, maximum + 24.0f);
        if (Math.abs(velocity) < 0.01f && Math.abs(position - Math.clamp(position, 0.0f, maximum)) < 0.1f) {
            position = Math.clamp(position, 0.0f, maximum);
        }
        return position;
    }

    public void scroll(double amount) {
        velocity -= (float) amount * 24.0f;
    }

    public void stop() {
        velocity = 0.0f;
        lastUpdate = 0L;
    }

}
