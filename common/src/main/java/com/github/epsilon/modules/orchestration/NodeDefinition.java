package com.github.epsilon.modules.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class NodeDefinition<E> implements NodeBuilder<E>, NodeRef<E> {
    private final ModuleId owner;
    private final Class<E> eventType;
    private final NodeKey key;
    private final List<NodeRef<?>> after = new ArrayList<>();
    private final List<NodeRef<?>> before = new ArrayList<>();
    private final List<Class<?>> provides = new ArrayList<>();
    private Phase phase = Phase.OBSERVE;
    private int priority;
    private EventHandler<E> handler;

    NodeDefinition(ModuleId owner, Class<E> eventType, NodeKey key) {
        this.owner = Objects.requireNonNull(owner); this.eventType = Objects.requireNonNull(eventType); this.key = Objects.requireNonNull(key);
    }
    @Override public NodeKey key() { return key; }
    @Override public Class<E> eventType() { return eventType; }
    @Override public ModuleId owner() { return owner; }
    @Override public NodeBuilder<E> phase(Phase phase) { this.phase = Objects.requireNonNull(phase); return this; }
    @Override public NodeBuilder<E> after(NodeRef<?> dependency) { after.add(Objects.requireNonNull(dependency)); return this; }
    @Override public NodeBuilder<E> before(NodeRef<?> dependency) { before.add(Objects.requireNonNull(dependency)); return this; }
    @Override public NodeBuilder<E> provides(Class<?> valueType) { provides.add(Objects.requireNonNull(valueType)); return this; }
    @Override public NodeBuilder<E> priority(int priority) { this.priority = priority; return this; }
    @Override public NodeRef<E> handler(EventHandler<E> handler) { this.handler = Objects.requireNonNull(handler); return this; }
    Phase phase() { return phase; }
    int priority() { return priority; }
    EventHandler<E> handler() { return handler; }
    List<NodeRef<?>> after() { return List.copyOf(after); }
    List<NodeRef<?>> before() { return List.copyOf(before); }
}
