package com.github.epsilon.modules.orchestration;

public interface NodeRef<E> {
    NodeKey key();
    Class<E> eventType();
    ModuleId owner();
}
