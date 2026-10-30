package com.github.epsilon.events.impl

import com.github.epsilon.events.bus.Cancellable
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

class StartDestroyBlockEvent(var blockPos: BlockPos, var direction: Direction) : Cancellable()
