package me.sofurry.via.injection.mixin;

import com.viaversion.viaversion.libs.fastutil.longs.Long2ObjectMap;
import me.sofurry.via.injection.access.core.bedrock.IChunkTracker;
import net.raphimc.viabedrock.api.chunk.BedrockChunk;
import net.raphimc.viabedrock.protocol.storage.ChunkTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(ChunkTracker.class)
public abstract class MixinChunkTracker implements IChunkTracker {

    @Shadow
    @Final
    private Set<?> subChunkRequests;

    @Shadow
    @Final
    private Set<?> pendingSubChunks;

    @Shadow
    @Final
    private Long2ObjectMap<BedrockChunk> chunks;

    @Override
    public int viaVersion$getSubChunkRequests() {
        return this.subChunkRequests.size();
    }

    @Override
    public int viaVersion$getPendingSubChunks() {
        return this.pendingSubChunks.size();
    }

    @Override
    public int viaVersion$getChunks() {
        return this.chunks.size();
    }

}
