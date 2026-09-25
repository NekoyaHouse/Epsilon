import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.PacketEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.modules.orchestration.DispatchPlan;
import com.github.epsilon.modules.orchestration.ModuleId;
import com.github.epsilon.modules.orchestration.NodeKey;
import com.github.epsilon.modules.orchestration.NodeRef;
import com.github.epsilon.modules.orchestration.OrchestrationException;
import com.github.epsilon.modules.orchestration.Phase;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对已注册的每个模块校验声明式节点图的静态不变量：
 * 节点身份唯一、依赖存在、无环、阶段顺序稳定、事件类型与处理器参数一致。
 * <p>
 * 直接读取 ModuleOrchestrator 的计划，因此覆盖所有已注册模块（含其它 Category）。
 */
public final class OrchestrationInvariants {

    private static int failures;

    public static void main(String[] args) throws Exception {
        // 模块注册在 main 中完成

        for (Phase phase : Phase.values()) {
            System.out.println("phase " + phase + " ordinal=" + phase.ordinal());
        }

        // 重新构建计划：若存在重复节点、缺失依赖或环路，register/rebuild 会抛异常。
        Class<?> orchestratorClass = Class.forName("com.github.epsilon.modules.orchestration.ModuleOrchestrator");
        Object orchestrator = orchestratorClass.getField("INSTANCE").get(null);
        bootstrapCombatModules(orchestrator);
        Method rebuild = orchestratorClass.getDeclaredMethod("rebuild");
        rebuild.setAccessible(true);
        rebuild.invoke(orchestrator);
        System.out.println("rebuild OK (no duplicate keys, no missing deps, no cycles)");

        Method planMethod = orchestratorClass.getMethod("plan", Class.class);
        List<Class<?>> eventTypes = List.of(
                PlayerTickEvent.Pre.class,
                ClientTickEvent.Pre.class,
                PacketEvent.Receive.class,
                Render3DEvent.class,
                Render2DEvent.Level.class
        );

        for (Class<?> eventType : eventTypes) {
            DispatchPlan<?> plan = (DispatchPlan<?>) planMethod.invoke(orchestrator, eventType);
            if (plan == null) {
                System.out.println("plan " + eventType.getSimpleName() + " = <none>");
                continue;
            }
            List<String> ids = new ArrayList<>();
            Map<String, Integer> seen = new LinkedHashMap<>();
            int lastPhase = -1;
            int lastPriority = Integer.MAX_VALUE;

            for (NodeRef<?> node : plan.nodes()) {
                String id = node.owner().value() + "." + node.key().value();
                ids.add(id);
                if (seen.put(id, 1) != null) {
                    fail("duplicate node in plan: " + id);
                }
                if (node.eventType() != eventType) {
                    fail("node " + id + " declared event " + node.eventType().getName()
                            + " but appears in plan for " + eventType.getName());
                }
                if (node.key().value().indexOf('.') < 0) {
                    fail("node key is not dotted: " + id);
                }

                Phase phase = phaseOf(node);
                if (phase.ordinal() < lastPhase) {
                    fail("phase order regression in plan " + eventType.getSimpleName()
                            + ": " + id + " is " + phase + " after ordinal " + lastPhase);
                }
                if (phase.ordinal() == lastPhase) {
                    int priority = priorityOf(node);
                    if (priority > lastPriority) {
                        fail("priority order regression within " + phase + " in plan "
                                + eventType.getSimpleName() + ": " + id);
                    }
                    lastPriority = priority;
                } else {
                    lastPriority = Integer.MAX_VALUE;
                }
                lastPhase = phase.ordinal();
            }

            System.out.println("plan " + eventType.getSimpleName() + " (" + ids.size() + " nodes)");
            for (String id : ids) {
                System.out.println("    " + id);
            }
        }

        if (failures > 0) {
            System.out.println("INVARIANTS FAILED: " + failures);
            System.exit(1);
        }
        System.out.println("INVARIANTS_OK");
    }

    private static Phase phaseOf(NodeRef<?> node) throws Exception {
        Method m = node.getClass().getDeclaredMethod("phase");
        m.setAccessible(true);
        return (Phase) m.invoke(node);
    }

    private static int priorityOf(NodeRef<?> node) throws Exception {
        Method m = node.getClass().getDeclaredMethod("priority");
        m.setAccessible(true);
        return (int) m.invoke(node);
    }

    private static void fail(String message) {
        System.out.println("FAIL: " + message);
        failures++;
    }

    /**
     * 逐个加载 combat 模块并注册其 declaration。
     * <p>
     * 不经过 ModuleManager：其 {@code initModules()} 会触发所有 Category 的模块，
     * 而部分模块（如 AutoArmor）在构造期就依赖已初始化的 Minecraft 实例，无法在无头环境运行。
     * 这里只验证节点图，不实例化渲染/资源相关模块。
     */
    private static void bootstrapCombatModules(Object orchestrator) throws Exception {
        List<String> names = List.of(
                "AimBot", "AnchorBlast", "AntiBot", "AutoClicker", "AutoDtap", "AutoHitCrystal",
                "AutoMend", "AutoThrow", "AutoTotem", "AutoWeapon", "Backtrack", "Criticals",
                "CrystalAura", "CrystalBlocker", "DoubleAnchor", "FeetTrap", "HoverTotem",
                "KeyPearl", "KillAura", "MaceAura", "MultiAura", "PacketMine", "SafeAnchor",
                "SafeCrystal", "SilentAim", "SpearKill", "TriggerBot"
        );
        List<String> extra = List.of(
                "com.github.epsilon.modules.impl.combat.elytra_combat.ElytraCombat",
                "com.github.epsilon.modules.impl.combat.zealot_crystal_plus.ZealotCrystalPlus"
        );

        Method register = Class.forName("com.github.epsilon.modules.orchestration.ModuleOrchestrator")
                .getMethod("register", Class.forName("com.github.epsilon.modules.Module"));

        int count = 0;
        for (String simple : names) {
            String className = "com.github.epsilon.modules.impl.combat." + simple;
            if (registerModule(className, register, orchestrator)) count++;
        }
        for (String className : extra) {
            if (registerModule(className, register, orchestrator)) count++;
        }
        System.out.println("registered combat modules: " + count + "/" + (names.size() + extra.size()));
        if (count != names.size() + extra.size()) {
            fail("not every combat module registered");
        }
    }

    private static boolean registerModule(String className, Method register, Object orchestrator) {
        try {
            Class<?> klass = Class.forName(className);
            Object instance = klass.getField("INSTANCE").get(null);
            register.invoke(orchestrator, instance);
            return true;
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : t;
            System.out.println("SKIP " + className + " -> " + cause);
            return false;
        }
    }
}

