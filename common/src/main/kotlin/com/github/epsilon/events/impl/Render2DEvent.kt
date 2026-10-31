package com.github.epsilon.events.impl

import com.github.epsilon.events.api.EventBus
import com.github.epsilon.events.api.IEvent
import com.github.epsilon.events.api.IPosting
import net.minecraft.client.gui.GuiGraphicsExtractor

open class Render2DEvent protected constructor(val guiGraphics: GuiGraphicsExtractor) {

    class Level(guiGraphics: GuiGraphicsExtractor) : Render2DEvent(guiGraphics), IEvent<Level>, IPosting by Companion {
        companion object : EventBus()
    }

    class HUD(guiGraphics: GuiGraphicsExtractor) : Render2DEvent(guiGraphics), IEvent<HUD>, IPosting by Companion {
        companion object : EventBus()
    }

}
