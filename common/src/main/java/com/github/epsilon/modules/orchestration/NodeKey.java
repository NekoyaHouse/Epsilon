package com.github.epsilon.modules.orchestration;

import java.util.Objects;

public record NodeKey(String value) {
    public NodeKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.indexOf('.') < 0) {
            throw new IllegalArgumentException("NodeKey must be a non-blank dotted identifier: " + value);
        }
    }

    public static NodeKey of(String value) {
        return new NodeKey(value);
    }
}
