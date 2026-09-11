package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.RegistryListSetting;
import net.minecraft.world.entity.EntityType;

import java.util.List;

public class NoRender extends Module {

    public static final NoRender INSTANCE = new NoRender();

    private NoRender() {
        super("No Render", Category.RENDER);
    }

    // Overlay
    public final BoolSetting portalOverlay = boolSetting("Portal Overlay", false);
    public final BoolSetting spyglassOverlay = boolSetting("Spyglass Overlay", false);
    public final BoolSetting nausea = boolSetting("Nausea", false);
    public final BoolSetting fireOverlay = boolSetting("Fire Overlay", false);
    public final BoolSetting liquidOverlay = boolSetting("Liquid Overlay", false);
    public final BoolSetting blockOverlay = boolSetting("Block Overlay", true);
    public final BoolSetting vignette = boolSetting("Vignette", false);
    public final BoolSetting totemAnimation = boolSetting("Totem Animation", false);
    public final BoolSetting eatParticles = boolSetting("Eat Particles", false);
    public final BoolSetting enchantGlint = boolSetting("Enchant Glint", false);

    // HUD
    public final BoolSetting bossBar = boolSetting("Boss Bar", false);
    public final BoolSetting scoreboard = boolSetting("Scoreboard", false);
    public final BoolSetting chat = boolSetting("Chat", false);
    public final BoolSetting crosshair = boolSetting("Crosshair", false);
    public final BoolSetting title = boolSetting("Title", false);
    public final BoolSetting heldItemName = boolSetting("Held Item Name", false);
    public final BoolSetting obfuscation = boolSetting("Obfuscation", false);
    public final BoolSetting potionIcons = boolSetting("Potion Icons", false);

    // World
    public final BoolSetting weather = boolSetting("Weather", false);
    public final BoolSetting worldBorder = boolSetting("World Border", false);
    public final BoolSetting blindness = boolSetting("Blindness", false);
    public final BoolSetting darkness = boolSetting("Darkness", false);
    public final BoolSetting fog = boolSetting("Fog", false);
    public final BoolSetting enchTableBook = boolSetting("Ench Table Book", false);
    public final BoolSetting signText = boolSetting("Sign Text", false);
    public final BoolSetting blockBreakOverlay = boolSetting("Block Break Overlay", false);
    public final BoolSetting beaconBeams = boolSetting("Beacon Beams", false);
    public final BoolSetting fallingBlocks = boolSetting("Falling Blocks", false);
    public final BoolSetting caveCulling = boolSetting("Cave Culling", false, _ -> mc.levelExtractor.allChanged());
    public final BoolSetting mapMarkers = boolSetting("Map Markers", false);
    public final BoolSetting mapContents = boolSetting("Map Contents", false);
    public final BoolSetting bannerRender = boolSetting("Banners", false);
    public final BoolSetting fireworkExplosions = boolSetting("Firework Explosions", false);
    public final BoolSetting barrierInvis = boolSetting("Barrier Invisibility", false);
    public final BoolSetting textureRotations = boolSetting("Texture Rotations", false, _ -> mc.levelExtractor.allChanged());

    // Entity
    public final RegistryListSetting<EntityType<?>> entities = entityTypeListSetting("Entities", List.of());
    public final BoolSetting dropSpawnPacket = boolSetting("Drop Spawn Packet", false);
    public final BoolSetting noArmor = boolSetting("No Armor", false);
    public final BoolSetting noMobInSpawner = boolSetting("No Mob In Spawner", false);
    public final BoolSetting noDeadEntities = boolSetting("No Dead Entities", false);
    public final BoolSetting noNametags = boolSetting("No Nametags", false);

    @Override
    protected void onEnable() {
        if (caveCulling.getValue() || textureRotations.getValue()) mc.levelExtractor.allChanged();
    }

    @Override
    protected void onDisable() {
        if (caveCulling.getValue() || textureRotations.getValue()) mc.levelExtractor.allChanged();
    }

    public boolean noEntity(EntityType<?> entityType) {
        return isEnabled() && entities.contains(entityType);
    }

}
