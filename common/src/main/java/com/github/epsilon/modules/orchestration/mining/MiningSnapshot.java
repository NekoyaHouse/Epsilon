package com.github.epsilon.modules.orchestration.mining;
import net.minecraft.core.BlockPos;
import java.util.Objects;
public record MiningSnapshot(BlockPos position, MiningKnowledge knowledge, int progressPercent, long revision, String sourceId) {
    public MiningSnapshot { Objects.requireNonNull(position); Objects.requireNonNull(knowledge); Objects.requireNonNull(sourceId); if (progressPercent < 0 || progressPercent > 100) throw new IllegalArgumentException("progressPercent must be 0..100"); }
}
