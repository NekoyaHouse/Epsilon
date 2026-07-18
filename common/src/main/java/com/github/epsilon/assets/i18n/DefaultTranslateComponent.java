package com.github.epsilon.assets.i18n;

import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.modules.impl.ClientSetting;

public class DefaultTranslateComponent implements TranslateComponent {

    private final String fullKey;
    private String cachedName;

    private DefaultTranslateComponent(String fullKey) {
        this.fullKey = fullKey;
    }

    public static DefaultTranslateComponent create(String fullKey) {
        DefaultTranslateComponent component = new DefaultTranslateComponent(fullKey);
        TranslateHolder.INSTANCE.registerTranslateComponent(component);
        return component;
    }

    @Override
    public String getFullKey() {
        return fullKey;
    }

    @Override
    public String getTranslatedName() {
        if (cachedName == null) {
            cachedName = resolveTranslation(fullKey);
        }
        return cachedName;
    }

    @Override
    public void refresh() {
        cachedName = resolveTranslation(fullKey);
    }

    private static String resolveTranslation(String key) {
        if (EpsilonLanguageManager.INSTANCE.has(key)) {
            return EpsilonLanguageManager.INSTANCE.getOrDefault(key);
        }
        return ClientSetting.INSTANCE.i18nFallback.getValue() ? formatKey(key) : key;
    }

    private static String formatKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        String lastPart = key;
        int lastDotIndex = key.lastIndexOf('.');
        if (lastDotIndex != -1) {
            lastPart = key.substring(lastDotIndex + 1);
        }

        StringBuilder result = new StringBuilder();
        String[] words = lastPart.split(" ");

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1));

                if (i < words.length - 1) {
                    result.append(" ");
                }
            }
        }

        return result.toString();
    }

    @Override
    public TranslateComponent createChild(String suffix) {
        return DefaultTranslateComponent.create(fullKey + "." + suffix);
    }

}
