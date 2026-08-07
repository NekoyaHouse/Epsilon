package com.github.epsilon.elements.impl.island.pattern.impl;

import com.github.epsilon.elements.impl.island.pattern.LCPattern;

import java.util.function.Supplier;

/**
 * 由外部条件决定何时关闭的条件。
 */
public class CheckPattern extends LCPattern {

    private final Supplier<Boolean> supplier;

    public CheckPattern(Supplier<Boolean> supplier) {
        this.supplier = supplier;
    }

    public Supplier<Boolean> getSupplier() {
        return supplier;
    }

}
