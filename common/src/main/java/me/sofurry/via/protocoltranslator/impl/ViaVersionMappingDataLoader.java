package me.sofurry.via.protocoltranslator.impl;

import com.viaversion.viaversion.api.data.MappingDataLoader;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

public final class ViaVersionMappingDataLoader extends MappingDataLoader {

    public static final Map<String, Material> MATERIALS = new HashMap<>();
    public static final Map<String, Map<ProtocolVersion, String>> BLOCK_MATERIALS = new HashMap<>();

    public static final ViaVersionMappingDataLoader INSTANCE = new ViaVersionMappingDataLoader();

    private ViaVersionMappingDataLoader() {
        super(ViaVersionMappingDataLoader.class, "assets/viafabricplus/data/");

        final JsonObject materialsData = this.loadData("materials-1.19.4.json");
        for (Map.Entry<String, JsonElement> entry : materialsData.getAsJsonObject("materials").entrySet()) {
            final JsonObject materialData = entry.getValue().getAsJsonObject();
            MATERIALS.put(entry.getKey(), new Material(
                    materialData.get("blocksMovement").getAsBoolean(),
                    materialData.get("burnable").getAsBoolean(),
                    materialData.get("liquid").getAsBoolean(),
                    materialData.get("blocksLight").getAsBoolean(),
                    materialData.get("replaceable").getAsBoolean(),
                    materialData.get("solid").getAsBoolean()
            ));
        }
        for (Map.Entry<String, JsonElement> blockEntry : materialsData.getAsJsonObject("blocks").entrySet()) {
            final Map<ProtocolVersion, String> blockMaterials = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : blockEntry.getValue().getAsJsonObject().entrySet()) {
                final ProtocolVersion version = ProtocolVersion.getClosest(entry.getKey());
                if (version == null) {
                    throw new IllegalStateException("Unknown protocol version: " + entry.getKey());
                }
                blockMaterials.put(version, entry.getValue().getAsString());
            }
            BLOCK_MATERIALS.put(blockEntry.getKey(), blockMaterials);
        }
    }

    public static String getBlockMaterial(final Block block) {
        return getBlockMaterial(block, ProtocolTranslator.getTargetVersion());
    }

    public static String getBlockMaterial(final Block block, ProtocolVersion version) {
        if (version.newerThan(ProtocolVersion.v1_19_4)) {
            version = ProtocolVersion.v1_19_4;
        }

        final Map<ProtocolVersion, String> materials = BLOCK_MATERIALS.get(BuiltInRegistries.BLOCK.getKey(block).toString());
        if (materials == null) {
            return null;
        }

        for (Map.Entry<ProtocolVersion, String> materialEntry : materials.entrySet()) {
            if (version.olderThanOrEqualTo(materialEntry.getKey())) {
                return materialEntry.getValue();
            }
        }
        return null;
    }

    public record Material(boolean blocksMovement, boolean burnable, boolean liquid, boolean blocksLight,
                           boolean replaceable, boolean solid) {
    }

}
