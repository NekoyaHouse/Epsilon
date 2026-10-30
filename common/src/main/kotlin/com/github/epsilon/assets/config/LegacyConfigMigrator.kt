package com.github.epsilon.assets.config

import com.github.epsilon.Constants
import com.github.epsilon.modules.Module
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class LegacyConfigMigrator(
    private val legacyRootDir: Path,
    private val targetConfigDir: Path,
    private val gson: Gson,
) {

    /**
     * Returns the per-module config file path for the new layout:
     * `{targetConfigDir}/epsilon/{moduleName}.json`
     */
    fun getModuleFile(module: Module): Path {
        return targetConfigDir.resolve("epsilon").resolve(module.name + ".json")
    }

    fun migrateIfNeeded(modules: List<Module>?) {
        val legacyConfigFile = legacyRootDir.resolve("config.json")
        if (!Files.exists(legacyConfigFile)) return
        if (modules == null) return

        // Only migrate when no per-module files have been created yet
        val anyExists = modules.any { m -> m != null && Files.exists(getModuleFile(m)) }
        if (anyExists) return

        Constants.LOGGER.info("检测到旧版 config.json，正在迁移到按模块分离的配置文件...")
        try {
            val json = Files.readString(legacyConfigFile, StandardCharsets.UTF_8)
            val parsed: JsonElement = JsonParser.parseString(json)
            if (!parsed.isJsonObject) return

            val root = parsed.asJsonObject
            val modulesObj = getObject(root, "modules") ?: return

            for (module in modules) {
                val moduleObj = getObject(modulesObj, module.name) ?: continue

                val dest = getModuleFile(module)
                Files.createDirectories(dest.parent)
                Files.writeString(
                    dest, gson.toJson(moduleObj), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            }

            // Rename the old file so we never migrate again
            val backupFile = getAvailableBackupPath()
            Files.move(legacyConfigFile, backupFile)
            Constants.LOGGER.info("迁移完成，旧配置已备份为 {}", backupFile.fileName)
        } catch (e: Exception) {
            Constants.LOGGER.error("迁移旧版配置失败: {}", legacyConfigFile, e)
        }
    }

    private fun getAvailableBackupPath(): Path {
        val backupFile = legacyRootDir.resolve("config.json.bak")
        if (!Files.exists(backupFile)) {
            return backupFile
        }

        var index = 1
        var candidate: Path
        do {
            candidate = legacyRootDir.resolve("config.json.bak.$index")
            index++
        } while (Files.exists(candidate))

        return candidate
    }

    companion object {

        private fun getObject(parent: JsonObject, key: String): JsonObject? {
            val el = parent.get(key)
            return if (el != null && el.isJsonObject) el.asJsonObject else null
        }

    }

}
