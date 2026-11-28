package com.github.epsilon.modules.impl.combat.autocrystal

import com.github.epsilon.assets.i18n.I18NJson
import com.github.epsilon.assets.i18n.TranslateComponent
import com.github.epsilon.kernel.gameplay.player.DamageUtils
import com.github.epsilon.modules.impl.combat.crystalaura.AutoCrystal
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
        assertEquals(listOf("Place", "Break", "Base Place", "Rotate", "Face Place", "Extrapolation", "Render", "Pause", "Targeting", "Misc"), module.settingGroups.map { it.name })
        assertTrue(module.settings.all { setting -> module.settingGroups.any { it === setting.groupRef } })
        assertEquals(module.settings.size, module.settings.map { it.name.lowercase() }.distinct().size)
        assertEquals(0, module.settings.first { it.name == "Place Delay" }.value)
        assertEquals("Strong", module.settings.first { it.name == "Place Sequential" }.value.toString())
        assertEquals("Always", module.settings.first { it.name == "Break Instant" }.value.toString())
        val presetDefaults: Map<String, Any> = mapOf(
            "Place Range" to 4.0,
            "Place Wall Range" to 4.0,
            "Place Max Self Damage" to 20.0,
            "Place Damage Ratio" to 1.8,
            "Place Mine Ignore" to false,
            "Place Always" to true,
            "Break Range" to 4.0,
            "Break Wall Range" to 4.0,
            "Break Max Self Damage" to 12.0,
            "Break Anti Suicide" to false,
            "Break Await" to false,
            "Break Ticks Existed" to 1,
            "Break Weakness" to "Silent",
            "Base Place" to true,
            "Base Place Delay" to 800,
            "Rotate" to true,
            "Face Place" to true,
            "Face Place Health" to 36.0,
            "Face Place Armor Threshold" to 26,
            "Face Place Damage Sync" to true,
            "Extrapolation Ticks" to 3,
            "Render Fill" to java.awt.Color(148, 161, 244, 32),
            "Render Outline" to java.awt.Color(148, 161, 244, 255),
            "Render Fade Time" to 0.5,
            "Pause Eating" to true,
            "Targeting Target Range" to 16.0,
            "Targeting Crystal Range" to 6.0,
            "Targeting Anti Quiver" to true,
            "Targeting Assume Best Armor" to true,
            "Targeting Timeout" to 1.8,
            "Misc Auto Swap" to "Silent",
            "Misc Multipoint" to true,
        )
        for ((name, expectedValue) in presetDefaults) {
            val value = module.settings.first { it.name == name }.value
            assertEquals(expectedValue, if (expectedValue is String) value.toString() else value, name)
        }
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
