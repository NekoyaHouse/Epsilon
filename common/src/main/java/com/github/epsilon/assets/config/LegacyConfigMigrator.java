package com.github.epsilon.assets.config;

import com.github.epsilon.Constants;
import com.github.epsilon.modules.Module;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

public class LegacyConfigMigrator {

    private final Path legacyRootDir;
    private final Path targetConfigDir;
    private final Gson gson;

    public LegacyConfigMigrator(Path legacyRootDir, Path targetConfigDir, Gson gson) {
        this.legacyRootDir = legacyRootDir;
        this.targetConfigDir = targetConfigDir;
        this.gson = gson;
    }

    /**
     * Returns the per-module config file path for the new layout:
     * {@code {targetConfigDir}/{addonId}/{moduleName}.json}
     */
    public Path getModuleFile(Module module) {
        return targetConfigDir.resolve(module.getAddonId()).resolve(module.getName() + ".json");
    }

    public void migrateIfNeeded(List<Module> modules) {
        Path legacyConfigFile = legacyRootDir.resolve("config.json");
        if (!Files.exists(legacyConfigFile)) return;
        if (modules == null) return;

        // Only migrate when no per-module files have been created yet
        boolean anyExists = modules.stream()
                .filter(Objects::nonNull)
                .anyMatch(m -> Files.exists(getModuleFile(m)));
        if (anyExists) return;

        Constants.LOGGER.info("检测到旧版 config.json，正在迁移到按模块分离的配置文件...");
        try {
            String json = Files.readString(legacyConfigFile, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) return;

            JsonObject root = parsed.getAsJsonObject();
            JsonObject modulesObj = getObject(root, "modules");
            if (modulesObj == null) return;

            for (Module module : modules) {
                if (module == null) continue;
                JsonObject moduleObj = getObject(modulesObj, module.getName());
                if (moduleObj == null) continue;

                Path dest = getModuleFile(module);
                Files.createDirectories(dest.getParent());
                Files.writeString(dest, gson.toJson(moduleObj), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
            }

            // Rename the old file so we never migrate again
            Path backupFile = getAvailableBackupPath();
            Files.move(legacyConfigFile, backupFile);
            Constants.LOGGER.info("迁移完成，旧配置已备份为 {}", backupFile.getFileName());
        } catch (Exception e) {
            Constants.LOGGER.error("迁移旧版配置失败: {}", legacyConfigFile, e);
        }
    }

    private Path getAvailableBackupPath() {
        Path backupFile = legacyRootDir.resolve("config.json.bak");
        if (!Files.exists(backupFile)) {
            return backupFile;
        }

        int index = 1;
        Path candidate;
        do {
            candidate = legacyRootDir.resolve("config.json.bak." + index);
            index++;
        } while (Files.exists(candidate));

        return candidate;
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }

}
