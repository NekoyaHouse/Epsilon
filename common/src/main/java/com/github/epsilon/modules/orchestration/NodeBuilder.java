package com.github.epsilon.modules.orchestration;

public interface NodeBuilder<E> {
    NodeBuilder<E> phase(Phase phase);
    NodeBuilder<E> after(NodeRef<?> dependency);
    NodeBuilder<E> before(NodeRef<?> dependency);
    NodeBuilder<E> provides(Class<?> valueType);
    NodeBuilder<E> priority(int priority);
    NodeRef<E> handler(EventHandler<E> handler);
}
