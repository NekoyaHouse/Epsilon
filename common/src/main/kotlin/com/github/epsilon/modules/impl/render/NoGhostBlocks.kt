package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.impl.DestroyBlockEvent
import com.github.epsilon.settings.getValue
import com.github.epsilon.events.impl.PlaceBlockEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.utils.player
import com.github.epsilon.utils.runSafe
import com.github.epsilon.utils.world

object NoGhostBlocks : Module("No Ghost Blocks", Category.RENDER) {

    private val breaking by boolSetting("Breaking", true)
    val placing by boolSetting("Placing", true)

    init {
        nonNullListener<DestroyBlockEvent> { onBreakBlock(it) }
        nonNullListener<PlaceBlockEvent> { onPlaceBlock(it) }
    }

    private fun onBreakBlock(event: DestroyBlockEvent) {
        if (mc.isLocalServer() || !breaking) return

        event.cancel()

        runSafe {
            val blockState = world.getBlockState(event.blockPos)
            blockState.block.playerWillDestroy(world, event.blockPos, blockState, player)
        }
    }

    private fun onPlaceBlock(event: PlaceBlockEvent) {
        if (placing) event.cancel()
    }

}
