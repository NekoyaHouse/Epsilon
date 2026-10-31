package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.core.BlockPos

class DestroyedBlockEvent(val blockPos: BlockPos) : IEvent<DestroyedBlockEvent>, IPosting by Companion {
    companion object : EventBus()
}
