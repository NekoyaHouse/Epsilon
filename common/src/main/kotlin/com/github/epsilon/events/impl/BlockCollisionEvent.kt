package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class BlockCollisionEvent(var state: BlockState, val pos: BlockPos) : IEvent<BlockCollisionEvent>,
    IPosting by Companion {
    companion object : EventBus()
}
