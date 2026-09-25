package com.github.epsilon.modules.orchestration.mining;
import net.minecraft.core.BlockPos;
import java.util.Optional;
public interface MiningCapability {
    Optional<MiningSnapshot> snapshot(BlockPos position);
    MiningDecision request(MiningIntentRequest request);
}
