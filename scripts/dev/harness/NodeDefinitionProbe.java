import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.orchestration.*;

final class NodeDefinitionProbe {
    private NodeDefinitionProbe() {}

    /** 声明一个依赖不存在节点的节点，用于验证缺失依赖诊断。 */
    @SuppressWarnings("unchecked")
    static void missing(ModuleDeclaration declaration) throws OrchestrationException {
        NodeRef<PlayerTickEvent.Pre> ghost = (NodeRef<PlayerTickEvent.Pre>) (NodeRef<?>) new GhostRef();
        declaration.node(PlayerTickEvent.Pre.class, NodeKey.of("decide.ghost"))
                .phase(Phase.DECIDE).after(ghost).handler((e, c) -> {});
        ModuleOrchestrator.INSTANCE.register(declaration);
    }

    private record GhostRef() implements NodeRef<PlayerTickEvent.Pre> {
        @Override public NodeKey key() { return NodeKey.of("ghost.missing"); }
        @Override public Class<PlayerTickEvent.Pre> eventType() { return PlayerTickEvent.Pre.class; }
        @Override public ModuleId owner() { return ModuleId.of("ghost"); }
    }
}
