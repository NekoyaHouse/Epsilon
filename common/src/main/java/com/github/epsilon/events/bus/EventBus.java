package com.github.epsilon.events.bus;

import com.github.epsilon.events.bus.listeners.IListener;
import com.github.epsilon.events.bus.listeners.LambdaListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class EventBus {

    public static final EventBus INSTANCE = new EventBus();

    private final Map<Object, List<IListener>> listenerCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<IListener>> staticListenerCache = new ConcurrentHashMap<>();

    private final Map<Class<?>, List<IListener>> listenerMap = new ConcurrentHashMap<>();

    private final List<LambdaFactoryInfo> lambdaFactoryInfos = new ArrayList<>();

    public void registerLambdaFactory(String packagePrefix, LambdaListener.Factory factory) {
        synchronized (lambdaFactoryInfos) {
            lambdaFactoryInfos.add(new LambdaFactoryInfo(packagePrefix, factory));
        }
    }

    public boolean isListening(Class<?> eventKlass) {
        List<IListener> listeners = listenerMap.get(eventKlass);
        return listeners != null && !listeners.isEmpty();
    }

    public <T> T post(T event) {
        List<IListener> listeners = listenerMap.get(event.getClass());

        if (listeners != null) {
            for (IListener listener : listeners) {
                listener.call(event);
                if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                    break;
                }
            }
        }

        return event;
    }

    public void subscribe(Object object) {
        subscribe(getListeners(object.getClass(), object), false);
    }

    public void subscribe(Class<?> klass) {
        subscribe(getListeners(klass, null), true);
    }

    public void subscribe(IListener listener) {
        subscribe(listener, false);
    }

    private void subscribe(List<IListener> listeners, boolean onlyStatic) {
        for (IListener listener : listeners) {
            subscribe(listener, onlyStatic);
        }
    }

    private void subscribe(IListener listener, boolean onlyStatic) {
        if (onlyStatic) {
            if (listener.isStatic()) {
                insert(listenerMap.computeIfAbsent(listener.getTarget(), aClass -> new CopyOnWriteArrayList<>()), listener);
            }
        } else {
            insert(listenerMap.computeIfAbsent(listener.getTarget(), aClass -> new CopyOnWriteArrayList<>()), listener);
        }
    }

    private void insert(List<IListener> listeners, IListener listener) {
        int i = 0;
        for (; i < listeners.size(); i++) {
            if (listener.getPriority() > listeners.get(i).getPriority()) {
                break;
            }
        }

        listeners.add(i, listener);
    }

    public void unsubscribe(Object object) {
        unsubscribe(getListeners(object.getClass(), object), false);
    }

    public void unsubscribe(Class<?> klass) {
        unsubscribe(getListeners(klass, null), true);
    }

    public void unsubscribe(IListener listener) {
        unsubscribe(listener, false);
    }

    private void unsubscribe(List<IListener> listeners, boolean staticOnly) {
        for (IListener listener : listeners) {
            unsubscribe(listener, staticOnly);
        }
    }

    private void unsubscribe(IListener listener, boolean staticOnly) {
        List<IListener> l = listenerMap.get(listener.getTarget());

        if (l != null) {
            if (staticOnly) {
                if (listener.isStatic()) {
                    l.remove(listener);
                }
            } else {
                l.remove(listener);
            }
        }
    }

    private List<IListener> getListeners(Class<?> klass, Object object) {
        Function<Object, List<IListener>> func = o -> {
            List<IListener> listeners = new CopyOnWriteArrayList<>();

            getListeners(listeners, klass, object);

            return listeners;
        };

        if (object == null) return staticListenerCache.computeIfAbsent(klass, func);

        // We need to check if the instances are the same and avoid using .equals() and .hashCode()
        for (Object key : listenerCache.keySet()) {
            if (key == object) {
                return listenerCache.get(object);
            }
        }

        List<IListener> listeners = func.apply(object);
        listenerCache.put(object, listeners);
        return listeners;
    }

    private void getListeners(List<IListener> listeners, Class<?> klass, Object object) {
        for (Method method : klass.getDeclaredMethods()) {
            if (isValid(method)) {
                listeners.add(new LambdaListener(getLambdaFactory(klass), klass, object, method));
            }
        }

        if (klass.getSuperclass() != null) getListeners(listeners, klass.getSuperclass(), object);
    }

    private boolean isValid(Method method) {
        if (!method.isAnnotationPresent(EventHandler.class)) return false;
        if (method.getReturnType() != void.class) return false;
        if (method.getParameterCount() != 1) return false;
        return !method.getParameters()[0].getType().isPrimitive();
    }

    private LambdaListener.Factory getLambdaFactory(Class<?> klass) {
        synchronized (lambdaFactoryInfos) {
            for (LambdaFactoryInfo info : lambdaFactoryInfos) {
                if (klass.getName().startsWith(info.packagePrefix)) return info.factory;
            }
        }

        throw new RuntimeException("No registered lambda listener for '" + klass.getName() + "'.");
    }

    private record LambdaFactoryInfo(String packagePrefix, LambdaListener.Factory factory) {
    }

}
