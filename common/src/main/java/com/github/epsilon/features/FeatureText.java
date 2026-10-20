package com.github.epsilon.features;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import net.minecraft.network.chat.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FeatureText {
    private static final Map<String, TranslateComponent> TEXT = new ConcurrentHashMap<>();
    private FeatureText() {}
    public static String get(String key) {
        return TEXT.computeIfAbsent(key, k -> EpsilonTranslateComponent.create("features", k)).getTranslatedName();
    }
    public static Component component(String key) { return Component.literal(get(key)); }
}
