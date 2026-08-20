package me.sofurry.via.features.hud;

import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.connection.UserConnection;
import me.sofurry.via.ViaVersionImpl;
import me.sofurry.via.injection.access.core.bedrock.IChunkTracker;
import me.sofurry.via.injection.access.core.bedrock.IRakSessionCodec;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import me.sofurry.via.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.raphimc.viabedrock.protocol.storage.ChunkTracker;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.storage.ExtensionProtocolMetadataStorage;
import net.raphimc.vialegacy.protocol.release.r1_1tor1_2_1_3.storage.SeedStorage;
import net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.storage.EntityTracker;
import org.cloudburstmc.netty.channel.raknet.RakClientChannel;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodec;

import java.util.ArrayList;
import java.util.List;

public final class ViaVersionDebugHudEntry implements DebugScreenEntry {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("viafabricplus", "viafabricplus");

    @Override
    public void display(final DebugScreenDisplayer lines, final Level world, final LevelChunk clientChunk, final LevelChunk chunk) {
        final List<String> information = new ArrayList<>();
        information.add(ChatUtil.PREFIX + ChatFormatting.RESET + " " + ViaVersionImpl.INSTANCE.getVersion());

        final UserConnection connection = ProtocolTranslator.getPlayNetworkUserConnection();
        if (connection == null) {
            lines.addLine(information.getFirst());
            return;
        }

        final ProtocolInfo info = connection.getProtocolInfo();
        information.add("P: " + info.getPipeline().pipes().size() + " C: " + info.protocolVersion() + " S: " + info.serverProtocolVersion());
        final EntityTracker entityTracker1_7_10 = connection.get(EntityTracker.class);
        if (entityTracker1_7_10 != null) {
            information.add("1.7 Entities: " + entityTracker1_7_10.getTrackedEntities().size() + ", Virtual holograms: " + entityTracker1_7_10.getVirtualHolograms().size());
        }
        final SeedStorage seedStorage = connection.get(SeedStorage.class);
        if (seedStorage != null && connection.getProtocolInfo().serverProtocolVersion().newerThanOrEqualTo(LegacyProtocolVersion.a1_2_0toa1_2_1_1)) {
            information.add("World Seed: " + seedStorage.seed);
        }
        final ExtensionProtocolMetadataStorage extensionProtocolMetadataStorage = connection.get(ExtensionProtocolMetadataStorage.class);
        if (extensionProtocolMetadataStorage != null) {
            information.add("CPE extensions: " + extensionProtocolMetadataStorage.getExtensionCount());
        }
        final ChunkTracker chunkTracker = connection.get(ChunkTracker.class);
        if (chunkTracker != null) {
            final IChunkTracker mixinChunkTracker = (IChunkTracker) chunkTracker;
            final int subChunkRequests = mixinChunkTracker.viaVersion$getSubChunkRequests();
            final int pendingSubChunks = mixinChunkTracker.viaVersion$getPendingSubChunks();
            final int chunks = mixinChunkTracker.viaVersion$getChunks();
            information.add("Chunk Tracker: R: " + subChunkRequests + ", P: " + pendingSubChunks + ", C: " + chunks);
        }
        if (connection.getChannel() instanceof RakClientChannel rakClientChannel) {
            final RakSessionCodec rakSessionCodec = rakClientChannel.parent().pipeline().get(RakSessionCodec.class);
            if (rakSessionCodec != null) {
                final IRakSessionCodec mixinRakSessionCodec = (IRakSessionCodec) rakSessionCodec;
                final int transmitQueue = mixinRakSessionCodec.viaVersion$getOutgoingPackets();
                final int retransmitQueue = mixinRakSessionCodec.viaVersion$SentDatagrams();
                information.add("RTT: " + Math.round(rakSessionCodec.getRTT()) + " ms, P: " + rakSessionCodec.getPing() + " ms" + ", TQ: " + transmitQueue + ", RTQ: " + retransmitQueue);
            }
        }
        lines.addToGroup(ID, information);
    }

    @Override
    public boolean isAllowed(final boolean reducedDebugInfo) {
        return true;
    }

}
