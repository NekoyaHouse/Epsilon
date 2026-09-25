package com.github.epsilon.modules.orchestration;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CapabilityRegistry {
    private final Map<CapabilityKey<?>, Object> values = new ConcurrentHashMap<>();

    public <T> void publish(CapabilityKey<T> key, T value) {
        if (!key.type().isInstance(value)) throw new IllegalArgumentException("Invalid capability value for " + key.type());
        values.put(key, value);
    }

    public <T> Optional<T> get(CapabilityKey<T> key) {
        return Optional.ofNullable(key.type().cast(values.get(key)));
    }

    public <T> Optional<T> get(Class<T> type) {
        return values.entrySet().stream().filter(e -> e.getKey().type() == type)
                .map(Map.Entry::getValue).map(type::cast).findFirst();
    }

    public void remove(CapabilityKey<?> key) { values.remove(key); }
    public void clear() { values.clear(); }
}
