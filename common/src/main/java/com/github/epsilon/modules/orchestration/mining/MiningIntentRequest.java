package com.github.epsilon.modules.orchestration.mining;
import net.minecraft.core.BlockPos;
import java.util.Objects;
public record MiningIntentRequest(BlockPos position, MiningIntent intent, int priority, String sourceId, long revision) {
    public MiningIntentRequest { Objects.requireNonNull(position); Objects.requireNonNull(intent); Objects.requireNonNull(sourceId); }
}
