package com.github.epsilon.assets.i18n;

import com.github.epsilon.Constants;
import com.github.epsilon.holders.TextureCacheHolder;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.modules.impl.ClientSetting;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class EpsilonLanguageManager {

    public static final EpsilonLanguageManager INSTANCE = new EpsilonLanguageManager();

    private static final Gson GSON = new Gson();
    private static final Pattern UNSUPPORTED_FORMAT_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");
    private static final String DEFAULT_LANGUAGE_CODE = "en_us";
    private static final String LANGUAGE_DIRECTORY = "i18n";

    private volatile Map<String, String> translations = Map.of();
    private volatile EpsilonLanguage selectedLanguage = EpsilonLanguage.English;

    private EpsilonLanguageManager() {
    }

    public void selectLanguage(EpsilonLanguage language) {
        if (language == null) {
            language = EpsilonLanguage.English;
        }
        selectedLanguage = language;
        reload(Constants.mc.getResourceManager());
    }

    public void refreshCustomLanguage() {
        if (selectedLanguage.isCustom()) {
            reload(Constants.mc.getResourceManager());
        }
    }

    public EpsilonLanguage getSelectedLanguage() {
        return selectedLanguage;
    }

    public synchronized void reload(ResourceManager resourceManager) {
        Map<String, String> loadedTranslations = new HashMap<>();
        appendLanguage(resourceManager, DEFAULT_LANGUAGE_CODE, loadedTranslations);

        String selectedCode = resolveSelectedLanguageCode();
        if (!selectedCode.isBlank() && !DEFAULT_LANGUAGE_CODE.equals(selectedCode)) {
            appendLanguage(resourceManager, selectedCode, loadedTranslations);
        }

        translations = Map.copyOf(loadedTranslations);
        refreshUi();
    }

    public String getOrDefault(String key) {
        return translations.getOrDefault(key, key);
    }

    public boolean has(String key) {
        return translations.containsKey(key);
    }

    private void appendLanguage(ResourceManager resourceManager, String languageCode, Map<String, String> output) {
        if (resourceManager == null) {
            return;
        }

        String path = String.format(Locale.ROOT, "%s/%s.json", LANGUAGE_DIRECTORY, languageCode);
        for (String namespace : resourceManager.getNamespaces()) {
            try {
                Identifier location = Identifier.fromNamespaceAndPath(namespace, path);
                appendResources(location, resourceManager.getResourceStack(location), output);
            } catch (Exception exception) {
                Constants.LOGGER.warn("跳过 Epsilon 语言文件: {}:{} ({})", namespace, path, exception.toString());
            }
        }
    }

    private void appendResources(Identifier location, List<Resource> resources, Map<String, String> output) {
        for (Resource resource : resources) {
            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                JsonObject entries = GSON.fromJson(reader, JsonObject.class);
                if (entries == null) {
                    continue;
                }
                I18NJson.read(entries, (key, value) -> {
                    String text = GsonHelper.convertToString(value, key);
                    output.put(key, UNSUPPORTED_FORMAT_PATTERN.matcher(text).replaceAll("%$1s"));
                });
            } catch (IOException | JsonParseException exception) {
                Constants.LOGGER.warn("读取 Epsilon 语言文件失败: {} from pack {} ({})", location, resource.sourcePackId(), exception.toString());
            }
        }
    }

    private void refreshUi() {
        TranslateHolder.INSTANCE.refresh();
        TextureCacheHolder.INSTANCE.clearCache();
    }

    private String resolveSelectedLanguageCode() {
        if (!selectedLanguage.isCustom()) {
            return selectedLanguage.getCode();
        }

        try {
            return ClientSetting.INSTANCE.customLanguage.getValue()
                    .trim()
                    .toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }
}
