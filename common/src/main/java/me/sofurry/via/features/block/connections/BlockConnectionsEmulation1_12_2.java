package me.sofurry.via.features.block.connections;

import com.github.epsilon.modules.impl.ClientSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.sofurry.via.protocoltranslator.ProtocolTranslator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;

/**
 * TODO/FIX:
 * - visual artifacts (collision/hitbox is correct, but visually the connection is wrong)
 * - door block place/redstone issue (doors are fine when joining/after updating) -^ RELATED
 * - when stairs are facing a certain way, sometimes fences wont connect to them for some reason (CORNER STAIRS?)
 * - possible inaccuracies missed
 * Nitpicks:
 * - wish it was as instant visually like in older or equal 1.12.2, there is a noticeable delay before blocks connect when placed (not on join)
 *   - Probably due to the update logic being handled in packet-related stuff instead of something native
 */
public final class BlockConnectionsEmulation1_12_2 {

    private static final int UPDATE_FLAGS = 18;
    private static final Object2ObjectOpenHashMap<Class<? extends Block>, IBlockStateHandler> connectionHandlers = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<Class<? extends Block>, IBlockStateHandler> lookupCache = new Object2ObjectOpenHashMap<>();

    public static void init() {
        connectionHandlers.put(DoorBlock.class, new DoorStateHandler());
        connectionHandlers.put(ChestBlock.class, new DoubleChestStateHandler());
        connectionHandlers.put(FenceGateBlock.class, new FenceGateStateHandler());
        connectionHandlers.put(FenceBlock.class, new CrossCollisionStateHandler(state -> state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.getBlock() instanceof SlimeBlock));
        connectionHandlers.put(FireBlock.class, new FireStateHandler());
        connectionHandlers.put(IronBarsBlock.class, new CrossCollisionStateHandler(state -> state.getBlock() instanceof IronBarsBlock));
        connectionHandlers.put(PipeBlock.class, new PipeStateHandler());
        connectionHandlers.put(RepeaterBlock.class, new RedStoneRepeaterStateHandler());
        connectionHandlers.put(RedStoneWireBlock.class, new RedStoneStateHandler());
        connectionHandlers.put(SnowyBlock.class, new SnowyGrassStateHandler());
        connectionHandlers.put(StairBlock.class, new StairsStateHandler());
        connectionHandlers.put(WallBlock.class, new WallStateHandler());
    }

    public static void updateChunkConnections(final LevelReader levelReader, final int chunkX, final int chunkZ) {
        if (!isApplicable() || !levelReader.hasChunk(chunkX, chunkZ)) {
            return;
        }

        final ChunkAccess chunkAccess = levelReader.getChunk(chunkX, chunkZ);
        final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        for (int sectionY = chunkAccess.getMinSectionY(); sectionY < chunkAccess.getMaxSectionY(); sectionY++) {
            final LevelChunkSection section = chunkAccess.getSection(chunkAccess.getSectionIndexFromSectionY(sectionY));
            if (section.hasOnlyAir()) {
                continue;
            }

            final int baseY = SectionPos.sectionToBlockCoord(sectionY);
            for (int x = -1; x <= 16; ++x) {
                final boolean insideX = x >= 0 && x < 16;
                for (int y = baseY; y < baseY + 16; y++) {
                    for (int z = -1; z <= 16; ++z) {
                        blockPos.set(SectionPos.sectionToBlockCoord(chunkX) + x, y, SectionPos.sectionToBlockCoord(chunkZ) + z);
                        if (!levelReader.hasChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()))) {
                            continue;
                        }

                        final BlockState blockState = levelReader.getBlockState(blockPos);
                        if (blockState.isAir()) {
                            continue;
                        }

                        final IBlockStateHandler connectionHandler = getConnectionHandler(blockState.getBlock().getClass());
                        if (connectionHandler == null) {
                            continue;
                        }

                        final BlockState newState = connectionHandler.connect(blockState, levelReader, blockPos);
                        if (newState != blockState && insideX && z >= 0 && z < 16) {
                            chunkAccess.setBlockState(blockPos, newState, UPDATE_FLAGS);
                        }
                    }
                }
            }
        }
    }

    public static void updateChunkNeighborConnections(final LevelReader levelReader, final int chunkX, final int chunkZ) {
        updateChunkConnections(levelReader, chunkX, chunkZ);
        updateChunkConnections(levelReader, chunkX + 1, chunkZ);
        updateChunkConnections(levelReader, chunkX - 1, chunkZ);
        updateChunkConnections(levelReader, chunkX, chunkZ + 1);
        updateChunkConnections(levelReader, chunkX, chunkZ - 1);
    }

    public static void updateChunkNeighborConnections(final LevelReader levelReader, final BlockPos blockPos) {
        updateChunkNeighborConnections(levelReader, SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
    }

    private static IBlockStateHandler getConnectionHandler(final Class<? extends Block> blockClass) {
        return lookupCache.computeIfAbsent(blockClass, clazz -> {
            Class<?> current = (Class<?>) clazz;
            while (current != Block.class && current != null) {
                final IBlockStateHandler handler = connectionHandlers.get(current);
                if (handler != null) {
                    return handler;
                }

                current = current.getSuperclass();
            }

            return null;
        });
    }

    private static boolean isApplicable() {
        if (ClientSetting.INSTANCE.experimentalBlockConnections.getValue()) {
            return ProtocolTranslator.getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) || ProtocolTranslator.getTargetVersion().equals(BedrockProtocolVersion.bedrockLatest);
        } else {
            return false;
        }
    }

}
