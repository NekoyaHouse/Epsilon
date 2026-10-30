package com.github.epsilon.events.impl

import com.github.epsilon.events.bus.Cancellable
import net.minecraft.core.BlockPos

class DestroyBlockEvent(val blockPos: BlockPos) : Cancellable()
