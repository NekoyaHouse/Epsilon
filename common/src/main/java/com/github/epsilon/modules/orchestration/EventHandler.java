package com.github.epsilon.modules.orchestration;

@FunctionalInterface
public interface EventHandler<E> {
    void handle(E event, ModuleContext context);
}
