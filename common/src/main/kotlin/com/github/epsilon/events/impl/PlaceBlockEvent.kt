package com.github.epsilon.events.impl

import com.github.epsilon.events.bus.Cancellable
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block

class PlaceBlockEvent(val blockPos: BlockPos, val block: Block) : Cancellable()
