package com.github.epsilon.events.impl

import com.github.epsilon.events.api.Cancellable
import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

class StartDestroyBlockEvent(var blockPos: BlockPos, var direction: Direction) : Cancellable(),
    IEvent<StartDestroyBlockEvent>, IPosting by Companion {
    companion object : EventBus()
}
