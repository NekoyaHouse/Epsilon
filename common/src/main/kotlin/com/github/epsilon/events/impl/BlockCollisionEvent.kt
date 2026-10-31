package com.github.epsilon.events.impl

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class BlockCollisionEvent(var state: BlockState, val pos: BlockPos)
