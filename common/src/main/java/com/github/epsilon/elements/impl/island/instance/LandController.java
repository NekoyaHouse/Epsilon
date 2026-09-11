package com.github.epsilon.elements.impl.island.instance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * 岛屿实例的注册表，负责回收已关闭实例并选出当前应当展示的那一个。
 */
public class LandController {

    public static LandController INSTANCE = new LandController();

    private final List<LandInstance> instances = new ArrayList<>();

    public void post(LandInstance instance) {
        if (instance == null) {
            return;
        }
        instances.add(instance);
    }

    /**
     * 移除已关闭实例，并返回优先级最高的实例。
     *
     * @return 当前应展示的实例，没有实例时返回 {@code null}
     */
    public LandInstance update() {
        Iterator<LandInstance> iterator = instances.iterator();
        while (iterator.hasNext()) {
            LandInstance instance = iterator.next();
            if (instance.isClosed()) {
                iterator.remove();
                instance.onRemoved();
            }
        }

        if (instances.isEmpty()) return null;

        Comparator<LandInstance> comparator = Comparator.comparingInt(LandInstance::getPriority);

        LandInstance best = null;
        for (int i = instances.size() - 1; i >= 0; i--) {
            LandInstance current = instances.get(i);
            if (best == null || comparator.compare(current, best) > 0) {
                best = current;
            }
        }

        return best;
    }

    public void removeInstances(Class<? extends LandInstance> type) {
        if (type == null) return;
        Iterator<LandInstance> iterator = instances.iterator();
        while (iterator.hasNext()) {
            LandInstance instance = iterator.next();
            if (type.isInstance(instance)) {
                iterator.remove();
                instance.onRemoved();
            }
        }
    }

    public <T extends LandInstance> T getInstance(Class<T> type) {
        if (type == null) return null;
        for (LandInstance instance : instances) {
            if (type.isInstance(instance)) {
                return type.cast(instance);
            }
        }
        return null;
    }

}
