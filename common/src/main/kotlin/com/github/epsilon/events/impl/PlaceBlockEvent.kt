package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block

class PlaceBlockEvent(val blockPos: BlockPos, val block: Block) : Cancellable(), IEvent<PlaceBlockEvent>,
    IPosting by Companion {
    companion object : EventBus()
}
