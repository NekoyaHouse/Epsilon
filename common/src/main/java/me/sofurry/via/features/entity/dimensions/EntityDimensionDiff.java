package me.sofurry.via.features.entity.dimensions;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.gson.JsonObject;
import me.sofurry.via.ViaVersionImpl;
import me.sofurry.via.protocoltranslator.impl.ViaVersionMappingDataLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * Data dump for entity dimension changes between versions.
 */
public class EntityDimensionDiff {

    /**
     * A map of entity types to a map of versions to dimensions.
     */
    private static final Map<EntityType<?>, Map<ProtocolVersion, EntityDimensions>> ENTITY_DIMENSIONS = new HashMap<>();

    public static void init() {
        // TODO: Find a way to isolate baby (or other conditional dimension changes) across entities with same code patterns
        // to reduce the need of manual mixing into each entity
        final JsonObject dimensionDiff = ViaVersionMappingDataLoader.INSTANCE.loadData("entity-dimensions.json");
        for (final String entity : dimensionDiff.keySet()) {
            final EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(entity)).orElse(null);
            if (entityType == null) {
                throw new IllegalStateException("Unknown entity: " + entity);
            }

            final JsonObject versions = dimensionDiff.getAsJsonObject(entity);
            final Map<ProtocolVersion, EntityDimensions> dimensionMap = new HashMap<>();
            for (final String version : versions.keySet()) {
                final ProtocolVersion protocolVersion = ProtocolVersion.getClosest(version);
                if (protocolVersion == null) {
                    throw new IllegalStateException("Unknown protocol version: " + version);
                }

                final JsonObject dimensionData = versions.getAsJsonObject(version);
                final float width = dimensionData.get("width").getAsFloat();
                final float height = dimensionData.get("height").getAsFloat();
                final float eyeHeight = dimensionData.get("eyeHeight").getAsFloat();
                final boolean fixed = dimensionData.get("fixed").getAsBoolean();
                final EntityDimensions entityDimensions = new EntityDimensions(width, height, eyeHeight, entityType.dimensions.attachments(), fixed);
                dimensionMap.put(protocolVersion, entityDimensions);
            }
            ENTITY_DIMENSIONS.put(entityType, dimensionMap);
        }

        ViaVersionImpl.CHANGE_PROTOCOL_VERSION.register((oldVersion, newVersion) -> Minecraft.getInstance().execute(() -> ENTITY_DIMENSIONS.forEach((entityType, dimensionMap) -> {
            for (Map.Entry<ProtocolVersion, EntityDimensions> entry : dimensionMap.entrySet()) {
                final ProtocolVersion version = entry.getKey();
                final EntityDimensions dimensions = entry.getValue();
                if (oldVersion.newerThan(version) && newVersion.olderThanOrEqualTo(version)) {
                    entityType.dimensions = dimensions;
                    break;
                }
                if (newVersion.newerThanOrEqualTo(version) && oldVersion.olderThanOrEqualTo(version)) {
                    entityType.dimensions = dimensions;
                }
            }
        })));
    }

}
