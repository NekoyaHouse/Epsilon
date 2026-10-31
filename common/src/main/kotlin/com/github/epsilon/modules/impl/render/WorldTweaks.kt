package com.github.epsilon.modules.impl.render

import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.core.Holder
import net.minecraft.world.clock.WorldClock
import java.awt.Color

object WorldTweaks : Module("World Tweaks", Category.RENDER) {

    val fogModify by boolSetting("Fog Modify", true).apply { rootSetting() }
    val fogStart by intSetting("Fog Start", 0, 0, 256, 1, dependency = { fogModify })
    val fogEnd by intSetting("Fog End", 64, 10, 256, 1, dependency = { fogModify })
    val fogColor by colorSetting("Fog Color", Color(0xA900FF), false, dependency = { fogModify })
    val changeTime by boolSetting("Change Time", false).apply { rootSetting() }
    val time by intSetting("Time", 21, 0, 23, 1, dependency = { changeTime })

    fun getModifiedClockTime(clock: Holder<WorldClock>, original: Long): Long {
        if (!isEnabled || !changeTime) return original
        val level = mc.level ?: return original
        return if (level.dimensionType().defaultClock().filter { clock == it }.isPresent) time * 1000L else original
    }

}
