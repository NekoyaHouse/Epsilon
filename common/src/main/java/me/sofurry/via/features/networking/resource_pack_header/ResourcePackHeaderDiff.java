package me.sofurry.via.features.networking.resource_pack_header;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import me.sofurry.via.protocoltranslator.impl.ViaVersionMappingDataLoader;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.world.level.storage.DataVersion;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This class file contains the {@link WorldVersion} for each protocol version.
 */
public class ResourcePackHeaderDiff {

    private final static Map<ProtocolVersion, WorldVersion> GAME_VERSION_DIFF = new HashMap<>();

    public static void init() {
        final JsonObject diff = ViaVersionMappingDataLoader.INSTANCE.loadData("resource-pack-headers.json");
        for (final String string : diff.keySet()) {
            fill(string, diff.getAsJsonObject(string));
        }
    }

    private static void fill(final String name, final JsonObject data) {
        final ProtocolVersion version = ProtocolVersion.getProtocol(data.get("version").getAsInt());
        if (!version.isKnown()) {
            throw new IllegalStateException("Unknown protocol version: " + version.getOriginalVersion());
        }

        final JsonElement packFormat = data.get("pack_format");
        if (packFormat.isJsonObject()) {
            final int major = packFormat.getAsJsonObject().get("major").getAsInt();
            final int minor = packFormat.getAsJsonObject().get("minor").getAsInt();
            registerVersion(version, major, minor, name, name);
        } else {
            registerVersion(version, packFormat.getAsInt(), -1, name, name);
        }
    }

    /**
     * @param version The {@link ProtocolVersion} to get the {@link WorldVersion} for.
     * @return The {@link WorldVersion} for the given {@link ProtocolVersion}.
     */
    public static WorldVersion get(final ProtocolVersion version) {
        if (!GAME_VERSION_DIFF.containsKey(version)) {
            return SharedConstants.getCurrentVersion();
        } else {
            return GAME_VERSION_DIFF.get(version);
        }
    }

    private static void registerVersion(final ProtocolVersion version, final int majorVersion, final int minorVersion, final String name, final String id) {
        GAME_VERSION_DIFF.put(version, new WorldVersion() {

            @Override
            public DataVersion dataVersion() {
                return null;
            }

            @Override
            public String id() {
                return id;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public int protocolVersion() {
                return version.getOriginalVersion();
            }

            @Override
            public PackFormat packVersion(final PackType type) {
                if (type == PackType.CLIENT_RESOURCES) {
                    return PackFormat.of(majorVersion, minorVersion);
                }
                throw new UnsupportedOperationException();
            }

            @Override
            public Date buildTime() {
                return null;
            }

            @Override
            public boolean stable() {
                return true;
            }
        });
    }

}
