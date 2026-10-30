package com.github.epsilon.events.bus.listeners;

import com.github.epsilon.events.bus.EventHandler;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Default implementation of a {@link IListener} that creates a lambda at runtime to call the target method.
 */
public class LambdaListener implements IListener {

    /**
     * 按事件处理类缓存 private lookup：同一类型的多个实例反复订阅时（例如切换旋转模式重建 RotationManager）不必重复提升访问权限。
     */
    private static final Map<Class<?>, MethodHandles.Lookup> LOOKUPS = new ConcurrentHashMap<>();

    private final Class<?> target;
    private final boolean isStatic;
    private final int priority;
    private Consumer<Object> executor;

    /**
     * Creates a new lambda listener, can be used for both static and non-static methods.
     *
     * @param klass  Class of the object
     * @param object Object, null if static
     * @param method Method to create lambda for
     */
    @SuppressWarnings("unchecked")
    public LambdaListener(Class<?> klass, Object object, Method method) {
        this.target = method.getParameters()[0].getType();
        this.isStatic = Modifier.isStatic(method.getModifiers());
        this.priority = method.getAnnotation(EventHandler.class).priority();

        try {
            String name = method.getName();
            MethodHandles.Lookup lookup = lookupFor(klass);

            MethodType methodType = MethodType.methodType(void.class, method.getParameters()[0].getType());

            MethodHandle methodHandle;
            MethodType invokedType;

            if (isStatic) {
                methodHandle = lookup.findStatic(klass, name, methodType);
                invokedType = MethodType.methodType(Consumer.class);
            } else {
                methodHandle = lookup.findVirtual(klass, name, methodType);
                invokedType = MethodType.methodType(Consumer.class, klass);
            }

            MethodHandle lambdaFactory = LambdaMetafactory.metafactory(lookup, "accept", invokedType, MethodType.methodType(void.class, Object.class), methodHandle, methodType).getTarget();

            if (isStatic) this.executor = (Consumer<Object>) lambdaFactory.invoke();
            else this.executor = (Consumer<Object>) lambdaFactory.invoke(object);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Failed to create event listener for " + klass.getName() + "#" + method.getName(), throwable);
        }
    }

    @Override
    public void call(Object event) {
        executor.accept(event);
    }

    @Override
    public Class<?> getTarget() {
        return target;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    /**
     * 直接为事件处理类取得带 private 访问权限的 lookup，监听类所在包不再需要预先注册。
     */
    private static MethodHandles.Lookup lookupFor(Class<?> klass) throws IllegalAccessException {
        MethodHandles.Lookup lookup = LOOKUPS.get(klass);
        if (lookup == null) {
            lookup = MethodHandles.privateLookupIn(klass, MethodHandles.lookup());
            LOOKUPS.put(klass, lookup);
        }
        return lookup;
    }

}
