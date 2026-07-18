package com.github.epsilon.holders;

import com.github.epsilon.Constants;
import com.github.epsilon.addon.EpsilonAddon;
import com.github.epsilon.assets.config.LegacyConfigMigrator;
import com.github.epsilon.elements.HudModule;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.settings.Setting;
import com.github.epsilon.settings.impl.*;
import com.google.gson.*;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ConfigHolder {

    private static final int CONFIG_VERSION = 3;
    private static final String DEFAULT_CONFIG_NAME = "default";
    private static final String CONFIGS_FOLDER = "configs";
    private static final String IMPORTS_FOLDER = "imports";
    private static final String EXPORTS_FOLDER = "exports";
    private static final String FRIENDS_FILE_NAME = "friends.json";
    private static final String ADDON_SETTINGS_FILE_NAME = "addon-settings.json";
    private static final String ACTIVE_CONFIG_FILE_NAME = "active-config.txt";
    private static final String ROOT_SETTINGS_FILE_NAME = "client-settings.json";
    private static final String EXPORT_METADATA_FILE_NAME = "config-info.json";
    private static final Pattern INVALID_CONFIG_NAME_PATTERN = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private static final Path configDir = Paths.get(System.getProperty("user.home"), ".epsilon");
    private static final Path configsDir = configDir.resolve(CONFIGS_FOLDER);
    private static final Path importsDir = configDir.resolve(IMPORTS_FOLDER);
    private static final Path exportsDir = configDir.resolve(EXPORTS_FOLDER);
    private static final Path activeConfigFile = configDir.resolve(ACTIVE_CONFIG_FILE_NAME);
    private static final Path rootSettingsFile = configDir.resolve(ROOT_SETTINGS_FILE_NAME);
    private static final Path legacyFriendFile = configDir.resolve(FRIENDS_FILE_NAME);

    public static final ConfigHolder INSTANCE = new ConfigHolder();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long CONFIG_LIST_CACHE_TTL_MS = 1000L;

    private String activeConfigName = DEFAULT_CONFIG_NAME;
    private boolean rootDirectoriesEnsured = false;
    private List<String> cachedConfigNames = List.of();
    private long configListCacheExpiresAt;
    private boolean configListCacheDirty = true;

    private ConfigHolder() {
    }

    public void initConfig() {
        try {
            ensureRootDirectories();
            migrateLegacyLayoutsIfNeeded(getConfigurableModules());
            activeConfigName = resolveStoredActiveConfigName();
            ensureConfigExists(activeConfigName);
            loadActiveConfigSnapshot();
        } catch (Exception e) {
            Constants.LOGGER.error("初始化配置失败", e);
        }
    }

    public synchronized Path getConfigDir() {
        return configDir;
    }

    public synchronized Path getConfigsDir() {
        return configsDir;
    }

    public synchronized Path getImportDir() {
        return importsDir;
    }

    public synchronized Path getExportDir() {
        return exportsDir;
    }

    public synchronized Path getActiveConfigStorageDir() {
        return getConfigStorageDir(activeConfigName);
    }

    public synchronized String getActiveConfigName() {
        return activeConfigName;
    }

    public synchronized List<String> listConfigs() {
        try {
            return listConfigsCached(true);
        } catch (IOException e) {
            Constants.LOGGER.error("列出配置失败", e);
            return List.of(activeConfigName);
        }
    }

    public synchronized void reload() {
        try {
            reloadOrThrow();
        } catch (Exception e) {
            Constants.LOGGER.error("重载配置失败", e);
        }
    }

    public synchronized void reloadOrThrow() throws IOException {
        loadActiveConfigSnapshot();
    }

    public synchronized void applyToModules(List<Module> modules) {
        if (modules == null) return;
        for (Module module : modules) {
            if (module != null) applyModuleFromDisk(module, getActiveConfigStorageDir());
        }
    }

    public synchronized void saveNow() {
        try {
            saveActiveConfigSnapshot();
        } catch (IOException e) {
            Constants.LOGGER.error("保存配置失败", e);
        }
    }

    public synchronized String saveAsConfig(String rawName) throws IOException {
        String configName = normalizeAndValidateConfigName(rawName);
        ensureRootDirectories();
        saveActiveConfigSnapshot();

        Path sourceDir = getActiveConfigStorageDir();
        Path targetDir = getConfigStorageDir(configName);
        if (!sourceDir.equals(targetDir)) {
            deleteDirectory(targetDir);
            copyDirectory(sourceDir, targetDir, false);
        }

        activeConfigName = configName;
        writeActiveConfigName(configName);
        invalidateConfigListCache();
        loadActiveConfigSnapshot();
        return configName;
    }

    public synchronized String newDefaultConfig(String rawName) throws IOException {
        String configName = normalizeAndValidateConfigName(rawName);
        ensureRootDirectories();
        Files.createDirectories(getConfigStorageDir(configName));
        invalidateConfigListCache();
        switchConfig(configName);
        return configName;
    }

    public synchronized void switchConfig(String rawName) throws IOException {
        String configName = normalizeAndValidateConfigName(rawName);
        ensureRootDirectories();
        if (Objects.equals(configName, activeConfigName)) {
            loadActiveConfigSnapshot();
            return;
        }

        saveActiveConfigSnapshot();
        ensureConfigExists(configName);
        activeConfigName = configName;
        writeActiveConfigName(configName);
        invalidateConfigListCache();
        loadActiveConfigSnapshot();
    }

    public synchronized boolean deleteConfig(String rawName) throws IOException {
        String configName = normalizeAndValidateConfigName(rawName);
        Path targetDir = getConfigStorageDir(configName);
        if (!Files.exists(targetDir)) {
            return false;
        }

        List<String> configs = listConfigsInternal(true);
        if (configs.size() <= 1 && configs.contains(configName)) {
            return false;
        }

        if (Objects.equals(activeConfigName, configName)) {
            String fallback = configs.stream()
                    .filter(name -> !Objects.equals(name, configName))
                    .findFirst()
                    .orElse(DEFAULT_CONFIG_NAME);
            activeConfigName = fallback;
            ensureConfigExists(fallback);
            writeActiveConfigName(fallback);
            loadActiveConfigSnapshot();
        }

        deleteDirectory(targetDir);
        invalidateConfigListCache();
        return true;
    }

    public synchronized Path exportActiveConfigToZip(String rawPath) throws IOException {
        return exportConfigToZip(activeConfigName, rawPath);
    }

    public synchronized Path exportConfigToZip(String rawName, String rawPath) throws IOException {
        String configName = normalizeAndValidateConfigName(rawName);
        ensureRootDirectories();
        if (Objects.equals(configName, activeConfigName)) {
            saveActiveConfigSnapshot();
        }

        Path sourceDir = getConfigStorageDir(configName);
        if (!Files.exists(sourceDir)) {
            throw new IOException("配置不存在: " + configName);
        }

        Path zipPath = resolveExportZipPath(rawPath, configName);
        Files.createDirectories(zipPath.getParent());

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE))) {
            String rootPrefix = configName + "/";
            writeStringZipEntry(zipOutputStream, rootPrefix + EXPORT_METADATA_FILE_NAME, gson.toJson(buildExportMetadata(configName)));

            try (Stream<Path> stream = Files.walk(sourceDir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String relative = sourceDir.relativize(file).toString().replace('\\', '/');
                    writeFileZipEntry(zipOutputStream, rootPrefix + relative, file);
                }
            }
        }

        return zipPath;
    }

    public synchronized String importConfigFromZip(String rawPath) throws IOException {
        Path zipPath = resolveImportZipPath(rawPath);
        return importConfigFromZip(zipPath, true);
    }

    public synchronized String importConfigFromZip(Path zipPath, boolean switchToImported) throws IOException {
        ensureRootDirectories();
        if (zipPath == null || !Files.exists(zipPath) || !Files.isRegularFile(zipPath)) {
            throw new IOException("Zip 文件不存在: " + zipPath);
        }

        Path tempDir = Files.createTempDirectory(configDir, "config-import-");
        try {
            unzipSecurely(zipPath, tempDir);
            Path configRoot = detectConfigRoot(tempDir);
            String importedName = buildImportedConfigName(configRoot, zipPath);
            Path targetDir = getConfigStorageDir(importedName);

            deleteDirectory(targetDir);
            copyDirectory(configRoot, targetDir, true);
            invalidateConfigListCache();

            if (switchToImported) {
                activeConfigName = importedName;
                writeActiveConfigName(importedName);
                loadActiveConfigSnapshot();
            }
            return importedName;
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private Path getModuleFile(Path configStorageDir, Module module) {
        String addonId = module.getAddonId() != null ? module.getAddonId() : "unknown";
        return configStorageDir.resolve(addonId).resolve(module.getName() + ".json");
    }

    private void applyModuleFromDisk(Module module, Path configStorageDir) {
        Path file = getModuleFile(configStorageDir, module);
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) return;
            applyModuleObject(module, parsed.getAsJsonObject());
        } catch (Exception e) {
            Constants.LOGGER.error("读取模块配置失败: {}", file, e);
        }
    }

    private void applyModuleObject(Module module, JsonObject moduleObj) {
        if (moduleObj.has("keyBind") && moduleObj.get("keyBind").isJsonPrimitive()) {
            try {
                module.setKeyBind(moduleObj.get("keyBind").getAsInt());
            } catch (Exception ignored) {
            }
        }

        if (moduleObj.has("bindMode") && moduleObj.get("bindMode").isJsonPrimitive()) {
            try {
                module.setBindMode(Module.BindMode.valueOf(moduleObj.get("bindMode").getAsString()));
            } catch (Exception ignored) {
            }
        }

        if (moduleObj.has("hidden") && moduleObj.get("hidden").isJsonPrimitive()) {
            try {
                module.setHidden(moduleObj.get("hidden").getAsBoolean());
            } catch (Exception ignored) {
            }
        }

        if (module instanceof HudModule hud) {
            HudModule.HorizontalAnchor horizontalAnchor = readHorizontalAnchor(moduleObj, "hudHorizontalAnchor");
            HudModule.VerticalAnchor verticalAnchor = readVerticalAnchor(moduleObj, "hudVerticalAnchor");
            Float anchorX = readFloat(moduleObj, "hudAnchorX");
            Float anchorY = readFloat(moduleObj, "hudAnchorY");

            if (horizontalAnchor != null && verticalAnchor != null && anchorX != null && anchorY != null) {
                hud.setAnchorState(horizontalAnchor, verticalAnchor, anchorX, anchorY);
            } else {
                Float renderX = readFloat(moduleObj, "hudX");
                Float renderY = readFloat(moduleObj, "hudY");
                if (renderX != null && renderY != null) {
                    hud.loadLegacyPosition(renderX, renderY);
                }
            }
        }

        JsonObject settingsObj = getObject(moduleObj, "settings");
        if (settingsObj != null) {
            for (Setting<?> setting : module.getSettings()) {
                if (setting != null && setting.isRootSetting()) {
                    continue;
                }
                applySetting(setting, settingsObj.get(setting.getName()));
            }
        }

        try {
            module.loadCustomState(getObject(moduleObj, "state"));
        } catch (Exception e) {
            Constants.LOGGER.error("读取模块自定义状态失败: {}", module.getName(), e);
        }

        // Apply enabled state last so onEnable/onDisable fire after settings are set
        if (moduleObj.has("enabled") && moduleObj.get("enabled").isJsonPrimitive()) {
            module.setEnabled(moduleObj.get("enabled").getAsBoolean());
        }
    }

    private void saveModuleToDisk(Module module, Path configStorageDir) throws IOException {
        Path file = getModuleFile(configStorageDir, module);
        try {
            Files.createDirectories(file.getParent());
            String json = gson.toJson(buildModuleObject(module));
            Files.writeString(file, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            Constants.LOGGER.error("写入模块配置失败: {}", file, e);
            throw e;
        }
    }

    private JsonObject buildModuleObject(Module module) {
        JsonObject obj = new JsonObject();
        obj.addProperty("version", CONFIG_VERSION);
        obj.addProperty("enabled", module.isEnabled());
        obj.addProperty("keyBind", module.getKeyBind());
        obj.addProperty("bindMode", module.getBindMode().name());
        obj.addProperty("hidden", module.isHidden());

        if (module instanceof HudModule hud) {
            hud.updateLayout();
            obj.addProperty("hudX", hud.x);
            obj.addProperty("hudY", hud.y);
            obj.addProperty("hudAnchorX", hud.getAnchorX());
            obj.addProperty("hudAnchorY", hud.getAnchorY());
            obj.addProperty("hudHorizontalAnchor", hud.getHorizontalAnchor().name());
            obj.addProperty("hudVerticalAnchor", hud.getVerticalAnchor().name());
        }

        JsonObject settingsObj = new JsonObject();
        for (Setting<?> setting : module.getSettings()) {
            if (setting == null) continue;
            if (setting.isRootSetting()) continue;
            JsonElement value = serializeSetting(setting);
            if (value != null) settingsObj.add(setting.getName(), value);
        }
        obj.add("settings", settingsObj);

        try {
            JsonObject stateObj = module.saveCustomState();
            if (stateObj != null) obj.add("state", stateObj);
        } catch (Exception e) {
            Constants.LOGGER.error("写入模块自定义状态失败: {}", module.getName(), e);
        }

        return obj;
    }

    private Path getAddonSettingsFile(Path configStorageDir, EpsilonAddon addon) {
        return configStorageDir.resolve(addon.getAddonId()).resolve(ADDON_SETTINGS_FILE_NAME);
    }

    private void applyAddonFromDisk(EpsilonAddon addon, Path configStorageDir) {
        if (addon == null || addon.getSettings().isEmpty()) {
            return;
        }

        Path file = getAddonSettingsFile(configStorageDir, addon);
        if (!Files.exists(file)) {
            return;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) {
                return;
            }

            JsonObject settingsObj = getObject(parsed.getAsJsonObject(), "settings");
            if (settingsObj == null) {
                return;
            }

            for (Setting<?> setting : addon.getSettings()) {
                applySetting(setting, settingsObj.get(setting.getName()));
            }
        } catch (Exception e) {
            Constants.LOGGER.error("读取 addon 配置失败: {}", file, e);
        }
    }

    private void saveAddonToDisk(EpsilonAddon addon, Path configStorageDir) throws IOException {
        if (addon == null || addon.getSettings().isEmpty()) {
            return;
        }

        Path file = getAddonSettingsFile(configStorageDir, addon);
        try {
            Files.createDirectories(file.getParent());
            String json = gson.toJson(buildAddonObject(addon));
            Files.writeString(file, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            Constants.LOGGER.error("写入 addon 配置失败: {}", file, e);
            throw e;
        }
    }

    private JsonObject buildAddonObject(EpsilonAddon addon) {
        JsonObject obj = new JsonObject();
        obj.addProperty("version", CONFIG_VERSION);

        JsonObject settingsObj = new JsonObject();
        for (Setting<?> setting : addon.getSettings()) {
            if (setting == null) continue;
            JsonElement value = serializeSetting(setting);
            if (value != null) settingsObj.add(setting.getName(), value);
        }
        obj.add("settings", settingsObj);

        return obj;
    }

    private synchronized void saveFriends(Path configStorageDir) throws IOException {
        Path friendFile = configStorageDir.resolve(FRIENDS_FILE_NAME);
        JsonArray array = new JsonArray();
        for (String name : Managers.FRIEND.getFriends()) {
            array.add(name);
        }
        try {
            Files.createDirectories(friendFile.getParent());
            Files.writeString(friendFile, gson.toJson(array), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            Constants.LOGGER.error("写入好友文件失败: {}", friendFile, e);
            throw e;
        }
    }

    private synchronized void loadFriends(Path configStorageDir) {
        Path friendFile = configStorageDir.resolve(FRIENDS_FILE_NAME);
        if (Managers.FRIEND != null) Managers.FRIEND.clearFriends();
        if (!Files.exists(friendFile)) return;
        try {
            String json = Files.readString(friendFile, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed != null && parsed.isJsonArray()) {
                for (JsonElement el : parsed.getAsJsonArray()) {
                    if (el.isJsonPrimitive()) {
                        Managers.FRIEND.addFriend(el.getAsString());
                    }
                }
            }
        } catch (Exception e) {
            Constants.LOGGER.error("读取好友文件失败: {}", friendFile, e);
        }
    }

    private static JsonElement serializeSetting(Setting<?> setting) {
        if (setting instanceof KeybindSetting s) return new JsonPrimitive(s.getValue());
        if (setting instanceof BoolSetting s) return new JsonPrimitive(s.getValue());
        if (setting instanceof IntSetting s) return new JsonPrimitive(s.getValue());
        if (setting instanceof DoubleSetting s) return new JsonPrimitive(s.getValue());
        if (setting instanceof StringSetting s) return new JsonPrimitive(s.getValue());
        if (setting instanceof StringListSetting s) {
            JsonArray array = new JsonArray();
            for (String str : s.getValue()) array.add(str);
            return array;
        }
        if (setting instanceof RegistryListSetting<?> s) {
            JsonArray array = new JsonArray();
            for (String id : s.getIds()) array.add(id);
            return array;
        }
        if (setting instanceof EnumSetting s) return new JsonPrimitive(s.getValue().toString());
        if (setting instanceof ColorSetting s) {
            Color c = s.getValue();
            return c == null ? null : new JsonPrimitive(c.getRGB());
        }
        return null;
    }

    private static void applySetting(Setting<?> setting, JsonElement value) {
        if (value == null) return;
        try {
            if (value.isJsonArray()) {
                List<String> ids = new java.util.ArrayList<>();
                for (JsonElement element : value.getAsJsonArray()) {
                    if (element != null && element.isJsonPrimitive()) ids.add(element.getAsString());
                }
                if (setting instanceof StringListSetting s) {
                    s.setValue(ids);
                    return;
                }
                if (setting instanceof RegistryListSetting<?> s) {
                    s.setIds(ids);
                    return;
                }
                return;
            }
            if (!value.isJsonPrimitive()) return;
            if (setting instanceof BoolSetting s) s.setValue(value.getAsBoolean());
            else if (setting instanceof KeybindSetting s) s.setValue(value.getAsInt());
            else if (setting instanceof IntSetting s) s.setUnboundedValue(value.getAsInt());
            else if (setting instanceof DoubleSetting s) s.setUnboundedValue(value.getAsDouble());
            else if (setting instanceof StringSetting s) s.setValue(value.getAsString());
            else if (setting == ClientSetting.INSTANCE.guiMode && setting instanceof EnumSetting s)
                s.setModeSilently(value.getAsString());
            else if (setting instanceof EnumSetting s) s.setMode(value.getAsString());
            else if (setting instanceof ColorSetting s) {
                int argb = value.getAsInt();
                Color c = new Color(argb, true);
                if (!s.isAllowAlpha()) c = new Color(c.getRed(), c.getGreen(), c.getBlue());
                s.setValue(c);
            }
        } catch (Exception ignored) {
        }
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }

    private static Float readFloat(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return null;
        try {
            return value.getAsFloat();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static HudModule.HorizontalAnchor readHorizontalAnchor(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return null;
        try {
            return HudModule.HorizontalAnchor.valueOf(value.getAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static HudModule.VerticalAnchor readVerticalAnchor(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return null;
        try {
            return HudModule.VerticalAnchor.valueOf(value.getAsString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void ensureRootDirectories() throws IOException {
        if (rootDirectoriesEnsured) {
            return;
        }
        Files.createDirectories(configDir);
        Files.createDirectories(configsDir);
        Files.createDirectories(importsDir);
        Files.createDirectories(exportsDir);
        rootDirectoriesEnsured = true;
    }

    private void ensureConfigExists(String configName) throws IOException {
        Files.createDirectories(getConfigStorageDir(configName));
    }

    private Path getConfigStorageDir(String configName) {
        return configsDir.resolve(configName);
    }

    private List<Module> getConfigurableModules() {
        List<Module> modules = new ArrayList<>();
        modules.addAll(ModuleHolder.INSTANCE.getModules());
        modules.addAll(HudElementHolder.INSTANCE.getElements());
        return modules;
    }

    private void resetModulesToDefaults(List<Module> modules) {
        if (modules == null) {
            return;
        }
        for (Module module : modules) {
            if (module != null) {
                module.reset();
            }
        }
    }

    private void resetAddonsToDefaults(List<EpsilonAddon> addons) {
        if (addons == null) {
            return;
        }
        for (EpsilonAddon addon : addons) {
            if (addon != null) {
                addon.resetSettings();
            }
        }
    }

    private void applyToAddons(List<EpsilonAddon> addons) {
        if (addons == null) {
            return;
        }
        for (EpsilonAddon addon : addons) {
            if (addon != null) {
                applyAddonFromDisk(addon, getActiveConfigStorageDir());
            }
        }
    }

    private void saveAddonsToDisk(List<EpsilonAddon> addons, Path configStorageDir) throws IOException {
        if (addons == null) {
            return;
        }
        for (EpsilonAddon addon : addons) {
            if (addon != null) {
                saveAddonToDisk(addon, configStorageDir);
            }
        }
    }

    private void loadActiveConfigSnapshot() throws IOException {
        ensureRootDirectories();
        ensureConfigExists(activeConfigName);
        writeActiveConfigName(activeConfigName);
        List<Module> modules = getConfigurableModules();
        List<EpsilonAddon> addons = AddonHolder.INSTANCE.getAddons();
        resetModulesToDefaults(modules);
        resetAddonsToDefaults(addons);
        applyToModules(modules);
        applyToAddons(addons);
        loadFriends(getActiveConfigStorageDir());
        loadRootClientSettings();
        ClientSetting.INSTANCE.syncFontGlyphUploadBudget();
    }

    private void saveActiveConfigSnapshot() throws IOException {
        ensureRootDirectories();
        ensureConfigExists(activeConfigName);
        writeActiveConfigName(activeConfigName);
        Path configStorageDir = getActiveConfigStorageDir();
        List<Module> modules = getConfigurableModules();
        for (Module module : modules) {
            if (module != null) {
                saveModuleToDisk(module, configStorageDir);
            }
        }
        saveAddonsToDisk(AddonHolder.INSTANCE.getAddons(), configStorageDir);
        saveFriends(configStorageDir);
        saveRootClientSettings();
    }

    private void loadRootClientSettings() {
        try {
            if (Files.exists(rootSettingsFile)) {
                String json = Files.readString(rootSettingsFile, StandardCharsets.UTF_8);
                JsonElement parsed = JsonParser.parseString(json);
                if (parsed != null && parsed.isJsonObject()) {
                    JsonElement value = parsed.getAsJsonObject().get("showWelcomeScreen");
                    if (value != null && value.isJsonPrimitive()) {
                        ClientSetting.INSTANCE.showWelcomeScreen.setValueSilently(value.getAsBoolean());
                    }
                }
                return;
            }

            Boolean legacyValue = readLegacyShowWelcomeScreen();
            if (legacyValue != null) {
                ClientSetting.INSTANCE.showWelcomeScreen.setValueSilently(legacyValue);
            }
            saveRootClientSettings();
        } catch (Exception e) {
            Constants.LOGGER.error("读取根配置失败", e);
        }
    }

    private void saveRootClientSettings() {
        try {
            ensureRootDirectories();
            JsonObject root = new JsonObject();
            root.addProperty("showWelcomeScreen", ClientSetting.INSTANCE.showWelcomeScreen.getValue());
            Files.writeString(rootSettingsFile, gson.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (Exception e) {
            Constants.LOGGER.error("写入根配置失败", e);
        }
    }

    private Boolean readLegacyShowWelcomeScreen() {
        Path legacyClientSettingFile = getModuleFile(getActiveConfigStorageDir(), ClientSetting.INSTANCE);
        if (!Files.exists(legacyClientSettingFile)) {
            return null;
        }
        try {
            String json = Files.readString(legacyClientSettingFile, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed != null && parsed.isJsonObject()) {
                JsonObject settingsObj = getObject(parsed.getAsJsonObject(), "settings");
                if (settingsObj != null) {
                    JsonElement value = settingsObj.get(ClientSetting.INSTANCE.showWelcomeScreen.getName());
                    if (value != null && value.isJsonPrimitive()) {
                        return value.getAsBoolean();
                    }
                }
            }
        } catch (Exception e) {
            Constants.LOGGER.error("读取旧版欢迎页配置失败", e);
        }
        return null;
    }

    private void migrateLegacyLayoutsIfNeeded(List<Module> modules) throws IOException {
        Path defaultConfigDir = getConfigStorageDir(DEFAULT_CONFIG_NAME);
        Files.createDirectories(defaultConfigDir);
        new LegacyConfigMigrator(configDir, defaultConfigDir, gson).migrateIfNeeded(modules);
        migrateLegacyFriendsIfNeeded(defaultConfigDir);
    }

    private void migrateLegacyFriendsIfNeeded(Path defaultConfigDir) throws IOException {
        if (!Files.exists(legacyFriendFile)) {
            return;
        }
        Path targetFriendFile = defaultConfigDir.resolve(FRIENDS_FILE_NAME);
        if (!Files.exists(targetFriendFile)) {
            Files.createDirectories(targetFriendFile.getParent());
            Files.copy(legacyFriendFile, targetFriendFile, StandardCopyOption.REPLACE_EXISTING);
        }
        Path backupFile = getAvailableBackupPath(legacyFriendFile);
        Files.move(legacyFriendFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private String resolveStoredActiveConfigName() throws IOException {
        if (Files.exists(activeConfigFile)) {
            String stored = Files.readString(activeConfigFile, StandardCharsets.UTF_8).trim();
            if (!stored.isEmpty() && isValidConfigName(stored)) {
                return stored;
            }
        }
        List<String> existingConfigs = listConfigsInternal(true);
        return existingConfigs.isEmpty() ? DEFAULT_CONFIG_NAME : existingConfigs.getFirst();
    }

    private void writeActiveConfigName(String configName) throws IOException {
        Files.writeString(activeConfigFile, configName, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private List<String> listConfigsInternal(boolean createDefaultIfMissing) throws IOException {
        ensureRootDirectories();
        List<String> configs;
        try (Stream<Path> stream = Files.list(configsDir)) {
            configs = stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (configs.isEmpty() && createDefaultIfMissing) {
            ensureConfigExists(DEFAULT_CONFIG_NAME);
            return List.of(DEFAULT_CONFIG_NAME);
        }
        return configs;
    }

    private List<String> listConfigsCached(boolean createDefaultIfMissing) throws IOException {
        long now = System.currentTimeMillis();
        if (!configListCacheDirty && now < configListCacheExpiresAt) {
            return cachedConfigNames;
        }
        List<String> configs = listConfigsInternal(createDefaultIfMissing);
        cachedConfigNames = List.copyOf(configs);
        configListCacheDirty = false;
        configListCacheExpiresAt = now + CONFIG_LIST_CACHE_TTL_MS;
        return cachedConfigNames;
    }

    private void invalidateConfigListCache() {
        configListCacheDirty = true;
        configListCacheExpiresAt = 0L;
    }

    private String normalizeAndValidateConfigName(String rawName) {
        String configName = rawName == null ? "" : rawName.trim();
        if (!isValidConfigName(configName)) {
            throw new IllegalArgumentException("配置名称不合法");
        }
        return configName;
    }

    private boolean isValidConfigName(String configName) {
        return configName != null
                && !configName.isBlank()
                && !configName.equals(".")
                && !configName.equals("..")
                && !configName.contains("..")
                && !INVALID_CONFIG_NAME_PATTERN.matcher(configName).find();
    }

    private Path resolveExportZipPath(String rawPath, String configName) {
        String normalized = rawPath == null ? "" : rawPath.trim();
        Path path = Paths.get(normalized);
        String fileName = normalized.isEmpty()
                ? configName + ".zip"
                : (path.getFileName() == null
                   ? configName + ".zip"
                   : path.getFileName().toString());
        if (!fileName.toLowerCase().endsWith(".zip")) {
            fileName = fileName + ".zip";
        }
        return exportsDir.resolve(fileName).normalize();
    }

    private Path resolveImportZipPath(String rawPath) {
        String normalized = rawPath == null ? "" : rawPath.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请输入要导入的 zip 文件");
        }
        Path path = Paths.get(normalized);
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (!fileName.isEmpty() && !fileName.toLowerCase().endsWith(".zip")) {
            path = path.resolveSibling(fileName + ".zip");
        }
        if (!path.isAbsolute()) {
            path = importsDir.resolve(path);
        }
        return path.normalize();
    }

    private JsonObject buildExportMetadata(String configName) {
        JsonObject object = new JsonObject();
        object.addProperty("version", CONFIG_VERSION);
        object.addProperty("configName", configName);
        object.addProperty("exportedAt", Instant.now().toString());
        return object;
    }

    private void writeStringZipEntry(ZipOutputStream zipOutputStream, String entryName, String contents) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(contents.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private void writeFileZipEntry(ZipOutputStream zipOutputStream, String entryName, Path file) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(Files.readAllBytes(file));
        zipOutputStream.closeEntry();
    }

    private void unzipSecurely(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path outputPath = targetDir.resolve(entry.getName()).normalize();
                if (!outputPath.startsWith(targetDir)) {
                    throw new IOException("非法 zip 条目: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zipInputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private Path detectConfigRoot(Path extractedRoot) throws IOException {
        try (Stream<Path> stream = Files.list(extractedRoot)) {
            List<Path> children = stream.toList();
            if (children.size() == 1 && Files.isDirectory(children.getFirst())) {
                return children.getFirst();
            }
        }
        return extractedRoot;
    }

    private String buildImportedConfigName(Path configRoot, Path zipPath) throws IOException {
        String baseName = fileNameWithoutExtension(zipPath.getFileName() == null ? zipPath.toString() : zipPath.getFileName().toString());
        Path metadataFile = configRoot.resolve(EXPORT_METADATA_FILE_NAME);
        String requestedName = baseName;
        if (Files.exists(metadataFile)) {
            try {
                JsonElement parsed = JsonParser.parseString(Files.readString(metadataFile, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) {
                    JsonElement nameElement = parsed.getAsJsonObject().get("configName");
                    if (nameElement != null && nameElement.isJsonPrimitive()) {
                        requestedName = nameElement.getAsString();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String candidate = isValidConfigName(requestedName) ? requestedName : baseName;
        String uniqueName = candidate;
        int index = 1;
        while (Files.exists(getConfigStorageDir(uniqueName))) {
            uniqueName = candidate + "-imported" + (index > 1 ? "-" + index : "");
            index++;
        }
        return uniqueName;
    }

    private String fileNameWithoutExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private void copyDirectory(Path sourceDir, Path targetDir, boolean skipExportMetadata) throws IOException {
        if (!Files.exists(sourceDir)) {
            Files.createDirectories(targetDir);
            return;
        }
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = sourceDir.relativize(dir);
                Files.createDirectories(targetDir.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (skipExportMetadata && EXPORT_METADATA_FILE_NAME.equals(file.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                Path relative = sourceDir.relativize(file);
                Files.copy(file, targetDir.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path getAvailableBackupPath(Path originalFile) throws IOException {
        Path parent = originalFile.getParent();
        String baseName = originalFile.getFileName().toString() + ".bak";
        Path backupFile = parent.resolve(baseName);
        if (!Files.exists(backupFile)) {
            return backupFile;
        }

        int index = 1;
        Path candidate;
        do {
            candidate = parent.resolve(baseName + "." + index);
            index++;
        } while (Files.exists(candidate));

        return candidate;
    }

}
