package com.github.epsilon.modules.impl.render

import com.github.epsilon.managers.FriendManager
import com.github.epsilon.settings.getValue
import com.github.epsilon.managers.ShaderManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import java.awt.Color

object Shaders : Module("Shaders", Category.RENDER) {

    enum class OutlineMode {
        Outline,
        Glow
    }

    enum class GlowColorMode {
        Solid,
        Gradient
    }

    private val sgTargets: SettingGroup = settingGroup("Targets")
    private val sgEntityShader: SettingGroup = settingGroup("Entity Shader")
    private val sgHandsShader: SettingGroup = settingGroup("Hands Shader")
    private val sgChestShader: SettingGroup = settingGroup("Chest Shader")

    val hands by boolSetting("Hands", true).group(sgTargets)
    private val players by boolSetting("Players", true).group(sgTargets)
    private val self by boolSetting("Self", true, Setting.Dependency { players }).group(sgTargets)
    private val friends by boolSetting("Friends", true).group(sgTargets)
    private val crystals by boolSetting("Crystals", true).group(sgTargets)
    private val chests by boolSetting("Chests", true).group(sgTargets)
    private val creatures by boolSetting("Creatures", false).group(sgTargets)
    private val monsters by boolSetting("Monsters", false).group(sgTargets)
    private val ambients by boolSetting("Ambients", false).group(sgTargets)
    private val others by boolSetting("Others", false).group(sgTargets)

    val maxRange by intSetting(
        "Max Range", 64, 16, 256, 1,
        Setting.Dependency { hasEntityTargets() || chests }
    ).group(sgTargets)

    val entityShader: ShaderSettings = ShaderSettings("", sgEntityShader, Setting.Dependency { hasEntityTargets() })
    val friendsColor by colorSetting("Friends Color", Color(0x30FF00), false, Setting.Dependency { friends }).group(sgEntityShader)
    val handsShader: ShaderSettings = ShaderSettings("Hands ", sgHandsShader, Setting.Dependency { hands })
    val chestShader: ShaderSettings = ShaderSettings("Chest ", sgChestShader, Setting.Dependency { chests })

    fun shouldRenderChest(blockPos: BlockPos): Boolean {
        val player = mc.player ?: return false
        return chests && player.blockPosition().distSqr(blockPos) <= maxRange * maxRange
    }

    fun shouldRender(entity: Entity): Boolean {
        val player = mc.player ?: return false

        if (player.distanceToSqr(entity.position()) > maxRange * maxRange) {
            return false
        }

        if (entity is Player) {
            if (entity === player && !self) {
                return false
            }
            if (isFriendOrSameTeam(entity)) {
                return friends
            }
            return players
        }

        if (entity is EndCrystal) {
            return crystals
        }

        return when (entity.type.category) {
            MobCategory.CREATURE, MobCategory.WATER_CREATURE -> creatures
            MobCategory.MONSTER -> monsters
            MobCategory.AMBIENT, MobCategory.WATER_AMBIENT -> ambients
            else -> others
        }
    }

    fun getOutlineColor(settings: ShaderSettings): Int {
        if (settings.outlineMode.`is`(OutlineMode.Glow)) {
            val glowColor =
                if (settings.glowColorMode.`is`(GlowColorMode.Gradient)) settings.glowColor1.value else settings.glowColor.value
            return glowColor.rgb
        }
        return settings.outlineColor.value.rgb
    }

    fun getOutlineColor(entity: Entity, settings: ShaderSettings): Int {
        if (settings === entityShader && isFriendOrSameTeam(entity)) {
            return friendsColor.rgb
        }
        return getOutlineColor(settings)
    }

    private fun isFriendOrSameTeam(entity: Entity): Boolean = entity !== mc.player &&
            entity is Player &&
            (FriendManager.INSTANCE.isFriend(entity) || TargetManager.INSTANCE.isSameTeam(entity))

    private fun hasEntityTargets(): Boolean = players || friends || crystals || creatures ||
            monsters || ambients || others

