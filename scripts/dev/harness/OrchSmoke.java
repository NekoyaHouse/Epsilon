import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.modules.orchestration.*;
import java.util.ArrayList;
import java.util.List;

public final class OrchSmoke {

    static final ModuleId A = ModuleId.of("alpha");
    static final ModuleId B = ModuleId.of("beta");

    public static void main(String[] args) {
        ModuleDeclaration first = new ModuleDeclaration(A);
        NodeRef<PlayerTickEvent.Pre> observe = first.node(PlayerTickEvent.Pre.class, NodeKey.of("observe.snapshot"))
                .phase(Phase.OBSERVE).priority(100).handler((event, ctx) -> {});
        NodeRef<PlayerTickEvent.Pre> decide = first.node(PlayerTickEvent.Pre.class, NodeKey.of("decide.plan"))
                .phase(Phase.DECIDE).after(observe).handler((event, ctx) -> {});
        NodeRef<PlayerTickEvent.Pre> commit = first.node(PlayerTickEvent.Pre.class, NodeKey.of("commit.action"))
                .phase(Phase.COMMIT).after(decide).handler((event, ctx) -> {});

        // 跨模块依赖：beta 的节点显式排在 alpha 的 commit 之后，且属于更早的阶段。
        ModuleDeclaration second = new ModuleDeclaration(B);
        NodeRef<PlayerTickEvent.Pre> cross = second.node(PlayerTickEvent.Pre.class, NodeKey.of("observe.cross"))
                .phase(Phase.OBSERVE).after(commit).handler((event, ctx) -> {});

        ModuleOrchestrator.INSTANCE.register(first);
        ModuleOrchestrator.INSTANCE.register(second);

        DispatchPlan<PlayerTickEvent.Pre> plan = ModuleOrchestrator.INSTANCE.plan(PlayerTickEvent.Pre.class);
        List<String> actual = new ArrayList<>();
        for (NodeRef<PlayerTickEvent.Pre> ref : plan.nodes()) {
            actual.add(ref.owner().value() + "." + ref.key().value());
        }
        System.out.println("plan=" + actual);

        List<String> expect = List.of("alpha.observe.snapshot", "alpha.decide.plan", "alpha.commit.action", "beta.observe.cross");
        if (!actual.equals(expect)) {
            System.out.println("FAIL: expected " + expect);
            System.exit(1);
        }

        // 阶段优先于 priority：低阶段的高 priority 节点仍然先执行。
        ModuleDeclaration third = new ModuleDeclaration(ModuleId.of("gamma"));
        third.node(PlayerTickEvent.Pre.class, NodeKey.of("commit.early")).phase(Phase.COMMIT).priority(1000).handler((e, c) -> {});
        third.node(PlayerTickEvent.Pre.class, NodeKey.of("observe.late")).phase(Phase.OBSERVE).priority(-1000).handler((e, c) -> {});
        ModuleOrchestrator.INSTANCE.register(third);
        DispatchPlan<PlayerTickEvent.Pre> plan2 = ModuleOrchestrator.INSTANCE.plan(PlayerTickEvent.Pre.class);
        List<String> gamma = new ArrayList<>();
        for (NodeRef<PlayerTickEvent.Pre> ref : plan2.nodes()) {
            if (ref.owner().value().equals("gamma")) gamma.add(ref.key().value());
        }
        System.out.println("gamma=" + gamma);
        if (!gamma.equals(List.of("observe.late", "commit.early"))) {
            System.out.println("FAIL: phase must outrank priority");
            System.exit(1);
        }

        // node(key) 默认事件类型必须是 PlayerTickEvent.Pre。
        ModuleDeclaration fourth = new ModuleDeclaration(ModuleId.of("delta"));
        NodeRef<?> defaulted = fourth.node(NodeKey.of("observe.default")).handler((e, c) -> {});
        System.out.println("defaultEventType=" + defaulted.eventType().getName());
        if (defaulted.eventType() != PlayerTickEvent.Pre.class) {
            System.out.println("FAIL: default event type");
            System.exit(1);
        }

        // 重复 NodeKey 必须产生诊断。
        try {
            ModuleDeclaration dup = new ModuleDeclaration(ModuleId.of("dup"));
            dup.node(PlayerTickEvent.Pre.class, NodeKey.of("observe.x")).handler((e, c) -> {});
            dup.node(PlayerTickEvent.Pre.class, NodeKey.of("observe.x")).handler((e, c) -> {});
            ModuleOrchestrator.INSTANCE.register(dup);
            System.out.println("FAIL: duplicate key accepted");
            System.exit(1);
        } catch (OrchestrationException expected) {
            System.out.println("duplicateKey=" + expected.getMessage());
        }

        // 缺失依赖必须产生诊断。
        try {
            ModuleDeclaration missing = new ModuleDeclaration(ModuleId.of("missing"));
            NodeDefinitionProbe.missing(missing);
            System.out.println("FAIL: missing dependency accepted");
            System.exit(1);
        } catch (OrchestrationException expected) {
            System.out.println("missingDep=" + expected.getMessage());
        }

        System.out.println("SMOKE_OK");
    }
}
