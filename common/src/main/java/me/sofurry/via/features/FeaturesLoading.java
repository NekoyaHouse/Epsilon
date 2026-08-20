package me.sofurry.via.features;

import com.viaversion.viaaprilfools.api.AprilFoolsProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import me.sofurry.via.ViaVersionImpl;
import me.sofurry.via.features.block.connections.BlockConnectionsEmulation1_12_2;
import me.sofurry.via.features.block.shape.CollisionShapes;
import me.sofurry.via.features.classic.cpe_extension.CPEAdditions;
import me.sofurry.via.features.entity.attribute.EnchantmentAttributesEmulation1_20_6;
import me.sofurry.via.features.entity.dimensions.EntityDimensionDiff;
import me.sofurry.via.features.font.FontCacheReload;
import me.sofurry.via.features.font.RenderableGlyphDiff;
import me.sofurry.via.features.item.filter_creative_tabs.VersionedRegistries;
import me.sofurry.via.features.networking.armor_hud.ArmorHudEmulation1_8;
import me.sofurry.via.features.networking.resource_pack_header.ResourcePackHeaderDiff;
import me.sofurry.via.features.recipe.Recipes1_11_2;
import me.sofurry.via.features.world.footstep_particle.FootStepParticle1_12_2;
import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttributes;

public final class FeaturesLoading {

    // Initialize various data classes required for feature mixins
    public static void init() {
        ResourcePackHeaderDiff.init();
        RenderableGlyphDiff.init();
        FootStepParticle1_12_2.init();
        CPEAdditions.init();

        ViaVersionImpl.CHANGE_PROTOCOL_VERSION.register((oldVersion, newVersion) -> Minecraft.getInstance().execute(() -> {
            CollisionShapes.reloadBlockShapes();

            if (oldVersion.equals(AprilFoolsProtocolVersion.s3d_shareware) || newVersion.equals(AprilFoolsProtocolVersion.s3d_shareware)) {
                Minecraft.getInstance().getSoundManager().reload();
            }

            FontCacheReload.reload();

            if (newVersion.olderThanOrEqualTo(ProtocolVersion.v1_11_1)) {
                Recipes1_11_2.reset();
            }

            EnvironmentAttributes.RESPAWN_ANCHOR_WORKS.isSyncable = newVersion.olderThanOrEqualTo(ProtocolVersion.v1_21_9);
        }));
    }

    // Make sure this is called *after* ViaVersion has been initialized
    public static void postInit() {
        VersionedRegistries.init();
        EntityDimensionDiff.init();
        EnchantmentAttributesEmulation1_20_6.init();
        BlockConnectionsEmulation1_12_2.init();
        Recipes1_11_2.init();
        ArmorHudEmulation1_8.init();
    }

}
