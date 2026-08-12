package com.github.epsilon.managers;

import com.github.epsilon.assets.i18n.TranslateComponent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class TranslationManager {

    public static TranslationManager INSTANCE = new TranslationManager();

    private final List<TranslateComponent> components = new CopyOnWriteArrayList<>();
    private final AtomicLong revision = new AtomicLong();

    private TranslationManager() {
    }

    public void refresh() {
        for (TranslateComponent component : components) {
            component.refresh();
        }
        revision.incrementAndGet();
    }

    public void registerTranslateComponent(TranslateComponent component) {
        components.add(component);
    }

    public long getRevision() {
        return revision.get();
    }

}
