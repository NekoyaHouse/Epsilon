package com.github.epsilon.assets.i18n

import com.github.epsilon.Constants
import com.github.epsilon.managers.HudElementManager
import com.github.epsilon.managers.ModuleManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.impl.EnumSetting
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File
import java.io.FileWriter
import java.io.IOException

object I18NFileGenerator {

    private const val EPSILON_OWNER = "epsilon"
    private const val PREFIX = "$EPSILON_OWNER."

    fun generate(filePath: String) {
        generate(filePath, null)
    }

    /**
     * 生成指定 owner 的空翻译模板。
     *
     * @param filePath 输出文件路径
     * @param ownerId  `epsilon`，或使用 `null` / 空字符串生成全部
     */
    fun generate(filePath: String, ownerId: String?) {
        val selectedOwner = normalizeOwner(ownerId)
        val root = JsonObject()
        val gson = GsonBuilder().setPrettyPrinting().create()
        var matched = false

        if (matchesOwner(EPSILON_OWNER, selectedOwner)) {
            addEpsilonKeys(root)
            matched = true
        }

        for (module in ModuleManager.getModules()) {
            if (matchesOwner(EPSILON_OWNER, selectedOwner)) {
                addModuleKeys(root, module)
                matched = true
            }
        }

        for (module in HudElementManager.getElements()) {
            if (matchesOwner(EPSILON_OWNER, selectedOwner)) {
                addModuleKeys(root, module)
                matched = true
            }
        }

        if (selectedOwner != null && !matched) {
            throw IllegalArgumentException("Unknown i18n owner: $selectedOwner")
        }

        val file = File(filePath)
        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (e: IOException) {
                Constants.LOGGER.warn("Failed to create i18n file: {}", filePath, e)
            }
        }

        try {
            FileWriter(filePath).use { writer ->
                gson.toJson(root, writer)
                Constants.LOGGER.info("I18N file generated successfully at: {}", filePath)
            }
        } catch (e: IOException) {
            Constants.LOGGER.warn("Failed to write i18n file: {}", filePath, e)
        }
    }

    private fun addEpsilonKeys(root: JsonObject) {
        I18NJson.addTranslation(root, EPSILON_OWNER, "")
        for (category in Category.entries) {
            val catKey = PREFIX + "categories." + category.toString().lowercase()
            I18NJson.addTranslation(root, catKey, "")
        }

        for (component in EpsilonTranslations.all()) {
            I18NJson.addTranslation(root, component.getFullKey(), "")
        }
    }

    private fun addModuleKeys(root: JsonObject, module: Module) {
        val translateComponent = module.translateComponent ?: return
        val moduleKey = translateComponent.getFullKey()
        I18NJson.addTranslation(root, moduleKey, "")

        for (group in module.settingGroups) {
            addSettingGroupKey(root, group)
        }

        for (setting in module.settings) {
            addSettingKey(root, setting)
        }
    }

    private fun addSettingGroupKey(root: JsonObject, group: SettingGroup) {
        val component = group.translateComponent
        if (component != null) {
            I18NJson.addTranslation(root, component.getFullKey(), "")
        }
        for (child in group.children) {
            addSettingGroupKey(root, child)
        }
    }

    private fun addSettingKey(root: JsonObject, setting: Setting<*>) {
        val component = setting.translateComponent ?: return
        val settingKey = component.getFullKey()
        I18NJson.addTranslation(root, settingKey, "")

        if (setting is EnumSetting<*>) {
            for (mode in setting.modes) {
                I18NJson.addTranslation(root, settingKey + "." + mode.toString().lowercase(), "")
            }
        }
    }

    private fun matchesOwner(ownerId: String, selectedOwner: String?): Boolean {
        return selectedOwner == null || selectedOwner == ownerId
    }

    private fun normalizeOwner(ownerId: String?): String? {
        if (ownerId == null || ownerId.isBlank()) {
            return null
        }
        return ownerId.trim()
    }

}
