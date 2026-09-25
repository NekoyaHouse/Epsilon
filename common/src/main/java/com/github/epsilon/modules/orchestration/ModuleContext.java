package com.github.epsilon.modules.orchestration;

import java.util.List;
import java.util.Optional;

public interface ModuleContext {
    <T> Optional<T> capability(Class<T> type);
    <T> Optional<T> capability(CapabilityKey<T> key);
    <T> void publish(T value);
    <T> List<T> values(Class<T> type);
    FrameTrace trace();
}
