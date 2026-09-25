package com.github.epsilon.modules.orchestration;

import java.util.List;

public record DispatchPlan<E>(Class<E> eventType, long version, List<NodeRef<E>> nodes) {
    public DispatchPlan { nodes = List.copyOf(nodes); }
}
