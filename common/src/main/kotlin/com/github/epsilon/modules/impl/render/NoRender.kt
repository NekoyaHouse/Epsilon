package com.github.epsilon.modules.impl.render

import com.github.epsilon.modules.Category
import com.github.epsilon.settings.getValue
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.RegistryListSetting
import net.minecraft.world.entity.EntityType

object NoRender : Module("No Render", Category.RENDER) {

    // Overlay
    val portalOverlay by boolSetting("Portal Overlay", false)
    val spyglassOverlay by boolSetting("Spyglass Overlay", false)
    val nausea by boolSetting("Nausea", false)
    val fireOverlay by boolSetting("Fire Overlay", false)
    val liquidOverlay by boolSetting("Liquid Overlay", false)
    val blockOverlay by boolSetting("Block Overlay", true)
    val vignette by boolSetting("Vignette", false)
    val totemAnimation by boolSetting("Totem Animation", false)
    val eatParticles by boolSetting("Eat Particles", false)
    val enchantGlint by boolSetting("Enchant Glint", false)

    // HUD
    val bossBar by boolSetting("Boss Bar", false)
    val scoreboard by boolSetting("Scoreboard", false)
    val chat by boolSetting("Chat", false)
    val crosshair by boolSetting("Crosshair", false)
    val title by boolSetting("Title", false)
    val heldItemName by boolSetting("Held Item Name", false)
    val obfuscation by boolSetting("Obfuscation", false)
    val potionIcons by boolSetting("Potion Icons", false)

    // World
    val weather by boolSetting("Weather", false)
    val worldBorder by boolSetting("World Border", false)
    val blindness by boolSetting("Blindness", false)
    val darkness by boolSetting("Darkness", false)
    val fog by boolSetting("Fog", false)
    val enchTableBook by boolSetting("Ench Table Book", false)
    val signText by boolSetting("Sign Text", false)
    val blockBreakOverlay by boolSetting("Block Break Overlay", false)
    val beaconBeams by boolSetting("Beacon Beams", false)
    val fallingBlocks by boolSetting("Falling Blocks", false)
    val caveCulling by boolSetting("Cave Culling", false) { _ -> mc.levelExtractor.allChanged() }
    val mapMarkers by boolSetting("Map Markers", false)
    val mapContents by boolSetting("Map Contents", false)
    val bannerRender by boolSetting("Banners", false)
    val explosions by boolSetting("Explosions", false)
    val fireworkExplosions by boolSetting("Firework Explosions", false)
    val barrierInvis by boolSetting("Barrier Invisibility", false)
    val textureRotations by boolSetting("Texture Rotations", false) { _ -> mc.levelExtractor.allChanged() }

    // Entity
    val entities by entityTypeListSetting("Entities", listOf<EntityType<*>>())
    val dropSpawnPacket by boolSetting("Drop Spawn Packet", false)
    val noArmor by boolSetting("No Armor", false)
    val noMobInSpawner by boolSetting("No Mob In Spawner", false)
    val noDeadEntities by boolSetting("No Dead Entities", false)
    val noNametags by boolSetting("No Nametags", false)

    override fun onEnable() {
        if (caveCulling || textureRotations) mc.levelExtractor.allChanged()
    }

    override fun onDisable() {
        if (caveCulling || textureRotations) mc.levelExtractor.allChanged()
    }

    fun noEntity(entityType: EntityType<*>): Boolean = isEnabled && entities.contains(entityType)

}
