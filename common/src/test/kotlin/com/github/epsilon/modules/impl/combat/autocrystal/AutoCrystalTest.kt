package com.github.epsilon.modules.impl.combat.autocrystal

import com.github.epsilon.assets.i18n.I18NJson
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.kernel.gameplay.player.DamageUtils
import com.github.epsilon.modules.impl.combat.AutoCrystal
import com.github.epsilon.settings.impl.EnumSetting
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoCrystalTest {
    private class Key(private val path: String) : TranslateComponent {
        override fun getFullKey() = path
        override fun getTranslatedName() = path
        override fun refresh() = Unit
        override fun createChild(suffix: String) = Key("$path.$suffix")
    }

    @Test fun explosionFormula() {
        assertEquals(85.0, DamageUtils.rawDamage(0.0, 1.0))
        assertEquals(32.5, DamageUtils.rawDamage(6.0, 1.0))
        assertEquals(1.0, DamageUtils.rawDamage(12.0, 1.0))
        assertEquals(1.0, DamageUtils.rawDamage(3.0, 0.0))
        assertEquals(0.0, DamageUtils.rawDamage(12.01, 1.0))
        for (distance in 0..12) assertTrue(DamageUtils.rawDamage(distance.toDouble(), 1.0) >= 0)
    }

    @Test fun settingsAndI18nTemplate() {
        val module = AutoCrystal
        module.initI18n(Key("epsilon.modules.auto crystal"))
        assertEquals(70, module.settings.size)
        assertEquals(module.settings.size, module.settings.map { it.name.lowercase() }.distinct().size)
        assertEquals(0, module.settings.first { it.name == "Place Delay" }.value)
        assertEquals("Strict", module.settings.first { it.name == "Place Sequential" }.value.toString())
        assertEquals("Always", module.settings.first { it.name == "Break Instant" }.value.toString())
        val root = JsonObject()
        I18NJson.addTranslation(root, module.translateComponent!!.getFullKey(), "")
        for (group in module.settingGroups) I18NJson.addTranslation(root, group.translateComponent!!.getFullKey(), "")
        for (setting in module.settings) {
            val path = setting.translateComponent!!.getFullKey()
            I18NJson.addTranslation(root, path, "")
            if (setting is EnumSetting<*>) for (mode in setting.modes) I18NJson.addTranslation(root, "$path.${mode.toString().lowercase()}", "")
        }
        // Generated from the actual registered settings, not an independently maintained key list.
        File("autocrystal-i18n.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(root))
        fun leaves(element: JsonElement, path: String = ""): Map<String, String> {
            if (element.isJsonObject) return element.asJsonObject.entrySet().flatMap { (key, value) ->
                leaves(value, if (path.isEmpty()) key else "$path.$key").entries.map { it.key to it.value }
            }.toMap()
            assertTrue(element.isJsonPrimitive && element.asJsonPrimitive.isString, "Non-string i18n leaf: $path")
            return mapOf(path to element.asString)
        }
        val expected = leaves(root).keys
        for (language in listOf("en_us", "zh_cn")) {
            val file = File("../src/main/resources/assets/epsilon/i18n/$language.json")
            val translations = leaves(JsonParser.parseString(file.readText()))
            for (key in expected) assertTrue(!translations[key].isNullOrBlank(), "Missing $language translation: $key")
        }
    }
}
