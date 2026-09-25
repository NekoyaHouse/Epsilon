package com.github.epsilon.modules.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ModuleDeclaration {
    private final ModuleId owner;
    private final List<NodeDefinition<?>> nodes = new ArrayList<>();
    private final List<ModulePart> parts = new ArrayList<>();

    public ModuleDeclaration(ModuleId owner) { this.owner = Objects.requireNonNull(owner); }
    public ModuleId owner() { return owner; }
    public List<NodeRef<?>> nodes() { return List.copyOf(nodes); }
    public List<ModulePart> parts() { return List.copyOf(parts); }

    public <E> NodeBuilder<E> node(NodeKey key) {
        @SuppressWarnings("unchecked") Class<E> type = (Class<E>) com.github.epsilon.events.impl.PlayerTickEvent.Pre.class;
        return node(type, key);
    }

    public <E> NodeBuilder<E> node(Class<E> eventType, NodeKey key) {
        NodeDefinition<E> definition = new NodeDefinition<>(owner, eventType, key);
        nodes.add(definition);
        return definition;
    }

    public void part(ModulePart part) {
        parts.add(Objects.requireNonNull(part));
        part.declare(this);
    }
}
