package com.github.epsilon.modules.impl.combat

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent
import com.github.epsilon.assets.i18n.I18NJson
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests only SelfTrap and framework metadata; no other modules are initialized. */
class SelfTrapTest {
    companion object {
        @BeforeClass @JvmStatic fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test fun upstreamSettingsAndTemplate() {
        val module = SelfTrap
        val names = "Multitask Timing Explosions Spawn Broke Surround Trap StandHead CrawlHead MineDownward Blocks Delay BPT SwapAction Center Rotate PlaceRange Protect BreakCrystal BreakRange AssumeBreak BreakTime AirPlace Support CalcDelay Multi Extend TrapExtend GroundOnly PauseOnFlying PauseInFluid DisableOnYDiff DisableOnJump DisableOnDeath".split(' ')
        assertEquals(names, module.settings.map { it.name })
        assertEquals(2, module.settings.first { it.name == "Delay" }.value)
        assertEquals(1, module.settings.first { it.name == "BPT" }.value)
        assertEquals("Hotbar", module.settings.first { it.name == "SwapAction" }.value.toString())
        assertEquals("OnActivate", module.settings.first { it.name == "Center" }.value.toString())
        assertFalse(module.settings.first { it.name == "Explosions" }.isVisible)
        @Suppress("UNCHECKED_CAST")
        val timing = module.settings.first { it.name == "Timing" } as EnumSetting<*>
        timing.setMode("Packet")
        assertTrue(module.settings.first { it.name == "Explosions" }.isVisible)
        timing.reset()
        val support = module.settings.first { it.name == "Support" } as IntSetting
        support.value = 0
        assertFalse(module.settings.first { it.name == "Multi" }.isVisible)
        support.reset()

        module.initI18n(EpsilonTranslateComponent.create("modules", "selftrap"))
        val root = JsonObject()
        I18NJson.addTranslation(root, module.translateComponent!!.getFullKey(), "")
        for (setting in module.settings) {
            val key = setting.translateComponent!!.getFullKey()
            I18NJson.addTranslation(root, key, "")
            if (setting is EnumSetting<*>) {
                for (mode in setting.modes) I18NJson.addTranslation(root, "$key.${mode.toString().lowercase()}", "")
            }
        }
        // Scope-only template, generated from actual registered settings, for complete_i18n.py.
        File("selftrap-empty-i18n.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(root))
        assertTrue(root.getAsJsonObject("epsilon").getAsJsonObject("modules").has("selftrap"))
    }
}
