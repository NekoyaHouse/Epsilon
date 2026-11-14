package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.ChunkOcclusionEvent
import com.github.epsilon.events.impl.PacketEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.group
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.level.block.AbstractBannerBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock

object NoRender : Module("No Render", Category.RENDER) {

    enum class BannerRenderMode {
        Everything,
        Pillar,
        None
    }

    private val overlayGroup = settingGroup("Overlay")
    private val hudGroup = settingGroup("HUD")
    private val worldGroup = settingGroup("World")
    private val entityGroup = settingGroup("Entity")

    private val noPortalOverlay by boolSetting("Portal Overlay", false).group(overlayGroup)
    private val noSpyglassOverlay by boolSetting("Spyglass Overlay", false).group(overlayGroup)
    private val noNausea by boolSetting("Nausea", false).group(overlayGroup)
    private val noPumpkinOverlay by boolSetting("Pumpkin Overlay", false).group(overlayGroup)
    private val noPowderedSnowOverlay by boolSetting("Powdered Snow Overlay", false).group(overlayGroup)
    private val noFireOverlay by boolSetting("Fire Overlay", false).group(overlayGroup)
    private val noLiquidOverlay by boolSetting("Liquid Overlay", false).group(overlayGroup)
    private val noInWallOverlay by boolSetting("In Wall Overlay", false).group(overlayGroup)
    private val noVignette by boolSetting("Vignette", false).group(overlayGroup)
    private val noGuiBackground by boolSetting("GUI Background", false).group(overlayGroup)
    private val noTotemAnimation by boolSetting("Totem Animation", false).group(overlayGroup)
    private val noEatParticles by boolSetting("Eating Particles", false).group(overlayGroup)
    private val noEnchantGlint by boolSetting("Enchantment Glint", false).group(overlayGroup)

    private val noBossBar by boolSetting("Boss Bar", false).group(hudGroup)
    private val noScoreboard by boolSetting("Scoreboard", false).group(hudGroup)
    private val noCrosshair by boolSetting("Crosshair", false).group(hudGroup)
    private val noTitle by boolSetting("Title", false).group(hudGroup)
    private val noHeldItemName by boolSetting("Held Item Name", false).group(hudGroup)
    private val noObfuscation by boolSetting("Obfuscation", false).group(hudGroup)
    private val noPotionIcons by boolSetting("Potion Icons", false).group(hudGroup)
    private val noMessageSignatureIndicator by boolSetting("Message Signature Indicator", false).group(hudGroup)

    private val noWeather by boolSetting("Weather", false).group(worldGroup)
    private val noWorldBorder by boolSetting("World Border", false).group(worldGroup)
    private val noBlindness by boolSetting("Blindness", false).group(worldGroup)
    private val noDarkness by boolSetting("Darkness", false).group(worldGroup)
    private val noFog by boolSetting("Fog", false).group(worldGroup)
    private val noEnchTableBook by boolSetting("Enchantment Table Book", false).group(worldGroup)
    private val noSignText by boolSetting("Sign Text", false).group(worldGroup)
    private val noBlockBreakOverlay by boolSetting("Block Break Overlay", false).group(worldGroup)
    private val noBeaconBeams by boolSetting("Beacon Beams", false).group(worldGroup)
    private val noFallingBlocks by boolSetting("Falling Blocks", false).group(worldGroup)
    private val noCaveCulling by boolSetting(
        "Cave Culling",
        false,
        onChanged = { mc.levelExtractor.allChanged() }).group(worldGroup)
    private val noMapMarkers by boolSetting("Map Markers", false).group(worldGroup)
    private val noMapContents by boolSetting("Map Contents", false).group(worldGroup)
    private val bannerRender by enumSetting("Banners", BannerRenderMode.Everything).group(worldGroup)
    private val noFireworkExplosions by boolSetting("Firework Explosions", false).group(worldGroup)
    private val particles by particleTypeListSetting("Particles", emptyList()).group(worldGroup)
    private val noBarrierInvis by boolSetting("Barrier Invisibility", false).group(worldGroup)
    private val noTextureRotations by boolSetting(
        "Texture Rotations",
        false,
        onChanged = { mc.levelExtractor.allChanged() }).group(worldGroup)
    private val blockEntities by blockListSetting(
        "Block Entities",
        emptyList(),
        filter = { it is EntityBlock && it !is AbstractBannerBlock }).group(worldGroup)

    private val entities by entityTypeListSetting("Entities", emptyList()).group(entityGroup)
    private val dropSpawnPacket by boolSetting("Drop Spawn Packets", false).group(entityGroup)
    private val noArmor by boolSetting("Armor", false).group(entityGroup)
    private val noInvisibility by boolSetting("Invisibility", false).group(entityGroup)
    private val noGlowing by boolSetting("Glowing", false).group(entityGroup)
    private val noMobInSpawner by boolSetting("Spawner Entities", false).group(entityGroup)
    private val noDeadEntities by boolSetting("Dead Entities", false).group(entityGroup)
    private val noNametags by boolSetting("Nametags", false).group(entityGroup)

