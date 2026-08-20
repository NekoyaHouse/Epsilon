package me.sofurry.via.features.font;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.IntTag;
import com.viaversion.nbt.tag.MixedListTag;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.protocoltranslator.impl.ViaVersionMappingDataLoader;

import java.util.Map;

import static com.viaversion.viaversion.api.protocol.version.ProtocolVersion.v1_20;

/**
 * Data dump which contains the {@link ProtocolVersion} for each renderable glyph. This is used to hide characters
 * which are not supported by the current protocol version. This is because some servers in older versions are using
 * characters which the client doesn't know about and therefore can't render as placeholder for e.g. scoreboards, we have
 * to hide them because our client would render them as a different character.
 */
public final class RenderableGlyphDiff {

    private static final Int2ObjectMap<ProtocolVersion> RENDERABLE_GLYPH_DIFF_LEGACY = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<ProtocolVersion> RENDERABLE_GLYPH_DIFF = new Int2ObjectOpenHashMap<>();

    public static void init() {
        final CompoundTag glyphs = ViaVersionMappingDataLoader.INSTANCE.loadNBT("renderable-glyphs.nbt");
        fill(glyphs.getCompoundTag("legacy"), RENDERABLE_GLYPH_DIFF_LEGACY);
        fill(glyphs.getCompoundTag("unihex"), RENDERABLE_GLYPH_DIFF);
    }

    private static void fill(final CompoundTag glyphs, final Int2ObjectMap<ProtocolVersion> map) {
        for (final Map.Entry<String, Tag> entry : glyphs) {
            final ProtocolVersion version = ProtocolVersion.getClosest(entry.getKey());
            if (version == null) {
                throw new IllegalStateException("Unknown protocol version: " + entry.getKey());
            }

            final MixedListTag list = (MixedListTag) entry.getValue();
            for (final Tag i : list) {
                if (i instanceof final IntTag intTag) {
                    map.put(intTag.asInt(), version);
                } else if (i instanceof CompoundTag compoundTag) {
                    final int from = compoundTag.getInt("from");
                    final int to = compoundTag.getInt("to");
                    for (int j = from; j <= to; j++) {
                        map.put(j, version);
                    }
                }
            }
        }
    }

    /**
     * @param codePoint the code point to check
     * @return true if the given code point is renderable in the current version of the game
     */
    public static boolean isGlyphRenderable(final int codePoint) {
        final ProtocolVersion targetVersion = ProtocolTranslator.getTargetVersion();

        if (targetVersion.newerThanOrEqualTo(v1_20)) { // 1.20 switched to using Unihex as a main font
            return !RENDERABLE_GLYPH_DIFF.containsKey(codePoint) || targetVersion.newerThanOrEqualTo(RENDERABLE_GLYPH_DIFF.get(codePoint));
        } else {
            return RENDERABLE_GLYPH_DIFF_LEGACY.containsKey(codePoint) && targetVersion.newerThanOrEqualTo(RENDERABLE_GLYPH_DIFF_LEGACY.get(codePoint));
        }
    }

}
