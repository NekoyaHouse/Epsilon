package com.github.epsilon.modules.orchestration;

import java.util.Objects;

public record ModuleId(String value) {
    public ModuleId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("ModuleId cannot be blank");
    }

    public static ModuleId of(String value) { return new ModuleId(value); }
}
