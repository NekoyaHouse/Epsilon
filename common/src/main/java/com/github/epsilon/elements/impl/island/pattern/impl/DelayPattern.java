package com.github.epsilon.elements.impl.island.pattern.impl;

import com.github.epsilon.elements.impl.island.pattern.LCPattern;

/**
 * 在存活指定毫秒数后自动关闭的条件。
 */
public class DelayPattern extends LCPattern {

    private final int delay;

    public DelayPattern(int delay) {
        this.delay = delay;
    }

    public int getDelay() {
        return delay;
    }

}
