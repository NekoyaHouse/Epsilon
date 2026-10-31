package com.github.epsilon.modules.impl.render

import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.ShaderManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.SettingGroup
import com.github.epsilon.settings.group
import com.github.epsilon.settings.impl.EnumSetting
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
    private val self by boolSetting("Self", true, { players }).group(sgTargets)
    private val friends by boolSetting("Friends", true).group(sgTargets)
    private val crystals by boolSetting("Crystals", true).group(sgTargets)
    private val chests by boolSetting("Chests", true).group(sgTargets)
    private val creatures by boolSetting("Creatures", false).group(sgTargets)
    private val monsters by boolSetting("Monsters", false).group(sgTargets)
    private val ambients by boolSetting("Ambients", false).group(sgTargets)
    private val others by boolSetting("Others", false).group(sgTargets)

    val maxRange by intSetting(
        "Max Range", 64, 16..256, 1,
        { hasEntityTargets() || chests }
    ).group(sgTargets)

    val entityShader: ShaderSettings = ShaderSettings("", sgEntityShader, { hasEntityTargets() })
    val friendsColor by colorSetting("Friends Color", Color(0x30FF00), false, { friends }).group(sgEntityShader)
    val handsShader: ShaderSettings = ShaderSettings("Hands ", sgHandsShader, { hands })
    val chestShader: ShaderSettings = ShaderSettings("Chest ", sgChestShader, { chests })

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
                if (settings.glowColorMode.`is`(GlowColorMode.Gradient)) settings.glowColor1 else settings.glowColor
            return glowColor.rgb
        }
        return settings.outlineColor.rgb
    }

    fun getOutlineColor(entity: Entity, settings: ShaderSettings): Int {
        if (settings === entityShader && isFriendOrSameTeam(entity)) {
            return friendsColor.rgb
        }
        return getOutlineColor(settings)
    }

    private fun isFriendOrSameTeam(entity: Entity): Boolean = entity !== mc.player &&
            entity is Player &&
            (FriendManager.isFriend(entity) || TargetManager.isSameTeam(entity))

    private fun hasEntityTargets(): Boolean = players || friends || crystals || creatures ||
            monsters || ambients || others

    class ShaderSettings internal constructor(
        prefix: String,
        group: SettingGroup,
        targetEnabled: () -> Boolean,
    ) {

        val mode: EnumSetting<ShaderManager.Shader> = enumSetting(
            prefix + "Mode",
            ShaderManager.Shader.Default,
            { targetEnabled() }
        ).group(group)
        val outlineMode: EnumSetting<OutlineMode> =
            enumSetting(prefix + "Outline Mode", OutlineMode.Outline, { targetEnabled() }).group(group)

        private val outlineAvailable: () -> Boolean =
            { targetEnabled() && outlineMode.`is`(OutlineMode.Outline) }
        private val glowAvailable: () -> Boolean =
            { targetEnabled() && outlineMode.`is`(OutlineMode.Glow) }

        val glowColorMode: EnumSetting<GlowColorMode> = enumSetting(
            prefix + "Glow Color Mode",
            GlowColorMode.Solid,
            { glowAvailable() }
        ).group(group)
        val glowRadius by intSetting(prefix + "Glow Radius", 4, 2..30, 1, { glowAvailable() }).group(group)
        val glowExposure by doubleSetting(
            prefix + "Glow Exposure",
            2.2,
            0.5..3.5,
            0.1,
            { glowAvailable() }
        ).group(group)
        var glowColor by colorSetting(
            prefix + "Glow Color", Color(255, 183, 197),
            true,
            { glowAvailable() && glowColorMode.`is`(GlowColorMode.Solid) }
        ).group(group)
        val glowColor1 by colorSetting(
            prefix + "Glow Color 1", Color(255, 183, 197),
            true,
            { glowAvailable() && glowColorMode.`is`(GlowColorMode.Gradient) }
        ).group(group)
        val glowColor2 by colorSetting(
            prefix + "Glow Color 2", Color(0, 255, 200),
            true,
            { glowAvailable() && glowColorMode.`is`(GlowColorMode.Gradient) }
        ).group(group)
        val glowGradientSpeed by doubleSetting(
            prefix + "Glow Gradient Speed", 2.0, 0.1..10.0, 0.1,
            { glowAvailable() && glowColorMode.`is`(GlowColorMode.Gradient) }
        ).group(group)
        val factor by doubleSetting(
            prefix + "Gradient Factor", 2.0, 0.0..20.0, 0.1,
            { outlineAvailable() && mode.`is`(ShaderManager.Shader.Gradient) }
        ).group(group)
        val gradient by doubleSetting(
            prefix + "Gradient", 2.0, 0.0..20.0, 0.1,
            { outlineAvailable() && mode.`is`(ShaderManager.Shader.Gradient) }
        ).group(group)
        val gradientAlpha by intSetting(
            prefix + "Gradient Alpha", 170, 0..255, 1,
            { outlineAvailable() && mode.`is`(ShaderManager.Shader.Gradient) }
        ).group(group)
        val lineWidth by intSetting(
            prefix + "Line Width", 2, 0..500, 1,
            { outlineAvailable() && !mode.`is`(ShaderManager.Shader.Snow) }
        ).group(group)
        val quality by intSetting(prefix + "Quality", 3, 0..6, 1, { outlineAvailable() }).group(group)
        val octaves by intSetting(
            prefix + "Smoke Octaves", 10, 5..30, 1,
            {
                outlineAvailable() &&
                        (mode.`is`(ShaderManager.Shader.Smoke) || mode.`is`(ShaderManager.Shader.Gradient))
            }
        ).group(group)
        val fillAlpha by intSetting(
            prefix + "Fill Alpha", 170, 0..255, 1,
            {
                outlineAvailable() &&
                        (mode.`is`(ShaderManager.Shader.Smoke) ||
                                mode.`is`(ShaderManager.Shader.Gradient) ||
                                mode.`is`(ShaderManager.Shader.Fade))
            }
        ).group(group)
        val glow by boolSetting(
            prefix + "Smoke Glow", true,
            { outlineAvailable() && !mode.`is`(ShaderManager.Shader.Snow) }
        ).group(group)
        val outlineColor by colorSetting(
            prefix + "Outline", Color(255, 255, 255, 136),
            true,
            {
                outlineAvailable() && !mode.`is`(ShaderManager.Shader.Gradient) && !mode.`is`(ShaderManager.Shader.Snow)
            }
        ).group(group)
        val smokeOutlineColor1 by colorSetting(
            prefix + "Smoke Outline", Color(255, 255, 255, 136),
            true,
            { outlineAvailable() && mode.`is`(ShaderManager.Shader.Smoke) }
        ).group(group)
        val smokeOutlineColor2 by colorSetting(
            prefix + "Smoke Outline 2", Color(250, 250, 250, 136),
            true,
            { outlineAvailable() && mode.`is`(ShaderManager.Shader.Smoke) }
        ).group(group)
        val fillColor1 by colorSetting(
            prefix + "Fill", Color(255, 255, 255, 136),
            true,
            { outlineAvailable() && !mode.`is`(ShaderManager.Shader.Gradient) }
        ).group(group)
        val fillColor2 by colorSetting(
            prefix + "Smoke Fill", Color(255, 255, 255, 136),
            true,
            {
                outlineAvailable() &&
                        (mode.`is`(ShaderManager.Shader.Smoke) || mode.`is`(ShaderManager.Shader.Fade))
            }
        ).group(group)
        val fillColor3 by colorSetting(
            prefix + "Smoke Fill 2", Color(255, 255, 255, 136),
            true,
            { outlineAvailable() && mode.`is`(ShaderManager.Shader.Smoke) }
        ).group(group)

    }

}
