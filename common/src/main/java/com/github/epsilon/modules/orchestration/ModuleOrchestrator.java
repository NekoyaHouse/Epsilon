package com.github.epsilon.modules.orchestration;

import com.github.epsilon.events.bus.Cancellable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.github.epsilon.modules.Module;

public final class ModuleOrchestrator {
    public static final ModuleOrchestrator INSTANCE = new ModuleOrchestrator();
    private final Map<ModuleId, ModuleDeclaration> declarations = new LinkedHashMap<>();
    private final Map<Class<?>, DispatchPlan<?>> plans = new ConcurrentHashMap<>();
    private final CapabilityRegistry capabilities = new CapabilityRegistry();
    private final Map<ModuleId, Module> modules = new ConcurrentHashMap<>();
    private long version;
    private boolean dispatching;

    private ModuleOrchestrator() {}
    public CapabilityRegistry capabilities() { return capabilities; }
    public synchronized void register(ModuleDeclaration declaration) {
        if (declarations.putIfAbsent(declaration.owner(), declaration) != null) throw new OrchestrationException("Duplicate module: " + declaration.owner());
        rebuild();
    }
    public void register(Module module) { modules.put(module.moduleId(), module); register(module.declaration()); }
    public void setEnabled(ModuleId id, boolean enabled) { /* 状态由 Module 持有；该入口用于计划原子切换的扩展点。 */ }
    public synchronized void unregister(ModuleId id) { declarations.remove(id); rebuild(); }
    public synchronized void rebuild() {
        Map<Class<?>, List<NodeDefinition<?>>> grouped = new LinkedHashMap<>();
        Set<NodeRef<?>> all = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, NodeRef<?>> keys = new HashMap<>();
        for (ModuleDeclaration declaration : declarations.values()) for (NodeRef<?> ref : declaration.nodes()) {
            NodeDefinition<?> node = (NodeDefinition<?>) ref;
            String identity = declaration.owner().value() + "." + node.key().value();
            if (keys.putIfAbsent(identity, node) != null) throw new OrchestrationException("Duplicate node: " + identity);
            all.add(node); grouped.computeIfAbsent(node.eventType(), ignored -> new ArrayList<>()).add(node);
        }
        Map<Class<?>, DispatchPlan<?>> next = new HashMap<>();
        for (var entry : grouped.entrySet()) next.put(entry.getKey(), makePlan(entry.getKey(), entry.getValue(), all));
        plans.clear(); plans.putAll(next); version++;
    }
    private <E> DispatchPlan<E> makePlan(Class<E> type, List<NodeDefinition<?>> raw, Set<NodeRef<?>> all) {
        List<NodeDefinition<E>> nodes = raw.stream().map(n -> ModuleOrchestrator.<E>cast(n)).toList();
        Map<NodeRef<?>, Set<NodeRef<?>>> edges = new IdentityHashMap<>();
        Map<NodeRef<?>, Integer> indegree = new IdentityHashMap<>();
        for (NodeDefinition<E> node : nodes) { edges.put(node, Collections.newSetFromMap(new IdentityHashMap<>())); indegree.put(node, 0); }
        for (NodeDefinition<E> node : nodes) {
            for (NodeRef<?> dep : node.after()) addEdge(dep, node, edges, indegree, all);
            for (NodeRef<?> dep : node.before()) addEdge(node, dep, edges, indegree, all);
        }
        List<NodeDefinition<E>> ordered = new ArrayList<>();
        while (ordered.size() < nodes.size()) {
            NodeDefinition<E> best = nodes.stream().filter(n -> !ordered.contains(n) && indegree.get(n) == 0)
                    .min(Comparator.<NodeDefinition<E>>comparingInt(n -> n.phase().ordinal()).thenComparing(Comparator.comparingInt((NodeDefinition<E> n) -> n.priority()).reversed())).orElse(null);
            if (best == null) throw new OrchestrationException("Dependency cycle in event " + type.getName());
            ordered.add(best); for (NodeRef<?> out : edges.get(best)) indegree.computeIfPresent(out, (k, v) -> v - 1);
        }
        return new DispatchPlan<>(type, version + 1, new ArrayList<>(ordered));
    }
    @SuppressWarnings("unchecked") private static <E> NodeDefinition<E> cast(NodeDefinition<?> node) { return (NodeDefinition<E>) node; }
    private static void addEdge(NodeRef<?> from, NodeRef<?> to, Map<NodeRef<?>, Set<NodeRef<?>>> edges, Map<NodeRef<?>, Integer> indegree, Set<NodeRef<?>> all) {
        if (!all.contains(from) || !all.contains(to)) throw new OrchestrationException("Missing node dependency: " + from.key());
        if (edges.get(from).add(to)) indegree.computeIfPresent(to, (k, v) -> v + 1);
    }
    public synchronized <E> E dispatch(E event) {
        @SuppressWarnings("unchecked") DispatchPlan<E> plan = (DispatchPlan<E>) plans.get(event.getClass());
        if (plan == null) return event;
        dispatching = true;
        FrameTrace trace = new FrameTrace();
        try { for (NodeRef<E> ref : plan.nodes()) { Module owner = modules.get(ref.owner()); if (owner != null && !owner.isEnabled()) continue; NodeDefinition<E> node = cast((NodeDefinition<?>) ref); if (node.handler() != null) { trace.record(ref.owner().value() + "." + ref.key().value()); node.handler().handle(event, context(trace)); } if (event instanceof Cancellable c && c.isCancelled()) break; } }
        finally { dispatching = false; }
        return event;
    }
    @SuppressWarnings("unchecked") public <E> DispatchPlan<E> plan(Class<E> type) { return (DispatchPlan<E>) plans.get(type); }
    public boolean isDispatching() { return dispatching; }
    private ModuleContext context(FrameTrace trace) { return new ModuleContext() {
        public <T> Optional<T> capability(Class<T> type) { return capabilities.get(type); }
        public <T> Optional<T> capability(CapabilityKey<T> key) { return capabilities.get(key); }
        public <T> void publish(T value) {}
        public <T> List<T> values(Class<T> type) { return List.of(); }
        public FrameTrace trace() { return trace; }
    }; }
}
