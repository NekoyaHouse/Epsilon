package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.DestroyBlockEvent
import com.github.epsilon.events.impl.PlaceBlockEvent
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module

object NoGhostBlocks : Module("No Ghost Blocks", Category.RENDER) {

    private val breaking by boolSetting("Breaking", true)
    val placing by boolSetting("Placing", true)

    init {
        safeEventHandler<DestroyBlockEvent> { onBreakBlock(it) }
        safeEventHandler<PlaceBlockEvent> { onPlaceBlock(it) }
    }

    private fun onBreakBlock(event: DestroyBlockEvent) {
        if (mc.isLocalServer || !breaking) return

        event.cancel()

        runSafe {
            val blockState = level.getBlockState(event.blockPos)
            blockState.block.playerWillDestroy(level, event.blockPos, blockState, player)
        }
    }

    private fun onPlaceBlock(event: PlaceBlockEvent) {
        if (placing) event.cancel()
    }

}
