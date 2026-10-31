package com.github.epsilon.managers

import com.github.epsilon.Constants
import com.github.epsilon.accounts.Account
import com.github.epsilon.assets.config.LegacyConfigMigrator
import com.github.epsilon.elements.HudModule
import com.github.epsilon.modules.Module
import com.github.epsilon.modules.impl.ClientSetting
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.*
import com.google.gson.*
import java.awt.Color
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ConfigManager {

    private const val CONFIG_VERSION = 4
    private const val DEFAULT_CONFIG_NAME = "default"
    private const val CONFIGS_FOLDER = "configs"
    private const val IMPORTS_FOLDER = "imports"
    private const val EXPORTS_FOLDER = "exports"
    private const val FRIENDS_FILE_NAME = "friends.json"
    private const val ACTIVE_CONFIG_FILE_NAME = "active-config.txt"
    private const val ROOT_SETTINGS_FILE_NAME = "client-settings.json"
    private const val ACCOUNTS_FILE_NAME = "accounts.json"
    private const val EXPORT_METADATA_FILE_NAME = "config-info.json"
    private const val SHOW_WELCOME_SCREEN_SETTING = "Show Welcome Screen"
    private val INVALID_CONFIG_NAME_PATTERN = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]")

    val configDir: Path = Paths.get(System.getProperty("user.home"), ".epsilon")
    val configsDir: Path = configDir.resolve(CONFIGS_FOLDER)
    val importDir: Path = configDir.resolve(IMPORTS_FOLDER)
    val exportDir: Path = configDir.resolve(EXPORTS_FOLDER)
    private val activeConfigFile: Path = configDir.resolve(ACTIVE_CONFIG_FILE_NAME)
    private val rootSettingsFile: Path = configDir.resolve(ROOT_SETTINGS_FILE_NAME)
    private val accountsFile: Path = configDir.resolve(ACCOUNTS_FILE_NAME)
    private val legacyFriendFile: Path = configDir.resolve(FRIENDS_FILE_NAME)

    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private const val CONFIG_LIST_CACHE_TTL_MS = 1000L

    var activeConfigName: String = DEFAULT_CONFIG_NAME
        private set

    private var rootDirectoriesEnsured = false
    private var cachedConfigNames: List<String> = emptyList()
    private var configListCacheExpiresAt = 0L
    private var configListCacheDirty = true

    fun initConfig() {
        try {
            ensureRootDirectories()
            migrateLegacyLayoutsIfNeeded(getConfigurableModules())
            activeConfigName = resolveStoredActiveConfigName()
            ensureConfigExists(activeConfigName)
            loadActiveConfigSnapshot()
        } catch (e: Exception) {
            Constants.LOGGER.error("初始化配置失败", e)
        }
    }

    @get:Synchronized
    val activeConfigStorageDir: Path
        get() = getConfigStorageDir(activeConfigName)

    @Synchronized
    fun listConfigs(): List<String> {
        try {
            return listConfigsCached(true)
        } catch (e: IOException) {
            Constants.LOGGER.error("列出配置失败", e)
            return listOf(activeConfigName)
        }
    }

    @Synchronized
    fun reload() {
        try {
            reloadOrThrow()
        } catch (e: Exception) {
            Constants.LOGGER.error("重载配置失败", e)
        }
    }

    @Synchronized
    fun reloadOrThrow() {
        loadActiveConfigSnapshot()
    }

    @Synchronized
    fun applyToModules(modules: List<Module>?) {
        if (modules == null) return
        for (module in modules) {
            applyModuleFromDisk(module, activeConfigStorageDir)
        }
    }

    @Synchronized
    fun saveNow() {
        try {
            saveActiveConfigSnapshot()
        } catch (e: IOException) {
            Constants.LOGGER.error("保存配置失败", e)
        }
    }

    @Synchronized
    fun saveAsConfig(rawName: String?): String {
        val configName = normalizeAndValidateConfigName(rawName)
        ensureRootDirectories()
        saveActiveConfigSnapshot()

        val sourceDir = activeConfigStorageDir
        val targetDir = getConfigStorageDir(configName)
        if (sourceDir != targetDir) {
            deleteDirectory(targetDir)
            copyDirectory(sourceDir, targetDir, false)
        }

        activeConfigName = configName
        writeActiveConfigName(configName)
        invalidateConfigListCache()
        loadActiveConfigSnapshot()
        return configName
    }

    @Synchronized
    fun newDefaultConfig(rawName: String?): String {
        val configName = normalizeAndValidateConfigName(rawName)
        ensureRootDirectories()
        Files.createDirectories(getConfigStorageDir(configName))
        invalidateConfigListCache()
        switchConfig(configName)
        return configName
    }

    @Synchronized
    fun switchConfig(rawName: String?) {
        val configName = normalizeAndValidateConfigName(rawName)
        ensureRootDirectories()
        if (configName == activeConfigName) {
            loadActiveConfigSnapshot()
            return
        }

        saveActiveConfigSnapshot()
        ensureConfigExists(configName)
        activeConfigName = configName
        writeActiveConfigName(configName)
        invalidateConfigListCache()
        loadActiveConfigSnapshot()
    }

    @Synchronized
    fun deleteConfig(rawName: String?): Boolean {
        val configName = normalizeAndValidateConfigName(rawName)
        val targetDir = getConfigStorageDir(configName)
        if (!Files.exists(targetDir)) {
            return false
        }

        val configs = listConfigsInternal(true)
        if (configs.size <= 1 && configs.contains(configName)) {
            return false
        }

        if (activeConfigName == configName) {
            val fallback = configs.firstOrNull { name -> name != configName } ?: DEFAULT_CONFIG_NAME
            activeConfigName = fallback
            ensureConfigExists(fallback)
            writeActiveConfigName(fallback)
            loadActiveConfigSnapshot()
        }

        deleteDirectory(targetDir)
        invalidateConfigListCache()
        return true
    }

    @Synchronized
    fun exportActiveConfigToZip(rawPath: String?): Path {
        return exportConfigToZip(activeConfigName, rawPath)
    }

    @Synchronized
    fun exportConfigToZip(rawName: String?, rawPath: String?): Path {
        val configName = normalizeAndValidateConfigName(rawName)
        ensureRootDirectories()
        if (configName == activeConfigName) {
            saveActiveConfigSnapshot()
        }

        val sourceDir = getConfigStorageDir(configName)
        if (!Files.exists(sourceDir)) {
            throw IOException("配置不存在: $configName")
        }

        val zipPath = resolveExportZipPath(rawPath, configName)
        Files.createDirectories(zipPath.parent)

        ZipOutputStream(
            Files.newOutputStream(
                zipPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        ).use { zipOutputStream ->
            val rootPrefix = "$configName/"
            writeStringZipEntry(
                zipOutputStream, rootPrefix + EXPORT_METADATA_FILE_NAME,
                gson.toJson(buildExportMetadata(configName))
            )

            Files.walk(sourceDir).use { stream ->
                for (file in stream.filter { path -> Files.isRegularFile(path) }.toList()) {
                    val relative = sourceDir.relativize(file).toString().replace('\\', '/')
                    writeFileZipEntry(zipOutputStream, rootPrefix + relative, file)
                }
            }
        }

        return zipPath
    }

    @Synchronized
    fun importConfigFromZip(rawPath: String?): String {
        val zipPath = resolveImportZipPath(rawPath)
        return importConfigFromZip(zipPath, true)
    }

    @Synchronized
    fun importConfigFromZip(zipPath: Path?, switchToImported: Boolean): String {
        ensureRootDirectories()
        if (zipPath == null || !Files.exists(zipPath) || !Files.isRegularFile(zipPath)) {
            throw IOException("Zip 文件不存在: $zipPath")
        }

        val tempDir = Files.createTempDirectory(configDir, "config-import-")
        try {
            unzipSecurely(zipPath, tempDir)
            val configRoot = detectConfigRoot(tempDir)
            val importedName = buildImportedConfigName(configRoot, zipPath)
            val targetDir = getConfigStorageDir(importedName)

            deleteDirectory(targetDir)
            copyDirectory(configRoot, targetDir, true)
            invalidateConfigListCache()

            if (switchToImported) {
                activeConfigName = importedName
                writeActiveConfigName(importedName)
                loadActiveConfigSnapshot()
            }
            return importedName
        } finally {
            deleteDirectory(tempDir)
        }
    }

    private fun getModuleFile(configStorageDir: Path, module: Module): Path {
        return configStorageDir.resolve("epsilon").resolve(module.name + ".json")
    }

    private fun applyModuleFromDisk(module: Module, configStorageDir: Path) {
        val file = getModuleFile(configStorageDir, module)
        if (!Files.exists(file)) return
        try {
            val json = Files.readString(file, StandardCharsets.UTF_8)
            val parsed = JsonParser.parseString(json)
            if (parsed == null || !parsed.isJsonObject) return
            applyModuleObject(module, parsed.asJsonObject)
        } catch (e: Exception) {
            Constants.LOGGER.error("读取模块配置失败: {}", file, e)
        }
    }

    private fun applyModuleObject(module: Module, moduleObj: JsonObject) {
        if (moduleObj.has("keyBind") && moduleObj.get("keyBind").isJsonPrimitive) {
            try {
                module.keyBind = moduleObj.get("keyBind").asInt
            } catch (_: Exception) {
            }
        }

        if (moduleObj.has("bindMode") && moduleObj.get("bindMode").isJsonPrimitive) {
            try {
                module.bindMode = Module.BindMode.valueOf(moduleObj.get("bindMode").asString)
            } catch (_: Exception) {
            }
        }

        if (moduleObj.has("hidden") && moduleObj.get("hidden").isJsonPrimitive) {
            try {
                module.isHidden = moduleObj.get("hidden").asBoolean
            } catch (_: Exception) {
            }
        }

        if (module is HudModule) {
            val horizontalAnchor = readHorizontalAnchor(moduleObj, "hudHorizontalAnchor")
            val verticalAnchor = readVerticalAnchor(moduleObj, "hudVerticalAnchor")
            val anchorX = readFloat(moduleObj, "hudAnchorX")
            val anchorY = readFloat(moduleObj, "hudAnchorY")

            if (horizontalAnchor != null && verticalAnchor != null && anchorX != null && anchorY != null) {
                module.setAnchorState(horizontalAnchor, verticalAnchor, anchorX, anchorY)
            } else {
                val renderX = readFloat(moduleObj, "hudX")
                val renderY = readFloat(moduleObj, "hudY")
                if (renderX != null && renderY != null) {
                    module.loadLegacyPosition(renderX, renderY)
                }
            }
        }

        val settingsObj = getObject(moduleObj, "settings")
        if (settingsObj != null) {
            for (setting in module.settings) {
                if (setting.isRootSetting) {
                    continue
                }
                applySetting(setting, settingsObj.get(setting.name))
            }
        }

        try {
            module.loadCustomState(getObject(moduleObj, "state") ?: JsonObject())
        } catch (e: Exception) {
            Constants.LOGGER.error("读取模块自定义状态失败: {}", module.name, e)
        }

        // Apply enabled state last so onEnable/onDisable fire after settings are set
        if (moduleObj.has("enabled") && moduleObj.get("enabled").isJsonPrimitive) {
            module.isEnabled = moduleObj.get("enabled").asBoolean
        }
    }

    private fun saveModuleToDisk(module: Module, configStorageDir: Path) {
        val file = getModuleFile(configStorageDir, module)
        try {
            Files.createDirectories(file.parent)
            val json = gson.toJson(buildModuleObject(module))
            Files.writeString(
                file, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
            )
        } catch (e: IOException) {
            Constants.LOGGER.error("写入模块配置失败: {}", file, e)
            throw e
        }
    }

    private fun buildModuleObject(module: Module): JsonObject {
        val obj = JsonObject()
        obj.addProperty("version", CONFIG_VERSION)
        obj.addProperty("enabled", module.isEnabled)
        obj.addProperty("keyBind", module.keyBind)
        obj.addProperty("bindMode", module.bindMode.name)
        obj.addProperty("hidden", module.isHidden)

        if (module is HudModule) {
            module.updateLayout()
            obj.addProperty("hudX", module.x)
            obj.addProperty("hudY", module.y)
            obj.addProperty("hudAnchorX", module.anchorX)
            obj.addProperty("hudAnchorY", module.anchorY)
            obj.addProperty("hudHorizontalAnchor", module.horizontalAnchor.name)
            obj.addProperty("hudVerticalAnchor", module.verticalAnchor.name)
        }

        val settingsObj = JsonObject()
        for (setting in module.settings) {
            if (setting.isRootSetting) continue
            val value = serializeSetting(setting)
            if (value != null) settingsObj.add(setting.name, value)
        }
        obj.add("settings", settingsObj)

        try {
            val stateObj = module.saveCustomState()
            if (stateObj != null) obj.add("state", stateObj)
        } catch (e: Exception) {
            Constants.LOGGER.error("写入模块自定义状态失败: {}", module.name, e)
        }

        return obj
    }

    @Synchronized
    private fun saveFriends(configStorageDir: Path) {
        val friendFile = configStorageDir.resolve(FRIENDS_FILE_NAME)
        val array = JsonArray()
        for (name in FriendManager.getFriends()) {
            array.add(name)
        }
        try {
            Files.createDirectories(friendFile.parent)
            Files.writeString(
                friendFile, gson.toJson(array), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        } catch (e: IOException) {
            Constants.LOGGER.error("写入好友文件失败: {}", friendFile, e)
            throw e
        }
    }

    @Synchronized
    private fun loadFriends(configStorageDir: Path) {
        val friendFile = configStorageDir.resolve(FRIENDS_FILE_NAME)
        FriendManager.clearFriends()
        if (!Files.exists(friendFile)) return
        try {
            val json = Files.readString(friendFile, StandardCharsets.UTF_8)
            val parsed = JsonParser.parseString(json)
            if (parsed != null && parsed.isJsonArray) {
                for (el in parsed.asJsonArray) {
                    if (el.isJsonPrimitive) {
                        FriendManager.addFriend(el.asString)
                    }
                }
            }
        } catch (e: Exception) {
            Constants.LOGGER.error("读取好友文件失败: {}", friendFile, e)
        }
    }

    @Synchronized
    fun saveAccounts() {
        try {
            ensureRootDirectories()
            val array = JsonArray()
            for (account in AccountManager.getAccounts()) {
                val obj = JsonObject()
                account.write(obj)
                array.add(obj)
            }
            Files.writeString(
                accountsFile, gson.toJson(array), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        } catch (e: IOException) {
            Constants.LOGGER.error("写入账号配置失败: {}", accountsFile, e)
        }
    }

    @Synchronized
    fun loadAccounts() {
        val loaded = ArrayList<Account<*>>()
        if (Files.exists(accountsFile)) {
            try {
                val json = Files.readString(accountsFile, StandardCharsets.UTF_8)
                val parsed = JsonParser.parseString(json)
                if (parsed != null && parsed.isJsonArray) {
                    for (element in parsed.asJsonArray) {
                        if (element == null || !element.isJsonObject) continue
                        try {
                            loaded.add(Account.fromJson(element.asJsonObject))
                        } catch (e: Exception) {
                            Constants.LOGGER.error("读取单个账号失败", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Constants.LOGGER.error("读取账号配置失败: {}", accountsFile, e)
            }
        }
        AccountManager.replaceAll(loaded)
    }

    private fun serializeSetting(setting: Setting<*>): JsonElement? {
        if (setting is KeybindSetting) return JsonPrimitive(setting.value)
        if (setting is BoolSetting) return JsonPrimitive(setting.value)
        if (setting is IntSetting) return JsonPrimitive(setting.value)
        if (setting is DoubleSetting) return JsonPrimitive(setting.value)
        if (setting is StringSetting) return JsonPrimitive(setting.value)
        if (setting is StringListSetting) {
            val array = JsonArray()
            for (str in setting.value) array.add(str)
            return array
        }
        if (setting is RegistryListSetting<*>) {
            val array = JsonArray()
            for (id in setting.getIds()) array.add(id)
            return array
        }
        if (setting is EnumSetting<*>) return JsonPrimitive(setting.value.toString())
        if (setting is ColorSetting) {
            val c = setting.value
            return if (c == null) null else JsonPrimitive(c.rgb)
        }
        return null
    }

    private fun applySetting(setting: Setting<*>, value: JsonElement?) {
        if (value == null) return
        try {
            if (value.isJsonArray) {
                val ids = ArrayList<String>()
                for (element in value.asJsonArray) {
                    if (element != null && element.isJsonPrimitive) ids.add(element.asString)
                }
                if (setting is StringListSetting) {
                    setting.value = ids
                    return
                }
                if (setting is RegistryListSetting<*>) {
                    setting.setIds(ids)
                    return
                }
                return
            }
            if (!value.isJsonPrimitive) return
            when {
                setting is BoolSetting -> setting.value = value.asBoolean
                setting is KeybindSetting -> setting.value = value.asInt
                setting is IntSetting -> setting.setUnboundedValue(value.asInt)
                setting is DoubleSetting -> setting.setUnboundedValue(value.asDouble)
                setting is StringSetting -> setting.value = value.asString
                setting === ClientSetting.guiMode && setting is EnumSetting<*> ->
                    (setting as EnumSetting<*>).setModeSilently(value.asString)
                setting is EnumSetting<*> -> setting.setMode(value.asString)
                setting is ColorSetting -> {
                    val argb = value.asInt
                    var c = Color(argb, true)
                    if (!setting.isAllowAlpha()) c = Color(c.red, c.green, c.blue)
                    setting.value = c
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun getObject(parent: JsonObject, key: String): JsonObject? {
        val el = parent.get(key)
        return if (el != null && el.isJsonObject) el.asJsonObject else null
    }

    private fun readFloat(obj: JsonObject, key: String): Float? {
        val value = obj.get(key)
        if (value == null || !value.isJsonPrimitive) return null
        try {
            return value.asFloat
        } catch (_: Exception) {
            return null
        }
    }

    private fun readHorizontalAnchor(obj: JsonObject, key: String): HudModule.HorizontalAnchor? {
        val value = obj.get(key)
        if (value == null || !value.isJsonPrimitive) return null
        try {
            return HudModule.HorizontalAnchor.valueOf(value.asString)
        } catch (_: Exception) {
            return null
        }
    }

    private fun readVerticalAnchor(obj: JsonObject, key: String): HudModule.VerticalAnchor? {
        val value = obj.get(key)
        if (value == null || !value.isJsonPrimitive) return null
        try {
            return HudModule.VerticalAnchor.valueOf(value.asString)
        } catch (_: Exception) {
            return null
        }
    }

    private fun ensureRootDirectories() {
        if (rootDirectoriesEnsured) {
            return
        }
        Files.createDirectories(configDir)
        Files.createDirectories(configsDir)
        Files.createDirectories(importDir)
        Files.createDirectories(exportDir)
        rootDirectoriesEnsured = true
    }

    private fun ensureConfigExists(configName: String) {
        Files.createDirectories(getConfigStorageDir(configName))
    }

    private fun getConfigStorageDir(configName: String): Path {
        return configsDir.resolve(configName)
    }

    private fun getConfigurableModules(): List<Module> {
        val modules = ArrayList<Module>()
        modules.addAll(ModuleManager.getModules())
        modules.addAll(HudElementManager.getElements())
        return modules
    }

    private fun resetModulesToDefaults(modules: List<Module>?) {
        if (modules == null) {
            return
        }
        for (module in modules) {
            module.reset()
        }
    }

    private fun loadActiveConfigSnapshot() {
        ensureRootDirectories()
        ensureConfigExists(activeConfigName)
        writeActiveConfigName(activeConfigName)
        val modules = getConfigurableModules()
        resetModulesToDefaults(modules)
        applyToModules(modules)
        loadFriends(activeConfigStorageDir)
        loadRootClientSettings()
        loadAccounts()
        ClientSetting.syncFontGlyphUploadBudget()
    }

    private fun saveActiveConfigSnapshot() {
        ensureRootDirectories()
        ensureConfigExists(activeConfigName)
        writeActiveConfigName(activeConfigName)
        val configStorageDir = activeConfigStorageDir
        val modules = getConfigurableModules()
        for (module in modules) {
            saveModuleToDisk(module, configStorageDir)
        }
        saveFriends(configStorageDir)
        saveRootClientSettings()
        saveAccounts()
    }

    private fun loadRootClientSettings() {
        try {
            if (Files.exists(rootSettingsFile)) {
                val json = Files.readString(rootSettingsFile, StandardCharsets.UTF_8)
                val parsed = JsonParser.parseString(json)
                if (parsed != null && parsed.isJsonObject) {
                    val value = parsed.asJsonObject.get("showWelcomeScreen")
                    if (value != null && value.isJsonPrimitive) {
                        setShowWelcomeScreenSilently(value.asBoolean)
                    }
                }
                return
            }

            val legacyValue = readLegacyShowWelcomeScreen()
            if (legacyValue != null) {
                setShowWelcomeScreenSilently(legacyValue)
            }
            saveRootClientSettings()
        } catch (e: Exception) {
            Constants.LOGGER.error("读取根配置失败", e)
        }
    }

    private fun setShowWelcomeScreenSilently(value: Boolean) {
        val setting = ClientSetting.settings.firstOrNull { it.name == SHOW_WELCOME_SCREEN_SETTING }
        if (setting is BoolSetting) setting.setValueSilently(value)
    }

    private fun saveRootClientSettings() {
        try {
            ensureRootDirectories()
            val root = JsonObject()
            root.addProperty("showWelcomeScreen", ClientSetting.showWelcomeScreen)
            Files.writeString(
                rootSettingsFile, gson.toJson(root), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        } catch (e: Exception) {
            Constants.LOGGER.error("写入根配置失败", e)
        }
    }

    private fun readLegacyShowWelcomeScreen(): Boolean? {
        val legacyClientSettingFile = getModuleFile(activeConfigStorageDir, ClientSetting)
        if (!Files.exists(legacyClientSettingFile)) {
            return null
        }
        try {
            val json = Files.readString(legacyClientSettingFile, StandardCharsets.UTF_8)
            val parsed = JsonParser.parseString(json)
            if (parsed != null && parsed.isJsonObject) {
                val settingsObj = getObject(parsed.asJsonObject, "settings")
                if (settingsObj != null) {
                    val value = settingsObj.get(SHOW_WELCOME_SCREEN_SETTING)
                    if (value != null && value.isJsonPrimitive) {
                        return value.asBoolean
                    }
                }
            }
        } catch (e: Exception) {
            Constants.LOGGER.error("读取旧版欢迎页配置失败", e)
        }
        return null
    }

    private fun migrateLegacyLayoutsIfNeeded(modules: List<Module>) {
        val defaultConfigDir = getConfigStorageDir(DEFAULT_CONFIG_NAME)
        Files.createDirectories(defaultConfigDir)
        LegacyConfigMigrator(configDir, defaultConfigDir, gson).migrateIfNeeded(modules)
        migrateLegacyFriendsIfNeeded(defaultConfigDir)
    }

    private fun migrateLegacyFriendsIfNeeded(defaultConfigDir: Path) {
        if (!Files.exists(legacyFriendFile)) {
            return
        }
        val targetFriendFile = defaultConfigDir.resolve(FRIENDS_FILE_NAME)
        if (!Files.exists(targetFriendFile)) {
            Files.createDirectories(targetFriendFile.parent)
            Files.copy(legacyFriendFile, targetFriendFile, StandardCopyOption.REPLACE_EXISTING)
        }
        val backupFile = getAvailableBackupPath(legacyFriendFile)
        Files.move(legacyFriendFile, backupFile, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun resolveStoredActiveConfigName(): String {
        if (Files.exists(activeConfigFile)) {
            val stored = Files.readString(activeConfigFile, StandardCharsets.UTF_8).trim()
            if (stored.isNotEmpty() && isValidConfigName(stored)) {
                return stored
            }
        }
        val existingConfigs = listConfigsInternal(true)
        return if (existingConfigs.isEmpty()) DEFAULT_CONFIG_NAME else existingConfigs.first()
    }

    private fun writeActiveConfigName(configName: String) {
        Files.writeString(
            activeConfigFile, configName, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    private fun listConfigsInternal(createDefaultIfMissing: Boolean): List<String> {
        ensureRootDirectories()
        val configs: List<String>
        Files.list(configsDir).use { stream ->
            configs = stream
                .filter { path -> Files.isDirectory(path) }
                .map { path -> path.fileName.toString() }
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList()
        }
        if (configs.isEmpty() && createDefaultIfMissing) {
            ensureConfigExists(DEFAULT_CONFIG_NAME)
            return listOf(DEFAULT_CONFIG_NAME)
        }
        return configs
    }

    private fun listConfigsCached(createDefaultIfMissing: Boolean): List<String> {
        val now = System.currentTimeMillis()
        if (!configListCacheDirty && now < configListCacheExpiresAt) {
            return cachedConfigNames
        }
        val configs = listConfigsInternal(createDefaultIfMissing)
        cachedConfigNames = configs.toList()
        configListCacheDirty = false
        configListCacheExpiresAt = now + CONFIG_LIST_CACHE_TTL_MS
        return cachedConfigNames
    }

    private fun invalidateConfigListCache() {
        configListCacheDirty = true
        configListCacheExpiresAt = 0L
    }

    private fun normalizeAndValidateConfigName(rawName: String?): String {
        val configName = rawName?.trim() ?: ""
        if (!isValidConfigName(configName)) {
            throw IllegalArgumentException("配置名称不合法")
        }
        return configName
    }

    private fun isValidConfigName(configName: String?): Boolean {
        return configName != null &&
            configName.isNotBlank() &&
            configName != "." &&
            !configName.contains("..") &&
            !INVALID_CONFIG_NAME_PATTERN.matcher(configName).find()
    }

    private fun resolveExportZipPath(rawPath: String?, configName: String): Path {
        val normalized = rawPath?.trim() ?: ""
        val path = Paths.get(normalized)
        var fileName = if (normalized.isEmpty()) {
            "$configName.zip"
        } else {
            path.fileName?.toString() ?: "$configName.zip"
        }
        if (!fileName.lowercase().endsWith(".zip")) {
            fileName += ".zip"
        }
        return exportDir.resolve(fileName).normalize()
    }

    private fun resolveImportZipPath(rawPath: String?): Path {
        val normalized = rawPath?.trim() ?: ""
        if (normalized.isEmpty()) {
            throw IllegalArgumentException("请输入要导入的 zip 文件")
        }
        var path = Paths.get(normalized)
        val fileName = path.fileName?.toString() ?: ""
        if (fileName.isNotEmpty() && !fileName.lowercase().endsWith(".zip")) {
            path = path.resolveSibling("$fileName.zip")
        }
        if (!path.isAbsolute) {
            path = importDir.resolve(path)
        }
        return path.normalize()
    }

    private fun buildExportMetadata(configName: String): JsonObject {
        val obj = JsonObject()
        obj.addProperty("version", CONFIG_VERSION)
        obj.addProperty("configName", configName)
        obj.addProperty("exportedAt", Instant.now().toString())
        return obj
    }

    private fun writeStringZipEntry(zipOutputStream: ZipOutputStream, entryName: String, contents: String) {
        zipOutputStream.putNextEntry(ZipEntry(entryName))
        zipOutputStream.write(contents.toByteArray(StandardCharsets.UTF_8))
        zipOutputStream.closeEntry()
    }

    private fun writeFileZipEntry(zipOutputStream: ZipOutputStream, entryName: String, file: Path) {
        zipOutputStream.putNextEntry(ZipEntry(entryName))
        zipOutputStream.write(Files.readAllBytes(file))
        zipOutputStream.closeEntry()
    }

    private fun unzipSecurely(zipPath: Path, targetDir: Path) {
        ZipInputStream(Files.newInputStream(zipPath)).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                val outputPath = targetDir.resolve(entry.name).normalize()
                if (!outputPath.startsWith(targetDir)) {
                    throw IOException("非法 zip 条目: " + entry.name)
                }
                if (entry.isDirectory) {
                    Files.createDirectories(outputPath)
                } else {
                    Files.createDirectories(outputPath.parent)
                    Files.copy(zipInputStream, outputPath, StandardCopyOption.REPLACE_EXISTING)
                }
                zipInputStream.closeEntry()
            }
        }
    }

    private fun detectConfigRoot(extractedRoot: Path): Path {
        Files.list(extractedRoot).use { stream ->
            val children = stream.toList()
            if (children.size == 1 && Files.isDirectory(children[0])) {
                return children[0]
            }
        }
        return extractedRoot
    }

    private fun buildImportedConfigName(configRoot: Path, zipPath: Path): String {
        val zipFileName = zipPath.fileName?.toString() ?: zipPath.toString()
        val baseName = fileNameWithoutExtension(zipFileName)
        val metadataFile = configRoot.resolve(EXPORT_METADATA_FILE_NAME)
        var requestedName = baseName
        if (Files.exists(metadataFile)) {
            try {
                val parsed = JsonParser.parseString(Files.readString(metadataFile, StandardCharsets.UTF_8))
                if (parsed.isJsonObject) {
                    val nameElement = parsed.asJsonObject.get("configName")
                    if (nameElement != null && nameElement.isJsonPrimitive) {
                        requestedName = nameElement.asString
                    }
                }
            } catch (_: Exception) {
            }
        }

        val candidate = if (isValidConfigName(requestedName)) requestedName else baseName
        var uniqueName = candidate
        var index = 1
        while (Files.exists(getConfigStorageDir(uniqueName))) {
            uniqueName = candidate + "-imported" + (if (index > 1) "-$index" else "")
            index++
        }
        return uniqueName
    }

    private fun fileNameWithoutExtension(fileName: String): String {
        val index = fileName.lastIndexOf('.')
        return if (index > 0) fileName.substring(0, index) else fileName
    }

    private fun copyDirectory(sourceDir: Path, targetDir: Path, skipExportMetadata: Boolean) {
        if (!Files.exists(sourceDir)) {
            Files.createDirectories(targetDir)
            return
        }
        Files.walkFileTree(sourceDir, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val relative = sourceDir.relativize(dir)
                Files.createDirectories(targetDir.resolve(relative))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (skipExportMetadata && EXPORT_METADATA_FILE_NAME == file.fileName.toString()) {
                    return FileVisitResult.CONTINUE
                }
                val relative = sourceDir.relativize(file)
                Files.copy(file, targetDir.resolve(relative), StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun deleteDirectory(directory: Path?) {
        if (directory == null || !Files.exists(directory)) {
            return
        }
        Files.walkFileTree(directory, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun getAvailableBackupPath(originalFile: Path): Path {
        val parent = originalFile.parent
        val baseName = originalFile.fileName.toString() + ".bak"
        val backupFile = parent.resolve(baseName)
        if (!Files.exists(backupFile)) {
            return backupFile
        }

        var index = 1
        var candidate: Path
        do {
            candidate = parent.resolve("$baseName.$index")
            index++
        } while (Files.exists(candidate))

        return candidate
    }

}