    init {
        onEnable {
            if (noCaveCulling || noTextureRotations) mc.levelExtractor.allChanged()
        }

        onDisable {
            if (noCaveCulling || noTextureRotations) mc.levelExtractor.allChanged()
        }

        handler<ChunkOcclusionEvent> {
            if (noCaveCulling) it.cancel()
        }

        handler<PacketEvent.Receive> {
            val packet = it.packet
            if (dropSpawnPacket && packet is ClientboundAddEntityPacket && packet.type in entities) it.cancel()
        }
    }

    fun noPortalOverlay(): Boolean = isEnabled && noPortalOverlay

    fun noSpyglassOverlay(): Boolean = isEnabled && noSpyglassOverlay

    fun noNausea(): Boolean = isEnabled && noNausea

    fun noPumpkinOverlay(): Boolean = isEnabled && noPumpkinOverlay

    fun noPowderedSnowOverlay(): Boolean = isEnabled && noPowderedSnowOverlay

    fun noFireOverlay(): Boolean = isEnabled && noFireOverlay

    fun noLiquidOverlay(): Boolean = isEnabled && noLiquidOverlay

    fun noInWallOverlay(): Boolean = isEnabled && noInWallOverlay

    fun noVignette(): Boolean = isEnabled && noVignette

    fun noGuiBackground(): Boolean = isEnabled && noGuiBackground

    fun noTotemAnimation(): Boolean = isEnabled && noTotemAnimation

    fun noEatParticles(): Boolean = isEnabled && noEatParticles

    fun noEnchantGlint(): Boolean = isEnabled && noEnchantGlint

    fun noBossBar(): Boolean = isEnabled && noBossBar

    fun noScoreboard(): Boolean = isEnabled && noScoreboard

    fun noCrosshair(): Boolean = isEnabled && noCrosshair

    fun noTitle(): Boolean = isEnabled && noTitle

    fun noHeldItemName(): Boolean = isEnabled && noHeldItemName

    fun noObfuscation(): Boolean = isEnabled && noObfuscation

    fun noPotionIcons(): Boolean = isEnabled && noPotionIcons

    fun noMessageSignatureIndicator(): Boolean = isEnabled && noMessageSignatureIndicator

    fun noWeather(): Boolean = isEnabled && noWeather

    fun noWorldBorder(): Boolean = isEnabled && noWorldBorder

    fun noBlindness(): Boolean = isEnabled && noBlindness

    fun noDarkness(): Boolean = isEnabled && noDarkness

    fun noFog(): Boolean = isEnabled && noFog

    fun noEnchTableBook(): Boolean = isEnabled && noEnchTableBook

    fun noSignText(): Boolean = isEnabled && noSignText

    fun noBlockBreakOverlay(): Boolean = isEnabled && noBlockBreakOverlay

    fun noBeaconBeams(): Boolean = isEnabled && noBeaconBeams

    fun noFallingBlocks(): Boolean = isEnabled && noFallingBlocks

    fun noCaveCulling(): Boolean = isEnabled && noCaveCulling

    fun noMapMarkers(): Boolean = isEnabled && noMapMarkers

    fun noMapContents(): Boolean = isEnabled && noMapContents

    fun noFireworkExplosions(): Boolean = isEnabled && noFireworkExplosions

    fun noBarrierInvis(): Boolean = isEnabled && noBarrierInvis

    fun noTextureRotations(): Boolean = isEnabled && noTextureRotations

    fun noArmor(): Boolean = isEnabled && noArmor

    fun noInvisibility(): Boolean = isEnabled && noInvisibility

    fun noGlowing(): Boolean = isEnabled && noGlowing

    fun noMobInSpawner(): Boolean = isEnabled && noMobInSpawner

    fun noDeadEntities(): Boolean = isEnabled && noDeadEntities

    fun noNametags(): Boolean = isEnabled && noNametags

    fun getBannerRenderMode(): BannerRenderMode = if (isEnabled) bannerRender else BannerRenderMode.Everything

    fun noParticle(type: ParticleType<*>): Boolean =
        isEnabled && (type in particles || noWeather && type == ParticleTypes.RAIN ||
                noFireworkExplosions && type == ParticleTypes.FIREWORK)

    fun noBlockEntity(block: Block): Boolean = isEnabled && block in blockEntities

    fun noEntity(entity: Entity): Boolean = isEnabled && (
            entity.type in entities || noFallingBlocks && entity is FallingBlockEntity ||
                    noDeadEntities && entity is LivingEntity && entity.isDeadOrDying
            )

}
