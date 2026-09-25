package com.github.epsilon.modules.orchestration;

import java.util.Objects;

public record CapabilityKey<T>(Class<T> type) {
    public CapabilityKey { Objects.requireNonNull(type, "type"); }
    public static <T> CapabilityKey<T> of(Class<T> type) { return new CapabilityKey<>(type); }
}