    class ShaderSettings internal constructor(
        prefix: String,
        group: SettingGroup,
        targetEnabled: Setting.Dependency,
    ) {

        val mode: EnumSetting<ShaderManager.Shader> by Shaders.enumSetting(prefix + "Mode", ShaderManager.Shader.Default, targetEnabled).group(group)
        val outlineMode: EnumSetting<OutlineMode> by Shaders.enumSetting(prefix + "Outline Mode", OutlineMode.Outline, targetEnabled).group(group)

        private val outlineAvailable: Setting.Dependency =
            Setting.Dependency { targetEnabled.check() && outlineMode.`is`(OutlineMode.Outline) }
        private val glowAvailable: Setting.Dependency =
            Setting.Dependency { targetEnabled.check() && outlineMode.`is`(OutlineMode.Glow) }

        val glowColorMode: EnumSetting<GlowColorMode> by Shaders.enumSetting(prefix + "Glow Color Mode", GlowColorMode.Solid, glowAvailable).group(group)
        val glowRadius by Shaders.intSetting(prefix + "Glow Radius", 4, 2, 30, 1, glowAvailable).group(group)
        val glowExposure by Shaders.doubleSetting(prefix + "Glow Exposure", 2.2, 0.5, 3.5, 0.1, glowAvailable).group(group)
        val glowColor by Shaders.colorSetting(
            prefix + "Glow Color", Color(255, 183, 197),
            Setting.Dependency { glowAvailable.check() && glowColorMode.`is`(GlowColorMode.Solid) }
        ).group(group)
        val glowColor1 by Shaders.colorSetting(
            prefix + "Glow Color 1", Color(255, 183, 197),
            Setting.Dependency { glowAvailable.check() && glowColorMode.`is`(GlowColorMode.Gradient) }
        ).group(group)
        val glowColor2 by Shaders.colorSetting(
            prefix + "Glow Color 2", Color(0, 255, 200),
            Setting.Dependency { glowAvailable.check() && glowColorMode.`is`(GlowColorMode.Gradient) }
        ).group(group)
        val glowGradientSpeed by Shaders.doubleSetting(
            prefix + "Glow Gradient Speed", 2.0, 0.1, 10.0, 0.1,
            Setting.Dependency { glowAvailable.check() && glowColorMode.`is`(GlowColorMode.Gradient) }
        ).group(group)
        val factor by Shaders.doubleSetting(
            prefix + "Gradient Factor", 2.0, 0.0, 20.0, 0.1,
            Setting.Dependency { outlineAvailable.check() && mode.`is`(ShaderManager.Shader.Gradient) }
        ).group(group)
        val gradient by Shaders.doubleSetting(
            prefix + "Gradient", 2.0, 0.0, 20.0, 0.1,
            Setting.Dependency { outlineAvailable.check() && mode.`is`(ShaderManager.Shader.Gradient) }
        ).group(group)
        val gradientAlpha by Shaders.intSetting(
            prefix + "Gradient Alpha", 170, 0, 255, 1,
            Setting.Dependency { outlineAvailable.check() && mode.`is`(ShaderManager.Shader.Gradient) }
        ).group(group)
        val lineWidth by Shaders.intSetting(
            prefix + "Line Width", 2, 0, 500, 1,
            Setting.Dependency { outlineAvailable.check() && !mode.`is`(ShaderManager.Shader.Snow) }
        ).group(group)
        val quality by Shaders.intSetting(prefix + "Quality", 3, 0, 6, 1, outlineAvailable).group(group)
        val octaves by Shaders.intSetting(
            prefix + "Smoke Octaves", 10, 5, 30, 1,
            Setting.Dependency {
                outlineAvailable.check() &&
                        (mode.`is`(ShaderManager.Shader.Smoke) || mode.`is`(ShaderManager.Shader.Gradient))
            }
        ).group(group)
        val fillAlpha by Shaders.intSetting(
            prefix + "Fill Alpha", 170, 0, 255, 1,
            Setting.Dependency {
                outlineAvailable.check() &&
                        (mode.`is`(ShaderManager.Shader.Smoke) ||
                                mode.`is`(ShaderManager.Shader.Gradient) ||
                                mode.`is`(ShaderManager.Shader.Fade))
            }
        ).group(group)
        val glow by Shaders.boolSetting(
            prefix + "Smoke Glow", true,
            Setting.Dependency { outlineAvailable.check() && !mode.`is`(ShaderManager.Shader.Snow) }
        ).group(group)
        val outlineColor by Shaders.colorSetting(
            prefix + "Outline", Color(255, 255, 255, 136),
            Setting.Dependency {
                outlineAvailable.check() && !mode.`is`(ShaderManager.Shader.Gradient) && !mode.`is`(ShaderManager.Shader.Snow)
            }
        ).group(group)
        val smokeOutlineColor1 by Shaders.colorSetting(
            prefix + "Smoke Outline", Color(255, 255, 255, 136),
            Setting.Dependency { outlineAvailable.check() && mode.`is`(ShaderManager.Shader.Smoke) }
        ).group(group)
        val smokeOutlineColor2 by Shaders.colorSetting(
            prefix + "Smoke Outline 2", Color(250, 250, 250, 136),
            Setting.Dependency { outlineAvailable.check() && mode.`is`(ShaderManager.Shader.Smoke) }
        ).group(group)
        val fillColor1 by Shaders.colorSetting(
            prefix + "Fill", Color(255, 255, 255, 136),
            Setting.Dependency { outlineAvailable.check() && !mode.`is`(ShaderManager.Shader.Gradient) }
        ).group(group)
        val fillColor2 by Shaders.colorSetting(
            prefix + "Smoke Fill", Color(255, 255, 255, 136),
            Setting.Dependency {
                outlineAvailable.check() &&
                        (mode.`is`(ShaderManager.Shader.Smoke) || mode.`is`(ShaderManager.Shader.Fade))
            }
        ).group(group)
        val fillColor3 by Shaders.colorSetting(
            prefix + "Smoke Fill 2", Color(255, 255, 255, 136),
            Setting.Dependency { outlineAvailable.check() && mode.`is`(ShaderManager.Shader.Smoke) }
        ).group(group)

    }

}
