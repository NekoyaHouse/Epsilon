package com.github.epsilon.assets.i18n

import com.github.epsilon.Constants
import com.github.epsilon.managers.TranslationManager
import com.github.epsilon.modules.impl.ClientSetting
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.GsonHelper
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

object EpsilonLanguageManager {

    private val GSON = Gson()
    private val UNSUPPORTED_FORMAT_PATTERN = Pattern.compile("%(\\d+\\$)?[\\d.]*[df]")
    private const val DEFAULT_LANGUAGE_CODE = "en_us"
    private const val LANGUAGE_DIRECTORY = "i18n"

    @Volatile
    private var translations: Map<String, String> = java.util.Map.of()

    @Volatile
    private var selectedLanguage: EpsilonLanguage = EpsilonLanguage.English

    fun selectLanguage(language: EpsilonLanguage?) {
        var target = language
        if (target == null) {
            target = EpsilonLanguage.English
        }
        selectedLanguage = target
        reload(Constants.mc.getResourceManager())
    }

    fun refreshCustomLanguage() {
        if (selectedLanguage.isCustom()) {
            reload(Constants.mc.getResourceManager())
        }
    }

    fun getSelectedLanguage(): EpsilonLanguage {
        return selectedLanguage
    }

    @Synchronized
    fun reload(resourceManager: ResourceManager?) {
        val loadedTranslations = HashMap<String, String>()
        appendLanguage(resourceManager, DEFAULT_LANGUAGE_CODE, loadedTranslations)

        val selectedCode = resolveSelectedLanguageCode()
        if (selectedCode.isNotBlank() && DEFAULT_LANGUAGE_CODE != selectedCode) {
            appendLanguage(resourceManager, selectedCode, loadedTranslations)
        }

        translations = java.util.Map.copyOf(loadedTranslations)
        refreshUi()
    }

    fun getOrDefault(key: String): String {
        return translations.getOrDefault(key, key)
    }

    fun has(key: String): Boolean {
        return translations.containsKey(key)
    }

    private fun appendLanguage(resourceManager: ResourceManager?, languageCode: String, output: MutableMap<String, String>) {
        if (resourceManager == null) {
            return
        }

        val path = String.format(Locale.ROOT, "%s/%s.json", LANGUAGE_DIRECTORY, languageCode)
        for (namespace in resourceManager.namespaces) {
            try {
                val location = Identifier.fromNamespaceAndPath(namespace, path)
                appendResources(location, resourceManager.getResourceStack(location), output)
            } catch (exception: Exception) {
                Constants.LOGGER.warn("跳过 Epsilon 语言文件: {}:{} ({})", namespace, path, exception.toString())
            }
        }
    }

    private fun appendResources(location: Identifier, resources: List<Resource>, output: MutableMap<String, String>) {
        for (resource in resources) {
            try {
                InputStreamReader(resource.open(), StandardCharsets.UTF_8).use { reader ->
                    val entries = GSON.fromJson(reader, JsonObject::class.java)
                    if (entries == null) {
                        return@use
                    }
                    I18NJson.read(entries) { key, value ->
                        val text = GsonHelper.convertToString(value, key)
                        output[key] = UNSUPPORTED_FORMAT_PATTERN.matcher(text).replaceAll("%\$1s")
                    }
                }
            } catch (exception: IOException) {
                Constants.LOGGER.warn("读取 Epsilon 语言文件失败: {} from pack {} ({})", location, resource.sourcePackId(), exception.toString())
            } catch (exception: JsonParseException) {
                Constants.LOGGER.warn("读取 Epsilon 语言文件失败: {} from pack {} ({})", location, resource.sourcePackId(), exception.toString())
            }
        }
    }

    private fun refreshUi() {
        TranslationManager.refresh()
    }

    private fun resolveSelectedLanguageCode(): String {
        if (!selectedLanguage.isCustom()) {
            return selectedLanguage.code
        }

        try {
            return ClientSetting.customLanguage
                .trim()
                .lowercase(Locale.ROOT)
        } catch (ignored: Throwable) {
            return ""
        }
    }

}
