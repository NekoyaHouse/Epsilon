package com.github.epsilon.modules.orchestration;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.modules.Module;
import java.lang.reflect.Method;

public final class LegacyAdapter {
    private LegacyAdapter() {}

    public static void adapt(Module module) {
        module.setDispatchModeForAdapter();
        EventBus.INSTANCE.unsubscribe(module);
        for (Class<?> type = module.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                EventHandler annotation = method.getAnnotation(EventHandler.class);
                if (annotation == null || method.getParameterCount() != 1 || method.getReturnType() != void.class) continue;
                Class<?> eventType = method.getParameterTypes()[0];
                NodeKey key = NodeKey.of("legacy." + module.moduleId().value() + "." + method.getName() + "." + eventType.getName().replace('.', '_'));
                NodeBuilder<Object> builder = module.nodeUnchecked(eventType, key).phase(phaseFor(eventType)).priority(annotation.priority());
                builder.handler((event, context) -> {
                    try {
                        method.setAccessible(true);
                        method.invoke(module, event);
                    } catch (ReflectiveOperationException e) {
                        throw new OrchestrationException("Legacy handler failed: " + method, e);
                    }
                });
            }
        }
        ModuleOrchestrator.INSTANCE.register(module);
    }

    private static Phase phaseFor(Class<?> eventType) {
        String name = eventType.getSimpleName();
        if (name.contains("Render")) return Phase.RENDER;
        if (name.contains("Destroy") || name.contains("Attack") || name.contains("UseItem")) return Phase.COMMIT;
        if (name.contains("Move") || name.contains("Rotation") || name.contains("Strafe")) return Phase.TRANSFORM;
        if (name.contains("Post") || name.contains("Left") || name.contains("Respawn")) return Phase.CLEANUP;
        return Phase.OBSERVE;
    }
}
